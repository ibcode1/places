package com.ib.placebook.ui

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.core.app.ActivityCompat

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Location
import android.util.Log
import android.view.WindowManager
import android.widget.ProgressBar
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.net.FetchPhotoRequest
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.ib.placebook.R
import com.ib.placebook.adapter.BookmarkInfoWindowAdapter
import com.ib.placebook.adapter.BookmarkListAdapter
import com.ib.placebook.viewmodel.MapsViewModel
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import com.ib.placebook.databinding.MainViewMapsBinding
import com.ib.placebook.databinding.ActivityMapsBinding


class MapsActivity : AppCompatActivity(), OnMapReadyCallback,
    BookmarkListAdapter.ListSelectionRecyclerViewClickListener {

    private lateinit var map: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var placesClient: PlacesClient
    private val mapsViewModel by viewModels<MapsViewModel>()
    private lateinit var main: MainViewMapsBinding
    private lateinit var binding: ActivityMapsBinding
    private lateinit var bookmarkListAdapter: BookmarkListAdapter
    private lateinit var bookmarkRecyclerView: RecyclerView
    private var markers = HashMap<Long, Marker>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMapsBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        setupLocationClient()
        setupToolbar()
        setupPlacesClient()
        setupNavigationDrawer()
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        setupMapListeners()
        createBookmarkObserver()
        getCurrentLocation()

    }

    private fun setupPlacesClient() {
        Places.initialize(
            getApplicationContext(), //is the same as applicationContext
            getString(R.string.google_maps_key)
        );
        placesClient = Places.createClient(this)
    }

    private fun setupMapListeners() {
        map.setInfoWindowAdapter(BookmarkInfoWindowAdapter(this))

        map.setOnPoiClickListener {
            displayPoi(it)
        }
        map.setOnInfoWindowClickListener { handleInfoWindowClick(it) }

        map.setOnMapLongClickListener { latLng -> newBookmark(latLng) }

        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener {
            searchAtCurrentLocation()
        }
    }

    private fun displayPoi(pointOfInterest: PointOfInterest) {
        showProgress()
        displayPoiGetPlaceStep(pointOfInterest)
    }


    private fun displayPoiGetPlaceStep(pointOfInterest: PointOfInterest) {
        val placeId = pointOfInterest.placeId

        val placeFields = listOf(
            Place.Field.ID,
            Place.Field.NAME,
            Place.Field.PHONE_NUMBER,
            Place.Field.PHOTO_METADATAS,
            Place.Field.ADDRESS,
            Place.Field.LAT_LNG,
            Place.Field.TYPES
        )

        val request = FetchPlaceRequest.builder(placeId, placeFields).build()

        placesClient.fetchPlace(request).addOnSuccessListener { response ->

            val place = response.place
            displayPoiGetPhotoStep(place)
        }.addOnFailureListener { exception ->

            if (exception is ApiException) {
                val statusCode = exception.statusCode
                Log.e(
                    TAG, "Place not found " +
                            exception.message + ", " +
                            "statusCode: " + statusCode)
                hideProgress()
            }
        }
    }

    private fun displayPoiGetPhotoStep(place: Place) {
        val photoMetadata = place.getPhotoMetadatas()?.get(0)
        if (photoMetadata == null) {
            displayPoiDisplayStep(place, null)
            return
        }
        val photoRequest = FetchPhotoRequest.builder(photoMetadata)
            .setMaxWidth(
                resources.getDimensionPixelSize(
                    R.dimen.default_image_width
                )
            )
            .setMaxHeight(
                resources.getDimensionPixelSize(
                    R.dimen.default_image_height
                )
            )
            .build()

        placesClient.fetchPhoto(photoRequest)
            .addOnSuccessListener { fetchPhotoResponse ->
                val bitmap = fetchPhotoResponse.bitmap
                displayPoiDisplayStep(place, bitmap)
            }.addOnFailureListener { exception ->
                if (exception is ApiException) {
                    val statusCode = exception.statusCode
                    Log.e(
                        TAG,
                        "Place not found: " +
                                exception.message + ", " +
                                "statusCode: " + statusCode
                    )
                }
                hideProgress()
            }
    }

    private fun displayPoiDisplayStep(place: Place, photo: Bitmap?) {

        hideProgress()

        val marker = map.addMarker(
            MarkerOptions()
                .position(place.latLng as LatLng)
                .title(place.name)
                .snippet(place.phoneNumber)
        )
        marker?.tag = PlaceInfo(place, photo)
        marker?.showInfoWindow()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_LOCATION) {
            if (grantResults.size == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation()
            } else {
                Log.e(TAG, "Location Permission Denied")
            }
        }
    }

    private fun setupLocationClient() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    private fun handleInfoWindowClick(marker: Marker) {
        when (marker.tag) {
            is MapsActivity.PlaceInfo -> {
                val placeInfo = (marker.tag as PlaceInfo)
                if (placeInfo.place != null && placeInfo.image != null) {

                    GlobalScope.launch {
                        mapsViewModel.addBookmarkFromPlace(placeInfo.place, placeInfo.image)
                    }
                }
                marker.remove()
            }
            is MapsViewModel.BookmarkView -> {
                val bookmarkMarkerView = (marker.tag as
                        MapsViewModel.BookmarkView)
                marker.hideInfoWindow()
                bookmarkMarkerView.id?.let {
                    startBookmarkDetails(it)
                }
            }
        }
    }

    private fun newBookmark(latLng: LatLng) {
        GlobalScope.launch {
            val bookmarkId = mapsViewModel.addBookmark(latLng)
            bookmarkId?.let { startBookmarkDetails(it) }
        }
    }

    private fun createBookmarkObserver() {
        mapsViewModel.getBookmarkViews()?.observe(
            this, Observer {

                map.clear()
                markers.clear()

                it?.let { displayAllBookmarks(it) }
                bookmarkListAdapter.setBookmarkData(it)
            })
    }

    private fun displayAllBookmarks(
        bookmarks: List<MapsViewModel.BookmarkView>
    ) {

        bookmarks.forEach { bookmark -> addPlaceMarker(bookmark) }
    }

    private fun addPlaceMarker(
        bookmark: MapsViewModel.BookmarkView
    ): Marker? {
        val marker = map.addMarker(
            MarkerOptions()
                .position(bookmark.location)
                .title(bookmark.name)
                .snippet(bookmark.phone)
                .icon(bookmark.categoryResourceId?.let {
                    BitmapDescriptorFactory.fromResource(it)
                })
                .alpha(0.8f)
        )

        marker.tag = bookmark

        bookmark.id?.let { markers.put(it, marker) }

        return marker
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestLocationPermissions()
        } else {
            map.isMyLocationEnabled = true
            fusedLocationClient.lastLocation.addOnCompleteListener {
                val location = it.result

                if (location != null) {
                    val latLng = LatLng(location.latitude, location.longitude)


                    val update = CameraUpdateFactory.newLatLngZoom(latLng, 16.0f)
                    map.moveCamera(update)
                } else {
                    Log.e(TAG, "No Location Found")
                }
                //Fuse Location Provider
            }
        }
    }

    private fun requestLocationPermissions() {
        ActivityCompat.requestPermissions(
            this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            REQUEST_LOCATION
        )
    }

    companion object {
        const val EXTRA_BOOKMARK_ID = "com.raywnderlich.placebook.EXTRA_BOOKMARK_ID"
        private const val REQUEST_LOCATION = 1
        private const val TAG = "MapsActivity"
        private const val AUTOCOMPLETE_REQUEST_CODE = 2
    }

    class PlaceInfo(val place: Place? = null, val image: Bitmap? = null)

    private fun startBookmarkDetails(bookmarkId: Long) {
        val intent = Intent(this, BookmarkDetailsActivity::class.java)
        intent.putExtra(EXTRA_BOOKMARK_ID, bookmarkId)
        startActivity(intent)
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.mainMapView.toolbar)
        val toggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.mainMapView.toolbar,
            R.string.open_drawer,
            R.string.close_drawer
        )
        toggle.syncState()
    }

    private fun setupNavigationDrawer() {
        bookmarkRecyclerView = findViewById(R.id.bookmarkRecyclerView)
        val layoutManager = LinearLayoutManager(this)
        bookmarkRecyclerView.layoutManager = layoutManager
        bookmarkListAdapter = BookmarkListAdapter(null, this, this)
        bookmarkRecyclerView.adapter = bookmarkListAdapter
    }

    private fun updateMapToLocation(location: Location) {
        val latLng = LatLng(location.latitude, location.longitude)
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(latLng, 16.0f)
        )
    }

    fun moveToBookmark(bookmark: MapsViewModel.BookmarkView) {
        binding.drawerLayout.closeDrawer(binding.drawerViewMaps.drawerView)

        val marker = markers[bookmark.id]
        marker?.showInfoWindow()

        val location = Location("")
        location.latitude = bookmark.location.latitude
        location.longitude = bookmark.location.longitude
        updateMapToLocation(location)
    }

    override fun listItemClicked(bookmarkView: MapsViewModel.BookmarkView) {
        moveToBookmark(bookmarkView)
    }

    private fun searchAtCurrentLocation() {
        val placeFields = listOf(
            Place.Field.ID,
            Place.Field.NAME,
            Place.Field.PHONE_NUMBER,
            Place.Field.PHOTO_METADATAS,
            Place.Field.LAT_LNG,
            Place.Field.ADDRESS,
            Place.Field.TYPES
        )
        val bounds = RectangularBounds.newInstance(map.projection.visibleRegion.latLngBounds)
        try {
            val intent = Autocomplete.IntentBuilder(
                AutocompleteActivityMode.OVERLAY, placeFields
            )
                .setLocationBias(bounds)
                .build(this)


            startActivityForResult(intent, AUTOCOMPLETE_REQUEST_CODE)
        } catch (e: GooglePlayServicesRepairableException) {
            //TODO: Handle exception

        } catch (e: GooglePlayServicesNotAvailableException) {
            //TODO:Handle exception
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            AUTOCOMPLETE_REQUEST_CODE ->
                if (resultCode == Activity.RESULT_OK && data != null) {
                    val place = Autocomplete.getPlaceFromIntent(data)

                    val location = Location("")
                    location.latitude = place.latLng?.latitude ?: 0.0
                    location.longitude = place.latLng?.longitude ?: 0.0
                    updateMapToLocation(location)
                    showProgress()

                    displayPoiGetPhotoStep(place)
                }
        }
    }

    private fun disableUserInteraction() {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
    }
    private fun enableUserInteraction(){
        window.clearFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
    }

    private fun showProgress(){
        binding.mainMapView.progressBar.visibility = ProgressBar.VISIBLE
        disableUserInteraction()
    }
    private fun hideProgress(){
        binding.mainMapView.progressBar.visibility = ProgressBar.GONE
        enableUserInteraction()
    }
}
