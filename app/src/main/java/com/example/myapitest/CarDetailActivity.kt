package com.example.myapitest

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.myapitest.databinding.ActivityCarDetailBinding
import com.example.myapitest.model.Item
import com.example.myapitest.model.ItemLocation
import com.example.myapitest.model.ItemValue
import com.example.myapitest.service.RetrofitClient
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

class CarDetailActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityCarDetailBinding
    private lateinit var item: Item
    private lateinit var map: GoogleMap
    private var marker: Marker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCarDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val carId = intent.getStringExtra(EXTRA_CAR_ID) ?: return
        setupMap()
        fetchCarDetails(carId)

        binding.btnSaveChanges.setOnClickListener {
            saveCarChanges()
        }
        binding.btnDeleteCar.setOnClickListener {
            deleteCar()
        }
    }

    private fun setupMap() {
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment
        mapFragment?.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map.uiSettings.isZoomControlsEnabled = true
        item.value.place?.let {
            updateMapLocation(it)
        }
    }

    private fun updateMapLocation(location: ItemLocation) {
        val latLng = LatLng(location.lat, location.long)
        marker?.remove()
        marker = map.addMarker(MarkerOptions().position(latLng).title("Localização do carro"))
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
    }

    private fun fetchCarDetails(carId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                item = RetrofitClient.apiService.getItem(carId)
                withContext(Dispatchers.Main) {
                    populateCarDetails(item)
                    item.value.place?.let { updateMapLocation(it) }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CarDetailActivity, "Erro ao carregar os detalhes do carro", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun populateCarDetails(item: Item) {
        binding.etCarName.setText(item.value.name)
        binding.etCarYear.setText(item.value.year)
        binding.etCarLicense.setText(item.value.licence)
        binding.etCarImageUrl.setText(item.value.imageUrl)
        binding.etLatitude.setText(item.value.place?.lat.toString())
        binding.etLongitude.setText(item.value.place?.long.toString())
    }

    private fun saveCarChanges() {
        val updatedItemValue = ItemValue(
            id = item.id,
            name = binding.etCarName.text.toString(),
            year = binding.etCarYear.text.toString(),
            licence = binding.etCarLicense.text.toString(),
            imageUrl = binding.etCarImageUrl.text.toString(),
            place = ItemLocation(
                lat = binding.etLatitude.text.toString().toDoubleOrNull() ?: 0.0,
                long = binding.etLongitude.text.toString().toDoubleOrNull() ?: 0.0
            )
        )

        CoroutineScope(Dispatchers.IO).launch {
            try {
                RetrofitClient.apiService.updateItem(item.id, updatedItemValue)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CarDetailActivity, "Carro atualizado com sucesso!", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CarDetailActivity, "Erro ao salvar as alterações", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun deleteCar() {
        val alertDialog = android.app.AlertDialog.Builder(this)
            .setTitle("Confirmar Exclusão")
            .setMessage("Tem certeza de que deseja excluir este carro?")
            .setPositiveButton("Sim") { _, _ ->
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        RetrofitClient.apiService.deleteItem(item.id)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@CarDetailActivity, "Carro deletado com sucesso!", Toast.LENGTH_SHORT).show()
                            setResult(RESULT_OK)
                            finish()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@CarDetailActivity, "Erro ao deletar o carro", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Não") { dialog, _ ->
                dialog.dismiss()
            }
            .create()

        alertDialog.show()
    }

    companion object {
        private const val EXTRA_CAR_ID = "extra_car_id"

        fun newIntent(context: Context, carId: String): Intent {
            return Intent(context, CarDetailActivity::class.java).apply {
                putExtra(EXTRA_CAR_ID, carId)
            }
        }
    }
}
