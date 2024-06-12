package com.kersnazzle.soundscapealpha.audio

interface AudioEngine {
    fun createBeacon(latitude: Double, longitude: Double) : Long
    fun updateGeometry(listenerLatitude: Double, listenerLongitude: Double, listenerHeading: Double)
    fun setBeaconType(beaconType: Int)
}