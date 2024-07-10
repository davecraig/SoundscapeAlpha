package com.scottishtecharmy.soundscape.gpx

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.scottishtecharmy.soundscape.services.LocationService
import com.scottishtecharmy.soundscape.ui.theme.SoundscapeAlphaTheme
import io.ticofab.androidgpxparser.parser.GPXParser
import io.ticofab.androidgpxparser.parser.domain.Gpx
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.InputStream

class GpxActivity : ComponentActivity() {
    private lateinit var settingsViewModel: GpxViewModel
    private var foregroundService: LocationService? = null

    private val connection = object : ServiceConnection {

        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            Log.d("gpx", "onServiceConnected")

            val binder = service as LocationService.LocalBinder
            foregroundService = binder.getService()

            setContent {
                SoundscapeAlphaTheme {
                    settingsViewModel.GpxScreen(foregroundService)
                }
            }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            // This is called when the connection with the service has been disconnected. Clean up.
            Log.d("gpx", "onServiceDisconnected")

            foregroundService = null
        }
    }

    private fun tryToBindToServiceIfRunning() {
        Intent(this, LocationService::class.java).also { intent ->
            bindService(intent, connection, 0)
        }
    }
    private fun parseGpxFile(input : InputStream) {
        Log.e("gpx", "Parsing GPX file")
        settingsViewModel.clearWaypoints()
        val parser = GPXParser()
        try {
            val parsedGpx: Gpx? = parser.parse(input)
            parsedGpx?.let {

                // TODO: We're parsing WayPoints and RoutePoints here. We don't really need both,
                //  but for testing it makes it easier.

                parsedGpx.wayPoints.forEach { waypoint ->
                    Log.e("gpx", "Waypoint " + waypoint.name)
                    val wp = Waypoint(waypoint.name, waypoint.latitude, waypoint.longitude)
                    settingsViewModel.addWaypoint(wp)
                }

                parsedGpx.routes.forEach { route ->
                    Log.e("gpx", "Route " + route.routeName)
                    route.routePoints.forEach { waypoint ->
                        val wp = Waypoint(waypoint.name, waypoint.latitude, waypoint.longitude)
                        settingsViewModel.addWaypoint(wp)
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

        tryToBindToServiceIfRunning()

        settingsViewModel = GpxViewModel()
        settingsViewModel.navigateToMainEvent.observe(this) {
            // We're done with the GPX activity, go back to the main activity
            finish()
        }

        val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            // Handle the selected file URI here
            Log.e("gpx", "Open GPX " + uri?.toString())
            uri?.let {
                contentResolver.openInputStream(it)?.use { inputStream ->
                    // Read the file content from the input stream
                    parseGpxFile(inputStream)
                }
            }
        }

        settingsViewModel.openFileEvent.observe(this) {
            getContent.launch("application/gpx+xml")
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        unbindService(connection)
    }
}
