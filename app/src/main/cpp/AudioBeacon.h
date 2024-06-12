#pragma once

#include "AudioBeaconBuffer.h"

namespace soundscape {

    class AudioEngine;
    class Beacon {
    public:
        Beacon(AudioEngine *engine,
               const std::string &filename1,
               const std::string &filename2,
               double latitude, double longitude);

        virtual ~Beacon();

        void updateGeometry(double heading, double latitude, double longitude);

    private:
        // We're going to assume that the beacons are close enough that the earth is effectively flat
        double m_Latitude = 0.0;
        double m_Longitude = 0.0;

        BeaconBufferGroup m_BufferGroup;
        FMOD::System *m_pSystem = nullptr;
        FMOD::Sound *m_pSound = nullptr;
        FMOD::Channel *m_pChannel = nullptr;
        AudioEngine *m_pEngine;
    };
}