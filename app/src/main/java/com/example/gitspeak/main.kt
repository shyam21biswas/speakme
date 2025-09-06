package com.example.gitspeak


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// ---------------- Message Data Model ----------------
data class Message(val text: String, val isMe: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatAppUI() {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var chats by remember {
        mutableStateOf(
            listOf(
                Message("Hello!", false),
                Message("Hi, how are you?", true),
                Message("Welcome back!", false)
            )
        )
    }
    var currentMessage by remember { mutableStateOf("") }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ChatDrawer(
                chats = chats,
                onNewChat = {
                    chats = chats + Message("New chat started!", false)
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Scaffold(
            topBar = {
                ChatTopBar { scope.launch { drawerState.open() } }
            },
            bottomBar = {
                ChatInputBar(
                    message = currentMessage,
                    onMessageChange = { currentMessage = it },
                    onSend = {
                        if (currentMessage.isNotBlank()) {
                            chats = chats + Message(currentMessage, true)
                            currentMessage = ""
                            focusManager.clearFocus()
                        }
                    }
                )
            }
        ) { paddingValues ->
            ChatBody(
                modifier = Modifier.padding(paddingValues),
                chats = chats
            )
        }
    }
}

/* ---------------- Small Composables ---------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(onMenuClick: () -> Unit) {
    TopAppBar(
        title = { Text("Chat Screen", color = Color.White) },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E1E1E)),
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White)
            }
        }
    )
}

@Composable
fun ChatDrawer(chats: List<Message>, onNewChat: () -> Unit) {
    ModalDrawerSheet {
        Text("Chats", fontSize = 20.sp, modifier = Modifier.padding(16.dp))
        Divider()
        LazyColumn {
            items(chats) { chat ->
                Text(
                    text = chat.text.take(20) + if (chat.text.length > 20) "..." else "",
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
        Divider()
        TextButton(
            onClick = onNewChat,
            modifier = Modifier.padding(16.dp)
        ) {
            Text("➕ New Chat")
        }
    }
}

@Composable
fun ChatBodyf(modifier: Modifier = Modifier, chats: List<Message>) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(chats.size) {
        if (chats.isNotEmpty()) {
            scope.launch { listState.animateScrollToItem(chats.size - 1) }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(chats) { msg ->
            ChatBubble(message = msg)
        }
    }
}

@Composable
fun ChatBody(modifier: Modifier = Modifier, chats: List<Message>) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Scroll only when a new message is added (not when typing)
    LaunchedEffect(chats.size) {
        if (chats.isNotEmpty()) {
            scope.launch { listState.animateScrollToItem(chats.size - 1) }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp)
            .imePadding(), // <-- prevents jump when keyboard opens
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(chats) { msg ->
            ChatBubble(message = msg)
        }
    }
}


@Composable
fun ChatBubble(message: Message) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isMe) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .background(
                    color = if (message.isMe) Color(0xFF4CAF50) else Color(0xFF333333),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(12.dp)
                .widthIn(max = 250.dp)
        ) {
            Text(
                text = message.text.trim(),
                color = Color.White,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun ChatInputBar( message: String,
                  onMessageChange: (String) -> Unit,
                  onSend: () -> Unit ) {
         Row( modifier = Modifier .fillMaxWidth()
        .padding(24.dp),
        verticalAlignment = Alignment.CenterVertically )
    {  TextField( value = message, onValueChange = onMessageChange,
        placeholder = { Text("Type a message...") },
        modifier = Modifier.weight(1f),
        maxLines = 3,
        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { onSend() }) )
        IconButton(onClick = onSend) { Icon(Icons.Default.Send, contentDescription = "Send") } } }