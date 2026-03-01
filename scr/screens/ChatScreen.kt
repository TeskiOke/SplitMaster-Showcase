package com.yourname.splitmastersimple.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.yourname.splitmastersimple.data.FirebaseService
import com.yourname.splitmastersimple.data.models.Message
import com.yourname.splitmastersimple.data.models.User
import com.yourname.splitmastersimple.viewmodel.UserCacheViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun ChatScreen(
    otherUserId: String,
    onBackClick: () -> Unit,
    onProfileClick: ((String) -> Unit)? = null
) {
    val firebaseService = FirebaseService()
    val currentUserId = firebaseService.getCurrentUserId() ?: ""
    val userCacheViewModel = androidx.lifecycle.viewmodel.compose.viewModel<UserCacheViewModel>()
    val userCache by userCacheViewModel.userCache.collectAsState()
    
    // Отслеживание статуса онлайн/офлайн:
    // при входе обновляем lastSeen, а оффлайн считаем по таймауту на стороне читающего
    LaunchedEffect(currentUserId) {
        if (currentUserId.isNotEmpty()) {
            firebaseService.setUserOnlineStatus(true)
        }
    }
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    
    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var messageText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var otherUser by remember { mutableStateOf<User?>(null) }
    var chatId by remember { mutableStateOf("") }
    var showClearDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var isClearing by remember { mutableStateOf(false) }
    var showScrollToBottomButton by remember { mutableStateOf(false) }
    var lastMessageCount by remember { mutableStateOf(0) }
    var selectedMessageIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var showRefreshIndicator by remember { mutableStateOf(false) }
    
    val listState = rememberLazyListState()
    
    // Загружаем данные пользователя (первый раз)
    LaunchedEffect(otherUserId) {
        if (otherUserId.isNotEmpty()) {
            userCacheViewModel.loadUsers(listOf(otherUserId), refresh = true)
            val result = firebaseService.getUserById(otherUserId)
            if (result.isSuccess) {
                otherUser = result.getOrNull()
            }
        }
    }
    
    // Тикер для обновления онлайн-статуса в реальном времени (каждые 5 секунд)
    var statusUpdateTrigger by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while(true) {
            kotlinx.coroutines.delay(5000)
            statusUpdateTrigger++
        }
    }

    // Live‑обновление статуса онлайн собеседника
    DisposableEffect(otherUserId) {
        if (otherUserId.isEmpty()) {
            return@DisposableEffect onDispose { }
        }

        val db = FirebaseFirestore.getInstance()
        val reg = db.collection("users").document(otherUserId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

                val data = snapshot.data
                val lastSeenRaw = data?.get("lastSeen")
                val lastSeen = when (lastSeenRaw) {
                    is com.google.firebase.Timestamp -> lastSeenRaw.toDate().time
                    is Long -> lastSeenRaw
                    else -> 0L
                }
                val now = System.currentTimeMillis()
                val isOnline = lastSeen > 0L && (now - lastSeen) < 90_000L
                val privacy = (data?.get("privacy") as? Map<*, *>)?.mapNotNull {
                    val key = it.key as? String
                    val value = it.value as? Boolean
                    if (key != null && value != null) key to value else null
                }?.toMap() ?: emptyMap()

                otherUser = User(
                    id = snapshot.id,
                    userId = data?.get("userId") as? String ?: "",
                    name = data?.get("name") as? String ?: otherUser?.name ?: "Пользователь",
                    email = data?.get("email") as? String ?: otherUser?.email ?: "",
                    emoji = data?.get("emoji") as? String ?: otherUser?.emoji ?: "",
                    isOnline = isOnline,
                    lastSeen = lastSeen,
                    privacy = privacy
                )
            }

        onDispose { reg.remove() }
    }
    
    // Создаем или получаем чат
    LaunchedEffect(currentUserId, otherUserId) {
        if (currentUserId.isNotEmpty() && otherUserId.isNotEmpty()) {
            isLoading = true
            val chatResult = firebaseService.createOrGetChat(otherUserId)
            if (chatResult.isSuccess) {
                chatId = chatResult.getOrNull() ?: ""
                // При входе в чат считаем сообщения прочитанными
                if (chatId.isNotEmpty()) {
                    scope.launch {
                        firebaseService.markMessagesAsRead(chatId)
                    }
                }
            }
            isLoading = false
        }
    }
    
    // Real-time listener для чата (проверка удаления/очистки)
    var chatDeleted by remember { mutableStateOf(false) }
    var chatCleared by remember { mutableStateOf(false) }
    var lastKnownMessageCount by remember { mutableStateOf(0) }
    
    DisposableEffect(chatId) {
        if (chatId.isEmpty()) {
            return@DisposableEffect onDispose { }
        }
        
        val db = FirebaseFirestore.getInstance()
        val chatListener: ListenerRegistration = db.collection("chats").document(chatId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                
                if (snapshot != null) {
                    if (!snapshot.exists()) {
                        // Чат удален
                        chatDeleted = true
                        messages = emptyList()
                        chatCleared = false
                    } else {
                        val data = snapshot.data
                        val lastMessage = data?.get("lastMessage") as? String ?: ""
                        // Проверяем очистку чата (lastMessage пустой и было сообщений)
                        if (lastMessage.isEmpty() && lastKnownMessageCount > 0 && !chatCleared) {
                            // Чат очищен
                            chatCleared = true
                            messages = emptyList()
                            lastKnownMessageCount = 0
                        } else if (lastMessage.isNotEmpty() && chatCleared) {
                            // Если появилось новое сообщение после очистки, сбрасываем флаг
                            chatCleared = false
                        }
                    }
                }
            }
        
        onDispose {
            chatListener.remove()
        }
    }
    
    // Real-time listener для сообщений с отслеживанием изменений
    DisposableEffect(chatId, chatDeleted, chatCleared) {
        if (chatId.isEmpty() || chatDeleted) {
            return@DisposableEffect onDispose { }
        }
        
        val db = FirebaseFirestore.getInstance()
        val messagesQuery: Query = db.collection("messages")
            .whereEqualTo("chatId", chatId)
            .orderBy("timestamp")
        
        var isInitialLoad = true
        
        // Используем AtomicLong для синхронизации между listener'ами
        val chatHistoryDeletedAt = java.util.concurrent.atomic.AtomicLong(0L)
        
        // Загружаем начальное значение historyDeletedAt
        scope.launch {
            try {
                val chatDoc = db.collection("chats").document(chatId).get().await()
                if (chatDoc.exists()) {
                    val historyDeletedAt = (chatDoc.get("historyDeletedAt") as? Map<String, Long>) ?: emptyMap()
                    chatHistoryDeletedAt.set(historyDeletedAt[currentUserId] ?: 0L)
                }
            } catch (e: Exception) {
                // Игнорируем ошибки
            }
        }
        
        // Слушаем изменения чата для обновления historyDeletedAt
        val chatListener = db.collection("chats").document(chatId)
            .addSnapshotListener { chatSnapshot, _ ->
                if (chatSnapshot != null && chatSnapshot.exists()) {
                    val historyDeletedAt = (chatSnapshot.get("historyDeletedAt") as? Map<String, Long>) ?: emptyMap()
                    chatHistoryDeletedAt.set(historyDeletedAt[currentUserId] ?: 0L)
                }
            }
        
        val listenerRegistration: ListenerRegistration = messagesQuery.addSnapshotListener { snapshot, error ->
            if (error != null) {
                return@addSnapshotListener
            }
            
            if (snapshot != null) {
                // Пропускаем кэшированные данные при первой загрузке только если не обновляем вручную
                // НО всегда загружаем данные, если их нет (для исправления бага с пропаданием)
                if (isInitialLoad && snapshot.metadata.isFromCache && !isRefreshing && messages.isNotEmpty()) {
                    return@addSnapshotListener
                }
                isInitialLoad = false
                isRefreshing = false
                
                val newMessages = snapshot.documents.mapNotNull { doc ->
                    val data = doc.data
                    val senderId = data?.get("senderId") as? String ?: ""
                    val deletedForSender = (data?.get("deletedForSender") as? Boolean) ?: false
                    val ts = data?.get("timestamp") as? com.google.firebase.Timestamp
                    val tsMillis = ts?.toDate()?.time ?: (data?.get("timestamp") as? Long) ?: System.currentTimeMillis()
                    
                    // Фильтруем сообщения так же, как в getChatMessages:
                    // 1. Исключаем свои сообщения, помеченные как deletedForSender = true
                    if (senderId == currentUserId && deletedForSender) {
                        return@mapNotNull null
                    }
                    
                    // 2. Исключаем сообщения старше historyDeletedAt (если чат был удален)
                    val deletedAtForMe = chatHistoryDeletedAt.get()
                    if (deletedAtForMe > 0L && tsMillis <= deletedAtForMe) {
                        return@mapNotNull null
                    }
                    
                    com.yourname.splitmastersimple.data.models.Message(
                        id = doc.id,
                        chatId = data?.get("chatId") as? String ?: "",
                        senderId = senderId,
                        text = data?.get("text") as? String ?: "",
                        timestamp = tsMillis,
                        isRead = (data?.get("isRead") as? Boolean) ?: false
                    )
                }.sortedBy { it.timestamp } // Явно сортируем по timestamp для надежности
                
                // Если чат был очищен, игнорируем все обновления до появления новых сообщений
                if (chatCleared) {
                    // Если появились новые сообщения после очистки, сбрасываем флаг и обновляем
                    if (newMessages.isNotEmpty()) {
                        chatCleared = false
                        messages = newMessages
                        lastMessageCount = newMessages.size
                        lastKnownMessageCount = newMessages.size
                    } else {
                        // Игнорируем пустые обновления
                        return@addSnapshotListener
                    }
                } else {
                    // Проверяем, есть ли новые сообщения (только если количество увеличилось)
                    val previousCount = messages.size
                    val hasNew = newMessages.size > previousCount
                    
                    // Обновляем список сообщений только если есть изменения
                    val messagesChanged = newMessages.size != previousCount || 
                        newMessages.map { it.id }.toSet() != messages.map { it.id }.toSet()
                    
                    if (messagesChanged) {
                        messages = newMessages
                        lastMessageCount = newMessages.size
                        lastKnownMessageCount = newMessages.size
                        
                        // Загружаем данные отправителей сообщений
                        val senderIds = newMessages.map { it.senderId }.distinct()
                        if (senderIds.isNotEmpty()) {
                            userCacheViewModel.loadUsers(senderIds)
                        }
                        
                        // Прокручиваем вниз только если пользователь внизу или это новое сообщение от другого пользователя
                        val hasNew = newMessages.size > previousCount
                        if (hasNew) {
                            val isNearBottom = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 >= messages.size - 3
                            val lastMessageSender = newMessages.lastOrNull()?.senderId
                            // Автопрокрутка только если пользователь внизу ИЛИ это сообщение от другого пользователя
                            if (isNearBottom && lastMessageSender != currentUserId) {
                                scope.launch {
                                    kotlinx.coroutines.delay(100)
                                    if (newMessages.isNotEmpty()) {
                                        listState.animateScrollToItem(newMessages.size - 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        onDispose {
            listenerRegistration.remove()
            chatListener.remove()
        }
    }
    
    // Обработка удаления/очистки чата
    LaunchedEffect(chatDeleted) {
        if (chatDeleted) {
            kotlinx.coroutines.delay(500)
            onBackClick()
        }
    }
    
    // Проверяем, нужно ли показывать кнопку прокрутки вниз
    // Кнопка появляется только если пользователь прокрутил вверх более чем на 5-10 сообщений
    LaunchedEffect(listState.firstVisibleItemIndex, messages.size) {
        val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        val threshold = 7 // Показываем кнопку если до конца больше 7 сообщений
        val shouldShow = lastVisibleIndex < messages.size - threshold && messages.isNotEmpty()
        showScrollToBottomButton = shouldShow
    }
    
    // Прокрутка при отправке сообщения
    LaunchedEffect(messageText) {
        if (messageText.isEmpty() && messages.isNotEmpty()) {
            // Сообщение отправлено, прокручиваем вниз
            kotlinx.coroutines.delay(150)
            scope.launch {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }
    
    val userName = otherUser?.name ?: userCache[otherUserId]?.name ?: "Пользователь"
    val userEmoji = if (otherUser?.emoji?.isNotEmpty() == true) {
        otherUser!!.emoji
    } else {
        userCache[otherUserId]?.emoji ?: "?"
    }
    
    // Используем statusUpdateTrigger для принудительной перерекомпозиции только статуса
    val showOnlineStatus = remember(otherUser, statusUpdateTrigger) {
        otherUser?.shouldShowOnline() ?: userCache[otherUserId]?.shouldShowOnline() ?: true
    }
    val otherIsOnline = remember(otherUser, statusUpdateTrigger) {
        otherUser?.isActuallyOnline() ?: userCache[otherUserId]?.isActuallyOnline() == true
    }
    
    val chatStatusColor = when {
        !showOnlineStatus -> Color.Gray
        otherIsOnline -> Color(0xFF4CAF50)
        else -> Color(0xFFF44336)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                val thresholdPx = 100.dp.toPx()
                                detectDragGestures(
                                    onDragEnd = {
                                        if (showRefreshIndicator) {
                                            scope.launch {
                                                isRefreshing = true
                                                showRefreshIndicator = false
                                                // Перезагружаем сообщения
                                                val db = FirebaseFirestore.getInstance()
                                                val messagesQuery: Query = db.collection("messages")
                                                    .whereEqualTo("chatId", chatId)
                                                    .orderBy("timestamp")
                                                val snapshot = messagesQuery.get().await()
                                                val newMessages = snapshot.documents.map { doc ->
                                                    val data = doc.data
                                                    val ts = data?.get("timestamp") as? com.google.firebase.Timestamp
                                                    val tsMillis = ts?.toDate()?.time ?: System.currentTimeMillis()
                                                    com.yourname.splitmastersimple.data.models.Message(
                                                        id = doc.id,
                                                        chatId = data?.get("chatId") as? String ?: "",
                                                        senderId = data?.get("senderId") as? String ?: "",
                                                        text = data?.get("text") as? String ?: "",
                                                        timestamp = tsMillis,
                                                        isRead = (data?.get("isRead") as? Boolean) ?: false
                                                    )
                                                }
                                                messages = newMessages
                                                isRefreshing = false
                                                // Показываем индикацию успешного обновления
                                                showRefreshIndicator = true
                                                scope.launch {
                                                    kotlinx.coroutines.delay(2000)
                                                    showRefreshIndicator = false
                                                }
                                            }
                                        }
                                    }
                                ) { change, _ ->
                                    val currentY = change.position.y
                                    if (currentY > thresholdPx) {
                                        showRefreshIndicator = true
                                    } else if (currentY < thresholdPx / 2f) {
                                        showRefreshIndicator = false
                                    }
                                }
                            },
                        // Выравниваем содержимое (эмодзи + имя) к левому краю, как раньше
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (showRefreshIndicator || isRefreshing) {
                            Text(
                                text = if (isRefreshing) "Обновление..." else "↓ Обновить",
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        } else {
                            Row(
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Эмодзи с цветной рамкой по кругу, аккуратно по центру
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .border(
                                            width = 2.dp,
                                            color = chatStatusColor,
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = userEmoji,
                                        fontSize = 20.sp,
                                        fontFamily = FontFamily.Default,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = userName,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White,
                                    modifier = Modifier.clickable {
                                        if (onProfileClick != null) {
                                            onProfileClick(otherUserId)
                                        }
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                ),
                navigationIcon = {
                    if (!isSelectionMode) {
                        Icon(
                            painter = painterResource(id = android.R.drawable.ic_menu_revert),
                            contentDescription = "Back",
                            modifier = Modifier
                                .padding(start = 16.dp)
                                .clickable { onBackClick() }
                        )
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        // Кнопка отмены
                        TextButton(
                            onClick = {
                                selectedMessageIds = emptySet()
                                isSelectionMode = false
                            }
                        ) {
                            Text(
                                text = "Отмена",
                                color = Color.White
                            )
                        }
                        
                        // Кнопка удаления
                        IconButton(
                            onClick = {
                                if (selectedMessageIds.isNotEmpty()) {
                                    val messagesToDelete = selectedMessageIds.toSet()
                                    selectedMessageIds = emptySet()
                                    isSelectionMode = false
                                    scope.launch {
                                        firebaseService.deleteSelectedMessages(chatId, messagesToDelete)
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Удалить",
                                tint = Color.White
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Сообщение об удалении/очистке чата
                if (chatDeleted) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Пользователь удалил чат с вами",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else if (chatCleared && messages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Чат очищен",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 16.sp
                        )
                    }
                }
                
                // Список сообщений (без полосы прокрутки)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                    itemsIndexed(messages) { index, message ->
                        val sender = if (message.senderId == currentUserId) {
                            "Вы"
                        } else {
                            userCache[message.senderId]?.name ?: "Пользователь"
                        }
                        val isSelected = message.id in selectedMessageIds
                        val isMyMessage = message.senderId == currentUserId
                        val selectionIndex = if (isSelected && isMyMessage) {
                            selectedMessageIds.filter { msgId ->
                                messages.find { it.id == msgId }?.senderId == currentUserId
                            }.sortedBy { msgId ->
                                messages.indexOfFirst { it.id == msgId }
                            }.indexOf(message.id) + 1
                        } else null

                        // Разделитель даты (мини-сообщение по центру)
                        val showDateHeader = index == 0 || !isSameDay(
                            messages[index - 1].timestamp,
                            message.timestamp
                        )
                        if (showDateHeader) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                ) {
                                    Text(
                                        text = formatMessageDate(message.timestamp),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        MessageBubble(
                            message = message,
                            isMyMessage = isMyMessage,
                            senderName = sender,
                            senderId = message.senderId,
                            isSelected = isSelected,
                            selectionIndex = selectionIndex,
                            isSelectionMode = isSelectionMode,
                            onClick = if (isSelectionMode && isMyMessage) {
                                {
                                    selectedMessageIds = if (isSelected) {
                                        selectedMessageIds - message.id
                                    } else {
                                        selectedMessageIds + message.id
                                    }
                                    // Если все сообщения отменены, выходим из режима выбора
                                    if (selectedMessageIds.isEmpty()) {
                                        isSelectionMode = false
                                    }
                                }
                            } else null,
                            onLongClick = if (isMyMessage) {
                                {
                                    if (!isSelectionMode) {
                                        isSelectionMode = true
                                    }
                                    selectedMessageIds = if (isSelected) {
                                        selectedMessageIds - message.id
                                    } else {
                                        selectedMessageIds + message.id
                                    }
                                    // Если все сообщения отменены, выходим из режима выбора
                                    if (selectedMessageIds.isEmpty()) {
                                        isSelectionMode = false
                                    }
                                }
                            } else null,
                            // Профиль теперь открывается только по имени в шапке, а не по имени в сообщениях
                            onSenderNameClick = null
                        )
                    }
                    }
                    
                    // Кнопка "Jump to Latest Message" (FAB) в правом нижнем углу
                    if (showScrollToBottomButton) {
                        FloatingActionButton(
                            onClick = {
                                scope.launch {
                                    if (messages.isNotEmpty()) {
                                        listState.animateScrollToItem(messages.size - 1)
                                    }
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 16.dp, bottom = 80.dp), // Выше поля ввода
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(
                                defaultElevation = 6.dp,
                                pressedElevation = 8.dp
                            )
                        ) {
                            Text(
                                text = "↓",
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                
                // Поле ввода сообщения (без Card, просто Row)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                            OutlinedTextField(
                                value = messageText,
                                onValueChange = { messageText = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { 
                                    Text(
                                        "Введите сообщение...",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                ),
                                shape = RoundedCornerShape(24.dp),
                                maxLines = 5,
                                singleLine = false
                            )
                            
                            IconButton(
                                onClick = {
                                    if (messageText.isNotBlank() && chatId.isNotEmpty()) {
                                        val textToSend = messageText
                                        messageText = ""
                                        scope.launch {
                                            val result = firebaseService.sendMessage(chatId, textToSend)
                                            if (!result.isSuccess) {
                                                messageText = textToSend
                                            }
                                        }
                                    }
                                },
                                enabled = messageText.isNotBlank() && chatId.isNotEmpty()
                            ) {
                                Icon(
                                    painter = painterResource(id = android.R.drawable.ic_menu_send),
                                    contentDescription = "Отправить",
                                    tint = if (messageText.isNotBlank() && chatId.isNotEmpty()) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    },
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                }
            }
        
        // Диалог подтверждения очистки чата
        if (showClearDialog) {
            AlertDialog(
                onDismissRequest = { showClearDialog = false },
                title = { Text("Очистить свои сообщения?") },
                text = { 
                    Text("Все ваши сообщения будут удалены, но сообщения собеседника останутся. Чат останется активным.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                isClearing = true
                                val result = firebaseService.clearMyMessages(chatId)
                                isClearing = false
                                if (result.isSuccess) {
                                    showClearDialog = false
                                }
                            }
                        },
                        enabled = !isClearing
                    ) {
                        Text(
                            if (isClearing) "Удаление..." else "Удалить",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showClearDialog = false },
                        enabled = !isClearing
                    ) {
                        Text("Отмена")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    isMyMessage: Boolean,
    senderName: String? = null,
    senderId: String,
    isSelected: Boolean = false,
    selectionIndex: Int? = null,
    isSelectionMode: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onSenderNameClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = if (isMyMessage) Arrangement.End else Arrangement.Start
    ) {
        // Индикатор выбора
        if (isSelected && selectionIndex != null) {
            Box(
                modifier = Modifier
                    .padding(end = 4.dp)
                    .size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = selectionIndex.toString(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        Card(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .then(
                    when {
                        onClick != null && onLongClick != null -> {
                            Modifier.combinedClickable(
                                onClick = onClick,
                                onLongClick = onLongClick
                            )
                        }
                        onLongClick != null -> {
                            Modifier.combinedClickable(
                                onClick = { },
                                onLongClick = onLongClick
                            )
                        }
                        onClick != null -> {
                            Modifier.clickable(onClick = onClick)
                        }
                        else -> Modifier
                    }
                ),
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (isMyMessage) 20.dp else 4.dp,
                bottomEnd = if (isMyMessage) 4.dp else 20.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    isSelected && isMyMessage -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    isSelected && !isMyMessage -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                    isMyMessage -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 2.dp,
                pressedElevation = 4.dp
            ),
            border = if (isSelected) {
                BorderStroke(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else null
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                // Имя отправителя
                if (senderName != null && !isMyMessage) {
                    Text(
                        text = senderName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(bottom = 4.dp)
                            .then(
                                if (onSenderNameClick != null) {
                                    Modifier.clickable(onClick = onSenderNameClick)
                                } else {
                                    Modifier
                                }
                            )
                    )
                }
                
                // Текст сообщения
                Text(
                    text = message.text,
                    fontSize = 15.sp,
                    color = if (isMyMessage) {
                        Color.White
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    lineHeight = 21.sp,
                    letterSpacing = 0.2.sp
                )
                
                // Время отправки в одной строке с текстом
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = formatMessageTime(message.timestamp),
                        fontSize = 11.sp,
                        color = if (isMyMessage) {
                            Color.White.copy(alpha = 0.8f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        },
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

fun formatMessageTime(timestamp: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
}

fun isSameDay(timestamp1: Long, timestamp2: Long): Boolean {
    if (timestamp1 == 0L || timestamp2 == 0L) return false
    val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    return dateFormat.format(Date(timestamp1)) == dateFormat.format(Date(timestamp2))
}

fun formatMessageDate(timestamp: Long): String {
    return SimpleDateFormat("d MMMM", Locale.getDefault()).format(Date(timestamp))
}
