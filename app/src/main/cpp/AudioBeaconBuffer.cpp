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
BeaconBufferGroup::BeaconBufferGroup(FMOD::System *system,
                  const std::string &filename1,
                  const std::string &filename2)
{
    TRACE("Create BeaconBufferGroup %p", this);
    m_pBuffers[0] = new BeaconBuffer(system, filename1);
    m_pBuffers[1] = new BeaconBuffer(system, filename2);
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

FMOD_RESULT F_CALLBACK BeaconBufferGroup::PcmReadCallback(FMOD_SOUND * sound MAYBE_UNUSED, void *data, unsigned int data_length)
{
    // TODO: Initial switch between sounds based on beacon relative heading
    if((degreesOffAxis > 90) || (degreesOffAxis < -90))
        m_CurrentBuffer = 1;
    else
        m_CurrentBuffer = 0;

    unsigned int bytes_read = m_pBuffers[m_CurrentBuffer]->Read(data, data_length, m_BytePos);
    m_BytePos += bytes_read;
    //TRACE("PcmReadCallback %d: %u @ %lu", m_CurrentBuffer, bytes_read, m_BytePos);

    return FMOD_OK;
}

FMOD_RESULT F_CALLBACK BeaconBufferGroup::StaticPcmReadCallback(FMOD_SOUND* sound, void *data, unsigned int data_length) {
    BeaconBufferGroup *bg;
    ((FMOD::Sound*)sound)->getUserData((void **)&bg);
    bg->PcmReadCallback(sound, data, data_length);

    return FMOD_OK;
}

void BeaconBufferGroup::updateGeometry(double degrees_off_axis, int distance MAYBE_UNUSED)
{
    degreesOffAxis = degrees_off_axis;
}
