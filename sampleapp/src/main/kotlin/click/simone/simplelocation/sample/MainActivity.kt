package click.simone.simplelocation.sample

import android.arch.lifecycle.Observer
import android.location.Location
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import click.simome.simplelocation.SimpleLocation
import click.simome.simplelocation.simpleLocationPermission
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity(), Observer<Location> {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        simpleLocationPermission()
    }

    override fun onResume() {
        super.onResume()
        SimpleLocation.mostRecentLocation.observe(this, this)
    }

    override fun onPause() {
        super.onPause()
        SimpleLocation.mostRecentLocation.removeObserver(this)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (!SimpleLocation.willHandleRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
            )
        ) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    override fun onChanged(location: Location?) {
        location?.let { loc ->
            location_text?.apply {
                text = StringBuilder(text).append("\n\n").append(loc)
            }
        }

    }
}
