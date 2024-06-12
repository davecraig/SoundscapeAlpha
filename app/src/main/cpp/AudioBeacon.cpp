#include <string>
#include <jni.h>

#include "GeoUtils.h"
#include "Trace.h"
#include "AudioBeacon.h"
#include "AudioEngine.h"
using namespace soundscape;

Beacon::Beacon(AudioEngine *engine,
       const std::string &filename1,
       const std::string &filename2,
       double latitude, double longitude)
        : m_BufferGroup(engine->getFmodSystem(), filename1, filename2)
{
    FMOD_RESULT result;

    TRACE("%s %p", __FUNCTION__, this);

    m_Latitude = latitude;
    m_Longitude = longitude;

    m_pEngine = engine;
    m_pSystem = engine->getFmodSystem();
    m_BufferGroup.CreateSound(m_pSystem, &m_pSound);

    result = m_pSound->set3DMinMaxDistance(10.0f * FMOD_DISTANCE_FACTOR, 5000.0f * FMOD_DISTANCE_FACTOR);
    ERROR_CHECK(result);

    result = m_pSound->setMode(FMOD_LOOP_NORMAL);
    ERROR_CHECK(result);

    {
        FMOD_VECTOR pos = {(float)m_Longitude, 0.0f, (float)m_Latitude};
        FMOD_VECTOR vel = {0.0f, 0.0f, 0.0f};

        result = m_pSystem->playSound(m_pSound, 0, false, &m_pChannel);
        ERROR_CHECK(result);

        result = m_pChannel->set3DAttributes(&pos, &vel);
        ERROR_CHECK(result);
    }
    m_pEngine->AddBeacon(this);
}

Beacon::~Beacon() {
    TRACE("%s %p", __FUNCTION__, this);
    m_pEngine->RemoveBeacon(this);

    auto result = m_pSound->release();
    ERROR_CHECK(result);

    TRACE("%s %p done", __FUNCTION__, this);
}

void Beacon::updateGeometry(double heading, double latitude, double longitude) {
    // Calculate how far off axis the beacon is given this new heading

    // Calculate the beacon heading
    auto beacon_heading = bearingFromTwoPoints(m_Latitude, m_Longitude, latitude, longitude);
    auto degrees_off_axis = beacon_heading - heading;
    if(degrees_off_axis > 180)
        degrees_off_axis -= 360;
    else if(degrees_off_axis < -180)
        degrees_off_axis += 360;

    int dist = (int)distance(latitude, longitude, m_Latitude, m_Longitude);
    m_BufferGroup.updateGeometry(degrees_off_axis, dist);

    //TRACE("%f %f -> %f (%f %f), %dm", heading, beacon_heading, degrees_off_axis, lat_delta, long_delta, dist)
}
