package com.example.gitspeak


import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

// Data Classes
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val content: String = "",
    val isFromUser: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val lastMessage: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val messageCount: Int = 0,
    val userId: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

// Optimized Firebase Repository
class ChatRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = Firebase.auth

    private fun getCurrentUserId(): String? = auth.currentUser?.uid

    // Optimized path structure: users/{userId}/chats/{chatId}
    private fun getUserChatsCollection() = getCurrentUserId()?.let { userId ->
        db.collection("users").document(userId).collection("chats")
    }

    // Optimized path structure: users/{userId}/chats/{chatId}/messages/{messageId}
    private fun getChatMessagesCollection(chatId: String) = getCurrentUserId()?.let { userId ->
        db.collection("users")
            .document(userId)
            .collection("chats")
            .document(chatId)
            .collection("messages")
    }

    // Create or update chat session
    suspend fun createOrUpdateChatSession(session: ChatSession): Result<ChatSession> {
        return try {
            val userId = getCurrentUserId() ?: return Result.failure(Exception("User not authenticated"))
            val sessionWithUser = session.copy(userId = userId)

            getUserChatsCollection()?.document(session.id)?.set(
                mapOf(
                    "id" to sessionWithUser.id,
                    "title" to sessionWithUser.title,
                    "lastMessage" to sessionWithUser.lastMessage,
                    "timestamp" to sessionWithUser.timestamp,
                    "messageCount" to sessionWithUser.messageCount,
                    "userId" to sessionWithUser.userId,
                    "createdAt" to sessionWithUser.createdAt
                ),
                SetOptions.merge()
            )?.await()

            Result.success(sessionWithUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Get all chat sessions for current user
    suspend fun getChatSessions(): Result<List<ChatSession>> {
        return try {
            val snapshot = getUserChatsCollection()
                ?.orderBy("timestamp", Query.Direction.DESCENDING)
                ?.get()
                ?.await()

            val sessions = snapshot?.documents?.mapNotNull { doc ->
                documentToChat(doc)
            } ?: emptyList()

            Result.success(sessions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Delete chat session and all its messages
    suspend fun deleteChatSession(sessionId: String): Result<Unit> {
        return try {
            val batch = db.batch()

            // Delete all messages
            val messagesSnapshot = getChatMessagesCollection(sessionId)
                ?.get()
                ?.await()

            messagesSnapshot?.documents?.forEach { doc ->
                batch.delete(doc.reference)
            }

            // Delete the chat session
            getUserChatsCollection()?.document(sessionId)?.let { chatRef ->
                batch.delete(chatRef)
            }

            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Add message to chat
    suspend fun addMessage(chatId: String, message: ChatMessage): Result<ChatMessage> {
        return try {
            getChatMessagesCollection(chatId)?.document(message.id)?.set(
                mapOf(
                    "id" to message.id,
                    "content" to message.content,
                    "isFromUser" to message.isFromUser,
                    "timestamp" to message.timestamp
                )
            )?.await()

            Result.success(message)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Get messages for a chat
    suspend fun getMessages(chatId: String): Result<List<ChatMessage>> {
        return try {
            val snapshot = getChatMessagesCollection(chatId)
                ?.orderBy("timestamp", Query.Direction.ASCENDING)
                ?.get()
                ?.await()

            val messages = snapshot?.documents?.mapNotNull { doc ->
                documentToMessage(doc)
            } ?: emptyList()

            Result.success(messages)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Real-time listener for chat sessions
    fun listenToChatSessions(onUpdate: (List<ChatSession>) -> Unit): ListenerRegistration? {
        return getUserChatsCollection()
            ?.orderBy("timestamp", Query.Direction.DESCENDING)
            ?.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onUpdate(emptyList())
                    return@addSnapshotListener
                }

                val sessions = snapshot?.documents?.mapNotNull { doc ->
                    documentToChat(doc)
                } ?: emptyList()

                onUpdate(sessions)
            }
    }

    // Real-time listener for messages
    fun listenToMessages(chatId: String, onUpdate: (List<ChatMessage>) -> Unit): ListenerRegistration? {
        return getChatMessagesCollection(chatId)
            ?.orderBy("timestamp", Query.Direction.ASCENDING)
            ?.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onUpdate(emptyList())
                    return@addSnapshotListener
                }

                val messages = snapshot?.documents?.mapNotNull { doc ->
                    documentToMessage(doc)
                } ?: emptyList()

                onUpdate(messages)
            }
    }

    // Helper functions
    private fun documentToChat(doc: DocumentSnapshot): ChatSession? {
        return try {
            val data = doc.data ?: return null
            ChatSession(
                id = data["id"] as? String ?: doc.id,
                title = data["title"] as? String ?: "",
                lastMessage = data["lastMessage"] as? String ?: "",
                timestamp = data["timestamp"] as? Long ?: System.currentTimeMillis(),
                messageCount = (data["messageCount"] as? Long)?.toInt() ?: 0,
                userId = data["userId"] as? String ?: "",
                createdAt = data["createdAt"] as? Long ?: System.currentTimeMillis()
            )
        } catch (e: Exception) {
            null
        }
    }



    private fun documentToMessage(doc: DocumentSnapshot): ChatMessage? {
        return try {
            val data = doc.data ?: return null
            ChatMessage(
                id = data["id"] as? String ?: doc.id,
                content = data["content"] as? String ?: "",
                isFromUser = data["isFromUser"] as? Boolean ?: false,
                timestamp = data["timestamp"] as? Long ?: System.currentTimeMillis()
            )
        } catch (e: Exception) {
            null
        }
    }
}

// Optimized ViewModel
class ChatViewModel : ViewModel() {
    private val repository = ChatRepository()

    // StateFlows for better performance
    private val _chatSessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val chatSessions: StateFlow<List<ChatSession>> = _chatSessions.asStateFlow()

    private val _currentMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val currentMessages: StateFlow<List<ChatMessage>> = _currentMessages.asStateFlow()

    private val _currentChatId = MutableStateFlow<String?>(null)
    val currentChatId: StateFlow<String?> = _currentChatId.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var sessionsListener: ListenerRegistration? = null
    private var messagesListener: ListenerRegistration? = null

    //for store curr AI data
     var lastAIMessage: String = "" // store the last AI response






    init {
        loadChatSessions()
    }

    private fun loadChatSessions() {
        sessionsListener?.remove()
        sessionsListener = repository.listenToChatSessions { sessions ->
            _chatSessions.value = sessions
        }
    }

    fun createNewChat() {
        viewModelScope.launch {
            try {
                val newChat = ChatSession(
                    id = UUID.randomUUID().toString(),
                    title = "New Chat",
                    lastMessage = "",
                    timestamp = System.currentTimeMillis(),
                    messageCount = 0,
                    createdAt = System.currentTimeMillis()
                )

                val result = repository.createOrUpdateChatSession(newChat)
                result.onSuccess { session ->
                    _currentChatId.value = session.id
                    _currentMessages.value = emptyList()
                    loadMessages(session.id)
                }.onFailure { exception ->
                    _errorMessage.value = "Failed to create chat: ${exception.message}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error: ${e.message}"
            }
        }
    }

    fun loadChat(chatId: String) {
        _currentChatId.value = chatId
        loadMessages(chatId)
    }

    private fun loadMessages(chatId: String) {
        messagesListener?.remove()
        messagesListener = repository.listenToMessages(chatId) { messages ->
            _currentMessages.value = messages
        }
    }

    fun sendMessage(input: String) {
        if (input.trim().isEmpty()) return

        var currentResponse = "";

        viewModelScope.launch {
            try {
                var sessionId = _currentChatId.value

                // Create new chat if needed
                if (sessionId.isNullOrEmpty()) {
                    val newChat = ChatSession(
                        id = UUID.randomUUID().toString(),
                        title = input.take(30),
                        lastMessage = "",
                        timestamp = System.currentTimeMillis(),
                        messageCount = 0
                    )

                    val result = repository.createOrUpdateChatSession(newChat)
                    result.onSuccess {
                        sessionId = it.id
                        _currentChatId.value = sessionId
                        loadMessages(sessionId!!) // snapshot listener starts here
                    }.onFailure {
                        _errorMessage.value = "Failed to create chat"
                        return@launch
                    }
                }

                sessionId?.let { chatId ->
                    _isLoading.value = true

                    // Save user message
                    val userMessage = ChatMessage(
                        content = input.trim(),
                        isFromUser = true
                    )
                    repository.addMessage(chatId, userMessage)

                    // Local mutable state for AI typing
                    val aiTypingState = mutableStateOf("")

                    try {
                        // Stream AI response character by character
                        streamGeminiResponse(input, this@ChatViewModel).collect { chunk ->
                            currentResponse += chunk
                            aiTypingState.value += chunk
                        }




                        // Once complete, push full AI message to DB
                        val aiMessage = ChatMessage(
                            content = aiTypingState.value.ifEmpty { "I couldn't generate a response. Please try again." },
                            isFromUser = false
                        )
                        lastAIMessage = aiMessage.content
                        repository.addMessage(chatId, aiMessage)

                        // Update chat session
                        updateChatSession(chatId, input, aiTypingState.value)

                    } catch (e: Exception) {
                        val errorMessage = ChatMessage(
                            content = "Error: ${e.message}",
                            isFromUser = false
                        )
                        repository.addMessage(chatId, errorMessage)
                    }

                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error sending message: ${e.message}"
                _isLoading.value = false
            }
        }
    }






    fun sendMessagef(input: String) {
        if (input.trim().isEmpty()) return

        viewModelScope.launch {
            try {
                var sessionId = _currentChatId.value

                // Create new chat if needed
                if (sessionId.isNullOrEmpty()) {
                    val newChat = ChatSession(
                        id = UUID.randomUUID().toString(),
                        title = input.take(30),
                        lastMessage = "",
                        timestamp = System.currentTimeMillis(),
                        messageCount = 0
                    )

                    val result = repository.createOrUpdateChatSession(newChat)
                    result.onSuccess {
                        sessionId = it.id
                        _currentChatId.value = sessionId
                        loadMessages(sessionId!!)
                    }.onFailure {
                        _errorMessage.value = "Failed to create chat"
                        return@launch
                    }
                }

                sessionId?.let { chatId ->
                    _isLoading.value = true

                    // Add user message
                    val userMessage = ChatMessage(
                        content = input.trim(),
                        isFromUser = true
                    )

                    repository.addMessage(chatId, userMessage).onSuccess {
                        _currentMessages.value = _currentMessages.value + userMessage
                    }

                    // Get AI response
                    try {
                        var aiResponse = ""
                        streamGeminiResponse(input,this@ChatViewModel).collect { chunk ->
                            aiResponse += chunk
                        }

                        // Save AI message
                        val aiMessage = ChatMessage(
                            content = aiResponse.ifEmpty { "I couldn't generate a response. Please try again." },
                            isFromUser = false
                        )

                        repository.addMessage(chatId, aiMessage).onSuccess {
                            _currentMessages.value = _currentMessages.value + aiMessage
                        }

                        // Update chat session
                        updateChatSession(chatId, input, aiResponse)

                    } catch (e: Exception) {
                        val errorMessage = ChatMessage(
                            content = "Error: ${e.message}",
                            isFromUser = false
                        )
                        repository.addMessage(chatId, errorMessage)
                        _currentMessages.value = _currentMessages.value + errorMessage
                    }

                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error sending message: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    private suspend fun updateChatSession(chatId: String, userInput: String, aiResponse: String) {
        val currentSession = _chatSessions.value.find { it.id == chatId }
        currentSession?.let { session ->
            val updatedSession = session.copy(
                title = if (session.messageCount == 0) userInput.take(30) else session.title,
                lastMessage = aiResponse.take(50),
                timestamp = System.currentTimeMillis(),
                messageCount = session.messageCount + 2
            )
            repository.createOrUpdateChatSession(updatedSession)
        }
    }

    fun deleteChatSession(sessionId: String) {
        viewModelScope.launch {
            try {
                repository.deleteChatSession(sessionId).onSuccess {
                    if (_currentChatId.value == sessionId) {
                        _currentChatId.value = null
                        _currentMessages.value = emptyList()
                    }
                }.onFailure {
                    _errorMessage.value = "Failed to delete chat"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error: ${e.message}"
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        sessionsListener?.remove()
        messagesListener?.remove()
    }
}