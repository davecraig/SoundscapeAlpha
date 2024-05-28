#include "fmod.hpp"
#include "fmod.h"
#include <unistd.h>
#include <thread>
#include <filesystem>
#include <assert.h>
#include <android/log.h>

#define TRACE(...) __android_log_print(ANDROID_LOG_INFO, "soundscape-audio", __VA_ARGS__);
#define ERRCHECK(a) TRACE("line %d, result %d", __LINE__, a);

const float DISTANCEFACTOR = 1.0f;          // Units per meter.  I.e feet would = 3.28.  centimeters would = 100.
const int INTERFACE_UPDATETIME = 50;

static bool running = true;
static std::thread *t1;

/* Cross platform OS Functions internal to the FMOD library, exposed for the example framework. */
extern "C" {
    typedef struct FMOD_OS_FILE            FMOD_OS_FILE;
    typedef struct FMOD_OS_CRITICALSECTION FMOD_OS_CRITICALSECTION;

    FMOD_RESULT F_API FMOD_OS_Time_GetUs(unsigned int *us);
    FMOD_RESULT F_API FMOD_OS_Debug_Output(const char *format, ...);
    FMOD_RESULT F_API FMOD_OS_File_Open(const char *name, int mode, unsigned int *filesize, FMOD_OS_FILE **handle);
    FMOD_RESULT F_API FMOD_OS_File_Close(FMOD_OS_FILE *handle);
    FMOD_RESULT F_API FMOD_OS_File_Read(FMOD_OS_FILE *handle, void *buf, unsigned int count, unsigned int *read);
    FMOD_RESULT F_API FMOD_OS_File_Write(FMOD_OS_FILE *handle, const void *buffer, unsigned int bytesToWrite, bool flush);
    FMOD_RESULT F_API FMOD_OS_File_Seek(FMOD_OS_FILE *handle, unsigned int offset);
    FMOD_RESULT F_API FMOD_OS_Time_Sleep(unsigned int ms);
    FMOD_RESULT F_API FMOD_OS_CriticalSection_Create(FMOD_OS_CRITICALSECTION **crit, bool memorycrit);
    FMOD_RESULT F_API FMOD_OS_CriticalSection_Free(FMOD_OS_CRITICALSECTION *crit, bool memorycrit);
    FMOD_RESULT F_API FMOD_OS_CriticalSection_Enter(FMOD_OS_CRITICALSECTION *crit);
    FMOD_RESULT F_API FMOD_OS_CriticalSection_Leave(FMOD_OS_CRITICALSECTION *crit);
    FMOD_RESULT F_API FMOD_OS_CriticalSection_TryEnter(FMOD_OS_CRITICALSECTION *crit, bool *entered);
    FMOD_RESULT F_API FMOD_OS_CriticalSection_IsLocked(FMOD_OS_CRITICALSECTION *crit, bool *locked);
    FMOD_RESULT F_API FMOD_OS_Thread_Create(const char *name, void (*callback)(void *param), void *param, FMOD_THREAD_AFFINITY affinity, FMOD_THREAD_PRIORITY priority, FMOD_THREAD_STACK_SIZE stacksize, void **handle);
    FMOD_RESULT F_API FMOD_OS_Thread_Destroy(void *handle);
}

class BeaconBuffer {
public:
    BeaconBuffer(FMOD::System *system, const std::string &filename) {
#if 1
    FMOD::Sound* sound;

    auto result = system->createSound(filename.c_str(), FMOD_DEFAULT | FMOD_OPENONLY, NULL, &sound);
    ERRCHECK(result);
/*
    FMOD_SOUND_FORMAT format;
    FMOD_SOUND_TYPE type;
    int bits, channels;
    result = sound->getFormat(&type, &format, &channels, &bits);
    ERRCHECK(result);
*/
    result = sound->getLength(&m_BufferSize, FMOD_TIMEUNIT_RAWBYTES);
    ERRCHECK(result);

    m_pBuffer = (unsigned char *)malloc(m_BufferSize);

    unsigned int bytes_read;
    result = sound->readData(m_pBuffer, m_BufferSize, &bytes_read);
    ERRCHECK(result);

    result = sound->release();
    ERRCHECK(result);
#else
        FMOD_OS_FILE *file = NULL;
        unsigned int bytesread;

        FMOD_OS_File_Open(filename.c_str(), 0, &m_BufferSize, &file);
        m_pBuffer = (char *)malloc(m_BufferSize);
        FMOD_OS_File_Read(file, m_pBuffer, m_BufferSize, &bytesread);
        assert(bytesread == m_BufferSize);
        FMOD_OS_File_Close(file);
#endif
    }

