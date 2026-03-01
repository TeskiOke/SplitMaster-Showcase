package com.yourname.splitmastersimple.data.models

data class Chat(
    val id: String = "", // ID чата (комбинация userId1_userId2, отсортированная)
    val participant1Id: String = "", // ID первого участника
    val participant2Id: String = "", // ID второго участника
    val lastMessage: String = "", // Последнее сообщение
    val lastMessageTime: Long = 0L, // Время последнего сообщения
    val unreadCount: Int = 0, // Количество непрочитанных сообщений
    val lastSenderId: String = "" // ID отправителя последнего сообщения
)





