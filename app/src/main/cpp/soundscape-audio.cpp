#include "fmod.hpp"
#include "fmod.h"
#include <unistd.h>
#include <thread>
#include <filesystem>
#include <assert.h>
#include <android/log.h>
#include <fcntl.h>

#define _USE_MATH_DEFINES
#include <cmath>

#define TRACE(...) __android_log_print(ANDROID_LOG_INFO, "soundscape-audio", __VA_ARGS__);
#define ERRCHECK(a) if(a) TRACE("line %d, result %d", __LINE__, a);

const int INTERFACE_UPDATETIME = 50;

static bool running = true;
static std::thread *t1;

//
// C++ versions of GeoUtils functions
//
const double DEGREES_TO_RADIANS = 2.0 * M_PI / 360.0;
const double RADIANS_TO_DEGREES = 1.0 / DEGREES_TO_RADIANS;
const double EARTH_RADIUS_METERS = 6378137.0;   //  Original Soundscape uses 6378137.0 not 6371000.0

// We use this for FMOD coordinates so that we can just pass in straight GPS values as if they
// were X/Y coordinates. We can only do this because we're always close enough to our beacons to
// consider the earth as flat. FMOD_DISTANCEFACTOR is set to the number of metres per degree of
// longitude/latitude.
const float FMOD_DISTANCEFACTOR = (2.0 * M_PI * EARTH_RADIUS_METERS) / 360.0;


static double toRadians(double degrees)
{
    return degrees * DEGREES_TO_RADIANS;
}

static double fromRadians(double degrees)
{
    return degrees * RADIANS_TO_DEGREES;
}

static double bearingFromTwoPoints(
        double lat1, double lon1,
        double lat2, double lon2)
{
    auto latitude1 = toRadians(lat1);
    auto latitude2 = toRadians(lat2);
    auto longDiff = toRadians(lon2 - lon1);
    auto y = sin(longDiff) * cos(latitude2);
    auto x = cos(latitude1) * sin(latitude2) - sin(latitude1) * cos(latitude2) * cos(longDiff);

    return ((int)(fromRadians(atan2(y, x)) + 360) % 360) - 180;
}

static void getDestinationCoordinate(double lat, double lon, double bearing, double distance, double &new_lat, double &new_lon)
{
    auto lat1 = toRadians(lat);
    auto lon1 = toRadians(lon);

    auto d = distance / EARTH_RADIUS_METERS; // Distance in radians

    auto bearingRadians = toRadians(bearing);

    auto lat2 = asin(sin(lat1) * cos(d) + cos(lat1) * sin(d) * cos(bearingRadians));
    auto lon2 = lon1 + atan2(sin(bearingRadians) * sin(d) * cos(lat1),
                                    cos(d) - sin(lat1) * sin(lat2));

    new_lat = fromRadians(lat2);
    new_lon = fromRadians(lon2);
}

double distance(double lat1, double long1, double lat2, double long2)
{
    auto deltaLat = toRadians(lat2 - lat1);
    auto deltaLon = toRadians(long2 - long1);

    auto a =
        sin(deltaLat / 2) * sin(deltaLat / 2) + cos(toRadians(lat1)) * cos(toRadians(lat2)) * sin(
                deltaLon / 2
        ) * sin(
                deltaLon / 2
        );

    auto c = 2 * asin(sqrt(a));

    return (EARTH_RADIUS_METERS * c);
}

//
//
//
class BeaconBuffer {
public:
    BeaconBuffer(FMOD::System *system, const std::string &filename) {
        FMOD::Sound* sound;

        auto result = system->createSound(filename.c_str(), FMOD_DEFAULT | FMOD_OPENONLY, NULL, &sound);
        ERRCHECK(result);

        result = sound->getLength(&m_BufferSize, FMOD_TIMEUNIT_RAWBYTES);
        ERRCHECK(result);

        m_pBuffer = (unsigned char *)malloc(m_BufferSize);

        unsigned int bytes_read;
        result = sound->readData(m_pBuffer, m_BufferSize, &bytes_read);
        ERRCHECK(result);

        result = sound->release();
        ERRCHECK(result);
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

    unsigned int GetBufferSize() { return m_BufferSize; }

private:
    unsigned int m_BufferSize = 0;
    unsigned char *m_pBuffer = nullptr;
};

//
//
//
class BeaconBufferGroup {
public:
    BeaconBufferGroup(FMOD::System *system,
                      const std::string &filename1,
                      const std::string &filename2) {
        m_pBuffers[0] = new BeaconBuffer(system, filename1);
        m_pBuffers[1] = new BeaconBuffer(system, filename2);
        m_CurrentBuffer = 0;
    }

