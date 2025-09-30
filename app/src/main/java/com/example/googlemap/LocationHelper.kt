package com.example.googlemap

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions

class LocationHelper(
    private val activity: AppCompatActivity,
    private val mMap: GoogleMap,
    private val fusedClient : FusedLocationProviderClient,
) {

    private var userMarker: Marker? = null
    private var cameraMovedOnce: Boolean = false

    private val locationCallback = object : LocationCallback(){
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            updatedMapLocation(loc)
        }
    }

    fun checkLocationPermissionAndEnable(requestPermission: () -> Unit){
        val fine = Manifest.permission.ACCESS_FINE_LOCATION
        when{
            ContextCompat.checkSelfPermission(activity,fine) == android.content.pm.PackageManager.PERMISSION_GRANTED->{
                checkIfLocationEnabled()
            }
            activity.shouldShowRequestPermissionRationale(fine) ->{
                AlertDialog.Builder(activity)
                    .setTitle("Location permission needed")
                    .setMessage("his app needs location to show your position on the map.")
                    .setPositiveButton("OK"){_,_ -> requestPermission()}
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            else -> requestPermission
        }
    }

    @SuppressLint("MissingPermission")
     fun enableMyLocation(){
        try {
            mMap.isMyLocationEnabled = true
            mMap.uiSettings.isMyLocationButtonEnabled = true

            fusedClient.lastLocation.addOnSuccessListener { location ->
                location?.let { updatedMapLocation(it) }
                startLocationUpdates()
            }
        }catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates(){
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY,5_000L)
            .setMinUpdateIntervalMillis(2_000L)
            .build()

        fusedClient.requestLocationUpdates(request,locationCallback,activity.mainLooper)
    }

    fun stopLocationUpdates() {
        fusedClient.removeLocationUpdates(locationCallback)
    }

    private fun updatedMapLocation(location: Location){
        val latLng = LatLng(location.latitude, location.longitude)

        if (userMarker == null) {
            userMarker = mMap.addMarker(MarkerOptions().position(latLng).title("You"))
        } else {
            userMarker?.position = latLng
        }
        if (!cameraMovedOnce) {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 4f))
            cameraMovedOnce = true
        }
    }

    private fun checkIfLocationEnabled(){
        val locationManager = activity.getSystemService(Activity.LOCATION_SERVICE) as LocationManager
        val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!gpsEnabled && !networkEnabled){
            AlertDialog.Builder(activity)
                .setTitle("Enable Location Services")
                .setMessage("Your location services are turned off. Please enable them in settings.")
                .setPositiveButton("Settings"){_,_ ->
                    activity.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
                .setNegativeButton("Cancel",null)
                .show()
        }else{
            enableMyLocation()
        }
    }
}