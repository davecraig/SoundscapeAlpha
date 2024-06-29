package com.scottishtecharmy.soundscape.services


import android.Manifest.permission
import android.annotation.SuppressLint
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.DeviceOrientation
import com.google.android.gms.location.DeviceOrientationListener
import com.google.android.gms.location.DeviceOrientationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.FusedOrientationProviderClient
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.scottishtecharmy.soundscape.R
import com.scottishtecharmy.soundscape.audio.NativeAudioEngine
import com.scottishtecharmy.soundscape.network.ITileDAO
import com.scottishtecharmy.soundscape.network.OkhttpClientInstance
import com.scottishtecharmy.soundscape.network.RetrofitClientInstance
import com.scottishtecharmy.soundscape.utils.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.awaitResponse
import java.util.concurrent.Executors
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


/**
 * Simple foreground service that shows a notification to the user and provides location updates.
 */
class LocationService : Service() {
    private val binder = LocalBinder()

    private val coroutineScope = CoroutineScope(Job())
    // core GPS service
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    // core Orientation service - test
    private lateinit var fusedOrientationProviderClient: FusedOrientationProviderClient
    private lateinit var listener: DeviceOrientationListener

    private val audioEngine = NativeAudioEngine()
    private var audioBeacon: Long = 0

    // secondary service
    private var timerJob: Job? = null

    // Flow to return Location objects
    private val _locationFlow = MutableStateFlow<Location?>(null)
    var locationFlow: StateFlow<Location?> = _locationFlow

    // Flow to return DeviceOrientation objects
    private val _orientationFlow = MutableStateFlow<DeviceOrientation?>(null)
    var orientationFlow: StateFlow<DeviceOrientation?> = _orientationFlow



    // Binder to allow local clients to Bind to our service
    inner class LocalBinder : Binder() {
        fun getService(): LocationService = this@LocationService
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "onBind")
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")

        startAsForegroundService()
        startLocationUpdates()

        // test
        startOrientationUpdates()

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")

        Toast.makeText(this, "Foreground Service created", Toast.LENGTH_SHORT).show()

        // Set up the location updates using the FusedLocationProviderClient but doesn't start them
        setupLocationUpdates()

        // Start the orientation updates using the FusedOrientationProviderClient - test
        startOrientationUpdates()

        // Start secondary service
        //startServiceRunningTicker()

        // Start audio engine
        audioEngine.initialize(applicationContext)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")

        audioEngine.destroy()

        fusedLocationClient.removeLocationUpdates(locationCallback)

        fusedOrientationProviderClient.removeOrientationUpdates(listener)

        timerJob?.cancel()
        coroutineScope.coroutineContext.cancelChildren()

