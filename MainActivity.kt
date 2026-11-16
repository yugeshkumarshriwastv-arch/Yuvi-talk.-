package com.yuvitalk.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

data class ChatMessage(
    val text: String = "",
    val senderId: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { YuviTalkApp() }
    }
}

@Composable
fun YuviTalkApp() {
    val auth = remember { FirebaseAuth.getInstance() }
    var user by remember { mutableStateOf(auth.currentUser) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            if (user == null) {
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                    Text("YuviTalk", style = MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.height(8.dp))
                    Text("Sign in or create your account")
                    Spacer(Modifier.height(20.dp))
                    OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(password, { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
                    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = {
                        if (email.isBlank() || password.length < 6) {
                            error = "Enter an email and a password with at least 6 characters."
                        } else {
                            auth.signInWithEmailAndPassword(email.trim(), password)
                                .addOnSuccessListener { user = auth.currentUser; error = "" }
                                .addOnFailureListener { signInError ->
                                    auth.createUserWithEmailAndPassword(email.trim(), password)
                                        .addOnSuccessListener { user = auth.currentUser; error = "" }
                                        .addOnFailureListener { error = signInError.message ?: "Authentication failed" }
                                }
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Continue / Sign up") }
                }
            } else {
                ChatScreen(
                    uid = user!!.uid,
                    onSignOut = { auth.signOut(); user = null }
                )
            }
        }
    }
}

@Composable
fun ChatScreen(uid: String, onSignOut: () -> Unit) {
    val db = remember { FirebaseFirestore.getInstance() }
    var draft by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var status by remember { mutableStateOf("Connected to YuviTalk") }

    DisposableEffect(Unit) {
        val registration = db.collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limit(100)
            .addSnapshotListener { snapshot, e ->
                if (e != null) status = e.localizedMessage ?: "Could not load messages"
                else messages = snapshot?.documents?.mapNotNull { it.toObject(ChatMessage::class.java) } ?: emptyList()
            }
        onDispose { registration.remove() }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("YuviTalk Chat", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onSignOut) { Text("Sign out") }
        }
        Text(status, style = MaterialTheme.typography.bodySmall)
        Divider(Modifier.padding(vertical = 8.dp))
        LazyColumn(Modifier.weight(1f), reverseLayout = false) {
            items(messages) { message ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(10.dp)) {
                        Text(if (message.senderId == uid) "You" else "YuviTalk user",
                            style = MaterialTheme.typography.labelSmall)
                        Text(message.text)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = draft, onValueChange = { draft = it },
                modifier = Modifier.weight(1f), label = { Text("Message") }
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                val text = draft.trim()
                if (text.isNotEmpty()) {
                    db.collection("messages").add(
                        ChatMessage(text = text, senderId = uid)
                    ).addOnFailureListener { status = it.localizedMessage ?: "Send failed" }
                    draft = ""
                }
            }) { Text("Send") }
        }
    }
}
