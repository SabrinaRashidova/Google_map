package com.example.googlemap

import android.Manifest
import android.location.Geocoder
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.googlemap.database.SavedPlace
import com.example.googlemap.database.SavedPlaceDao
import com.example.googlemap.database.SavedPlaceDatabase
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
    private lateinit var dao: SavedPlaceDao

    private lateinit var db: SavedPlaceDatabase

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        db = SavedPlaceDatabase.getInstance(this)
        dao = db.savedPlaceDao()

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



    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mapHelper = MapHelper(mMap,this)
        mMap.isMyLocationEnabled = true
        mMap.uiSettings.isMyLocationButtonEnabled = true
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.uiSettings.isScrollGesturesEnabled = true
        mMap.uiSettings.isRotateGesturesEnabled = true
        mMap.uiSettings.isTiltGesturesEnabled = true

        mMap.isTrafficEnabled = true
        mMap.isBuildingsEnabled = true

        locationHelper = LocationHelper(this,mMap,fusedClient)
        locationHelper.checkLocationPermissionAndEnable { requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
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

    private fun savePlace(name: String,latLng: LatLng){
        lifecycleScope.launch {
            dao.insert(SavedPlace(name = name,lat = latLng.latitude,long = latLng.longitude))
            loadSavedPlaces()
        }
    }

    private fun loadSavedPlaces(){

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

}