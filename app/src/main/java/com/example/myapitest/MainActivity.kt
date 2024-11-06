package com.example.myapitest

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
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        fetchItems()

        binding.addCta.setOnClickListener {
            val intent = Intent(this, NewCarActivity::class.java)
            startActivity(intent)
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
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Erro ao buscar dados", Toast.LENGTH_SHORT).show()
            } finally {
                binding.swipeRefreshLayout.isRefreshing = false
            }
        }
    }

//    private fun fetchItems() {
//        lifecycleScope.launch {
//            try {
//                val items: List<Item> = RetrofitClient.apiService.getItems()
//                Log.d("MainActivity", "Itens recebidos: $items")
//                updateRecyclerView(items)
//            } catch (e: Exception) {
//                Toast.makeText(this@MainActivity, "Erro ao buscar dados: ${e.message}", Toast.LENGTH_SHORT).show()
//                e.printStackTrace()
//            } finally {
//                binding.swipeRefreshLayout.isRefreshing = false
//            }
//        }
//    }



    override fun onResume() {
        super.onResume()
        fetchItems()
    }

    private fun updateRecyclerView(items: List<Item>) {
        val adapter = ItemAdapter(items) { item ->
            val intent = CarDetailActivity.newIntent(this, item.id)
            startActivity(intent)
        }
        binding.recyclerView.adapter = adapter
        adapter.notifyDataSetChanged() // Garante atualização
    }


    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_logout -> {
                performLogout()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun performLogout() {
        FirebaseAuth.getInstance().signOut()
        Toast.makeText(this, "Logout bem-sucedido", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
