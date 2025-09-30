package com.example.googlemap

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.registerForActivityResult
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.googlemap.databinding.ActivityMainBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.maps.android.PolyUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.URL
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityMainBinding
    private lateinit var mMap: GoogleMap
    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var locationHelper: LocationHelper
    private lateinit var autoCompleteHelper: AutoCompleteHelper
    private lateinit var mapHelper: MapHelper

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()){granted ->
            if (granted){
                locationHelper.enableMyLocation()
            }else {
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

        fusedClient = LocationServices.getFusedLocationProviderClient(this)

        Places.initialize(applicationContext,getString(R.string.maps_api_key))
        autoCompleteHelper = AutoCompleteHelper(com.google.android.libraries.places.api.Places.createClient(this))
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

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        locationHelper = LocationHelper(this, mMap, fusedClient)
        locationHelper.checkLocationPermissionAndEnable { requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
        mapHelper = MapHelper(mMap,this)
    }


    private fun geocodeAndShow(start: String, destination: String){
        if (start.isBlank() || destination.isBlank()){
            Toast.makeText(this, "Please enter start and destination", Toast.LENGTH_SHORT).show()
            return
        }

            val geocoder = Geocoder(this, Locale.getDefault())
            lifecycleScope.launch {
                try {
                    val startList = withContext(Dispatchers.IO) {
                        geocoder.getFromLocationName(start, 1)
                    }
                    val destList = withContext(Dispatchers.IO) {
                        geocoder.getFromLocationName(destination, 1)
                    }

                    if (startList != null && destList != null && startList.isNotEmpty() && destList.isNotEmpty()) {
                        val startLatLng = LatLng(startList[0].latitude, startList[0].longitude)
                        val destLatLng = LatLng(destList[0].latitude, destList[0].longitude)

                        withContext(Dispatchers.Main) {
                            mMap.addMarker(MarkerOptions().position(startLatLng).title("Start"))
                            mMap.addMarker(MarkerOptions().position(destLatLng).title("Destination"))
                            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(startLatLng, 12f))
                            mapHelper.drawRoute(startLatLng, destLatLng)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "Address not found", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: IOException) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Geocoding failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
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