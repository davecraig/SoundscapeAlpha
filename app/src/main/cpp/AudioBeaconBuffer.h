#pragma once

#include "fmod.hpp"
#include "fmod.h"
#include <string>

namespace soundscape {

    class BeaconBuffer {
    public:
        BeaconBuffer(FMOD::System *system, const std::string &filename);

        virtual ~BeaconBuffer();

        unsigned int Read(void *data, unsigned int data_length, unsigned long pos);

        unsigned int GetBufferSize() { return m_BufferSize; }

    private:
        unsigned int m_BufferSize = 0;
        unsigned char *m_pBuffer = nullptr;
    };

    class BeaconBufferGroup {
    public:
        BeaconBufferGroup(FMOD::System *system,
                          const std::string &filename1,
                          const std::string &filename2);

        ~BeaconBufferGroup();

        void CreateSound(FMOD::System *system, FMOD::Sound **sound);

        FMOD_RESULT F_CALLBACK PcmReadCallback(FMOD_SOUND *sound, void *data, unsigned int data_length);

        void updateGeometry(double degrees_off_axis, int distance);

    private:
        static FMOD_RESULT F_CALLBACK
        StaticPcmReadCallback(FMOD_SOUND *sound, void *data, unsigned int data_length);

        BeaconBuffer *m_pBuffers[2];
        int m_CurrentBuffer = -1;
        unsigned long m_BytePos = 0;
        double degreesOffAxis = 0;
    };
}