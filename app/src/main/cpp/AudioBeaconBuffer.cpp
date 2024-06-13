#include <unistd.h>
#include <thread>
#include <filesystem>
#include <assert.h>
#include <android/log.h>
#include <fcntl.h>
#include "GeoUtils.h"
#include "Trace.h"
#include <cmath>
#include <jni.h>
#include "AudioBeaconBuffer.h"
#include "BeaconDescriptor.h"
#include "AudioBeacon.h"

using namespace soundscape;

BeaconBuffer::BeaconBuffer(FMOD::System *system, const std::string &filename)
{
        FMOD::Sound* sound;

        auto result = system->createSound(filename.c_str(), FMOD_DEFAULT | FMOD_OPENONLY, NULL, &sound);
        ERROR_CHECK(result);

        result = sound->getLength(&m_BufferSize, FMOD_TIMEUNIT_RAWBYTES);
        ERROR_CHECK(result);

        m_pBuffer = (unsigned char *)malloc(m_BufferSize);

        unsigned int bytes_read;
        result = sound->readData(m_pBuffer, m_BufferSize, &bytes_read);
        ERROR_CHECK(result);

        result = sound->release();
        ERROR_CHECK(result);
}

BeaconBuffer::~BeaconBuffer() {
    free(m_pBuffer);
    TRACE("~BeaconBuffer");
}

unsigned int BeaconBuffer::Read(void *data, unsigned int data_length, unsigned long pos) {
    unsigned int remainder = 0;
    unsigned char *dest =(unsigned char *)data;
    pos %= m_BufferSize;
    if((m_BufferSize - pos) < data_length) {
        remainder = data_length - (m_BufferSize - pos);
        data_length = m_BufferSize - pos;
    }
    memcpy(dest, m_pBuffer + pos, data_length);
    if(remainder)
        memcpy(dest + data_length, m_pBuffer + pos + data_length, remainder);

    return data_length;
}

//
//
//
BeaconBufferGroup::BeaconBufferGroup(const AudioEngine *ae, PositionedAudio *parent)
: BeaconAudioSource(parent)
{
    TRACE("Create BeaconBufferGroup %p", this);

    auto bd = ae->GetBeaconDescriptor();

    int index = 0;
    auto system = ae->GetFmodSystem();
    for(const auto &filename: bd->m_Filenames) {
        m_pBuffers[index] = new BeaconBuffer(system, filename);
        ++index;
    }
    m_CurrentBuffer = 0;
}

BeaconBufferGroup::~BeaconBufferGroup()
{
    TRACE("~BeaconBufferGroup %p", this);
    delete m_pBuffers[0];
    delete m_pBuffers[1];
    TRACE("~BeaconBufferGroup done");
}

void BeaconBufferGroup::CreateSound(FMOD::System *system, FMOD::Sound **sound)
{
#define BEAT_COUNT 6        // This is taken from the original Soundscape

    TRACE("BeaconBufferGroup CreateSound %p", this);

    FMOD_CREATESOUNDEXINFO extra_info;
    memset(&extra_info, 0, sizeof(FMOD_CREATESOUNDEXINFO));
    extra_info.cbsize = sizeof(FMOD_CREATESOUNDEXINFO);  /* Required. */
    extra_info.numchannels = 1;
    extra_info.defaultfrequency = 44100;
    extra_info.length = m_pBuffers[0]->GetBufferSize();                         /* Length of PCM data in bytes of whole song (for Sound::getLength) */
    extra_info.decodebuffersize = extra_info.length / (4 *
                                                       BEAT_COUNT);       /* Chunk size of stream update in samples. This will be the amount of data passed to the user callback. */
    extra_info.format = FMOD_SOUND_FORMAT_PCM16;                    /* Data format of sound. */
    extra_info.pcmreadcallback = StaticPcmReadCallback;             /* User callback for reading. */
    extra_info.userdata = this;

    auto result = system->createSound(0,
                                      FMOD_OPENUSER | FMOD_LOOP_NORMAL | FMOD_3D |
                                      FMOD_CREATESTREAM,
                                      &extra_info,
                                      sound);
    ERROR_CHECK(result);
}

