package com.example.gitspeak
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatApp(
    viewModel: ChatViewModel = viewModel(),
    onSignOut: () -> Unit = {}
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Observe ViewModel states
    val chatSessions by viewModel.chatSessions.collectAsState()
    val currentMessages by viewModel.currentMessages.collectAsState()
    val currentChatId by viewModel.currentChatId.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val currentUser = FirebaseAuth.getInstance().currentUser

    var currentInput by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Show error messages
    errorMessage?.let { message ->
        LaunchedEffect(message) {
            // You can show a snackbar here
            delay(3000)
            viewModel.clearError()
        }
    }

    // Initialize first chat if needed
    LaunchedEffect(Unit) {
        if (chatSessions.isEmpty() && currentChatId == null) {
            viewModel.createNewChat()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(
                chatSessions = chatSessions,
                onNewChat = {
                    viewModel.createNewChat()
                    scope.launch { drawerState.close() }
                },
                onChatSelect = { chatId ->
                    viewModel.loadChat(chatId)
                    scope.launch { drawerState.close() }
                },
                currentChatId = currentChatId,
                onDeleteChat = { sessionId ->
                    viewModel.deleteChatSession(sessionId)
                },
                userEmail = currentUser?.email ?: "",
                onSignOut = {
                    FirebaseAuth.getInstance().signOut()
                    onSignOut()
                }
            )
        },
        gesturesEnabled = true
    ) {
        Scaffold(
            topBar = {
                ChatTopBar(
                    onMenuClick = { scope.launch { drawerState.open() } },
                    currentChat = chatSessions.find { it.id == currentChatId }
                )
            },
            bottomBar = {
                ChatInputBar(
                    input = currentInput,
                    onInputChange = { currentInput = it },
                    onSend = {
                        if (currentInput.trim().isNotEmpty()) {
                            viewModel.sendMessage(currentInput.trim())
                            currentInput = ""
                            keyboardController?.hide()
                        }
                    },
                    enabled = !isLoading
                )
            }
        ) { paddingValues ->
            ChatContent(
                messages = currentMessages,
                modifier = Modifier.padding(paddingValues),
                onSendMessage = { msg -> viewModel.sendMessage(msg) },
                isLoading = isLoading
            )
        }
    }
}

@Composable
fun DrawerContent(
    chatSessions: List<ChatSession>,
    onNewChat: () -> Unit,
    onChatSelect: (String) -> Unit,
    currentChatId: String?,
    onDeleteChat: (String) -> Unit,
    userEmail: String,
    onSignOut: () -> Unit
) {
    ModalDrawerSheet(
        modifier = Modifier.width(320.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header with user info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AI Chat",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = userEmail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Default.Face,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // New Chat Button
            Button(
                onClick = onNewChat,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("New Chat")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Recent Chats",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Chat Sessions
            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(chatSessions) { session ->
                    ChatSessionItem(
                        session = session,
                        isSelected = session.id == currentChatId,
                        onClick = { onChatSelect(session.id) },
                        onDelete = { onDeleteChat(session.id) }
                    )
                }
            }

            Divider()

            // Sign Out Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSignOut() }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.ExitToApp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Sign Out",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun ChatSessionItem(
    session: ChatSession,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (session.lastMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = session.lastMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTimestamp(session.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (session.messageCount > 0) {
                        Text(
                            text = "${session.messageCount} messages",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete chat",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(
    onMenuClick: () -> Unit,
    currentChat: ChatSession?
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = currentChat?.title ?: "AI Assistant",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Default.Menu, contentDescription = "Menu")
            }
        },
        actions = {
            IconButton(onClick = { }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More options")
            }
        }
    )
}

@Composable
fun ChatContent(
    messages: List<ChatMessage>,
    modifier: Modifier = Modifier,
    onSendMessage: (String) -> Unit,
    isLoading: Boolean
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (messages.isEmpty()) {
            EmptyState(onSuggestionClick = onSendMessage)
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(items = messages, key = {  it.id + {it.timestamp}}) { message ->
                    MessageItem(message)
                }


                if (isLoading) {
                    item {
                        TypingIndicator()
                    }
                }

            }
        }
    }
}

