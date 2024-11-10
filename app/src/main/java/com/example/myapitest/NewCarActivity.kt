package com.example.myapitest

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
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
import com.example.myapitest.databinding.ActivityNewCarBinding
import com.example.myapitest.model.ItemLocation
import com.example.myapitest.model.ItemValue
import com.example.myapitest.service.RetrofitClient
import com.google.android.gms.location.FusedLocationProviderClient
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
import java.util.UUID

class NewCarActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityNewCarBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var map: GoogleMap
    private var marker: Marker? = null

    private var imageUri: Uri? = null
    private var imageFile: File? = null
    private val cameraLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            displayCapturedImage()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNewCarBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupMap()

        binding.btnTakePicture.setOnClickListener { takePicture() }
        binding.btnSaveCar.setOnClickListener { saveCar() }
    }

    private fun setupMap() {
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment
        mapFragment?.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map.uiSettings.isZoomControlsEnabled = true
        getDeviceLocation()

        map.setOnMapClickListener { latLng ->
            moveMarker(latLng)
        }
    }

    private fun getDeviceLocation() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) == PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                location?.let {
                    val currentLocation = LatLng(it.latitude, it.longitude)
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 15f))
                }
            }
        } else {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 100)
        }
    }

    private fun moveMarker(latLng: LatLng) {
        marker?.remove()
        marker = map.addMarker(MarkerOptions().position(latLng).title(R.string.localizacao_selecionada.toString()))
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17f))
        binding.etLatitude.setText(latLng.latitude.toString())
        binding.etLongitude.setText(latLng.longitude.toString())
    }

    private fun takePicture() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
        ) == PERMISSION_GRANTED) {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            imageUri = createImageUri()
            intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri)
            cameraLauncher.launch(intent)
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 101)
        }
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
            binding.ivCapturedImage.setImageBitmap(bitmap)
            binding.etCarImageUrl.setText(R.string.imagem_capturada)
        }
    }

    private fun saveCar() {
        val name = binding.etCarName.text.toString()
        val year = binding.etCarYear.text.toString()
        val licence = binding.etCarLicense.text.toString()
        val imageUrl = imageUri?.toString() ?: binding.etCarImageUrl.text.toString()

        if (name.isEmpty() || year.isEmpty() || licence.isEmpty() || marker == null) {
            showToast(R.string.preencha_todos_campos.toString())
            return
        }

        val location = ItemLocation(marker!!.position.latitude, marker!!.position.longitude)
        val newItemValue = ItemValue(
            id = UUID.randomUUID().toString(),
            name = name,
            year = year,
            licence = licence,
            imageUrl = imageUrl,
            place = location
        )

        CoroutineScope(Dispatchers.IO).launch {
            try {
                RetrofitClient.apiService.addItem(newItemValue)
                withContext(Dispatchers.Main) {
                    setResult(Activity.RESULT_OK)
                    showToast(R.string.carro_adicionado_sucesso.toString())
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast(R.string.erro_salvar_carro.toString())
                }
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        fun newIntent(context: Context) = Intent(context, NewCarActivity::class.java)
    }
}
