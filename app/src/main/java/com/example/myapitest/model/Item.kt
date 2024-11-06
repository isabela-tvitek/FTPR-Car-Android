package com.example.myapitest.model

data class Item(
    val id: String,
    val value: ItemValue
)

data class ItemValue(
    val id: String,
    val imageUrl: String,
    val year: String,
    val name: String,
    val licence: String,
    val place: ItemLocation?,
)

data class ItemLocation(
    val lat: Double,
    val long: Double
)