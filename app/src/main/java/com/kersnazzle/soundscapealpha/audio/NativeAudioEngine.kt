package com.kersnazzle.soundscapealpha.audio

import android.content.Context
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class NativeAudioEngine : AudioEngine, TextToSpeech.OnInitListener {
    private var engineHandle : Long = 0
    private val engineMutex = Object()

    private lateinit var textToSpeech : TextToSpeech
    private lateinit var ttsSocket : ParcelFileDescriptor

    private external fun create() : Long
    private external fun destroy(engineHandle: Long)
    private external fun createNativeBeacon(engineHandle: Long, latitude: Double, longitude: Double) :  Long
    private external fun createNativeTextToSpeech(engineHandle: Long, latitude: Double, longitude: Double, ttsSocket: Int) :  Long
    private external fun updateGeometry(engineHandle: Long, latitude: Double, longitude: Double, heading: Double)
    private external fun setBeaconType(engineHandle: Long, beaconType: Int)

    fun destroy()
    {
        synchronized(engineMutex)
        {
            if (engineHandle == 0L) {
                return
            }
            destroy(engineHandle)
            engineHandle = 0
            textToSpeech.shutdown()
        }
    }
    fun initialize(context : Context)
    {
        synchronized(engineMutex) {
            if (engineHandle != 0L) {
                return
            }
            engineHandle = this.create()
            textToSpeech = TextToSpeech(context, this)
        }
    }

        override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {

            Log.e("TTS", "setLanguage")
            val result = textToSpeech.setLanguage(Locale.UK)

            Log.e("TTS", "setLanguage returned")
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "The Language not supported!")
            }
        }
    }

    override fun createBeacon(latitude: Double, longitude: Double) : Long
    {
        synchronized(engineMutex) {
            if(engineHandle != 0L) {
                Log.d(TAG, "Call createNativeBeacon")
                return createNativeBeacon(engineHandle, latitude, longitude)
            }

            return 0
        }
    }

    override fun createTextToSpeech(latitude: Double, longitude: Double, text: String) : Long
    {
        synchronized(engineMutex) {
            if(engineHandle != 0L) {

                val tts_socket_pair = ParcelFileDescriptor.createSocketPair()
                ttsSocket = tts_socket_pair[0]

                val params = Bundle()
                textToSpeech.synthesizeToFile(text, params, ttsSocket, "")

                Log.d(TAG, "Call createNativeTextToSpeech")
                return createNativeTextToSpeech(engineHandle, latitude, longitude, tts_socket_pair[1].getFd())
            }

            return 0
        }
    }
    override fun updateGeometry(listenerLatitude: Double, listenerLongitude: Double, listenerHeading: Double)
    {
        synchronized(engineMutex) {
            if(engineHandle != 0L)
                updateGeometry(engineHandle, listenerLatitude, listenerLongitude, listenerHeading)
        }
    }
    override fun setBeaconType(beaconType: Int)
    {
    }

    companion object {
        private const val TAG = "NativeAudioEngine"
        init {
            System.loadLibrary("fmod")
            System.loadLibrary("soundscape-audio")
        }
    }
}