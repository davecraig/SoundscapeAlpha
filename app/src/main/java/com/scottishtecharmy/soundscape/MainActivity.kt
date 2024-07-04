package com.scottishtecharmy.soundscape

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.scottishtecharmy.soundscape.services.LocationService
import io.ticofab.androidgpxparser.parser.GPXParser
import io.ticofab.androidgpxparser.parser.domain.Gpx
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.InputStream

class MainActivity : ComponentActivity() {
    // GeoJSON Tile data direct from backend
    val viewModel by viewModels<MainViewModel>()

    private var exampleService: LocationService? = null

    private var serviceBoundState by mutableStateOf(false)
    private var displayableLocation by mutableStateOf<String?>(null)
    private var displayableOrientation by mutableStateOf<String?>(null)
    private var displayableTileString by mutableStateOf<String?>(null)
    private var displayableBeacon by mutableStateOf<String?>(null)

    private var location by mutableStateOf<Location?>(null)
    private var tileXY by mutableStateOf<Pair<Int, Int>?>(null)

    private var intentLatitude: Double = 0.0
    private var intentLongitude: Double = 0.0

    // needed to communicate with the service.
    private val connection = object : ServiceConnection {

        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            // we've bound to ExampleLocationForegroundService, cast the IBinder and get ExampleLocationForegroundService instance.
            Log.d(TAG, "onServiceConnected")

            val binder = service as LocationService.LocalBinder
            exampleService = binder.getService()
            serviceBoundState = true

            onServiceConnected()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            // This is called when the connection with the service has been disconnected. Clean up.
            Log.d(TAG, "onServiceDisconnected")

            serviceBoundState = false
            exampleService = null
        }
    }

    // we need location permission to be able to start the service
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                // Precise location access granted, service can run
                startForegroundService()
            }

            else -> {
                // No location access granted, service can't be started as it will crash
                Toast.makeText(this, "Fine Location permission is required.", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    // we need notification permission to be able to display a notification for the foreground service
    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {
            // Next, get the location permissions
            requestLocationPermissions()
        }

    private fun testGpxParsing() {
        val parser = GPXParser()
        try {
            val input: InputStream = assets.open("Test.gpx")
            val parsedGpx: Gpx? = parser.parse(input)
            parsedGpx?.let {

                parsedGpx.routes.forEach { route ->
                    route.routePoints.forEach { waypoint ->
                        Log.e("gpx", "${waypoint.name}: ${waypoint.latitude}, ${waypoint.longitude} ")
                    }
                }
            } ?: {
                Log.e("gpx", "Error parsing GPX file")
            }
        } catch (e: IOException) {
            Log.e("gpx", "IOException whilst parsing GPX file")
            e.printStackTrace()
        } catch (e: XmlPullParserException) {
            Log.e("gpx", "XmlPullParserException whilst parsing GPX file")
            e.printStackTrace()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        Log.e("version","Version: ${BuildConfig.VERSION_NAME}")

        // Figure out what to do based on the intent type
        if(intent != null) {
            Log.e("intent", intent.data.toString())
            if(intent.data != null) {
                val regex = Regex("geo:([-+]?[0-9]*\\.[0-9]+|[0-9]+),([-+]?[0-9]*\\.[0-9]+|[0-9]+).*")
                val matchResult = regex.find(intent.data.toString())
                if (matchResult != null) {
                    val latitude = matchResult.groupValues[1]
                    val longitude = matchResult.groupValues[2]

                    Log.e("intent","latitude: $latitude")
                    Log.e("intent","longitude: $longitude")

                    // We have a geo intent with a GPS position. Set a beacon at that point
                    intentLatitude = latitude.toDouble()
                    intentLongitude = longitude.toDouble()

                    displayableBeacon = "Beacon: $latitude, $longitude"
                }
            }
        }
        testGpxParsing()

        org.fmod.FMOD.init(applicationContext)

        setContent {
            ForegroundServiceScreen(
                serviceRunning = serviceBoundState,
                currentLocation = displayableLocation,
                beaconLocation = displayableBeacon,
                currentOrientation = displayableOrientation,
                tileString = displayableTileString,
                location = location,
                onClick = ::onStartOrStopForegroundServiceClick
            )
        }

        checkAndRequestPermissions()
        tryToBindToServiceIfRunning()
    }
    override fun onDestroy() {
        Log.d(TAG, "MainActivity OnDestroy")
        super.onDestroy()
        org.fmod.FMOD.close()

        // We leave the foreground service running for now - the user can click on the close button to stop it
        //exampleService?.stopForegroundService()

        unbindService(connection)
    }

    /**
     * Check for notification permission before starting the service so that the notification is visible
     */
    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            )) {
                android.content.pm.PackageManager.PERMISSION_GRANTED -> {
                    // permission already granted
                    startForegroundService()
                }

                else -> {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
        else {
            when (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            )) {
                android.content.pm.PackageManager.PERMISSION_GRANTED -> {
                    // permission already granted
                    startForegroundService()
                }
                else -> {
                    requestLocationPermissions()
                }
            }
        }
    }

    private fun requestLocationPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            locationPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                )
            )
        }
        else {
            locationPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun onStartOrStopForegroundServiceClick() {
        if (exampleService == null) {
            // service is not yet running, start it after permission check
            checkAndRequestPermissions()
        } else {
            // service is already running, stop it
            exampleService?.stopForegroundService()
            exampleService = null
        }
    }

    /**
     * Creates and starts the LocationService as a foreground service.
     *
     * It also tries to bind to the service to update the UI with location updates.
     */
    private fun startForegroundService() {
        // start the service
        startForegroundService(Intent(this, LocationService::class.java))

        // bind to the service to update UI
        tryToBindToServiceIfRunning()
    }

    private fun tryToBindToServiceIfRunning() {
        Intent(this, LocationService::class.java).also { intent ->
            bindService(intent, connection, 0)
        }
    }

    private fun onServiceConnected() {

        if(intentLatitude != 0.0 && intentLongitude != 0.0)
            exampleService?.createBeacon(intentLatitude, intentLongitude)

        lifecycleScope.launch {
            // observe location updates from the service
            exampleService?.locationFlow?.map {
                it?.let { location ->
                    "${location.latitude}, ${location.longitude} (${location.accuracy})"
                }
            }?.collectLatest {
                displayableLocation = it
            }
        }

        lifecycleScope.launch {
            exampleService?.orientationFlow?.map {
                it?.let {
                    orientation ->
                    "Device orientation: ${orientation.headingDegrees}"
                }
            }?.collect {
                displayableOrientation = it
            }
        }

        lifecycleScope.launch {
            delay(10000)
            val test = exampleService?.getTileString()

            println(test)
        }

        lifecycleScope.launch {
            delay(10000)
            val test = exampleService?.getTileStringCaching(application)

            println(test)
        }



    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
