package org.example

class DynamicMessage {
    private val userMessages = mutableMapOf<Long, MutableList<MessageInfo>>()

    data class MessageInfo(
        val messageId: Long,
        val messageType: MessageType,
        val timestamp: Long = System.currentTimeMillis()
    )

    enum class MessageType {
        MENU,
        STATISTICS,
        ANSWER_RESULT,
        WORD_LIST,
        NONE
    }

    fun addMessage(chatId: Long, messageId: Long, type: MessageType) {
        val messages = userMessages.getOrPut(chatId) { mutableListOf() }
        messages.add(MessageInfo(messageId, type))

        if (messages.size > 10) {
            messages.removeAt(0)
        }
    }

    fun getLastMessageId(chatId: Long): Long? {
        return userMessages[chatId]?.lastOrNull()?.messageId
    }

    fun getLastMessageType(chatId: Long): MessageType {
        return userMessages[chatId]?.lastOrNull()?.messageType ?: MessageType.NONE
    }

    fun undo(chatId: Long): MessageInfo? {
        val messages = userMessages[chatId] ?: return null
        if (messages.size <= 1) return null

        messages.removeAt(messages.size - 1)

        return messages.lastOrNull()
    }
}
