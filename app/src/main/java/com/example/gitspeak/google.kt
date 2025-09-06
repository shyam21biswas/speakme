package com.example.gitspeak

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.toObject

import kotlinx.coroutines.tasks.await
import androidx.compose.runtime.State

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID


// Firebase Repository
class ChatRepository {
    private val db = Firebase.firestore
    private val auth = Firebase.auth

    private fun getCurrentUserId(): String {
        return auth.currentUser?.uid ?: "anonymous"
    }

    // Chat Sessions Operations
    suspend fun createChatSession(session: ChatSession): Result<ChatSession> {
        return try {
            val sessionWithUserId = session.copy(userId = getCurrentUserId())
            db.collection("chatSessions")
                .document(session.id)
                .set(sessionWithUserId)
                .await()
            Result.success(sessionWithUserId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getChatSessions(): Result<List<ChatSession>> {

            try {
            val snapshot = db.collection("chatSessions")
                .whereEqualTo("userId", getCurrentUserId())
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .await()

            val sessions = snapshot.documents.mapNotNull { document ->
                document.toObject<ChatSession>()
            }
             return Result.success(sessions)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    suspend fun updateChatSession(session: ChatSession): Result<Unit> {
        return try {
            db.collection("chatSessions")
                .document(session.id)
                .set(session.copy(userId = getCurrentUserId()))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteChatSession(sessionId: String): Result<Unit> {
        return try {
            // Delete all messages in the session
            val messagesSnapshot = db.collection("chatMessages")
                .whereEqualTo("sessionId", sessionId)
                .get()
                .await()

            val batch = db.batch()
            messagesSnapshot.documents.forEach { document ->
                batch.delete(document.reference)
            }

            // Delete the session
            batch.delete(db.collection("chatSessions").document(sessionId))
            batch.commit().await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Messages Operations
    suspend fun addMessage(sessionId: String, message: ChatMessage): Result<ChatMessage> {
        return try {
            val messageData = hashMapOf(
                "id" to message.id,
                "content" to message.content,
                "isFromUser" to message.isFromUser,
                "timestamp" to message.timestamp,
                "sessionId" to sessionId,
                "userId" to getCurrentUserId()
            )

            db.collection("chatMessages")
                .document(message.id)
                .set(messageData)
                .await()

            Result.success(message)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMessages(sessionId: String): Result<List<ChatMessage>> {
        return try {
            val snapshot = db.collection("chatMessages")
                .whereEqualTo("sessionId", sessionId)
                .whereEqualTo("userId", getCurrentUserId())
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .get()
                .await()

            val messages = snapshot.documents.mapNotNull { document ->
                val data = document.data
                data?.let {
                    ChatMessage(
                        id = it["id"] as? String ?: "",
                        content = it["content"] as? String ?: "",
                        isFromUser = it["isFromUser"] as? Boolean ?: false,
                        timestamp = it["timestamp"] as? Long ?: 0L
                    )
                }
            }
            Result.success(messages)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Real-time listeners
    fun listenToChatSessions(onUpdate: (List<ChatSession>) -> Unit): ListenerRegistration {
        return db.collection("chatSessions")
            .whereEqualTo("userId", getCurrentUserId())
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }

                val sessions = snapshot?.documents?.mapNotNull { document ->
                    document.toObject<ChatSession>()
                } ?: emptyList()

                onUpdate(sessions)
            }
    }

    fun listenToMessages(sessionId: String, onUpdate: (List<ChatMessage>) -> Unit): ListenerRegistration {
        return db.collection("chatMessages")
            .whereEqualTo("sessionId", sessionId)
            .whereEqualTo("userId", getCurrentUserId())
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }

                val messages = snapshot?.documents?.mapNotNull { document ->
                    val data = document.data
                    data?.let {
                        ChatMessage(
                            id = it["id"] as? String ?: "",
                            content = it["content"] as? String ?: "",
                            isFromUser = it["isFromUser"] as? Boolean ?: false,
                            timestamp = it["timestamp"] as? Long ?: 0L
                        )
                    }
                } ?: emptyList()

                onUpdate(messages)
            }
    }
}





class ChatViewModel : ViewModel() {
    private val repository = ChatRepository()

    private val _chatSessions = mutableStateOf<List<ChatSession>>(emptyList())
    val chatSessions: State<List<ChatSession>> = _chatSessions

    private val _currentMessages = mutableStateOf<List<ChatMessage>>(emptyList())
    val currentMessages: State<List<ChatMessage>> = _currentMessages

    private val _currentChatId = mutableStateOf<String?>(null)
    val currentChatId: State<String?> = _currentChatId

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    private val _errorMessage = mutableStateOf<String?>(null)
    val errorMessage: State<String?> = _errorMessage

    private var sessionsListener: ListenerRegistration? = null
    private var messagesListener: ListenerRegistration? = null

    init {
        loadChatSessions()
    }

    private fun loadChatSessions() {
        sessionsListener = repository.listenToChatSessions { sessions ->
            _chatSessions.value = sessions
        }
    }


    fun createNewChatAndGetId(): String {
        val newId = UUID.randomUUID().toString()

        val chatSession = ChatSession(
            id = newId,
            title = "New Chat",
            lastMessage = "",
            timestamp = System.currentTimeMillis(),
            messageCount = 0,
            userId = "someUserId" // replace with actual current user if needed
        )

        // Save as map to Firestore (Firestore doesn’t mind a data class either, but map is fine)
        val chatMap = mapOf(
            "id" to chatSession.id,
            "title" to chatSession.title,
            "lastMessage" to chatSession.lastMessage,
            "timestamp" to chatSession.timestamp,
            "messageCount" to chatSession.messageCount,
            "userId" to chatSession.userId
        )
        Firebase.firestore.collection("chatSessions")
            .document(newId)
            .set(chatMap)

        // Update state — now strongly typed
        _chatSessions.value = listOf(chatSession) + _chatSessions.value
        _currentChatId.value = newId
        _currentMessages.value = emptyList()

        return newId
    }






    fun createNewChat() {
        viewModelScope.launch {
            try {
                val newChat = ChatSession(
                    title = "New Chat",
                    lastMessage = "",
                    timestamp = System.currentTimeMillis(),
                    messageCount = 0
                )
                val result = repository.createChatSession(newChat)
                result.onSuccess { session ->
                    _currentChatId.value = session.id
                    _currentMessages.value = emptyList()
                    _chatSessions.value = listOf(session) + _chatSessions.value
                    loadMessages(session.id)

                   /* // 🔥 add default bot message
                    val welcomeMessage = ChatMessage(
                        content = "👋 Hello! I’m your AI assistant. Ask me anything to get started.",
                        isFromUser = false
                    )
                    repository.addMessage(session.id, welcomeMessage)
                    _currentMessages.value = listOf(welcomeMessage)*/

                }.onFailure { exception ->
                    _errorMessage.value = exception.message
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message
            }
        }
    }


    fun loadChat(chatId: String) {
        _currentChatId.value = chatId
        loadMessages(chatId)
    }

    private fun loadMessages(sessionId: String) {
        messagesListener?.remove()
        messagesListener = repository.listenToMessages(sessionId) { messages ->
            _currentMessages.value = messages
        }
    }



    fun sendMessagef(input: String) {
        if (input.trim().isEmpty()) return

        viewModelScope.launch {
            try {
                var sessionId = _currentChatId.value
                if (sessionId.isNullOrEmpty()) {
                    sessionId = createNewChatAndGetId()
                    _currentChatId.value = sessionId
                    _currentMessages.value = emptyList() // reset messages for new session
                    // 🔥 Add the new session locally so it shows up in drawer
                    val newSession = ChatSession(
                        id = sessionId,
                        title = "New Chat",
                        lastMessage = "",
                        timestamp = System.currentTimeMillis(),
                        messageCount = 0
                    )
                    _chatSessions.value = _chatSessions.value + newSession

                }

                _isLoading.value = true

                // 1️⃣ User message
                val userMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    content = input.trim(),
                    isFromUser = true,
                    timestamp = System.currentTimeMillis()
                )
                repository.addMessage(sessionId, userMessage)
                _currentMessages.value = _currentMessages.value + userMessage

                // 2️⃣ Typing indicator
                val typingMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    content = "",
                    isFromUser = false,
                    isTyping = true,
                    timestamp = System.currentTimeMillis()
                )
                _currentMessages.value = _currentMessages.value + typingMessage

                delay(2000) // simulate AI delay

                // 3️⃣ Replace typing indicator with real AI message
                val aiResponse = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    content = generateResponse(input),
                    isFromUser = false,
                    timestamp = System.currentTimeMillis()
                )
                repository.addMessage(sessionId, aiResponse)
                _currentMessages.value =
                    _currentMessages.value.filterNot { it.isTyping } + aiResponse

                // 4️⃣ Update session metadata
                val currentSession = _chatSessions.value.find { it.id == sessionId }
                currentSession?.let { session ->
                    val updatedSession = session.copy(
                        title = if (session.messageCount == 0) input.take(30) else session.title,
                        lastMessage = aiResponse.content.take(50),
                        timestamp = System.currentTimeMillis(),
                        messageCount = session.messageCount + 2
                    )
                    repository.updateChatSession(updatedSession)
                }

                _isLoading.value = false
            } catch (e: Exception) {
                _errorMessage.value = e.message
                _isLoading.value = false
            }
        }
    }
    fun sendMessage(input: String) {
        if (input.trim().isEmpty()) return

        viewModelScope.launch {
            try {
                var sessionId = _currentChatId.value
                if (sessionId.isNullOrEmpty()) {
                    sessionId = createNewChatAndGetId()
                    _currentChatId.value = sessionId
                    _currentMessages.value = emptyList() // reset messages for new session

                    // 🔥 Add the new session locally so it shows up in drawer immediately
                    val newSession = ChatSession(
                        id = sessionId,
                        title = "New Chat",
                        lastMessage = "",
                        timestamp = System.currentTimeMillis(),
                        messageCount = 0
                    )
                    _chatSessions.value = _chatSessions.value + newSession
                }

                _isLoading.value = true

                // 1️⃣ Add user message
                val userMessage = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    content = input.trim(),
                    isFromUser = true,
                    timestamp = System.currentTimeMillis()
                )
                repository.addMessage(sessionId, userMessage)
                _currentMessages.value = _currentMessages.value + userMessage

                // 2️⃣ Placeholder AI message (empty at first)
                val aiMessageId = UUID.randomUUID().toString()
                var aiMessage = ChatMessage(
                    id = aiMessageId,
                    content = "",
                    isFromUser = false,
                    timestamp = System.currentTimeMillis()
                )
                _currentMessages.value = _currentMessages.value + aiMessage

                // 3️⃣ Stream response from Gemini
                try {
                    streamGeminiResponse(input).collect { chunk ->
                        // Append new chunk
                        aiMessage = aiMessage.copy(content = aiMessage.content + chunk)
                        _currentMessages.value =
                            _currentMessages.value.dropLast(1) + aiMessage
                    }
                } catch (e: Exception) {
                    aiMessage = aiMessage.copy(content = "⚠️ Error: ${e.message}")
                    _currentMessages.value =
                        _currentMessages.value.dropLast(1) + aiMessage
                }

                // 4️⃣ Save final AI message
                repository.addMessage(sessionId, aiMessage)

                // 5️⃣ Update session metadata
                val currentSession = _chatSessions.value.find { it.id == sessionId }
                currentSession?.let { session ->
                    val updatedSession = session.copy(
                        title = if (session.messageCount == 0) input.take(30) else session.title,
                        lastMessage = aiMessage.content.take(50),
                        timestamp = System.currentTimeMillis(),
                        messageCount = session.messageCount + 2
                    )
                    repository.updateChatSession(updatedSession)
                }

                _isLoading.value = false
            } catch (e: Exception) {
                _errorMessage.value = e.message
                _isLoading.value = false
            }
        }
    }



    fun deleteChatSession(sessionId: String) {
        viewModelScope.launch {
            try {
                repository.deleteChatSession(sessionId)
                // 🔥 update drawer instantly
                _chatSessions.value = _chatSessions.value.filterNot { it.id == sessionId }
                if (_currentChatId.value == sessionId) {
                    _currentChatId.value = null
                    _currentMessages.value = emptyList()
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun generateResponse(input: String): String {
        val responses = listOf(
            "That's a great question! Let me help you with that.",
            "I understand what you're asking. Here's my perspective on this topic.",
            "Interesting! This is something I can definitely assist you with.",
            "Let me break this down for you in a clear way.",
            "That's a thoughtful inquiry. Here's what I think about it."
        )
        return responses.random() + "\n\n" +
                "Your question was: \"$input\"\n\nThis is a demo response."
    }

    override fun onCleared() {
        super.onCleared()
        sessionsListener?.remove()
        messagesListener?.remove()
    }
}


