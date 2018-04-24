package click.simome.simplelocation

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.arch.lifecycle.MutableLiveData
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.app.Fragment
import android.support.v4.content.PermissionChecker
import android.util.Log
import android.widget.Toast
import com.google.android.gms.location.*
import kotlinx.coroutines.experimental.async
import java.util.concurrent.atomic.AtomicBoolean

const val PERMISSION_REQUEST_CODE: Int = 10101

fun Activity.simpleLocationPermission() {
    if (needsLocationPermission()) {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            PERMISSION_REQUEST_CODE
        )
    }
}

fun Fragment.simpleLocationPermission() {
    context?.let { ctx ->
        ctx.locationManager()?.let { locationManager ->
            if (ctx.needsLocationPermission()) {
                if (isAdded) {
                    requestPermissions(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        PERMISSION_REQUEST_CODE
                    )
                } else {
                    log("Fragment.simpleLocationPermission: fragment not added")
                }
            }
        } ?: run {
            log("Fragment.simpleLocationPermission: location manager null")
        }
    } ?: run {
        log("Fragment.simpleLocationPermission: context null")
    }
}

object SimpleLocation : LocationCallback() {

    private lateinit var locationMonitor: LocationMonitor
    private var geocoder: Geocoder? = null

    private object providerChangeListener : LocationProviderChangeReceiver.Listener {
        override fun onLocationProvidersEnabled() {
            log("location providers enabled")
            locationMonitor.startLocationUpdates()
        }

        override fun onLocationProvidersDisabled() {
            log("location providers disabled")
            locationMonitor.stopLocationUpdates()
        }
    }

    var debugLog = false
    val mostRecentLocation = MutableLiveData<Location>()
    val mostRecentAddress = MutableLiveData<Address>()

    fun init(
        application: Application,
        locationRequest: LocationRequest = LocationRequest.create(),
        debugLog: Boolean = false
    ) {
        this.debugLog = debugLog
        this.geocoder = application.geocoder()
        locationMonitor = LocationMonitor(application, locationRequest, this)
        application.registerActivityLifecycleCallbacks(
            LocationLifecycleCallbacks(
                providerChangeListener
            )
        )
    }

    fun willHandleRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ): Boolean {
        return locationMonitor.willHandleRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )
    }

    override fun onLocationResult(locationResult: LocationResult?) {
        super.onLocationResult(locationResult)
        locationResult?.let {
            log("location update: ${it.lastLocation}")
            mostRecentLocation.postValue(it.lastLocation)
            it.lastLocation?.let {
                async { mostRecentAddress.postValue(address(it).await()) }
            }
        } ?: kotlin.run {
            log("location update: received null location result")
            mostRecentLocation.postValue(null)
        }
    }

    private suspend fun address(location: Location) = async {
        val address = geocoder?.getFromLocation(location.latitude, location.longitude, 1)?.let {
            if (it.isEmpty()) null else
                if (location.equals(mostRecentLocation.value)) it.get(0) else null
        }
        log("address: $address")
        address
    }
}

private fun log(s: String) {
    if (SimpleLocation.debugLog) Log.d("SimpleLocation", s)
}

private fun Context.geocoder() = if (Geocoder.isPresent()) Geocoder(this) else null

private fun Context.locationManager() =
    this.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

private fun Context.needsLocationPermission() = PermissionChecker.checkSelfPermission(
    this,
    android.Manifest.permission.ACCESS_FINE_LOCATION
) != PermissionChecker.PERMISSION_GRANTED


@SuppressLint("MissingPermission")
private fun Application.requestLocationUpdates(
    locationRequest: LocationRequest,
    locationCallback: LocationCallback
): FusedLocationProviderClient? {
    return if (!needsLocationPermission()) {
        val client = LocationServices.getFusedLocationProviderClient(this)
        client.requestLocationUpdates(locationRequest, locationCallback, null)
        client
    } else {
        log("Application.requestLocationUpdates: need permissions")
        null
    }
}

private fun LocationManager?.systemEnabled() =
    this?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true ||
            this?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true

private val doNothing = {}

private class LocationMonitor constructor(
    val application: Application,
    val locationRequest: LocationRequest,
    val locationCallback: LocationCallback
) {
    private val isRequesting = AtomicBoolean()
    private var client: FusedLocationProviderClient? = null

    init {
        startLocationUpdates()
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        if (!isRequesting.getAndSet(true)) {
            if (application.locationManager().systemEnabled()) {
                client = application.requestLocationUpdates(locationRequest, locationCallback)
                if (client == null) {
                    log("start: skipping updates - location client null")
                    isRequesting.set(false)
                } else {
                    log("start: request updates successful")
                }
            } else {
                log("start: skipping updates - all location providers disabled")
                Toast.makeText(
                    application,
                    "Please turn location services on in the device settings",
                    Toast.LENGTH_LONG
                ).show()
                isRequesting.set(false)
            }
        } else {
            log("start: called while currently requesting updates")
        }
    }

    fun stopLocationUpdates() {
        if (isRequesting.getAndSet(false)) {
            log("stop: removing updates")
            client?.removeLocationUpdates(locationCallback)
        } else {
            log("stop: called while not requesting updates")
        }
    }

    fun willHandleRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ): Boolean {
        return when (requestCode) {
            PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty()) {
                    when (grantResults[0]) {
                        PackageManager.PERMISSION_GRANTED -> startLocationUpdates()
                        PackageManager.PERMISSION_DENIED -> stopLocationUpdates()
                        else -> doNothing
                    }
                }
                true
            }
            else -> {
                log("LocationMonitor.willHandleRequestPermissionsResult: not handling - incorrect request code")
                false
            }
        }
    }
}

private class LocationProviderChangeReceiver : BroadcastReceiver() {

    private var listener: Listener? = null

    internal fun register(context: Context, listener: Listener? = null) {
        this.listener = listener
        context.registerReceiver(this, IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION))
    }

    internal fun unregister(context: Context) {
        context.unregisterReceiver(this)
        this.listener = null
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        context?.let {
            val locationManager = it.locationManager()
            locationManager?.let {
                if (locationManager.systemEnabled()) {
                    listener?.onLocationProvidersEnabled()
                } else {
                    listener?.onLocationProvidersDisabled()
                }
            }
        }
    }

    interface Listener {
        fun onLocationProvidersEnabled()
        fun onLocationProvidersDisabled()
    }
}

private class LocationLifecycleCallbacks constructor(val listener: LocationProviderChangeReceiver.Listener) :
    Application.ActivityLifecycleCallbacks {

    private val receiver = LocationProviderChangeReceiver()

    override fun onActivityCreated(activity: Activity?, savedInstanceState: Bundle?) {
    }

    override fun onActivityStarted(activity: Activity?) {
    }

    override fun onActivityResumed(activity: Activity?) {
        activity?.let {
            // log("${it.javaClass.simpleName} resumed - registering provider change receiver")
            receiver.register(it, listener)
        }
    }

    override fun onActivityPaused(activity: Activity?) {
        activity?.let {
            // log("${it.javaClass.simpleName} paused - unregistering provider change receiver")
            receiver.unregister(it)
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity?, outState: Bundle?) {
    }

    override fun onActivityStopped(activity: Activity?) {
    }

    override fun onActivityDestroyed(activity: Activity?) {
    }

}