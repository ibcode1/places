package com.ib.placebook.adapter

import android.app.Activity
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.Marker
import com.ib.placebook.R
import com.ib.placebook.ui.MapsActivity
import com.ib.placebook.viewmodel.MapsViewModel

class BookmarkInfoWindowAdapter(private val context: Activity) :
    GoogleMap.InfoWindowAdapter {
    private val contents: View = context.layoutInflater.inflate(
        R.layout.content_bookmark_info, null
    )

    override fun getInfoWindow(marker: Marker): View? {
        return null
    }

    override fun getInfoContents(marker: Marker): View? {
        val titleView = contents.findViewById<TextView>(R.id.title)
        titleView.text = marker.title ?: ""

        val phoneView = contents.findViewById<TextView>(R.id.phone)
        phoneView.text = marker.snippet ?: ""

        val imageView = contents.findViewById<ImageView>(R.id.photo)

        when (marker.tag) {

            is MapsActivity.PlaceInfo -> {
                imageView.setImageBitmap(
                    (marker.tag as MapsActivity.PlaceInfo).image)
            }
            is MapsViewModel.BookmarkView -> {
                var bookmarkView = marker.tag as MapsViewModel.BookmarkView
                //set imageView Bitmap here
                imageView.setImageBitmap(bookmarkView.getImage(context))
            }
        }

        return contents
    }
}