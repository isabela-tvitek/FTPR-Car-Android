package com.example.myapitest

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.myapitest.databinding.ActivityMainBinding
import com.example.myapitest.model.Item
import com.example.myapitest.model.ItemLocation
import com.example.myapitest.model.ItemValue
import com.example.myapitest.service.RetrofitClient
import com.example.myapitest.ui.ItemAdapter
import kotlinx.coroutines.launch
import org.json.JSONObject
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var newCarLauncher: ActivityResultLauncher<Intent>
    private lateinit var carDetailLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        fetchItems()


        newCarLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                fetchItems()
            }
        }

        carDetailLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                fetchItems()
            }
        }

        binding.addCta.setOnClickListener {
            val intent = NewCarActivity.newIntent(this)
            newCarLauncher.launch(intent)
        }
    }

    private fun setupRecyclerView() {
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.swipeRefreshLayout.setOnRefreshListener {
            fetchItems()
        }
    }

    private fun fetchItems() {
        lifecycleScope.launch {
            try {
                val response: List<Map<String, Any>> = RetrofitClient.apiService.getItemsAsMap()

                val items = response.map { itemData ->
                    val itemJson = JSONObject(itemData)

                    val imageUrl = itemJson.optString("imageUrl", "https://www.shutterstock.com/image-vector/default-ui-image-placeholder-wireframes-600nw-1037719192.jpg")
                    val itemValue = ItemValue(
                        id = itemJson.optString("id"),
                        imageUrl = imageUrl,
                        year = itemJson.optString("year"),
                        name = itemJson.optString("name"),
                        licence = itemJson.optString("licence"),
                        place = itemJson.optJSONObject("place")?.let { place ->
                            ItemLocation(
                                lat = place.optDouble("lat"),
                                long = place.optDouble("long")
                            )
                        }
                    )

                    Item(
                        id = itemJson.optString("id"),
                        value = itemValue
                    )
                }

                updateRecyclerView(items)
                Log.d("MainActivity", "Itens recebidos: $items")

            } catch (e: Exception) {
                Log.e("MainActivity", "Erro ao buscar itens", e)
                Toast.makeText(this@MainActivity, "Erro ao buscar dados", Toast.LENGTH_SHORT).show()
            } finally {
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_logout -> {
                onLogout()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun onLogout() {
        FirebaseAuth.getInstance().signOut()
        val intent = LoginActivity.newIntent(this)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun updateRecyclerView(items: List<Item>) {
        val adapter = ItemAdapter(items) { item ->
            val intent = CarDetailActivity.newIntent(this, item.id)
            carDetailLauncher.launch(intent)
        }
        binding.recyclerView.adapter = adapter
    }

    companion object {
        fun newIntent(context: Context) = Intent(context, MainActivity::class.java)
    }
}
