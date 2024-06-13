#include "AudioEngine.h"
#include "AudioBeacon.h"
#include "GeoUtils.h"
#include "Trace.h"

#include <thread>
#include <memory>
#include <mutex>
#include <android/log.h>
#include <jni.h>

namespace soundscape {

    AudioEngine::AudioEngine() noexcept {
        FMOD_RESULT result;

        TRACE("%s %p", __FUNCTION__, this);

        // Create a System object and initialize
        FMOD::System *system;
        FMOD::System_Create(&system);
        m_pSystem = system;

        result = m_pSystem->setSoftwareFormat(22050, FMOD_SPEAKERMODE_SURROUND, 0);
        ERROR_CHECK(result);

        result = m_pSystem->init(32, FMOD_INIT_NORMAL, 0);
        ERROR_CHECK(result);

        result = m_pSystem->set3DSettings(1.0, FMOD_DISTANCE_FACTOR, 1.0f);
        ERROR_CHECK(result);
    }

    AudioEngine::~AudioEngine() {

        TRACE("%s %p", __FUNCTION__, this);

        {
            std::lock_guard<std::recursive_mutex> guard(m_BeaconsMutex);
            // Deleting the PositionedAudio calls RemoveBeacon which removes it from m_Beacons
            while(!m_Beacons.empty())
            {
                delete *m_Beacons.begin();
            }
        }

        TRACE("System release");
        auto result = m_pSystem->release();
        ERROR_CHECK(result);
        m_pSystem = 0;
    }

    void
    AudioEngine::UpdateGeometry(double listenerLatitude, double listenerLongitude,
                                double listenerHeading) {
        const FMOD_VECTOR up = {0.0f, 1.0f, 0.0f};

        // Set listener position
        FMOD_VECTOR listener_position;
        listener_position.x = listenerLongitude;
        listener_position.y = 0.0f;
        listener_position.z = listenerLatitude;

        // ********* NOTE ******* READ NEXT COMMENT!!!!!
        // vel = how far we moved last FRAME (m/f), then time compensate it to SECONDS (m/s).
        // TODO: replace INTERFACE_UPDATE_TIME with calculated time difference
        const int INTERFACE_UPDATE_TIME = 50;

        FMOD_VECTOR vel;
        vel.x = (listener_position.x - m_LastPos.x) * (1000 / INTERFACE_UPDATE_TIME);
        vel.y = (listener_position.y - m_LastPos.y) * (1000 / INTERFACE_UPDATE_TIME);
        vel.z = (listener_position.z - m_LastPos.z) * (1000 / INTERFACE_UPDATE_TIME);

        // store pos for next time
        m_LastPos = listener_position;

        // Set listener direction
        float rads = (listenerHeading * M_PI) / 180.0;
        FMOD_VECTOR forward = {sin(rads), 0.0f, cos(rads)};

        //TRACE("heading: %d %f, %f %f", heading, rads, forward.x, forward.z)
        {
            std::lock_guard<std::recursive_mutex> guard(m_BeaconsMutex);
            auto it = m_Beacons.begin();
            while(it != m_Beacons.end()) {
                if((*it)->IsEof()) {
                    TRACE("Remove EOF beacon");
                    delete *it;
                    it = m_Beacons.begin();
                    continue;
                }

                (*it)->UpdateGeometry(listenerHeading, listenerLatitude, listenerLongitude);
                ++it;
            }
        }

        auto result = m_pSystem->set3DListenerAttributes(0, &listener_position, &vel, &forward, &up);
        ERROR_CHECK(result);

        result = m_pSystem->update();
        ERROR_CHECK(result);
    }

    void AudioEngine::SetBeaconType(int beaconType)
    {
        TRACE("BeaconType: %d", beaconType);
    }

    const BeaconDescriptor *AudioEngine::GetBeaconDescriptor() const
    {
        const static BeaconDescriptor bd({ "file:///android_asset/tactile_on_axis.wav", "file:///android_asset/tactile_behind.wav" });

        return &bd;
    }

    void AudioEngine::AddBeacon(PositionedAudio *beacon)
    {
        std::lock_guard<std::recursive_mutex> guard(m_BeaconsMutex);
        m_Beacons.insert(beacon);
        TRACE("AddBeacon -> %d beacons", m_Beacons.size());
    }