        Toast.makeText(this, "Foreground Service destroyed", Toast.LENGTH_SHORT).show()
    }

    /**
     * Promotes the service to a foreground service, showing a notification to the user.
     *
     * This needs to be called within 10 seconds of starting the service or the system will throw an exception.
     */
    private fun startAsForegroundService() {

        // promote service to foreground service
        // FOREGROUND_SERVICE_TYPE_LOCATION needs to be in AndroidManifest.xml
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            getNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        )
    }

    /**
     * Stops the foreground service and removes the notification.
     * Can be called from inside or outside the service.
     */
    fun stopForegroundService() {
        stopSelf()
    }

    /**
     * Sets up the location updates using the FusedLocationProviderClient, but doesn't actually start them.
     * To start the location updates, call [startLocationUpdates].
     */
    private fun setupLocationUpdates() {
        if (ContextCompat.checkSelfPermission(
                this.applicationContext, permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            if (ContextCompat.checkSelfPermission(
                    this.applicationContext, permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { location: Location? ->
                        // Handle the retrieved location here
                        if (location != null) {
                            _locationFlow.value = location
                        }
                    }
                    .addOnFailureListener { exception: Exception ->
                    }
            }
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    _locationFlow.value = location
                }
            }
        }
    }

    private fun startOrientationUpdates(){

        fusedOrientationProviderClient =
            LocationServices.getFusedOrientationProviderClient(this)

        /*listener = DeviceOrientationListener { orientation: DeviceOrientation ->
                    // Use the orientation object

                    Log.d(TAG, "Device Orientation: ${orientation.headingDegrees} deg")
                }*/
        listener = DeviceOrientationListener { orientation ->
            _orientationFlow.value = orientation  // Emit the DeviceOrientation object
            val location = locationFlow.value
            if(location != null) {
                audioEngine.updateGeometry(
                    location.latitude,
                    location.longitude,
                    orientation.headingDegrees.toDouble()
                )
            }
        }

        // OUTPUT_PERIOD_DEFAULT = 50Hz / 20ms:
        val request = DeviceOrientationRequest.Builder(DeviceOrientationRequest.OUTPUT_PERIOD_DEFAULT).build()
        // Thought I could use a Looper here like for location but it seems to want an Executor instead
        // Not clear on what the difference is...
        val executor = Executors.newSingleThreadExecutor()
        fusedOrientationProviderClient.requestOrientationUpdates(request, executor, listener)
    }


    /**
     * Starts the location updates using the FusedLocationProviderClient.
     * Suppressing IDE warning with annotation. Will check for this in UI.
     *  TODO: Add permission checks and observe for permission changes by user
     */
    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        fusedLocationClient.requestLocationUpdates(
            LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                LOCATION_UPDATES_INTERVAL_MS
            ).apply {
                setMinUpdateDistanceMeters(1f)
                setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
                setWaitForAccurateLocation(true)
            }.build(),
            locationCallback,
            Looper.getMainLooper(),
        )
    }

    /**
     * Starts a ticker that shows a toast every [TICKER_PERIOD_SECONDS] seconds to indicate that the service is still running.
     */
    private fun startServiceRunningTicker() {
        timerJob?.cancel()
        timerJob = coroutineScope.launch {
            tickerFlow()
                .collectLatest {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@LocationService,
                            "Foreground Service still running.",
                            Toast.LENGTH_SHORT
                        ).show()
                        //audioEngine.createTextToSpeech(-90.0, 0.0, "Located")
                        //audioEngine.createTextToSpeech(90.0, 0.0, "speech test")
                    }
                }
        }
    }

    private fun tickerFlow(
        period: Duration = TICKER_PERIOD_SECONDS,
        initialDelay: Duration = TICKER_PERIOD_SECONDS
    ) = flow {
        delay(initialDelay)
        while (true) {
            emit(Unit)
            delay(period)
        }
    }

    private fun getNotification(): Notification {
        createServiceNotificationChannel()

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)

        return builder.build()
    }

    private fun createServiceNotificationChannel() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(channel)
    }

    // testing out basic network connection with Retrofit and cleaning the tile
    suspend fun getTileString(): String? {

        val tileXY = _locationFlow.value?.let { getXYTile(it.latitude, _locationFlow.value!!.longitude) }

        return withContext(Dispatchers.IO) {
            val service = RetrofitClientInstance.retrofitInstance?.create(ITileDAO::class.java)

            val tile = async { tileXY?.let { service?.getTile(it.first, tileXY.second) } }
            val result = tile.await()?.awaitResponse()?.body()
            return@withContext result?.let<String, String> {
                tileXY?.let { it1 ->
                    cleanTileGeoJSON(
                        it1.first, tileXY.second, 16.0, it)
                }.toString()
            }
        }
    }

    suspend fun getTileStringCaching(application: Application): String? {
        val tileXY = _locationFlow.value?.let { getXYTile(it.latitude, _locationFlow.value!!.longitude) }

        val okhttpClientInstance = OkhttpClientInstance(application)
        return withContext(Dispatchers.IO) {

            val service = okhttpClientInstance.retrofitInstance?.create(ITileDAO::class.java)
            val tile = async { tileXY?.let { service?.getTileWithCache(it.first, tileXY.second) } }
            val result = tile.await()?.awaitResponse()?.body()
            return@withContext result?.let<String, String> {
                tileXY?.let { it1 ->
                    cleanTileGeoJSON(
                        it1.first, tileXY.second, 16.0, it)
                }.toString()
            }

        }

    }

    fun createBeacon(latitude: Double, longitude: Double) {
        if(audioBeacon != 0L)
        {
            audioEngine.destroyBeacon(audioBeacon)
        }
        audioBeacon = audioEngine.createBeacon(latitude, longitude)
    }

    companion object {
        private const val TAG = "LocationService"
        // Check for GPS every n seconds
        private val LOCATION_UPDATES_INTERVAL_MS = 1.seconds.inWholeMilliseconds
        // Secondary "service" every n seconds
        private val TICKER_PERIOD_SECONDS = 10.seconds

        private const val CHANNEL_ID = "LocationService_channel_01"
        private const val NOTIFICATION_CHANNEL_NAME = "Soundscape_LocationService"
        private const val NOTIFICATION_ID = 1000000
    }
}
