package com.yourname.splitmastersimple.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourname.splitmastersimple.components.ExpenseItem
import com.yourname.splitmastersimple.components.ExpenseDetailDialog
import com.yourname.splitmastersimple.components.MemberItem
import androidx.compose.foundation.clickable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yourname.splitmastersimple.data.FirebaseService
import com.yourname.splitmastersimple.data.models.Expense
import com.yourname.splitmastersimple.utils.BalanceCalculator
import com.yourname.splitmastersimple.utils.Currency
import com.yourname.splitmastersimple.viewmodel.ExpensesViewModel
import com.yourname.splitmastersimple.viewmodel.UserCacheViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    groupId: String,
    onBackClick: () -> Unit,
    onAddExpenseClick: () -> Unit
) {
    val expensesViewModel: ExpensesViewModel = viewModel()
    val userCacheViewModel: UserCacheViewModel = viewModel()
    val firebaseService = FirebaseService()
    val expenses by expensesViewModel.expenses.collectAsState()
    val isLoading by expensesViewModel.isLoading.collectAsState()
    val userCache by userCacheViewModel.userCache.collectAsState()
    
    var groupName by remember { mutableStateOf("Загрузка...") }
    var members by remember { mutableStateOf<List<String>>(emptyList()) }
    var groupCurrency by remember { mutableStateOf("USD") }
    
    // State for dialogs
    var selectedExpense by remember { mutableStateOf<Expense?>(null) }
    var showPhantomDialog by remember { mutableStateOf(false) }
    var phantomName by remember { mutableStateOf("") }
    
    // State for multi-select deletion
    var selectedPhantoms by remember { mutableStateOf<Set<String>>(emptySet()) }
    val isSelectionMode = selectedPhantoms.isNotEmpty()
    
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    
    // Рассчитываем балансы
    val balances = remember(expenses, members) {
        if (members.isNotEmpty() && expenses.isNotEmpty()) {
            BalanceCalculator.calculateBalances(expenses, members)
        } else {
            emptyMap()
        }
    }
    
    // Загрузка группы и расходов
    LaunchedEffect(groupId) {
        // Загружаем группу
        val groupResult = firebaseService.getGroupById(groupId)
        if (groupResult.isSuccess) {
            val group = groupResult.getOrNull()
            if (group != null) {
                groupName = group.name
                members = group.members
                groupCurrency = group.currency
                // Загружаем имена пользователей СРАЗУ, до отображения (включая фантомов)
                userCacheViewModel.loadUsersForGroup(group.members, groupId)
                // Небольшая задержка для загрузки имен
                kotlinx.coroutines.delay(100)
            }
        }
        
        // Загружаем расходы
        expensesViewModel.loadExpenses(groupId)
    }
    
    // Загружаем имена пользователей для расходов
    LaunchedEffect(expenses) {
        val userIds = expenses.flatMap { listOf(it.paidBy) + it.participants }.distinct()
        if (userIds.isNotEmpty()) {
            userCacheViewModel.loadUsers(userIds)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isSelectionMode) "Выбрано: ${selectedPhantoms.size}" else groupName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                ),
                navigationIcon = {
                    Icon(
                        painter = painterResource(id = if (isSelectionMode) android.R.drawable.ic_menu_close_clear_cancel else android.R.drawable.ic_menu_revert),
                        contentDescription = if (isSelectionMode) "Cancel" else "Back",
                        modifier = Modifier
                            .padding(start = 16.dp)
                            .clickable { 
                                if (isSelectionMode) {
                                    selectedPhantoms = emptySet()
                                } else {
                                    onBackClick()
                                }
                            }
                    )
                },
                actions = {
                    if (isSelectionMode) {
                        androidx.compose.material3.IconButton(
                            onClick = {
                                scope.launch {
                                    selectedPhantoms.forEach { phantomId ->
                                        firebaseService.deletePhantomUser(phantomId, groupId)
                                    }
                                    selectedPhantoms = emptySet()
                                    // Refresh group members
                                    val groupResult = firebaseService.getGroupById(groupId)
                                    if (groupResult.isSuccess) {
                                        val group = groupResult.getOrNull()
                                        if (group != null) {
                                            members = group.members
                                            userCacheViewModel.loadUsersForGroup(members, groupId)
                                        }
                                    }
                                    android.widget.Toast.makeText(context, "Участники удалены", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(
                                painter = painterResource(id = android.R.drawable.ic_menu_delete),
                                contentDescription = "Delete",
                                tint = Color.White
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            Button(
                onClick = onAddExpenseClick,
                modifier = Modifier.padding(16.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_menu_add),
                        contentDescription = "Add Expense",
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(text = "Добавить расход")
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Статистика группы
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Общая статистика",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Всего расходов",
                                    fontSize = 14.sp,
                                    color = Color.Gray
                                )
                                Text(
                                    text = Currency.formatAmount(expenses.sumOf { it.amount }, groupCurrency),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Column {
                                Text(
                                    text = "Кол-во трат",
                                    fontSize = 14.sp,
                                    color = Color.Gray
                                )
                                Text(
                                    text = "${expenses.size}",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Column {
                                Text(
                                    text = "Участников",
                                    fontSize = 14.sp,
                                    color = Color.Gray
                                )
                                Text(
                                    text = "${members.size}",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Участники
            item {
                Text(
                    text = "Участники",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            items(members) { memberId ->
                val balance = balances[memberId]?.balance ?: 0.0
                val user = userCache[memberId]
                val isPhantom = user?.isPhantom == true
                
                MemberItem(
                    name = user?.name ?: "Загрузка...",
                    emoji = user?.emoji ?: "?",
                    balance = balance,
                    currency = groupCurrency,
                    isSelectionMode = isSelectionMode,
                    isSelected = selectedPhantoms.contains(memberId),
                    isPhantom = isPhantom,
                    onSelect = if (isPhantom) {
                        {
                            selectedPhantoms = if (selectedPhantoms.contains(memberId)) {
                                selectedPhantoms - memberId
                            } else {
                                selectedPhantoms + memberId
                            }
                        }
                    } else null,
                    onLongClick = if (isPhantom && !isSelectionMode) {
                        {
                            // Long press activates selection mode and selects this phantom
                            selectedPhantoms = setOf(memberId)
                        }
                    } else null
                )
            }
            
            // Add Phantom Button (Small)
            item {
                TextButton(
                    onClick = { showPhantomDialog = true },
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Icon(painter = painterResource(id = android.R.drawable.ic_input_add), contentDescription = null)
                    Spacer(modifier = Modifier.padding(4.dp))
                    Text("Добавить участника (без аккаунта)")
                }
            }

            // Расходы
            item {
                Text(
                    text = "Последние расходы",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                )
            }

            if (isLoading && expenses.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        androidx.compose.material3.CircularProgressIndicator()
                    }
                }
            } else {
                items(expenses) { expense ->
                    val paidByName = userCache[expense.paidBy]?.name
                    ExpenseItem(
                        title = expense.title,
                        amount = expense.amount,
                        paidBy = paidByName ?: "Загрузка...",
                        date = expense.createdAt,
                        currency = groupCurrency,
                        onClick = { selectedExpense = expense }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
        
        // Dialogs
        if (selectedExpense != null) {
            val expense = selectedExpense!! // Safe because of logic
            val paidByName = userCache[expense.paidBy]?.name ?: "..."
            val participantNames = expense.participants.mapNotNull { participantId ->
                val user = userCache[participantId]
                if (user != null) {
                    Pair(user.emoji.ifEmpty { "?" }, user.name)
                } else {
                    null
                }
            }
            
            ExpenseDetailDialog(
                expense = expense,
                paidByName = paidByName,
                participantNames = participantNames,
                currency = groupCurrency,
                onDismiss = { selectedExpense = null },
                onDelete = {
                    scope.launch {
                        val result = firebaseService.deleteExpense(expense.id)
                        if (result.isSuccess) {
                            android.widget.Toast.makeText(context, "Расход удален", android.widget.Toast.LENGTH_SHORT).show()
                            selectedExpense = null
                            expensesViewModel.loadExpenses(groupId) // Refresh
                        } else {
                            android.widget.Toast.makeText(context, "Ошибка: ${result.exceptionOrNull()?.message}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }
        
        if (showPhantomDialog) {
            AlertDialog(
                onDismissRequest = { showPhantomDialog = false },
                title = { Text("Новый участник") },
                text = {
                    Column {
                        Text("Создать виртуального участника для распределения долгов (например, для тех, у кого нет приложения).")
                        Spacer(modifier = Modifier.height(8.dp))
                        androidx.compose.material3.OutlinedTextField(
                            value = phantomName,
                            onValueChange = { phantomName = it },
                            label = { Text("Имя") },
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (phantomName.isNotBlank()) {
                                scope.launch {
                                    val result = firebaseService.createPhantomUser(phantomName, groupId)
                                    if (result.isSuccess) {
                                        showPhantomDialog = false
                                        phantomName = ""
                                        // Refresh group members
                                        val groupResult = firebaseService.getGroupById(groupId)
                                        val group = groupResult.getOrNull()
                                        if (group != null) {
                                            members = group.members
                                            userCacheViewModel.loadUsersForGroup(members, groupId)
                                        }
                                        android.widget.Toast.makeText(context, "Участник добавлен", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        android.widget.Toast.makeText(context, "Ошибка: ${result.exceptionOrNull()?.message}", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    ) {
                        Text("Создать")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPhantomDialog = false }) {
                        Text("Отмена")
                    }
                }
            )
        }
    }
}