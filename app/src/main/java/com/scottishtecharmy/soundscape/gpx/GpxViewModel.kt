package com.scottishtecharmy.soundscape.gpx

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.scottishtecharmy.soundscape.services.LocationService
import io.ticofab.androidgpxparser.parser.domain.RoutePoint

class GpxViewModel {
    private val waypoints = mutableListOf<RoutePoint>()

    private val _navigateToMainEvent = MutableLiveData<Unit>()
    val navigateToMainEvent: LiveData<Unit> = _navigateToMainEvent

    fun addWaypoint(waypoint: RoutePoint) {
        Log.e("gpx", "${waypoint.name}: ${waypoint.latitude}, ${waypoint.longitude} ")
        waypoints.add(waypoint)
    }

    @Composable
    internal fun GpxScreen(foregroundService : LocationService?) {
        Column(
            verticalArrangement = Arrangement.Top,
            modifier = Modifier
                .verticalScroll(rememberScrollState())
        )
        {
            waypoints.forEach { waypoint ->
                Button(
                    onClick = {
                        Log.e(
                            "gpx",
                            "waypointClicked " + waypoint.name + ", set beacon at ${waypoint.latitude}, ${waypoint.longitude}"
                        )
                        foregroundService?.createBeacon(waypoint.latitude, waypoint.longitude)
                        _navigateToMainEvent.value = Unit
                    }
                )
                {
                    Text(waypoint.name)
                }
            }
        }
    }
}

