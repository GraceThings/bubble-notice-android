package io.github.gracethings.bubblenotice.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object UnreadMessageManager {
    data class Message(
        val packageName: String,
        val senderName: String,
        val messageText: String,
        val timestamp: Long
    )

    private val _messagesFlow = MutableStateFlow<List<Message>>(emptyList())
    val messagesFlow: StateFlow<List<Message>> = _messagesFlow.asStateFlow()

    private val messagesList = mutableListOf<Message>()

    fun addMessage(packageName: String, senderName: String, messageText: String, timestamp: Long) {
        synchronized(messagesList) {
            // 避免在短时间内添加完全相同的重复消息 / Avoid adding identical duplicate messages in quick succession.
            val isDuplicate = messagesList.any { 
                it.packageName == packageName && 
                it.senderName == senderName && 
                it.messageText == messageText && 
                Math.abs(it.timestamp - timestamp) < 1000 
            }
            if (!isDuplicate) {
                messagesList.add(Message(packageName, senderName, messageText, timestamp))
                _messagesFlow.value = ArrayList(messagesList)
            }
        }
    }

    fun clearMessagesForSender(packageName: String, senderName: String) {
        synchronized(messagesList) {
            messagesList.removeAll { it.packageName == packageName && it.senderName == senderName }
            _messagesFlow.value = ArrayList(messagesList)
        }
    }

    fun clearAll() {
        synchronized(messagesList) {
            messagesList.clear()
            _messagesFlow.value = emptyList()
        }
    }
}