FMOD_RESULT F_CALLBACK BeaconBufferGroup::PcmReadCallback(void *data, unsigned int data_length)
{
    // TODO: Initial switch between sounds based on beacon relative heading
    if((degreesOffAxis > 90) || (degreesOffAxis < -90))
        m_CurrentBuffer = 1;
    else
        m_CurrentBuffer = 0;

    unsigned int bytes_read = m_pBuffers[m_CurrentBuffer]->Read(data, data_length, m_BytePos);
    m_BytePos += bytes_read;
    //TRACE("BBG callback %d: %u @ %lu", m_CurrentBuffer, bytes_read, m_BytePos);

    return FMOD_OK;
}

//
//
//

TtsAudioSource::TtsAudioSource(const AudioEngine *ae MAYBE_UNUSED, PositionedAudio *parent, int tts_socket)
              : BeaconAudioSource(parent),
                m_TtsSocket(tts_socket)
{
}

TtsAudioSource::~TtsAudioSource()
{
}

void TtsAudioSource::CreateSound(FMOD::System *system, FMOD::Sound **sound)
{
    FMOD_CREATESOUNDEXINFO extra_info;

    memset(&extra_info, 0, sizeof(FMOD_CREATESOUNDEXINFO));
    extra_info.cbsize = sizeof(FMOD_CREATESOUNDEXINFO);

    extra_info.numchannels = 1;
    extra_info.defaultfrequency = 22050;
    extra_info.length = extra_info.defaultfrequency;
    extra_info.decodebuffersize = extra_info.defaultfrequency / 10;

    extra_info.format = FMOD_SOUND_FORMAT_PCM16;                    /* Data format of sound. */
    extra_info.pcmreadcallback = StaticPcmReadCallback;             /* User callback for reading. */
    extra_info.userdata = this;

    auto result = system->createSound(0,
                                      FMOD_OPENUSER | FMOD_LOOP_OFF | FMOD_3D |
                                      FMOD_CREATESTREAM,
                                      &extra_info,
                                      sound);
    ERROR_CHECK(result);

}
FMOD_RESULT F_CALLBACK TtsAudioSource::PcmReadCallback(void *data, unsigned int data_length)
{
    // The text to speech data is sent over a socket from Kotlin. When the data ends, it
    // currently just results in a short packet but no sign of EOF. The current m_MidStream state
    // is a hack that works on my phone. What's probably required is the Kotlin code to spot the
    // end of the utterance and close its socket. Until I figure that out, this will do.
    // Marking the Beacon as EOF means that next time through the system update loop the Sound
    // will be destroyed.
    // TODO: Close the socket on the Kotlin end when the speech is fully synthesised and handle
    //       that here - obviously we need to read all the data that was written prior to the close

    ssize_t total_bytes_read = 0;
    ssize_t bytes_read;
    unsigned char *write_ptr = (unsigned char *)data;
    while(data_length > 0) {
        bytes_read = read(m_TtsSocket, write_ptr, data_length);
        //TRACE("%p: read %zd/%zd/%u", this, bytes_read, total_bytes_read, data_length);
        if(bytes_read == 0) {
            if(total_bytes_read == 0) {
                TRACE("TTS EOF");
                return FMOD_ERR_FILE_EOF;
            }
            else
                break;
        }
        else if(bytes_read == -1)
            break;

        write_ptr += bytes_read;
        data_length -= bytes_read;
        total_bytes_read += bytes_read;

        if(m_MidStream && (data_length != 0))
            break;
    }

    //TRACE("TTS callback %zd/%u", total_bytes_read, data_length);
    memset(write_ptr, 0, data_length);

    if(!m_MidStream)
    {
        if((bytes_read == total_bytes_read) && (data_length == 0)) {
            m_MidStream = true;
            //TRACE("MID STREAM!");
        }
    }
    else
    {
        if((bytes_read != total_bytes_read) || (data_length != 0)) {
            TRACE("Deduced TTS EOF");
            m_pParent->Eof();
            return FMOD_ERR_FILE_EOF;
        }
    }
    return FMOD_OK;
}


//
//
//
FMOD_RESULT F_CALLBACK BeaconAudioSource::StaticPcmReadCallback(FMOD_SOUND* sound, void *data, unsigned int data_length) {
    BeaconAudioSource *bg;
    ((FMOD::Sound*)sound)->getUserData((void **)&bg);
    bg->PcmReadCallback(data, data_length);

    return FMOD_OK;
}

void BeaconAudioSource::UpdateGeometry(double degrees_off_axis, int distance MAYBE_UNUSED)
{
    degreesOffAxis = degrees_off_axis;
}