@Composable
fun EmptyState(modifier: Modifier = Modifier, onSuggestionClick: (String) -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Face,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Start a conversation",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Ask me anything! I'm here to help with your questions.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Column {
            SuggestionChip("How does AI work?") { onSuggestionClick("How does AI work?") }
            Spacer(modifier = Modifier.height(8.dp))
            SuggestionChip("Explain Jetpack Compose") { onSuggestionClick("Explain Jetpack Compose") }
            Spacer(modifier = Modifier.height(8.dp))
            SuggestionChip("Best practices for Android development") { onSuggestionClick("Best practices for Android development") }
        }
    }
}

@Composable
fun SuggestionChip(
    text: String,
    onClick: () -> Unit
) {
    SuggestionChip(
        onClick = onClick,
        label = { Text(text) },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun MessageItem(message: ChatMessage) {
    val alignment = if (message.isFromUser) Alignment.CenterEnd else Alignment.CenterStart

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentAlignment = alignment
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(), // full width
            horizontalArrangement = if (message.isFromUser) Arrangement.End else Arrangement.Start
        ) {
            if (!message.isFromUser) {
                MessageAvatar(isUser = false)
                Spacer(modifier = Modifier.width(8.dp))
            }

            MessageBubble(
                message = message,
                isAI = !message.isFromUser // pass flag to expand AI bubble
            )

            if (message.isFromUser) {
                Spacer(modifier = Modifier.width(8.dp))
                MessageAvatar(isUser = true)
            }
        }
    }
}

@Composable
fun MessageItem1(message: ChatMessage) {
    val alignment = if (message.isFromUser) Alignment.CenterEnd else Alignment.CenterStart

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentAlignment = alignment
    ) {
        Row(
            modifier = Modifier.widthIn(/*max = 280.dp*/ max = 500.dp),
            horizontalArrangement = if (message.isFromUser) Arrangement.End else Arrangement.Start
        ) {
            if (!message.isFromUser) {
                MessageAvatar(isUser = false)
                Spacer(modifier = Modifier.width(8.dp))
            }

            MessageBubble(message = message)

            if (message.isFromUser) {
                Spacer(modifier = Modifier.width(8.dp))
                MessageAvatar(isUser = true)
            }
        }
    }
}

@Composable
fun MessageAvatar(isUser: Boolean) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(
                if (isUser) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.secondary
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isUser) Icons.Default.Person else Icons.Default.Face,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun MessageBubble(message: ChatMessage, isAI: Boolean = false) {
    val backgroundColor = if (message.isFromUser) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surface
    }

    val textColor = if (message.isFromUser) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = if (isAI) Modifier.fillMaxWidth() else Modifier.wrapContentWidth(), // AI fills width
        shape = RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomStart = if (message.isFromUser) 16.dp else 4.dp,
            bottomEnd = if (message.isFromUser) 4.dp else 16.dp,
        ),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            SelectionContainer {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = formatTimestamp(message.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.7f)
            )
        }
    }
}
/*
@Composable
fun MessageBubble(message: ChatMessage) {
    val backgroundColor = if (message.isFromUser) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surface
    }

    val textColor = if (message.isFromUser) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(


        shape = RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomStart = if (message.isFromUser) 16.dp else 4.dp,
            bottomEnd = if (message.isFromUser) 4.dp else 16.dp,

        ),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            SelectionContainer {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = formatTimestamp(message.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.7f)
            )
        }
    }
}
*/
@Composable
fun TypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(8.dp)
    ) {
        repeat(3) { index ->
            val offsetY by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -6f, // bounce height
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 400, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * 150) // stagger bounce
                )
            )

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .offset(y = offsetY.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )

            if (index < 2) Spacer(modifier = Modifier.width(6.dp))
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = "AI is thinking...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun TypingIndicatore() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(8.dp)
    ) {
        repeat(3) { index ->
            val alpha by animateFloatAsState(
                targetValue = if ((System.currentTimeMillis() / 500) % 3 == index.toLong()) 1f else 0.3f,
                animationSpec = tween(500),
                label = ""
            )

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                    )
            )

            if (index < 2) {
                Spacer(modifier = Modifier.width(4.dp))
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "AI is thinking...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInputBar(
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            TextField(
                value = input,
                onValueChange = onInputChange,
                placeholder = {
                    Text(
                        "Type your message...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp, max = 120.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                maxLines = 4,
                enabled = enabled
            )

            Spacer(modifier = Modifier.width(8.dp))

            FloatingActionButton(
                onClick = onSend,
                modifier = Modifier
                    .size(52.dp)
                    .padding(bottom = 8.dp),
                containerColor = if (input.trim().isNotEmpty() && enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send",
                    tint = if (input.trim().isNotEmpty() && enabled) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }

}

fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60000 -> "Just now"
        diff < 3600000 -> "${diff / 60000}m ago"
        diff < 86400000 -> "${diff / 3600000}h ago"
        diff < 604800000 -> "${diff / 86400000}d ago"
        else -> SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(timestamp))
    }
}



