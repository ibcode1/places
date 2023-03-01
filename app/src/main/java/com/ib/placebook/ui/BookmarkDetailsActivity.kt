package com.ib.placebook.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.Observer
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.ib.placebook.R
import com.ib.placebook.databinding.ActivityBookmarkDetailsBinding
import com.ib.placebook.viewmodel.BookmarkDetailsViewModel
import util.ImageUtils
import java.io.File
import java.net.URLEncoder


class BookmarkDetailsActivity : AppCompatActivity(),
    PhotoOptionDialogFragment.PhotoOptionDialogListener {

    private val bookmarkDetailsViewModel by viewModels<BookmarkDetailsViewModel>()
    private var bookmarkDetailsView: BookmarkDetailsViewModel.BookmarkDetailsView? = null
    private lateinit var binding: ActivityBookmarkDetailsBinding
    private var photoFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBookmarkDetailsBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        setupToolbar()
        getIntentData()
        setupFab()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
    }

    private fun populateFields() {
        bookmarkDetailsView?.let { bookmarkView ->
            binding.editTextName.setText(bookmarkView.name)
            binding.editTextPhone.setText(bookmarkView.phone)
            binding.editTextNotes.setText(bookmarkView.notes)
            binding.editTextAddress.setText(bookmarkView.address)
        }
    }

    private fun populateImageView() {
        bookmarkDetailsView?.let { bookmarkView ->
            val placeImage = bookmarkView.getImage(this)
            placeImage?.let {
                binding.imageViewPlace.setImageBitmap(placeImage)
            }
        }
        binding.imageViewPlace.setOnClickListener {
            replaceImage()
        }
    }

    private fun getIntentData() {
        val bookmarkId = intent.getLongExtra(
            MapsActivity.Companion.EXTRA_BOOKMARK_ID, 0
        )
        bookmarkDetailsViewModel.getBookmark(bookmarkId)?.observe(this,
            Observer {
                it?.let {
                    bookmarkDetailsView = it
                    populateFields()
                    populateImageView()
                    populateCategoryList()
                }
            })
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.menu_bookmark_details, menu)
        return true
    }

    private fun saveChanges() {
        val name = binding.editTextName.text.toString()
        if (name.isEmpty()) {
            return
        }
        bookmarkDetailsView?.let { bookmarkView ->
            bookmarkView.name = binding.editTextName.text.toString()
            bookmarkView.notes = binding.editTextNotes.text.toString()
            bookmarkView.address = binding.editTextAddress.text.toString()
            bookmarkView.phone = binding.editTextPhone.text.toString()
            bookmarkView.category = binding.spinnerCategory.selectedItem as String
            bookmarkDetailsViewModel.updateBookmark(bookmarkView)
        }
        finish()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_save -> {
                saveChanges()
                return true
            }
            R.id.action_delete -> {
                deleteBookmark()
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onCaptureClick() {
        photoFile = null
        try {
            photoFile = ImageUtils.createUniqueImageFile(this)
        } catch (ex: java.io.IOException) {
            return
        }
        photoFile?.let { photoFile ->
            val photoUri = FileProvider.getUriForFile(
                this,
                "com.raywenderlich.placebook.fileprovider",
                photoFile
            )

            val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)

            val intentActivities = packageManager.queryIntentActivities(
                captureIntent, PackageManager.MATCH_DEFAULT_ONLY
            )
            intentActivities.map { it.activityInfo.packageName }
                .forEach {
                    grantUriPermission(
                        it,
                        photoUri,
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )

                    startActivityForResult(captureIntent, REQUEST_CAPTURE_IMAGE)
                }
        }
    }

    override fun onPickClick() {
        val pickIntent = Intent(
            Intent.ACTION_PICK,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        )
        startActivityForResult(pickIntent, REQUEST_GALLERY_IMAGE)
    }

    private fun replaceImage() {
        val newFragment = PhotoOptionDialogFragment.newInstance(this)
        newFragment?.show(supportFragmentManager, "photoOptionDialog")
    }

    companion object {
        private const val REQUEST_CAPTURE_IMAGE = 1
        private const val REQUEST_GALLERY_IMAGE = 2
    }

    private fun updateImage(image: Bitmap) {
        val bookmarkView = bookmarkDetailsView ?: return
        binding.imageViewPlace.setImageBitmap(image)
        bookmarkView.setImage(this, image)
    }

    private fun getImageWithPath(filePath: String): Bitmap? {
        return ImageUtils.decodeFileToSize(
            filePath,
            resources.getDimensionPixelSize(
                R.dimen.default_image_width
            ),
            resources.getDimensionPixelSize(
                R.dimen.default_image_height
            )
        )
    }

    private fun getImageWithAuthority(uri: Uri): Bitmap? {
        return ImageUtils.decodeUriStreamToSize(
            uri,
            resources.getDimensionPixelSize(
                R.dimen.default_image_width
            ),
            resources.getDimensionPixelSize(
                R.dimen.default_image_height
            ),
            this
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == android.app.Activity.RESULT_OK) {
            when (requestCode) {
                REQUEST_CAPTURE_IMAGE -> {
                    val photoFile = photoFile ?: return
                    val uri = FileProvider.getUriForFile(
                        this,
                        "com.raywenderlich.placebook.fileprovider",
                        photoFile
                    )
                    revokeUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

                    val image = getImageWithPath(photoFile.absolutePath)
                    image?.let { updateImage(it) }
                }


                REQUEST_GALLERY_IMAGE -> if (data != null && data.data != null) {
                    val imageUri = data.data as Uri
                    val image = getImageWithAuthority(imageUri)
                    image?.let { updateImage(it) }
                }
            }
        }

    }

    private fun populateCategoryList() {
        val bookmarkView = bookmarkDetailsView ?: return

        val resourceId = bookmarkDetailsViewModel.getCategoryResourceId(
            bookmarkView.category
        )

        resourceId?.let { binding.imageViewCategory.setImageResource(it) }

        val categories = bookmarkDetailsViewModel.getCategories()

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        adapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        )

        binding.spinnerCategory.adapter = adapter

        val placeCategory = bookmarkView.category
        binding.spinnerCategory.setSelection(
            adapter.getPosition(placeCategory)
        )

        binding.spinnerCategory.post {
            binding.spinnerCategory.onItemSelectedListener =
                object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        parent: AdapterView<*>?,
                        view: View?,
                        position: Int,
                        id: Long
                    ) {
                        val category = parent?.getItemAtPosition(position) as String

                        val resourceId = bookmarkDetailsViewModel.getCategoryResourceId(category)
                        resourceId?.let {
                            binding.imageViewCategory.setImageResource(it)
                        }
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) {
                        TODO("Not yet implemented")
                    }
                }
        }
    }

    private fun deleteBookmark() {
        val bookmarkView = bookmarkDetailsView ?: return

        AlertDialog.Builder(this)
            .setMessage("Delete")
            .setPositiveButton("OK") { _, _ ->
                bookmarkDetailsViewModel.deleteBookmark(bookmarkView)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .create().show()
    }

    private fun sharePlace() {
        val bookmarkView = bookmarkDetailsView ?: return

        var mapURL = ""
        if (bookmarkView.placeId == null) {
            val location = URLEncoder.encode(
                "${bookmarkView.latitude}," + "${bookmarkView.longitude}",
                "utf-8")
            mapURL = "https://www.google.com/maps/dir/?api=1" +
                    "&destination=$location"
        } else {
            val name = URLEncoder.encode(bookmarkView.name, "utf-8")
            mapURL = "https://www.google.com/maps/dir/?api=1" +
                    "&destination=$name&destination_place_id=" +
                    "${bookmarkView.placeId}"
        }
        val sendIntent = Intent()
        sendIntent.action = Intent.ACTION_SEND

        sendIntent.putExtra(Intent.EXTRA_TEXT, "Check out ${bookmarkView.name} at:\n$mapURL")
        sendIntent.putExtra(Intent.EXTRA_SUBJECT,"Sharing ${bookmarkView.name}")
        sendIntent.type = "text/plain"
        startActivity(sendIntent)
    }
    private fun setupFab(){
        findViewById<FloatingActionButton>(R.id.fab)
            .setOnClickListener { sharePlace() }
    }
}