    virtual ~BeaconBuffer() {
        free(m_pBuffer);
    }

    unsigned int Read(void *data, unsigned int datalen, unsigned long pos) {
        unsigned int remainder = 0;
        unsigned char *dest =(unsigned char *)data;
        pos %= m_BufferSize;
        if((m_BufferSize - pos) < datalen) {
            remainder = datalen - (m_BufferSize - pos);
            datalen = m_BufferSize - pos;
        }
        memcpy(dest, m_pBuffer + pos, datalen);
        if(remainder)
            memcpy(dest + datalen, m_pBuffer + pos + datalen, remainder);

        return datalen;
    }

    void * GetBuffer() { return m_pBuffer; }
    unsigned int GetBufferSize() { return m_BufferSize; }

private:
    unsigned int m_BufferSize = 0;
    unsigned char *m_pBuffer = nullptr;
};

class BeaconBufferGroup
{
public:
    BeaconBufferGroup(FMOD::System *system,
                      const std::string &filename1,
                      const std::string &filename2)
                : b1(system, filename1),
                  b2(system, filename2)
    {
    }

    void CreateSound(FMOD::System *system, FMOD::Sound **sound) {
#define BEAT_COUNT 6        // This is taken from the original Soundscape

        FMOD_CREATESOUNDEXINFO exinfo;
        memset(&exinfo, 0, sizeof(FMOD_CREATESOUNDEXINFO));
        exinfo.cbsize = sizeof(FMOD_CREATESOUNDEXINFO);  /* Required. */
        exinfo.numchannels = 1;
        exinfo.defaultfrequency = 44100;
        exinfo.length = b1.GetBufferSize();                         /* Length of PCM data in bytes of whole song (for Sound::getLength) */
        exinfo.decodebuffersize = exinfo.length / (4 * BEAT_COUNT);       /* Chunk size of stream update in samples. This will be the amount of data passed to the user callback. */
        exinfo.format = FMOD_SOUND_FORMAT_PCM16;                    /* Data format of sound. */
        exinfo.pcmreadcallback = StaticPcmReadCallback;             /* User callback for reading. */
        exinfo.userdata = this;

        auto result = system->createSound(0,
                                          FMOD_OPENUSER | FMOD_LOOP_NORMAL | FMOD_3D | FMOD_CREATESTREAM,
                                                &exinfo,
                                                sound);
        ERRCHECK(result);
    }

    FMOD_RESULT F_CALLBACK PcmReadCallback(FMOD_SOUND *sound, void *data, unsigned int datalen) {
        unsigned int bytes_read = m_pCurrentBuffer->Read(data, datalen, m_BytePos);
        TRACE("PcmReadCallback %u @ %lu", bytes_read, m_BytePos);
        m_BytePos += bytes_read;
        ++m_PingPongCount;
        if(m_PingPongCount > 50) {
            m_PingPongCount = 0;
            if(m_pCurrentBuffer == &b1)
                m_pCurrentBuffer = &b2;
            else
                m_pCurrentBuffer = &b1;
            TRACE("PingPong");
        }

        return FMOD_OK;
    }

private:
    static FMOD_RESULT F_CALLBACK StaticPcmReadCallback(FMOD_SOUND* sound, void *data, unsigned int datalen);
    //FMOD_RESULT F_CALLBACK SeekCallback(FMOD::Sound* sound, int subsound, unsigned int position, FMOD_TIMEUNIT postype)    { return FMOD_OK}

    BeaconBuffer b1;
    BeaconBuffer b2;
    BeaconBuffer *m_pCurrentBuffer = &b1;
    unsigned long m_BytePos = 0;
    unsigned int m_PingPongCount  = 0;
};

FMOD_RESULT F_CALLBACK BeaconBufferGroup::StaticPcmReadCallback(FMOD_SOUND* sound, void *data, unsigned int datalen) {
    BeaconBufferGroup *bg;
    ((FMOD::Sound*)sound)->getUserData((void **)&bg);
    bg->PcmReadCallback(sound, data, datalen);

    return FMOD_OK;
}

