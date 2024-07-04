package com.scottishtecharmy.soundscape.gpx

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.scottishtecharmy.soundscape.MainActivity
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
    private fun parseGpxFile() {
        val parser = GPXParser()
        try {
            val input: InputStream = assets.open("Test.gpx")
            val parsedGpx: Gpx? = parser.parse(input)
            parsedGpx?.let {

                parsedGpx.routes.forEach { route ->
                    route.routePoints.forEach { waypoint ->
                        settingsViewModel.addWaypoint(waypoint)
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
        parseGpxFile()

        settingsViewModel.navigateToMainEvent.observe(this) {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        unbindService(connection)
    }
}
