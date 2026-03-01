package com.yourname.splitmastersimple.data.models

data class Expense(
    val id: String = "",
    val groupId: String = "",
    val title: String = "",
    val amount: Double = 0.0,
    val description: String = "",
    val paidBy: String = "",
    val participants: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)