/*
//////////////////////////////////////............................................................................................
import android.graphics.BlurMaskFilter
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.rotate
import kotlin.math.cos
import kotlin.math.sin

// Data classes (unchanged from your version)
data class ChatSession(val id: String, val title: String, val lastMessage: String, val timestamp: Long, val messageCount: Int)
data class ChatMessage(val id: String, val content: String, val timestamp: Long, val isFromUser: Boolean)



// best one..............................................................................................................

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatApp() {
    // Dummy data for previewing the UI
    val chatSessions = remember { mutableStateOf(listOf(
        ChatSession("1", "UI/UX Design", "Gradients add so much depth...", System.currentTimeMillis() - 50000, 5),
        ChatSession("2", "Weekend Plans", "How about a hike?", System.currentTimeMillis() - 86400000, 12),
        ChatSession("3", "Project Ideas", "A wallpaper-based color theme!", System.currentTimeMillis() - 172800000, 8)
    )) }
    val currentMessages = remember { mutableStateOf(listOf(
        ChatMessage("a", "Welcome! I've been updated with a new, more elegant design. How can I assist you?", System.currentTimeMillis() - 10000, false),
        ChatMessage("b", "This new gradient background is amazing!", System.currentTimeMillis() - 5000, true)
    )) }
    val currentChatId = remember { mutableStateOf("1") }
    val isLoading = remember { mutableStateOf(false) }
    val currentUser = FirebaseAuth.getInstance().currentUser

    // State management
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentInput by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    // ✨ A vibrant, dynamic color palette inspired by Material You.
    val dynamicColorScheme = lightColorScheme(
        primary = Color(0xFF6200EE),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFEADDFF),
        onPrimaryContainer = Color(0xFF21005D),
        secondary = Color(0xFF625B71),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE8DEF8),
        onSecondaryContainer = Color(0xFF1D192B),
        tertiary = Color(0xFF7D5260),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFD8E4),
        onTertiaryContainer = Color(0xFF31111D),
        background = Color(0xFFFEF7FF),
        onBackground = Color(0xFF1D1B20),
        surface = Color(0xFFFEF7FF),
        onSurface = Color(0xFF1D1B20),
        surfaceVariant = Color(0xFFE7E0EC),
        onSurfaceVariant = Color(0xFF49454F),
        outline = Color(0xFF79747E),
        error = Color(0xFFB3261E)
    )

    MaterialTheme(colorScheme = dynamicColorScheme) {
        // ✨ An elegant background gradient that sweeps across the screen.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        ) {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    DrawerContent(
                        chatSessions = chatSessions.value,
                        onNewChat = { scope.launch { drawerState.close() } },
                        onChatSelect = { chatId ->
                            currentChatId.value = chatId
                            scope.launch { drawerState.close() }
                        },
                        currentChatId = currentChatId.value,
                        userEmail = currentUser?.email ?: "user@example.com",
                        onSignOut = { FirebaseAuth.getInstance().signOut() }
                    )
                },
            ) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        ChatTopBar(
                            onMenuClick = { scope.launch { drawerState.open() } },
                            currentChatTitle = chatSessions.value.find { it.id == currentChatId.value }?.title ?: "New Chat"
                        )
                    },
                    bottomBar = {}, // Input bar is a floating overlay
                    containerColor = Color.Transparent // Scaffold must be transparent to see the gradient
                ) { paddingValues ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        ChatContent(
                            messages = currentMessages.value,
                            modifier = Modifier.padding(paddingValues),
                            isLoading = isLoading.value
                        )

                        // The input bar floats over the content list.
                        ChatInputBar(
                            input = currentInput,
                            onInputChange = { currentInput = it },
                            onSend = {
                                if (currentInput.trim().isNotEmpty()) {
                                    val newMessage = ChatMessage(UUID.randomUUID().toString(), currentInput.trim(), System.currentTimeMillis(), true)
                                    currentMessages.value += newMessage
                                    currentInput = ""
                                    keyboardController?.hide()
                                    scope.launch {
                                        isLoading.value = true
                                        delay(5000)
                                        val aiResponse = ChatMessage(UUID.randomUUID().toString(), "I agree! Gradients add a nice touch.", System.currentTimeMillis(), false)
                                        currentMessages.value += aiResponse
                                        isLoading.value = false
                                    }
                                }
                            },
                            enabled = !isLoading.value,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }
    }
}

// Android 15 Style: Simplified and cleaner drawer using standard components.
@Composable
fun DrawerContent(
    chatSessions: List<ChatSession>,
    onNewChat: () -> Unit,
    onChatSelect: (String) -> Unit,
    currentChatId: String?,
    userEmail: String,
    onSignOut: () -> Unit
) {
    ModalDrawerSheet(
        modifier = Modifier.statusBarsPadding() // Respects the status bar area
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .padding(12.dp)
        ) {
            Text(
                "AI Chat",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onNewChat,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = CircleShape // Pill shape
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(12.dp))
                Text("New Chat", fontWeight = FontWeight.Medium)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))
            LazyColumn(modifier = Modifier.weight(1f)) {
                item {
                    Text(
                        "Recent Chats",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(chatSessions) { session ->
                    NavigationDrawerItem(
                        label = { Text(session.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        selected = session.id == currentChatId,
                        onClick = { onChatSelect(session.id) },
                        icon = { Icon(Icons.Default.Face, contentDescription = null) },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Person, contentDescription = "User Icon", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(16.dp))
                Text(
                    text = userEmail,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Sign Out", tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text("Sign Out", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(onMenuClick: () -> Unit, currentChatTitle: String) {
    TopAppBar(
        title = {
            Text(
                text = currentChatTitle,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.titleLarge
            )
        },
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Default.Menu, contentDescription = "Menu")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

@Composable
fun ChatContent(
    messages: List<ChatMessage>,
    modifier: Modifier = Modifier,
    isLoading: Boolean
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        // ✨ CHANGE THIS LINE ✨
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 12.dp,
            bottom = 90.dp // Increased bottom padding to clear the input bar
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        reverseLayout = false
    ) {
        items(items = messages, key = { it.id }) { message ->
            // Android 15 Style: Fluid, bouncy spring animation for messages.
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)) +
                        slideInVertically(
                            initialOffsetY = { it / 2 },
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        )
            ) {


                MessageItem(message)
            }
        }
        if (isLoading) {
            item { TypingIndicator() }
        }
    }
}

@Composable
fun MessageItem(message: ChatMessage) {
    val horizontalArrangement = if (message.isFromUser) Arrangement.End else Arrangement.Start
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.Top
    ) {
        if (!message.isFromUser) {
            MessageAvatar(isUser = false)
            Spacer(modifier = Modifier.width(8.dp))
        }
        MessageBubble(message = message)
    }
}

@Composable
fun MessageAvatar(isUser: Boolean) {
    Icon(
        imageVector = if (isUser) Icons.Default.Person else Icons.Outlined.Star,
        contentDescription = null,
        tint = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer)
            .padding(6.dp)
    )
}

// Android 15 Style: Bubbly, pill-shaped message containers.
@Composable
fun MessageBubble(message: ChatMessage) {
    val bubbleShape = RoundedCornerShape(
        topStart = 20.dp, topEnd = 20.dp,
        bottomStart = if (message.isFromUser) 20.dp else 4.dp,
        bottomEnd = if (message.isFromUser) 4.dp else 20.dp
    )

    Surface(
        shape = bubbleShape,
        color = if (message.isFromUser)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surfaceVariant,
    ) {
        SelectionContainer {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                color = if (message.isFromUser)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }
}

// You will need this custom glow modifier from our previous designs
fun Modifier.glow(color: Color, radius: Dp, alpha: Float = 0.9f) = this.drawBehind { /* ... */ }



