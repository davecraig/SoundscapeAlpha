#pragma once

#include "AudioBeaconBuffer.h"

namespace soundscape {

    class AudioEngine;

    class PositionedAudio {
    public:
        PositionedAudio(AudioEngine *engine,
                        double latitude, double longitude);

        virtual ~PositionedAudio();

        void UpdateGeometry(double heading, double latitude, double longitude);

        virtual void CreateAudioSource() = 0;
        bool IsEof() { return m_Eof; }
        void Eof() { m_Eof = true; }

    protected:
        void Init();

        // We're going to assume that the beacons are close enough that the earth is effectively flat
        double m_Latitude = 0.0;
        double m_Longitude = 0.0;

        std::atomic<bool> m_Eof;

        std::unique_ptr<BeaconAudioSource> m_pAudioSource;
        FMOD::System *m_pSystem = nullptr;
        FMOD::Sound *m_pSound = nullptr;
        FMOD::Channel *m_pChannel = nullptr;
        AudioEngine *m_pEngine;
    };

    class Beacon : public PositionedAudio {
    public:
        Beacon(AudioEngine *engine, double latitude, double longitude)
         : PositionedAudio(engine, latitude, longitude)
        {
            Init();
        }

    protected:
        void CreateAudioSource()
        {
            m_pAudioSource = std::make_unique<BeaconBufferGroup>(m_pEngine, this);
        }
    };

    class TextToSpeech : public PositionedAudio {
    public:
        TextToSpeech(AudioEngine *engine, double latitude, double longitude, int tts_socket)
                : m_TtsSocket(tts_socket),
                  PositionedAudio(engine, latitude, longitude)
        {
            Init();
        }

    protected:
        void CreateAudioSource()
        {
            m_pAudioSource = std::make_unique<TtsAudioSource>(m_pEngine, this, m_TtsSocket);
        }

        int m_TtsSocket;
    };
}