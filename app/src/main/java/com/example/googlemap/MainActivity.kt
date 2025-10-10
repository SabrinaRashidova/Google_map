package com.example.googlemap

import android.Manifest
import android.annotation.SuppressLint
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.googlemap.databinding.ActivityMainBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.api.Places
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityMainBinding

    private lateinit var mMap: GoogleMap
    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var locationHelper: LocationHelper
    private lateinit var autoCompleteHelper: AutoCompleteHelper
    private lateinit var mapHelper: MapHelper

    private var startMarker: Marker? = null
    private var destinationMarker: Marker? = null

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()){granted->
            if (granted){
                locationHelper.enableMyLocation()
            }else{
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_LONG).show()
            }
        }

    private val locationPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private val backgroundLocationPermission = Manifest.permission.ACCESS_BACKGROUND_LOCATION

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedClient = LocationServices.getFusedLocationProviderClient(this)

        if (!Places.isInitialized()){
            Places.initialize(applicationContext,getString(R.string.maps_api_key))
        }

        autoCompleteHelper = AutoCompleteHelper(Places.createClient(this))
        autoCompleteHelper.setupAutoComplete(binding.etStart)
        autoCompleteHelper.setupAutoComplete(binding.etDestination)

        binding.btnRoute.setOnClickListener {
            val start = binding.etStart.text.toString()
            val destination = binding.etDestination.text.toString()

            if (start.isNotEmpty() && destination.isNotEmpty()) {
                geocodeAndShow(start,destination)
            }
        }

    }



//    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
//    @SuppressLint("MissingPermission")
    override fun onMapReady(googleMap: GoogleMap) {
        if (googleMap == null){
            Toast.makeText(this, "Failed to load google map", Toast.LENGTH_SHORT).show()
            return
        }
        mMap = googleMap

        mapHelper = MapHelper(mMap,this)
        mMap.uiSettings.isMyLocationButtonEnabled = true
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.uiSettings.isScrollGesturesEnabled = true
        mMap.uiSettings.isRotateGesturesEnabled = true
        mMap.uiSettings.isTiltGesturesEnabled = true

        mMap.isTrafficEnabled = true
        mMap.isBuildingsEnabled = true

        locationHelper = LocationHelper(this,mMap,fusedClient)
        locationHelper.checkLocationPermissionAndEnable { requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED){
            mMap.isMyLocationEnabled = true
            locationHelper.enableMyLocation()
            showBackgroundPermissionExplanation()
        }else{
            locationHelper.checkLocationPermissionAndEnable {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    locationHelper.realTrack()
    }

    private fun geocodeAndShow(start: String, destination: String){
        if (start.isBlank() || destination.isBlank()){
            Toast.makeText(this, "Please enter start and destination", Toast.LENGTH_SHORT).show()
            return
        }

            val geocoder = Geocoder(this, Locale.getDefault())
            lifecycleScope.launch {
                try {
                    val startList = geocoder.getFromLocationName(start,10)
                    val destList = geocoder.getFromLocationName(destination,10)

                    if (!startList.isNullOrEmpty() && !destList.isNullOrEmpty()){
                        val startLatLng = LatLng(startList[0].latitude,startList[0].longitude)
                        val destLatLng = LatLng(destList[0].latitude,destList[0].longitude)

                        withContext(Dispatchers.Main){
                            locationHelper.followUser = false

                            startMarker?.remove()
                            destinationMarker?.remove()
                            startMarker = mMap.addMarker(MarkerOptions().position(startLatLng).title("Start"))
                            destinationMarker = mMap.addMarker(MarkerOptions().position(destLatLng).title("Destination"))
                            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(startLatLng, 12f))
                            mapHelper.drawRoute(startLatLng, destLatLng)

                        }
                    }else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "Address not found", Toast.LENGTH_SHORT).show()
                        }
                    }

                }catch (e: IOException){
                    Toast.makeText(this@MainActivity, "Geocoding failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun requestBackgroundPermissionIfNeeded(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
            if (checkSelfPermission(backgroundLocationPermission) != android.content.pm.PackageManager.PERMISSION_GRANTED){
                requestPermissionLauncher.launch(backgroundLocationPermission)
            }
        }
    }

    private fun showBackgroundPermissionExplanation(){
        AlertDialog.Builder(this)
            .setTitle("Allow background location access")
            .setMessage("To keep tracking your location even when the app is closed, please allow background location access")
            .setPositiveButton("Allow"){_,_ ->
                requestBackgroundPermissionIfNeeded()
            }
            .setNegativeButton("No need",null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        if (::mMap.isInitialized) {
            locationHelper.checkLocationPermissionAndEnable { requestPermissionLauncher.launch(
                Manifest.permission.ACCESS_FINE_LOCATION) }
        }
    }

    override fun onPause() {
        super.onPause()
        locationHelper.stopLocationUpdates()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::locationHelper.isInitialized){
            locationHelper.stopLocationUpdates()
        }
    }


}