    void CreateSound(FMOD::System *system, FMOD::Sound **sound) {
#define BEAT_COUNT 6        // This is taken from the original Soundscape

        FMOD_CREATESOUNDEXINFO exinfo;
        memset(&exinfo, 0, sizeof(FMOD_CREATESOUNDEXINFO));
        exinfo.cbsize = sizeof(FMOD_CREATESOUNDEXINFO);  /* Required. */
        if (m_pBuffers[0]) {
            exinfo.numchannels = 1;
            exinfo.defaultfrequency = 44100;
            exinfo.length = m_pBuffers[0]->GetBufferSize();                         /* Length of PCM data in bytes of whole song (for Sound::getLength) */
            exinfo.decodebuffersize = exinfo.length / (4 *
                                                       BEAT_COUNT);       /* Chunk size of stream update in samples. This will be the amount of data passed to the user callback. */
        } else {
            exinfo.numchannels = 1;
            exinfo.defaultfrequency = 22050;
            exinfo.length = exinfo.defaultfrequency;
            exinfo.decodebuffersize = exinfo.defaultfrequency / 10;
        }
        exinfo.format = FMOD_SOUND_FORMAT_PCM16;                    /* Data format of sound. */
        exinfo.pcmreadcallback = StaticPcmReadCallback;             /* User callback for reading. */
        exinfo.userdata = this;

        auto result = system->createSound(0,
                                          FMOD_OPENUSER | FMOD_LOOP_NORMAL | FMOD_3D |
                                          FMOD_CREATESTREAM,
                                          &exinfo,
                                          sound);
        ERRCHECK(result);
    }

    FMOD_RESULT F_CALLBACK PcmReadCallback(FMOD_SOUND *sound, void *data, unsigned int datalen) {
        unsigned int bytes_read = 0;
        bytes_read = m_pBuffers[m_CurrentBuffer]->Read(data, datalen, m_BytePos);

        //TRACE("PcmReadCallback %u @ %lu", bytes_read, m_BytePos);
        m_BytePos += bytes_read;
        if((degreesOffAxis > 90) || (degreesOffAxis < -90))
            m_CurrentBuffer = 1;
        else
            m_CurrentBuffer = 0;

        return FMOD_OK;
    }

    void updateGeometry(int degrees_off_axis, int distance) {
        degreesOffAxis = degrees_off_axis;
        distanceFromListener = distance;
    }

private:
    static FMOD_RESULT F_CALLBACK StaticPcmReadCallback(FMOD_SOUND* sound, void *data, unsigned int datalen);
    //FMOD_RESULT F_CALLBACK SeekCallback(FMOD::Sound* sound, int subsound, unsigned int position, FMOD_TIMEUNIT postype)    { return FMOD_OK}

