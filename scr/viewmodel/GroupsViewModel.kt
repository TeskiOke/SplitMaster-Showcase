package com.yourname.splitmastersimple.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.splitmastersimple.data.FirebaseService
import com.yourname.splitmastersimple.data.models.Group
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GroupsViewModel : ViewModel() {
    private val firebaseService = FirebaseService()

    private val _groups = MutableStateFlow<List<Group>>(emptyList())
    val groups: StateFlow<List<Group>> = _groups.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow("")
    val errorMessage: StateFlow<String> = _errorMessage.asStateFlow()

    init {
        loadGroups()
    }

    fun loadGroups() {
        val userId = firebaseService.getCurrentUserId()
        if (userId == null) {
            _errorMessage.value = "Пользователь не авторизован"
            return
        }

        _isLoading.value = true
        _errorMessage.value = ""

        viewModelScope.launch {
            val result = firebaseService.getGroupsForUser(userId)
            _isLoading.value = false

            if (result.isSuccess) {
                _groups.value = result.getOrNull() ?: emptyList()
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "Ошибка загрузки групп"
            }
        }
    }

    fun createGroup(name: String, description: String, currency: String = "USD", onSuccess: () -> Unit) {
        if (name.isBlank()) {
            _errorMessage.value = "Введите название группы"
            return
        }

        _isLoading.value = true
        _errorMessage.value = ""

        viewModelScope.launch {
            val result = firebaseService.createGroup(name, description, currency)
            _isLoading.value = false

            if (result.isSuccess) {
                // Небольшая задержка, чтобы Firestore успел обновить данные
                kotlinx.coroutines.delay(500)
                loadGroups() // Перезагружаем список групп
                onSuccess()
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "Ошибка создания группы"
            }
        }
    }

    fun clearError() {
        _errorMessage.value = ""
    }
    
    suspend fun getTotalExpensesForGroup(groupId: String): Double {
        return try {
            val result = firebaseService.getExpensesForGroup(groupId)
            if (result.isSuccess) {
                val expenses = result.getOrNull() ?: emptyList()
                expenses.sumOf { it.amount }
            } else {
                0.0
            }
        } catch (e: Exception) {
            0.0
        }
    }
}

