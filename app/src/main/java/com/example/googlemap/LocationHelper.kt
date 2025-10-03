package com.example.googlemap

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.provider.Settings
import androidx.annotation.RequiresPermission
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
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions

class LocationHelper(
    private val activity: AppCompatActivity,
    private val mMap: GoogleMap,
    private val fusedClient : FusedLocationProviderClient,
) {

    private var userMarker: Marker? = null
    private var cameraMovedOnce: Boolean = false
    private var polyLine: Polyline? = null
    private val pathPoints = mutableListOf<LatLng>()
    var followUser: Boolean = true

    var locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY,2000)
        .setMinUpdateDistanceMeters(5f).build()

    private var locationCallback: LocationCallback = object : LocationCallback(){
        override fun onLocationResult(result: LocationResult) {
            for (location in result.locations){
                val latLng = LatLng(location.latitude, location.longitude)

                updatedMapLocation(location)
                pathPoints.add(latLng)

                if (polyLine == null) {
                    polyLine = mMap.addPolyline(
                        PolylineOptions().color(android.graphics.Color.BLUE).width(10f)
                    )
                }
                polyLine?.points = pathPoints

                if (followUser) {
                    mMap.animateCamera(CameraUpdateFactory.newLatLng(latLng))
                }
            }
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    fun realTrack(){
        fusedClient.requestLocationUpdates(locationRequest,locationCallback, Looper.getMainLooper())
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
            mMap.uiSettings.isZoomControlsEnabled = true


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
        stopLocationUpdates()

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
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng,16f))
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