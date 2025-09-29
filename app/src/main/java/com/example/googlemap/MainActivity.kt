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
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale
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

    private var userMarker: Marker? = null
    private var cameraMovedOnce: Boolean = false

    private val locationCallback = object : LocationCallback(){
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            updateMapLocation(loc)
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()){granted->
            if (granted){
                enableMyLocation()
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

        fusedClient = LocationServices.getFusedLocationProviderClient(this)

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
//        val tashkent = LatLng(41.2995, 69.2401)
//        mMap.addMarker(MarkerOptions().position(tashkent).title("Marker in Tashkent"))
//        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(tashkent,12f))

        checkLocationPermissionAndEnable()
    }

    private fun geocodeAndShow(start: String, destination: String){
        if (start.isBlank() || destination.isBlank()){
            Toast.makeText(this, "Please enter start and destination", Toast.LENGTH_SHORT).show()
            return
        }

            val geocoder = Geocoder(this, Locale.getDefault())
            lifecycleScope.launch {
                try {
                    val startList = geocoder.getFromLocationName(start,1)
                    val destList = geocoder.getFromLocationName(destination,1)

                    if (startList != null && destList != null){
                        val startLatLng = LatLng(startList[0].latitude,startList[0].longitude)
                        val destLatLng = LatLng(destList[0].latitude,destList[0].longitude)

                        withContext(Dispatchers.Main){
                            mMap.addMarker(MarkerOptions().position(startLatLng).title("Start"))
                            mMap.addMarker(MarkerOptions().position(destLatLng).title("Destination"))
                            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(startLatLng, 12f))
                            drawRoute(startLatLng, destLatLng)
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
    }

    private fun checkLocationPermissionAndEnable(){
        val fine = Manifest.permission.ACCESS_FINE_LOCATION
        when{
            ContextCompat.checkSelfPermission(this,fine) == android.content.pm.PackageManager.PERMISSION_GRANTED->{
                checkIfLocationEnabled()
            }
            shouldShowRequestPermissionRationale(fine) ->{
                AlertDialog.Builder(this)
                    .setTitle("Location permission needed")
                    .setMessage("This app needs location to show your position on the map.")
                    .setPositiveButton("OK"){
                            _,_ -> requestPermissionLauncher.launch(fine)
                    }
                    .setNegativeButton("Cancel",null)
                    .show()
            }
            else -> {
                requestPermissionLauncher.launch(fine)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableMyLocation(){
        try {
            mMap.isMyLocationEnabled = true
            mMap.uiSettings.isMyLocationButtonEnabled = true

            fusedClient.lastLocation.addOnSuccessListener { location ->
                location?.let { updateMapLocation(it) }
                startLocationUpdates()
            }
        }catch(e: SecurityException){
            e.printStackTrace()
        }
    }

    private fun updateMapLocation(location: Location) {
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

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates(){
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY,5_000L)
            .setMinUpdateIntervalMillis(2_000L)
            .build()

        fusedClient.requestLocationUpdates(request,locationCallback, Looper.getMainLooper())
    }

    private fun stopLocationUpdates() {
        fusedClient.removeLocationUpdates(locationCallback)
    }

    private fun checkIfLocationEnabled() {
        val lm = getSystemService(LOCATION_SERVICE) as android.location.LocationManager
        val gpsEnabled = lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
        val networkEnabled = lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)

        if (!gpsEnabled && !networkEnabled) {
            AlertDialog.Builder(this)
                .setTitle("Enable Location Services")
                .setMessage("Your location services are turned off. Please enable them in settings.")
                .setPositiveButton("Settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
                .setNegativeButton("Cancel", null)
                .show()
        }else {
            enableMyLocation()
        }
    }

    private fun drawRoute(start: LatLng, dest: LatLng){
        val url = "https://maps.googleapis.com/maps/api/directions/json?" +
                "origin=${start.latitude},${start.longitude}" +
                "&destination=${dest.latitude},${dest.longitude}" +
                "&key=${getString(R.string.maps_api_key)}"

        Thread{
            val connection = URL(url).openConnection() as HttpsURLConnection
            val inputStream = connection.inputStream
            val response = inputStream.bufferedReader().use { it.readText() }

            val json = JSONObject(response)
            val routes = json.getJSONArray("routes")
            if (routes.length() >0){
                val overviewPolyline = routes.getJSONObject(0)
                    .getJSONObject("overview_polyline")
                    .getString("points")

                val points = PolyUtil.decode(overviewPolyline)
                runOnUiThread {
                    mMap.addPolyline(
                        PolylineOptions()
                        .addAll(points)
                        .width(10f)
                        .color(Color.BLUE))
                }
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        if (::mMap.isInitialized) {
            checkLocationPermissionAndEnable()
        }
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }



}