package com.yourname.splitmastersimple.utils

import com.yourname.splitmastersimple.data.models.Expense

object BalanceCalculator {
    data class UserBalance(
        val userId: String,
        val totalPaid: Double = 0.0,
        val totalOwed: Double = 0.0,
        val balance: Double = 0.0 // Положительное = должен получить, отрицательное = должен заплатить
    )
    
    /**
     * Рассчитывает балансы всех участников группы на основе расходов
     */
    fun calculateBalances(expenses: List<Expense>, memberIds: List<String>): Map<String, UserBalance> {
        val balances = memberIds.associateWith { userId ->
            UserBalance(userId = userId)
        }.toMutableMap()
        
        // Проходим по всем расходам
        expenses.forEach { expense ->
            val amount = expense.amount
            val paidBy = expense.paidBy
            val participants = expense.participants
            
            if (participants.isEmpty()) return@forEach
            
            // Сумма на одного участника
            val perPerson = amount / participants.size
            
            // Обновляем баланс того, кто заплатил
            balances[paidBy]?.let { current ->
                balances[paidBy] = current.copy(
                    totalPaid = current.totalPaid + amount,
                    balance = current.balance + amount - perPerson
                )
            }
            
            // Обновляем балансы участников (кроме того, кто заплатил)
            participants.forEach { participantId ->
                if (participantId != paidBy) {
                    balances[participantId]?.let { current ->
                        balances[participantId] = current.copy(
                            totalOwed = current.totalOwed + perPerson,
                            balance = current.balance - perPerson
                        )
                    }
                } else {
                    // Если тот, кто заплатил, тоже в списке участников
                    balances[paidBy]?.let { current ->
                        balances[paidBy] = current.copy(
                            balance = current.balance - perPerson
                        )
                    }
                }
            }
        }
        
        return balances
    }
    
    /**
     * Получить баланс конкретного пользователя
     */
    fun getUserBalance(userId: String, expenses: List<Expense>, memberIds: List<String>): UserBalance {
        val allBalances = calculateBalances(expenses, memberIds)
        return allBalances[userId] ?: UserBalance(userId = userId)
    }
}




























