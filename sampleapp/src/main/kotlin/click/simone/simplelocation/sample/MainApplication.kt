package click.simone.simplelocation.sample

import android.app.Application
import click.simome.simplelocation.SimpleLocation
import com.google.android.gms.location.LocationRequest
import java.util.concurrent.TimeUnit


class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val locationRequest = LocationRequest.create()
            .setInterval(TimeUnit.SECONDS.toMillis(10))
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
        SimpleLocation.init(this, locationRequest, BuildConfig.DEBUG)
    }
}