#pragma once

#include <set>
#include <thread>
#include <mutex>
#include "fmod.hpp"
#include "fmod.h"

namespace soundscape {

    class Beacon;
    class AudioEngine {
    public:
        AudioEngine() noexcept;
        ~AudioEngine();

        void updateGeometry(double listenerLatitude, double listenerLongitude, double listenerHeading);
        void setBeaconType(int beaconType);
        FMOD::System * getFmodSystem() { return m_pSystem; };

        void AddBeacon(Beacon *beacon);
        void RemoveBeacon(Beacon *beacon);

    private:
        FMOD::System * m_pSystem;
        FMOD_VECTOR m_LastPos = {0.0f, 0.0f, 0.0f};

        std::recursive_mutex m_BeaconsMutex;
        std::set<Beacon *> m_Beacons;
    };

} // soundscape
