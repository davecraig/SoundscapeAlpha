package com.scottishtecharmy.soundscape

import android.location.Location
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt
import com.scottishtecharmy.soundscape.ui.theme.ForegroundServiceTheme

@Composable
internal fun ForegroundServiceScreen(
    serviceRunning: Boolean,
    tileString: String?,
    currentLocation: Location?,
    currentHeading: Float,
    beaconLocation: LngLatAlt?,
    onServiceClick: () -> Unit,
    onGpxClick: () -> Unit,
    modifier: Modifier = Modifier
) {

    ForegroundServiceTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            ForegroundServiceSampleScreenContent(
                serviceRunning = serviceRunning,
                currentLocation = currentLocation,
                currentHeading = currentHeading,
                beaconLocation = beaconLocation,
                onServiceClick = onServiceClick,
                onGpxClick = onGpxClick,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun ForegroundServiceSampleScreenContent(
    serviceRunning: Boolean,
    currentLocation: Location?,
    currentHeading: Float,
    beaconLocation: LngLatAlt?,
    onServiceClick: () -> Unit,
    onGpxClick: () -> Unit,
    modifier: Modifier = Modifier
) {

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Row {
                val cameraPositionState = rememberCameraPositionState()
                if(currentLocation != null)
                    cameraPositionState.position = CameraPosition(LatLng(currentLocation.latitude, currentLocation.longitude), 15f, 45f, currentHeading)

                val markerState = rememberMarkerState()
                if(beaconLocation != null)
                    markerState.position = LatLng(beaconLocation.latitude, beaconLocation.longitude)

                GoogleMap(
                    modifier = Modifier.height(550.dp),
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(isMyLocationEnabled = true)
                )
                {
                    Marker(state = markerState, title = "Beacon")
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                ServiceStatusContent(
                    serviceRunning = serviceRunning,
                    onClick = onServiceClick
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                LocationUpdate(
                    visible = serviceRunning,
                    heading = currentHeading
                )
                Button(
                    onClick = onGpxClick
                ) {
                    Text(text = "GPX")
                }
                Text(
                    text = "v" + BuildConfig.VERSION_NAME,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun ServiceStatusContent(
    serviceRunning: Boolean,
    onClick: () -> Unit
) {
    Spacer(modifier = Modifier.height(16.dp))

    Button(onClick = onClick) {
        Text(
            text = stringResource(
                id = if (serviceRunning) {
                    R.string.foreground_service_sample_button_stop
                } else {
                    R.string.foreground_service_sample_button_start
                }
            )
        )
    }
}

@Composable
private fun LocationUpdate(
    visible: Boolean,
    heading: Float,
) {
    if (visible) {
        Text(
            text = "Heading: $heading"
        )
    }
}