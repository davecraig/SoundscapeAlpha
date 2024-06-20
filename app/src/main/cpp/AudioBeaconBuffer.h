#pragma once

#include "fmod.hpp"
#include "fmod.h"
#include <string>
#include <atomic>

#include "AudioEngine.h"

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

    class BeaconAudioSource {
    public:
        BeaconAudioSource(PositionedAudio *parent) : m_pParent(parent) {}
        virtual ~BeaconAudioSource() {}

        virtual void CreateSound(FMOD::System *system, FMOD::Sound **sound) = 0;
        virtual FMOD_RESULT F_CALLBACK PcmReadCallback(void *data, unsigned int data_length) = 0;

        void UpdateGeometry(double degrees_off_axis, int distance);

    protected:
        PositionedAudio *m_pParent;

        static FMOD_RESULT F_CALLBACK
        StaticPcmReadCallback(FMOD_SOUND *sound, void *data, unsigned int data_length);

        std::atomic<double> degreesOffAxis;
    };

    class BeaconBufferGroup : public BeaconAudioSource {
    public:
        BeaconBufferGroup(const AudioEngine *ae, PositionedAudio *parent);
        virtual ~BeaconBufferGroup();

        virtual void CreateSound(FMOD::System *system, FMOD::Sound **sound);
        virtual FMOD_RESULT F_CALLBACK PcmReadCallback(void *data, unsigned int data_length);

    private:
        BeaconBuffer *m_pBuffers[2];
        int m_CurrentBuffer = -1;
        unsigned long m_BytePos = 0;
    };

    class TtsAudioSource : public BeaconAudioSource {
    public:
        TtsAudioSource(const AudioEngine *ae, PositionedAudio *parent, int tts_socket);
        virtual ~TtsAudioSource();

        virtual void CreateSound(FMOD::System *system, FMOD::Sound **sound);
        virtual FMOD_RESULT F_CALLBACK PcmReadCallback(void *data, unsigned int data_length);

    private:
        int m_TtsSocket;
        int m_ReadsWithoutData = 0;
    };

}