@Composable
fun TypingIndicator() {
    // The animation logic remains the same
    val geminiColors = listOf(
        Color(0xFF4285F4), // Blue
        Color(0xFF9B72F8), // Purple
        Color(0xFFF472B6), // Pink
        Color(0xFFFBBC05)  // Amber
    )
    val infiniteTransition = rememberInfiniteTransition(label = "gemini-transition")
    val starRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "star-rotation"
    )

    // ✨ MODIFIED: The Row now only contains the animation, positioned to the left.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Start // Aligns the indicator to the left
    ) {
        // MessageAvatar and Spacer have been removed.

        Box(
            modifier = Modifier.size(52.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "AI Icon",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier
                    .size(24.dp)
                    .rotate(starRotation)
            )

            geminiColors.forEachIndexed { index, color ->
                val orbProgress by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 1500, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart,
                        initialStartOffset = StartOffset(index * (1500 / geminiColors.size))
                    ),
                    label = "orb-progress-$index"
                )

                val angle = Math.toRadians((orbProgress * 360).toDouble())
                val radius = 22.dp.value
                val xOffset = (radius * cos(angle)).dp
                val yOffset = (radius * sin(angle)).dp
                val scale = 1f - (0.5f * kotlin.math.abs(cos(2 * angle))).toFloat()

                Box(
                    modifier = Modifier
                        .offset(x = xOffset, y = yOffset)
                        .scale(scale)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(color)
                )
            }
        }
    }
}
@Composable
fun TypingIndicator1() {
    // ✨ The vibrant, multi-color palette inspired by Gemini
    val geminiColors = listOf(
        Color(0xFF4285F4), // Blue
        Color(0xFF9B72F8), // Purple
        Color(0xFFF472B6), // Pink
        Color(0xFFFBBC05)  // Amber
    )

    val infiniteTransition = rememberInfiniteTransition(label = "gemini-transition")

    // A slow, continuous rotation for the central star
    val starRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "star-rotation"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        MessageAvatar(isUser = false) // Assuming MessageAvatar is defined elsewhere
        Spacer(Modifier.width(12.dp))

        // The main container for the animation
        Box(
            modifier = Modifier.size(52.dp),
            contentAlignment = Alignment.Center
        ) {
            // The central, slowly rotating star
            Icon(
                imageVector = Icons.Default.Star, // Your star icon
                contentDescription = "AI Icon",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier
                    .size(24.dp)
                    .rotate(starRotation)
            )

            // The four orbiting and pulsing orbs
            geminiColors.forEachIndexed { index, color ->
                // Each orb gets a staggered start time for the "chasing" effect
                val orbProgress by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 1500, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart,
                        initialStartOffset = StartOffset(index * (1500 / geminiColors.size))
                    ),
                    label = "orb-progress-$index"
                )

                // Calculate the orb's position on the circle
                val angle = Math.toRadians((orbProgress * 360).toDouble())
                val radius = 22.dp.value
                val xOffset = (radius * cos(angle)).dp
                val yOffset = (radius * sin(angle)).dp

                // Calculate the orb's scale based on its progress for a pulsing effect
                val scale = 1f - (0.5f * kotlin.math.abs(cos(2 * angle))).toFloat()

                // The orb itself
                Box(
                    modifier = Modifier
                        .offset(x = xOffset, y = yOffset)
                        .scale(scale)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(color)
                )
            }
        }
    }
}
// Android 15 Style: A clean, pill-shaped input bar.
@Composable
fun ChatInputBar(input: String, onInputChange: (String) -> Unit, onSend: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding() // Respects system navigation
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = CircleShape,
        colors = CardDefaults.cardColors(
            // The magic: a semi-transparent color for the glass effect
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)) // Subtle border
    ) {
        Row(modifier = Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = input,
                onValueChange = onInputChange,
                placeholder = { Text("Message...") },
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                ),
                maxLines = 5, enabled = enabled
            )
            IconButton(
                onClick = onSend,
                enabled = input.trim().isNotEmpty() && enabled
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (input.trim().isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/*
//best given by clade  black and white  ......................................................................................
data class ChatSession(val id: String, val title: String, val lastMessage: String, val timestamp: Long, val messageCount: Int)
data class ChatMessage(val id: String, val content: String, val timestamp: Long, val isFromUser: Boolean)

// Elegant shadow effect for premium feel
fun Modifier.elegantShadow(
    color: Color = Color.Black,
    alpha: Float = 0.15f,
    borderRadius: Dp = 0.dp,
    shadowRadius: Dp = 12.dp,
    offsetY: Dp = 4.dp,
    offsetX: Dp = 0.dp
) = this.drawBehind {
    val transparentColor = color.copy(alpha = 0f).toArgb()
    val shadowColor = color.copy(alpha = alpha).toArgb()
    val paint = Paint().asFrameworkPaint()

    paint.color = transparentColor
    paint.maskFilter = BlurMaskFilter(shadowRadius.toPx(), BlurMaskFilter.Blur.NORMAL)

    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawRoundRect(
            offsetX.toPx(),
            offsetY.toPx(),
            size.width + offsetX.toPx(),
            size.height + offsetY.toPx(),
            borderRadius.toPx(),
            borderRadius.toPx(),
            paint.apply { this.color = shadowColor }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatApp() {
    val chatSessions = remember { mutableStateOf(listOf(
        ChatSession("1", "UI/UX Design Ideas", "Elegant interfaces matter...", System.currentTimeMillis() - 50000, 5),
        ChatSession("2", "Project Brainstorm", "Classic design principles", System.currentTimeMillis() - 86400000, 12),
        ChatSession("3", "Creative Writing", "Storytelling techniques", System.currentTimeMillis() - 172800000, 8)
    )) }
    val currentMessages = remember { mutableStateOf(listOf(
        ChatMessage("a", "Good day! I'm here to assist you with thoughtful, elegant solutions. How may I help you today?", System.currentTimeMillis() - 10000, false),
        ChatMessage("b", "What makes a design truly timeless and elegant?", System.currentTimeMillis() - 5000, true),
        ChatMessage("c", "Timeless design balances simplicity with sophistication. It uses refined typography, harmonious spacing, and a restrained color palette. Classic designs avoid trends, focusing instead on clarity, proportion, and enduring aesthetic principles.", System.currentTimeMillis() - 2000, false)
    )) }
    val currentChatId = remember { mutableStateOf("1") }
    val isLoading = remember { mutableStateOf(false) }
    val currentUser = FirebaseAuth.getInstance().currentUser

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentInput by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Classic color scheme with refined neutrals
    val classicColorScheme = lightColorScheme(
        primary = Color(0xFF1A1A2E),
        onPrimary = Color(0xFFFAFAFA),
        primaryContainer = Color(0xFFF5F5F5),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF2C2C2C),
        surfaceVariant = Color(0xFFF8F9FA),
        onSurfaceVariant = Color(0xFF5F6368),
        background = Color(0xFFFBFBFB),
        secondary = Color(0xFF4A5568),
        secondaryContainer = Color(0xFFE8EAF0),
        error = Color(0xFFB71C1C),
        outline = Color(0xFFE0E0E0)
    )

    MaterialTheme(colorScheme = classicColorScheme) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    DrawerContent(
                        chatSessions = chatSessions.value,
                        onNewChat = { scope.launch { drawerState.close() } },
                        onChatSelect = { chatId ->
                            currentChatId.value = chatId
                            scope.launch { drawerState.close() }
                        },
                        currentChatId = currentChatId.value,
                        onDeleteChat = { },
                        userEmail = currentUser?.email ?: "user@example.com",
                        onSignOut = { FirebaseAuth.getInstance().signOut() }
                    )
                },
                gesturesEnabled = true,
                scrimColor = Color.Black.copy(alpha = 0.3f)
            ) {
                Scaffold(
                    topBar = {
                        ChatTopBar(
                            onMenuClick = { scope.launch { drawerState.open() } },
                            currentChatTitle = chatSessions.value.find { it.id == currentChatId.value }?.title ?: "New Conversation"
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.background
                ) { paddingValues ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        ChatContent(
                            messages = currentMessages.value,
                            modifier = Modifier.padding(paddingValues),
                            onSuggestionClick = { suggestion ->
                                currentInput = suggestion
                            },
                            isLoading = isLoading.value
                        )
                        ChatInputBar(
                            input = currentInput,
                            onInputChange = { currentInput = it },
                            onSend = {
                                if (currentInput.trim().isNotEmpty()) {
                                    val newMessage = ChatMessage(UUID.randomUUID().toString(), currentInput.trim(), System.currentTimeMillis(), true)
                                    currentMessages.value += newMessage
                                    currentInput = ""
                                    keyboardController?.hide()
                                    scope.launch {
                                        isLoading.value = true
                                        delay(2000)
                                        val aiResponse = ChatMessage(
                                            UUID.randomUUID().toString(),
                                            "I appreciate your inquiry. Let me provide a thoughtful response that addresses your question with care and precision.",
                                            System.currentTimeMillis(),
                                            false
                                        )
                                        currentMessages.value += aiResponse
                                        isLoading.value = false
                                    }
                                }
                            },
                            enabled = !isLoading.value,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 20.dp, vertical = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DrawerContent(
    chatSessions: List<ChatSession>,
    onNewChat: () -> Unit,
    onChatSelect: (String) -> Unit,
    currentChatId: String?,
    onDeleteChat: (String) -> Unit,
    userEmail: String,
    onSignOut: () -> Unit
) {
    ModalDrawerSheet(
        modifier = Modifier.width(320.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerContentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header with refined styling
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Email,
                            contentDescription = "App Icon",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "AI Assistant",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.5.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Classic Edition",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // New Chat Button
            Button(
                onClick = onNewChat,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "New Conversation",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(Modifier.height(20.dp))

            // Chat History
            LazyColumn(modifier = Modifier.weight(1f)) {
                item {
                    Text(
                        "Recent Conversations",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.3.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                }
                items(chatSessions) { session ->
                    ChatSessionItem(
                        session = session,
                        isSelected = session.id == currentChatId,
                        onClick = { onChatSelect(session.id) },
                        onDelete = { onDeleteChat(session.id) }
                    )
                }
            }

            Divider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )

            // User Profile Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = "User Icon",
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = userEmail,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                TextButton(
                    onClick = onSignOut,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = "Sign Out",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Sign Out", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
fun ChatSessionItem(
    session: ChatSession,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        animationSpec = tween(durationMillis = 200), label = ""
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = session.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = session.lastMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = "Delete chat",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(onMenuClick: () -> Unit, currentChatTitle: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = currentChatTitle,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onMenuClick) {
                    Icon(
                        Icons.Default.Menu,
                        contentDescription = "Menu",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            actions = {
                IconButton(onClick = { }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )
    }
}

@Composable
fun ChatContent(
    messages: List<ChatMessage>,
    modifier: Modifier = Modifier,
    onSuggestionClick: (String) -> Unit,
    isLoading: Boolean
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (messages.isEmpty() && !isLoading) {
            EmptyState(onSuggestionClick = onSuggestionClick)
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 16.dp,
                    bottom = 110.dp
                ),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                items(items = messages, key = { it.id }) { message ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(animationSpec = tween(300)) +
                                slideInVertically(
                                    initialOffsetY = { it / 3 },
                                    animationSpec = tween(300)
                                )
                    ) {
                        MessageItem(message)
                    }
                }
                if (isLoading) {
                    item { TypingIndicator() }
                }
            }
        }
    }
}

@Composable
fun EmptyState(modifier: Modifier = Modifier, onSuggestionClick: (String) -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                modifier = Modifier.size(50.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Welcome",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "How may I assist you today?",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = "Try asking about:",
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.3.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SuggestionChip("Principles of elegant design") { onSuggestionClick("Principles of elegant design") }
            SuggestionChip("Classic literature recommendations") { onSuggestionClick("Classic literature recommendations") }
            SuggestionChip("Timeless productivity methods") { onSuggestionClick("Timeless productivity methods") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestionChip(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.widthIn(max = 320.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun MessageItem(message: ChatMessage) {
    val horizontalArrangement = if (message.isFromUser) Arrangement.End else Arrangement.Start

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.Top
    ) {
        if (!message.isFromUser) {
            MessageAvatar(isUser = false)
            Spacer(modifier = Modifier.width(12.dp))
        }

        Column(horizontalAlignment = if (message.isFromUser) Alignment.End else Alignment.Start) {
            MessageBubble(message = message)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = formatTimestamp(message.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }

        if (message.isFromUser) {
            Spacer(modifier = Modifier.width(12.dp))
            MessageAvatar(isUser = true)
        }
    }
}

@Composable
fun MessageAvatar(isUser: Boolean) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(
                if (isUser)
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                else
                    MaterialTheme.colorScheme.secondaryContainer
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isUser) Icons.Default.Person else Icons.Outlined.Star,
            contentDescription = null,
            tint = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    val bubbleShape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (message.isFromUser) 16.dp else 4.dp,
        bottomEnd = if (message.isFromUser) 4.dp else 16.dp
    )

    Surface(
        modifier = Modifier
            .widthIn(max = 300.dp)
            .then(
                if (message.isFromUser) {
                    Modifier.elegantShadow(
                        color = MaterialTheme.colorScheme.primary,
                        alpha = 0.15f,
                        borderRadius = 16.dp,
                        shadowRadius = 12.dp
                    )
                } else Modifier
            ),
        shape = bubbleShape,
        color = if (message.isFromUser)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = if (message.isFromUser) 0.dp else 1.dp
    ) {
        SelectionContainer {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        lineHeight = 24.sp,
                        letterSpacing = 0.15.sp
                    ),
                    color = if (message.isFromUser)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun TypingIndicator() {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        MessageAvatar(isUser = false)
        Spacer(Modifier.width(12.dp))

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "")
                repeat(3) { index ->
                    val scale by infiniteTransition.animateFloat(
                        initialValue = 0.5f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse,
                            initialStartOffset = StartOffset(index * 150)
                        ), label = ""
                    )
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .scale(scale)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    )
                }
            }
        }
    }
}

@Composable
fun ChatInputBar(input: String, onInputChange: (String) -> Unit, onSend: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    val isInputEmpty = input.trim().isEmpty()
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
    ) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = input, onValueChange = onInputChange,
                placeholder = { Text("Message...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp, max = 150.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                ), maxLines = 5, enabled = enabled
            )
            Spacer(modifier = Modifier.width(8.dp))
            val buttonScale by animateFloatAsState(targetValue = if (isInputEmpty) 0f else 1f, animationSpec = spring(), label = "")
            Box(
                modifier = Modifier.size(48.dp).scale(buttonScale).clip(CircleShape).background(MaterialTheme.colorScheme.primary).clickable(enabled = !isInputEmpty && enabled, onClick = onSend)
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}
fun formatTimestamp(timestamp: Long): String {
    val messageDate = Calendar.getInstance().apply { timeInMillis = timestamp }
    val now = Calendar.getInstance()
    return when {
        now.get(Calendar.YEAR) == messageDate.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == messageDate.get(Calendar.DAY_OF_YEAR) -> {
            SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
        }
        now.get(Calendar.YEAR) == messageDate.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) - messageDate.get(Calendar.DAY_OF_YEAR) == 1 -> {
            "Yesterday"
        }
        else -> {
            SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(timestamp))
        }
    }
}
*/