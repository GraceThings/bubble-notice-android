/*
 * Copyright (C) 2026 Grace Chan <velviagris@outlook.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package io.github.gracethings.bubblenotice.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object UnreadMessageManager {
    data class Message(
        val packageName: String,
        val senderName: String,
        val messageText: String,
        val timestamp: Long,
        val contentIntent: android.app.PendingIntent? = null
    )

    private val _messagesFlow = MutableStateFlow<List<Message>>(emptyList())
    val messagesFlow: StateFlow<List<Message>> = _messagesFlow.asStateFlow()

    private val messagesList = mutableListOf<Message>()

    fun addMessage(packageName: String, senderName: String, messageText: String, timestamp: Long, contentIntent: android.app.PendingIntent? = null) {
        synchronized(messagesList) {
            // 避免在短时间内添加完全相同的重复消息 / Avoid adding identical duplicate messages in quick succession.
            val isDuplicate = messagesList.any { 
                it.packageName == packageName && 
                it.senderName == senderName && 
                it.messageText == messageText && 
                Math.abs(it.timestamp - timestamp) < 1000 
            }
            if (!isDuplicate) {
                messagesList.add(Message(packageName, senderName, messageText, timestamp, contentIntent))
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
