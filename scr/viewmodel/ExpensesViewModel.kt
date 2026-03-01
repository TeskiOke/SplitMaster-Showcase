package com.yourname.splitmastersimple.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.splitmastersimple.data.FirebaseService
import com.yourname.splitmastersimple.data.models.Expense
import com.yourname.splitmastersimple.utils.ErrorLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ExpensesViewModel : ViewModel() {
    private val firebaseService = FirebaseService()

    private val _expenses = MutableStateFlow<List<Expense>>(emptyList())
    val expenses: StateFlow<List<Expense>> = _expenses.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow("")
    val errorMessage: StateFlow<String> = _errorMessage.asStateFlow()

    fun loadExpenses(groupId: String) {
        if (groupId.isBlank()) {
            _errorMessage.value = "ID группы не указан"
            return
        }

        _isLoading.value = true
        _errorMessage.value = ""

        viewModelScope.launch {
            val result = firebaseService.getExpensesForGroup(groupId)
            _isLoading.value = false

            if (result.isSuccess) {
                _expenses.value = result.getOrNull() ?: emptyList()
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "Ошибка загрузки расходов"
            }
        }
    }

    fun addExpense(
        groupId: String,
        title: String,
        amount: Double,
        description: String,
        paidBy: String,
        participants: List<String>,
        onSuccess: () -> Unit,
        context: Context? = null
    ) {
        if (title.isBlank()) {
            _errorMessage.value = "Введите название расхода"
            return
        }

        if (amount <= 0) {
            _errorMessage.value = "Сумма должна быть больше нуля"
            return
        }

        if (participants.isEmpty()) {
            _errorMessage.value = "Выберите участников"
            return
        }

        _isLoading.value = true
        _errorMessage.value = ""

        viewModelScope.launch {
            try {
                val result = firebaseService.addExpense(groupId, title, amount, description, paidBy, participants)
                _isLoading.value = false

                if (result.isSuccess) {
                    _errorMessage.value = "" // Очищаем ошибку при успехе
                    loadExpenses(groupId) // Перезагружаем список расходов
                    onSuccess()
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Ошибка добавления расхода"
                    _errorMessage.value = error
                    // Логируем ошибку (всегда, даже если context null - используем try-catch)
                    try {
                        context?.let { ErrorLogger.logError(it, "Добавление расхода: $error") }
                    } catch (e: Exception) {
                        android.util.Log.e("ExpensesViewModel", "Не удалось залогировать ошибку: ${e.message}")
                    }
                    android.util.Log.e("ExpensesViewModel", "Ошибка добавления расхода: $error")
                }
            } catch (e: Exception) {
                _isLoading.value = false
                val errorMsg = "Ошибка: ${e.message ?: "Неизвестная ошибка"}"
                _errorMessage.value = errorMsg
                // Логируем ошибку (всегда, даже если context null)
                try {
                    context?.let { ErrorLogger.logError(it, "Добавление расхода (исключение): $errorMsg") }
                } catch (ex: Exception) {
                    android.util.Log.e("ExpensesViewModel", "Не удалось залогировать ошибку: ${ex.message}")
                }
                android.util.Log.e("ExpensesViewModel", "Исключение при добавлении расхода: $errorMsg", e)
            }
        }
    }

    fun clearError() {
        _errorMessage.value = ""
    }
}

