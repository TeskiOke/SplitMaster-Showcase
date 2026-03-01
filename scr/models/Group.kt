package com.yourname.splitmastersimple.data.models

data class Group(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val createdBy: String = "",
    val members: List<String> = emptyList(),
    val currency: String = "USD" // Валюта группы
)