    BeaconBuffer *m_pBuffers[2];
    int m_CurrentBuffer = -1;
    unsigned long m_BytePos = 0;
    int degreesOffAxis = 0;
    int distanceFromListener = 0;
};

FMOD_RESULT F_CALLBACK BeaconBufferGroup::StaticPcmReadCallback(FMOD_SOUND* sound, void *data, unsigned int datalen) {
    BeaconBufferGroup *bg;
    ((FMOD::Sound*)sound)->getUserData((void **)&bg);
    bg->PcmReadCallback(sound, data, datalen);

    return FMOD_OK;
}

//
//
//
class Beacon
{
public:
    Beacon(FMOD::System *system,
           const std::string &filename1,
           const std::string &filename2,
           double latitude, double longitude)
        : m_BufferGroup(system, filename1, filename2)
    {
        FMOD_RESULT result;

        m_Latitude = latitude;
        m_Longitude = longitude;

        m_pSystem = system;
        m_BufferGroup.CreateSound(system, &m_pSound);

        result = m_pSound->set3DMinMaxDistance(10.0f * FMOD_DISTANCEFACTOR, 5000.0f * FMOD_DISTANCEFACTOR);
        ERRCHECK(result);

        result = m_pSound->setMode(FMOD_LOOP_NORMAL);
        ERRCHECK(result);

        {
            FMOD_VECTOR pos = {(float)m_Longitude, 0.0f, (float)m_Latitude};
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

    void updateGeometry(int heading, double latitude, double longitude) {
        // Calculate how far off axis the beacon is given this new heading

        // Calculate the beacon heading
        auto long_delta = longitude  - m_Longitude;
        auto lat_delta = latitude  - m_Latitude;
        auto beacon_heading = (int)bearingFromTwoPoints(m_Latitude, m_Longitude, latitude, longitude);
        auto degrees_off_axis = beacon_heading - heading;
        if(degrees_off_axis > 180)
            degrees_off_axis -= 360;
        else if(degrees_off_axis < -180)
            degrees_off_axis += 360;

        int dist = (int)distance(latitude, longitude, m_Latitude, m_Longitude);
        m_BufferGroup.updateGeometry(degrees_off_axis, dist);

        TRACE("%d %d -> %d (%f %f), %dm", heading, beacon_heading, degrees_off_axis, lat_delta, long_delta, dist)
    }

private:
    // We're going to assume that the beacons are close enough that the earth is effectively flat
    double m_Latitude = 0.0;
    double m_Longitude = 0.0;

    BeaconBufferGroup m_BufferGroup;
    FMOD::System *m_pSystem = nullptr;
    FMOD::Sound *m_pSound = nullptr;
    FMOD::Channel *m_pChannel = nullptr;
};

static Beacon *audio_beacon = 0;
static Beacon *tts_beacon = 0;
static int listener_heading = 0;
static double listener_latitude = 0.0;
static double listener_longitude = 0.0;
void main_audio_loop(FMOD::System *system)
{
    FMOD_VECTOR listenerpos = {0.0f, 0.0f, 0.0f};
    FMOD_RESULT result;

    do {
        {
            static float t = 0;
            static FMOD_VECTOR lastpos = {0.0f, 0.0f, 0.0f};
            FMOD_VECTOR forward = {0.0f, 0.0f, 1.0f};
            FMOD_VECTOR up = {0.0f, 1.0f, 0.0f};
            FMOD_VECTOR vel =  {0.0f, 0.0f, 0.0f};

            // Set listener position
            listenerpos.x = listener_longitude;
            listenerpos.z = listener_latitude;

            // ********* NOTE ******* READ NEXT COMMENT!!!!!
            // vel = how far we moved last FRAME (m/f), then time compensate it to SECONDS (m/s).
            vel.x = (listenerpos.x - lastpos.x) * (1000 / INTERFACE_UPDATETIME);
            vel.y = (listenerpos.y - lastpos.y) * (1000 / INTERFACE_UPDATETIME);
            vel.z = (listenerpos.z - lastpos.z) * (1000 / INTERFACE_UPDATETIME);

            // store pos for next time
            lastpos = listenerpos;

            // Set listener direction
            float rads = (listener_heading * M_PI) / 180.0;
            forward.z = cos(rads);
            forward.x = sin(rads);

            if((listener_latitude != 0.0) && !audio_beacon) {
                // First valid location data that we have received so create a test beacon 100m
                // from here at a bearing of 45 degrees
                double beacon_latitude;
                double beacon_longitude;
                getDestinationCoordinate(listener_latitude, listener_longitude,
                                         45, 100,
                                         beacon_latitude, beacon_longitude);

                audio_beacon = new Beacon(system,
                                          "file:///android_asset/tactile_on_axis.wav",
                                          "file:///android_asset/tactile_behind.wav",
                                          beacon_latitude, beacon_longitude);
            }
            if(audio_beacon)
                audio_beacon->updateGeometry(listener_heading, listener_latitude, listener_longitude);

            result = system->set3DListenerAttributes(0, &listenerpos, &vel, &forward, &up);
            ERRCHECK(result);

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

    result = system->setSoftwareFormat(22050, FMOD_SPEAKERMODE_SURROUND, 0);
    ERRCHECK(result);

    result = system->init(32, FMOD_INIT_NORMAL, extradriverdata);
    ERRCHECK(result);

    result = system->set3DSettings(1.0, FMOD_DISTANCEFACTOR, 1.0f);
    ERRCHECK(result);

    t1 = new std::thread(main_audio_loop, system);

    return 0;
}

void FMOD_Shutdown()
{
    running = false;
    t1->join();
    delete t1;
    delete audio_beacon;

//    result = system->close();
//    ERRCHECK(result);
//    result = system->release();
//    ERRCHECK(result);
}

#include <jni.h>
extern "C" void Java_com_kersnazzle_soundscapealpha_services_LocationServiceKt_fmodStart(JNIEnv *env, jclass thiz)
{
    FMOD_Startup();
}

extern "C" void Java_com_kersnazzle_soundscapealpha_services_LocationServiceKt_fmodStop(JNIEnv *env, jclass thiz)
{
    FMOD_Shutdown();
}

extern "C" void Java_com_kersnazzle_soundscapealpha_services_LocationServiceKt_updateHeading(JNIEnv *env, jclass thiz, jint heading)
{
    listener_heading = heading;
}

extern "C" void Java_com_kersnazzle_soundscapealpha_services_LocationServiceKt_updateLocation(JNIEnv *env, jclass thiz, jfloat latitude, jfloat longitude)
{
    listener_latitude = latitude;
    listener_longitude = longitude;
}