class Beacon
{
public:
    Beacon(FMOD::System *system, const std::string &filename1, const std::string &filename2)
        : m_BufferGroup(system, filename1, filename2)
    {
        FMOD_RESULT result;

        m_pSystem = system;
        m_BufferGroup.CreateSound(system, &m_pSound);

        result = m_pSound->set3DMinMaxDistance(10.0f * DISTANCEFACTOR, 5000.0f * DISTANCEFACTOR);
        ERRCHECK(result);

        result = m_pSound->setMode(FMOD_LOOP_NORMAL);
        ERRCHECK(result);

        {
            FMOD_VECTOR pos = {-10.0f * DISTANCEFACTOR, 0.0f, 0.0f};
            FMOD_VECTOR vel = {0.0f, 0.0f, 0.0f};

            result = m_pSystem->playSound(m_pSound, 0, false, &m_pChannel);
            ERRCHECK(result);

            result = m_pChannel->set3DAttributes(&pos, &vel);
            ERRCHECK(result);
        }
    }

    virtual ~Beacon() {
        FMOD_RESULT result;

        result = m_pSound->release();
        ERRCHECK(result);
    }


private:
    BeaconBufferGroup m_BufferGroup;
    FMOD::System *m_pSystem = nullptr;
    FMOD::Sound *m_pSound = nullptr;
    FMOD::Channel *m_pChannel = nullptr;
};

static Beacon *b1 = 0;
void main_audio_loop(FMOD::System *system, Beacon *beacon)
{
    FMOD_VECTOR listenerpos = {0.0f, 0.0f, -1.0f * DISTANCEFACTOR};
    FMOD_RESULT result;

    do {
        {
            static float t = 0;
            static FMOD_VECTOR lastpos = {0.0f, 0.0f, 0.0f};
            FMOD_VECTOR forward = {0.0f, 0.0f, 1.0f};
            FMOD_VECTOR up = {0.0f, 1.0f, 0.0f};
            FMOD_VECTOR vel;

            listenerpos.x = (float) sin(t * 0.05f) * 24.0f * DISTANCEFACTOR; // left right pingpong

            // ********* NOTE ******* READ NEXT COMMENT!!!!!
            // vel = how far we moved last FRAME (m/f), then time compensate it to SECONDS (m/s).
            vel.x = (listenerpos.x - lastpos.x) * (1000 / INTERFACE_UPDATETIME);
            vel.y = (listenerpos.y - lastpos.y) * (1000 / INTERFACE_UPDATETIME);
            vel.z = (listenerpos.z - lastpos.z) * (1000 / INTERFACE_UPDATETIME);

            // store pos for next time
            lastpos = listenerpos;

            result = system->set3DListenerAttributes(0, &listenerpos, &vel, &forward, &up);
            //ERRCHECK(result);

            t += (30 * (1.0f /
                        (float) INTERFACE_UPDATETIME));    // t is just a time value .. it increments in 30m/s steps in this example
        }

        result = system->update();

        usleep(50000);

    } while (running);

    ERRCHECK(0);
}

int FMOD_Startup() {
    FMOD::System *system;
    FMOD::Sound *sound1;
    FMOD_RESULT result;
    void *extradriverdata = 0;

    // Create a System object and initialize
    FMOD::System_Create(&system);

    result = system->init(32, FMOD_INIT_NORMAL, extradriverdata);
    ERRCHECK(result);

    result = system->set3DSettings(1.0, DISTANCEFACTOR, 1.0f);
    ERRCHECK(result);

    b1 = new Beacon(system, "file:///android_asset/tactile_on_axis.wav", "file:///android_asset/tactile_behind.wav");
    t1 = new std::thread(main_audio_loop, system, b1);

    return 0;
}

void FMOD_Shutdown()
{
    running = false;
    t1->join();
    delete t1;
    delete b1;

//    result = system->close();
//    ERRCHECK(result);
//    result = system->release();
//    ERRCHECK(result);
}

#include <jni.h>
extern "C" void Java_com_kersnazzle_soundscapealpha_MainActivityKt_fmodmain(JNIEnv *env, jclass thiz)
{
    FMOD_Startup();
}

extern "C" void Java_com_kersnazzle_soundscapealpha_MainActivityKt_fmodstop(JNIEnv *env, jclass thiz)
{
    FMOD_Shutdown();
}
