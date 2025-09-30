package com.example.googlemap

import android.app.Activity
import android.content.Context
import android.graphics.Color
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PolylineOptions
import com.google.maps.android.PolyUtil
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class MapHelper(private val map: GoogleMap, private val context: Context) {

     fun drawRoute(start: LatLng, dest: LatLng){
        val url = "https://maps.googleapis.com/maps/api/directions/json?" +
                "origin=${start.latitude},${start.longitude}" +
                "&destination=${dest.latitude},${dest.longitude}" +
                "&key=${context.getString(R.string.maps_api_key)}"

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
                (context as Activity).runOnUiThread {
                    map.addPolyline(
                        PolylineOptions()
                           .addAll(points)
                            .width(12f)
                            .color(Color.BLUE))
                }
            }
        }.start()
    }

}