    void AudioEngine::RemoveBeacon(PositionedAudio *beacon)
    {
        std::lock_guard<std::recursive_mutex> guard(m_BeaconsMutex);
        m_Beacons.erase(beacon);

        TRACE("RemoveBeacon -> %d beacons", m_Beacons.size());
    }



} // soundscape

extern "C"
JNIEXPORT jlong JNICALL
Java_com_kersnazzle_soundscapealpha_audio_NativeAudioEngine_create__(JNIEnv *env MAYBE_UNUSED, jobject thiz MAYBE_UNUSED) {
    auto ae = std::make_unique<soundscape::AudioEngine>();

    if (not ae) {
        TRACE("Failed to create audio engine");
        ae.reset(nullptr);
    }

    return reinterpret_cast<jlong>(ae.release());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_kersnazzle_soundscapealpha_audio_NativeAudioEngine_destroy__J(JNIEnv *env MAYBE_UNUSED,
                                                                       jobject thiz MAYBE_UNUSED,
                                                                       jlong engine_handle) {
    auto* ae =
            reinterpret_cast<soundscape::AudioEngine*>(engine_handle);
    if(ae)
        delete ae;
    else
        TRACE("destroy failed - no AudioEngine");
}

extern "C"
JNIEXPORT void JNICALL
Java_com_kersnazzle_soundscapealpha_audio_NativeAudioEngine_updateGeometry(JNIEnv *env MAYBE_UNUSED,
                                                                           jobject thiz MAYBE_UNUSED,
                                                                           jlong engine_handle,
                                                                           jdouble latitude,
                                                                           jdouble longitude,
                                                                           jdouble heading) {
    auto* ae =
            reinterpret_cast<soundscape::AudioEngine*>(engine_handle);

    if (ae) {
        ae->UpdateGeometry(latitude, longitude, heading);
    } else {
        TRACE("UpdateGeometry failed - no AudioEngine");
    }
}
extern "C"
JNIEXPORT void JNICALL
Java_com_kersnazzle_soundscapealpha_audio_NativeAudioEngine_setBeaconType(JNIEnv *env MAYBE_UNUSED, jobject thiz MAYBE_UNUSED,
                                                                          jlong engine_handle,
                                                                          jint beacon_type) {
    auto* ae =
            reinterpret_cast<soundscape::AudioEngine*>(engine_handle);

    if (ae) {
        ae->SetBeaconType(beacon_type);
    } else {
        TRACE("SetBeaconType failed - no AudioEngine");
    }
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_kersnazzle_soundscapealpha_audio_NativeAudioEngine_createNativeBeacon(JNIEnv *env MAYBE_UNUSED,
                                                                               jobject thiz MAYBE_UNUSED,
                                                                               jlong engine_handle,
                                                                               jdouble latitude,
                                                                               jdouble longitude) {
    auto* ae = reinterpret_cast<soundscape::AudioEngine*>(engine_handle);
    if(ae) {

        auto beacon = std::make_unique<soundscape::Beacon>(ae, latitude, longitude);
        if (not beacon) {
            TRACE("Failed to create audio beacon");
            beacon.reset(nullptr);
        }
        return reinterpret_cast<jlong>(beacon.release());
    }
    return 0L;
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_kersnazzle_soundscapealpha_audio_NativeAudioEngine_createNativeTextToSpeech(JNIEnv *env MAYBE_UNUSED,
                                                                                     jobject thiz MAYBE_UNUSED,
                                                                                     jlong engine_handle,
                                                                                     jdouble latitude,
                                                                                     jdouble longitude,
                                                                                     jint tts_socket) {
    auto* ae = reinterpret_cast<soundscape::AudioEngine*>(engine_handle);
    if(ae) {

        auto tts = std::make_unique<soundscape::TextToSpeech>(ae, latitude, longitude, tts_socket);
        if (not tts) {
            TRACE("Failed to create text to speech");
            tts.reset(nullptr);
        }
        return reinterpret_cast<jlong>(tts.release());
    }
    return 0L;
}