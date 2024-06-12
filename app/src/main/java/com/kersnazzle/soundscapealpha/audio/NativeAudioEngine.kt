package com.kersnazzle.soundscapealpha.audio

import android.util.Log

class NativeAudioEngine : AudioEngine {
    private var engineHandle: Long = 0
    private val engineMutex = Object()

    private external fun create() : Long
    private external fun destroy(engineHandle: Long)
    private external fun createNativeBeacon(engineHandle: Long, latitude: Double, longitude: Double) :  Long
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
        }
    }
    fun initialize()
    {
        synchronized(engineMutex) {
            if (engineHandle != 0L) {
                return
            }
            engineHandle = this.create()
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