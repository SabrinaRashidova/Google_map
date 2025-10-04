package com.example.googlemap

import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient

class AutoCompleteHelper(private val placesClient: PlacesClient) {

    fun setupAutoComplete(textView: AutoCompleteTextView){
        val adapter = ArrayAdapter<String>(textView.context,android.R.layout.simple_dropdown_item_1line)
        textView.setAdapter(adapter)

        textView.addTextChangedListener(object : TextWatcher{
            override fun afterTextChanged(s: Editable?) {}

            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) {}

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {
                if (!s.isNullOrEmpty()){
                    val request = FindAutocompletePredictionsRequest.builder()
                        .setQuery(s.toString())
                        .build()

                    placesClient.findAutocompletePredictions(request)
                        .addOnSuccessListener { response ->
                            val suggestions = response.autocompletePredictions.map { it.getFullText(null).toString() }
                            adapter.clear()
                            adapter.addAll(suggestions)
                            adapter.notifyDataSetChanged()
                        }
                }
            }

        })
    }
}