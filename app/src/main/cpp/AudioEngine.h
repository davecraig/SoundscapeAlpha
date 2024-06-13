#pragma once

#include <set>
#include <thread>
#include <mutex>
#include "fmod.hpp"
#include "fmod.h"
#include "BeaconDescriptor.h"

namespace soundscape {

    class PositionedAudio;
    class AudioEngine {
    public:
        AudioEngine() noexcept;
        ~AudioEngine();

        void UpdateGeometry(double listenerLatitude, double listenerLongitude, double listenerHeading);
        FMOD::System * GetFmodSystem() const { return m_pSystem; };

        void SetBeaconType(int beaconType);
        const BeaconDescriptor *GetBeaconDescriptor() const;

        void AddBeacon(PositionedAudio *beacon);
        void RemoveBeacon(PositionedAudio *beacon);

    private:
        FMOD::System * m_pSystem;
        FMOD_VECTOR m_LastPos = {0.0f, 0.0f, 0.0f};

        std::recursive_mutex m_BeaconsMutex;
        std::set<PositionedAudio *> m_Beacons;
    };

} // soundscape
