package com.example.myapitest

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.graphics.BitmapFactory
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.example.myapitest.databinding.ActivityCarDetailBinding
import com.example.myapitest.model.Item
import com.example.myapitest.model.ItemLocation
import com.example.myapitest.model.ItemValue
import com.example.myapitest.service.RetrofitClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

class CarDetailActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityCarDetailBinding
    private lateinit var map: GoogleMap
    private var marker: Marker? = null
    private lateinit var item: Item
    private var imageUri: Uri? = null
    private var imageFile: File? = null
    private var isNewImageCaptured = false

    private val cameraLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            displayCapturedImage()
            isNewImageCaptured = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCarDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val carId = intent.getStringExtra(EXTRA_CAR_ID) ?: return
        fetchCarDetails(carId)

        binding.apply {
            btnSaveChanges.setOnClickListener { saveCarChanges() }
            btnDeleteCar.setOnClickListener { confirmDeleteCar() }
            btnTakePicture.setOnClickListener { takePicture() }
        }

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment
        mapFragment?.getMapAsync(this)
    }

    private fun takePicture() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
        ) == PERMISSION_GRANTED) {
            openCamera()
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE)
        }
    }
    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        imageUri = createImageUri()
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri)
        cameraLauncher.launch(intent)
    }

    @SuppressLint("SimpleDateFormat")
    private fun createImageUri(): Uri {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val imageFileName = "JPEG_${timeStamp}_"
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        imageFile = File.createTempFile(imageFileName, ".jpg", storageDir)
        return FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.fileprovider", imageFile!!
        )
    }

    private fun displayCapturedImage() {
        imageFile?.let {
            val bitmap = BitmapFactory.decodeFile(it.absolutePath)
            binding.ivCarImage.setImageBitmap(bitmap)
            binding.etCarImageUrl.setText(it.absolutePath)
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map.uiSettings.isZoomControlsEnabled = true
        loadCurrentLocation()

        map.setOnMapClickListener { latLng ->
            moveMarker(latLng)
        }
    }

    private fun loadCurrentLocation() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PERMISSION_GRANTED) {
            map.isMyLocationEnabled = true
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                location?.let {
                    val currentLocation = LatLng(it.latitude, it.longitude)
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 15f))
                }
            }
        }
    }

    private fun moveMarker(latLng: LatLng) {
        marker?.remove()
        marker = map.addMarker(
            MarkerOptions().position(latLng).title(R.string.localizacao_selecionada.toString())
        )
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17f))
        binding.etLatitude.setText(latLng.latitude.toString())
        binding.etLongitude.setText(latLng.longitude.toString())
    }

    private fun fetchCarDetails(carId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                item = RetrofitClient.apiService.getItem(carId)
                withContext(Dispatchers.Main) {
                    populateCarDetails(item)
                    item.value.place?.let {
                        moveMarker(LatLng(it.lat, it.long))
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast(R.string.erro_carregar_detalhes_carro.toString())
                }
            }
        }
    }

    private fun populateCarDetails(item: Item) {
        binding.apply {
            etCarName.setText(item.value.name)
            etCarYear.setText(item.value.year)
            etCarLicense.setText(item.value.licence)
            etLatitude.setText(item.value.place?.lat.toString())
            etLongitude.setText(item.value.place?.long.toString())
            etCarImageUrl.setText(item.value.imageUrl)
            loadImage(item.value.imageUrl)
        }
    }

    private fun loadImage(imageUrl: String?) {
        Glide.with(this)
            .load(imageUrl)
            .placeholder(R.drawable.carrodefault)
            .error(R.drawable.carrodefault)
            .into(binding.ivCarImage)
    }

    private fun saveCarChanges() {
        val updatedImageUrl = if (isNewImageCaptured && imageUri != null) {
            imageUri.toString()
        } else {
            binding.etCarImageUrl.text.toString()
        }

        val updatedItemValue = ItemValue(
            id = item.id,
            name = binding.etCarName.text.toString(),
            year = binding.etCarYear.text.toString(),
            licence = binding.etCarLicense.text.toString(),
            imageUrl = updatedImageUrl,
            place = ItemLocation(
                lat = binding.etLatitude.text.toString().toDoubleOrNull() ?: 0.0,
                long = binding.etLongitude.text.toString().toDoubleOrNull() ?: 0.0
            )
        )

        CoroutineScope(Dispatchers.IO).launch {
            try {
                RetrofitClient.apiService.updateItem(item.id, updatedItemValue)
                withContext(Dispatchers.Main) {
                    setResult(RESULT_OK)
                    showToast(R.string.carro_atualizado_com_sucesso.toString())
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast(R.string.erro_atualizar_carro.toString())
                }
            }
        }
    }

    private fun deleteCar() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                RetrofitClient.apiService.deleteItem(item.id)
                withContext(Dispatchers.Main) {
                    setResult(RESULT_OK)
                    showToast(R.string.carro_deletado_com_sucesso.toString())
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast(R.string.erro_deletar_carro.toString())
                }
            }
        }
    }

    private fun confirmDeleteCar() {
        AlertDialog.Builder(this)
            .setTitle(R.string.deletar_carro)
            .setMessage(R.string.confirmacao_deletar_carro)
            .setPositiveButton(R.string.sim) { _, _ -> deleteCar() }
            .setNegativeButton(R.string.nao, null)
            .show()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 101
        private const val EXTRA_CAR_ID = "extra_car_id"

        fun newIntent(context: Context, carId: String): Intent {
            return Intent(context, CarDetailActivity::class.java).apply {
                putExtra(EXTRA_CAR_ID, carId)
            }
        }
    }
}
