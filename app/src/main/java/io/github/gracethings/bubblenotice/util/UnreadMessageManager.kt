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
        val contentIntent: android.app.PendingIntent? = null,
        val actions: List<android.app.Notification.Action> = emptyList()
    )

    private val _messagesFlow = MutableStateFlow<List<Message>>(emptyList())
    val messagesFlow: StateFlow<List<Message>> = _messagesFlow.asStateFlow()

    private val messagesList = mutableListOf<Message>()

    fun addMessage(packageName: String, senderName: String, messageText: String, timestamp: Long, contentIntent: android.app.PendingIntent? = null, actions: List<android.app.Notification.Action> = emptyList()) {
        synchronized(messagesList) {
            val existingIndex = messagesList.indexOfFirst { it.packageName == packageName && it.senderName == senderName }
            val newMessage = Message(packageName, senderName, messageText, timestamp, contentIntent, actions)
            if (existingIndex != -1) {
                // 为了模仿原生 Android 堆叠通知，这里替换该发送者的现有消息。 / To mimic native Android stacked notifications, replace the existing message from this sender.
                // 对于 MessagingStyle 应用，如 Google Chat，新的 messageText 包含“1\n1\n1”这样的完整历史记录。 / For MessagingStyle apps, such as Google Chat, the new messageText contains the full history like "1\n1\n1".
                // 对于微信，新的 messageText 是类似“[3条]AAA: 1”的摘要。 / For WeChat, the new messageText is a summary such as "[3条]AAA: 1".
                val oldMessage = messagesList[existingIndex]
                val mergedIntent = contentIntent ?: oldMessage.contentIntent
                val mergedActions = if (actions.isNotEmpty()) actions else oldMessage.actions
                
                messagesList[existingIndex] = newMessage.copy(
                    contentIntent = mergedIntent,
                    actions = mergedActions
                )
            } else {
                messagesList.add(newMessage)
            }
            _messagesFlow.value = ArrayList(messagesList)
        }
    }


    fun removeMessage(message: Message) {
        synchronized(messagesList) {
            messagesList.remove(message)
            _messagesFlow.value = ArrayList(messagesList)
        }
    }
    fun clearMessagesForPackage(packageName: String) {
        synchronized(messagesList) {
            messagesList.removeAll { it.packageName == packageName }
            _messagesFlow.value = ArrayList(messagesList)
        }
    }

    fun hasMessagesForPackage(packageName: String): Boolean {
        synchronized(messagesList) {
            return messagesList.any { it.packageName == packageName }
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


