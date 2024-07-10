package com.scottishtecharmy.soundscape.gpx

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.scottishtecharmy.soundscape.services.LocationService

data class Waypoint(val name: String, val latitude: Double, val longitude: Double)

class GpxViewModel : ViewModel() {

    private val waypoints = mutableStateListOf<Waypoint>()

    private val _navigateToMainEvent = MutableLiveData<Unit>()
    val navigateToMainEvent: LiveData<Unit> = _navigateToMainEvent

    private val _openFileEvent = MutableLiveData<Unit>()
    val openFileEvent: LiveData<Unit> = _openFileEvent

    fun clearWaypoints() {
        waypoints.clear()
    }

    fun addWaypoint(waypoint: Waypoint) {
        Log.e("gpx", "${waypoint.name}: ${waypoint.latitude}, ${waypoint.longitude} ")
        waypoints.add(waypoint)
    }

    @Composable
    fun WaypointItem(waypoint: Waypoint, foregroundService : LocationService?) {
        Column(modifier = Modifier.padding(8.dp).fillMaxWidth()) {
            OutlinedButton(
                onClick = {
                    foregroundService?.createBeacon(waypoint.latitude, waypoint.longitude)
                    _navigateToMainEvent.value = Unit
                }
            )
            {
                Text(waypoint.name)
            }
        }
    }
    @Composable
    internal fun GpxScreen(foregroundService : LocationService?) {
        Column(
            verticalArrangement = Arrangement.Top,
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .fillMaxWidth()
        )
        {
            Button(
                onClick = {
                    _openFileEvent.value = Unit
                }
            )
            {
                Text("Open GPX file")
            }

            LazyColumn(modifier = Modifier.height(2000.dp)) {
                items(waypoints) { waypoint ->
                    WaypointItem(waypoint, foregroundService)
                }
            }
        }
    }
}

