package com.secret.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private val Bg = Color(0xFF090B0A)
private val Panel = Color(0xFF121614)
private val Green = Color(0xFF00FF88)
private val MainText = Color(0xFFF2F5F3)
private val Muted = Color(0xFF9AA49E)

data class ChatMessage(val role: String, val content: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SecretApp() }
    }
}

@Composable
fun SecretApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("secret_chat", 0) }
    val messages = remember {
        mutableStateListOf<ChatMessage>().apply {
            val saved = prefs.getString("messages", null)
            if (saved != null) {
                runCatching {
                    val arr = JSONArray(saved)
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        add(ChatMessage(item.getString("role"), item.getString("content")))
                    }
                }
            }
        }
    }
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    fun saveMessages() {
        val arr = JSONArray()
        messages.forEach { m ->
            arr.put(JSONObject().put("role", m.role).put("content", m.content))
        }
        prefs.edit().putString("messages", arr.toString()).apply()
    }

    suspend fun sendMessage(text: String) {
        if (text.isBlank() || loading) return
        val userText = text.trim()
        messages.add(ChatMessage("user", userText))
        input = ""
        loading = true
        error = null
        saveMessages()
        try {
            val answer = withContext(Dispatchers.IO) {
                val client = OkHttpClient.Builder()
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(100, TimeUnit.SECONDS)
                    .build()
                val arr = JSONArray()
                arr.put(JSONObject().put("role", "system").put("content",
                    "You are Secret, a helpful AI assistant. Answer in Persian when the user writes in Persian. Use markdown formatting for code, lists, and emphasis."))
                messages.takeLast(20).forEach { m ->
                    arr.put(JSONObject().put("role", m.role).put("content", m.content))
                }
                val body = JSONObject().put("messages", arr).toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())
                val req = Request.Builder()
                    .url(BuildConfig.API_BASE_URL.trimEnd('/') + "/api/chat")
                    .post(body)
                    .build()
                client.newCall(req).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw Exception("خطای سرور (${response.code}): ${raw.take(400)}")
                    }
                    val json = JSONObject(raw)
                    json.optString("reply").ifBlank {
                        json.optJSONArray("choices")?.optJSONObject(0)
                            ?.optJSONObject("message")?.optString("content")
                            ?: "پاسخی دریافت نشد."
                    }
                }
            }
            messages.add(ChatMessage("assistant", answer))
            saveMessages()
        } catch (e: Exception) {
            error = "اتصال برقرار نشد: ${e.message ?: "خطای نامشخص"}"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(messages.size, loading) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Green, background = Bg, surface = Panel,
        onBackground = MainText, onSurface = MainText
    )) {
        Column(
            modifier = Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().background(Panel).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("SECRET", color = Green, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                    Text("دستیار هوش مصنوعی", color = Muted, fontSize = 12.sp)
                }
                IconButton(onClick = {
                    messages.clear()
                    saveMessages()
                    error = null
                }) {
                    Icon(Icons.Default.Delete, contentDescription = "پاک کردن گفتگو", tint = Muted)
                }
                IconButton(onClick = {
                    messages.clear()
                    saveMessages()
                    error = null
                }) {
                    Icon(Icons.Default.Add, contentDescription = "گفتگوی جدید", tint = Green)
                }
            }

            if (messages.isEmpty()) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("چه چیزی توی ذهنته؟", color = MainText, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Text("هر سؤالی داری از Secret بپرس.", color = Muted, fontSize = 15.sp)
                    Spacer(Modifier.height(24.dp))
                    listOf("پایتون رو از پایه بهم یاد بده", "برای درس خوندن برنامه بده", "ایده‌ای برای یک پروژه پیشنهاد کن").forEach { prompt ->
                        OutlinedButton(
                            onClick = { scope.launch { sendMessage(prompt) } },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MainText)
                        ) { Text(prompt) }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                    state = listState,
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    itemsIndexed(messages) { _, msg ->
                        val isUser = msg.role == "user"
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(0.92f)
                                    .background(if (isUser) Color(0xFF153326) else Panel, RoundedCornerShape(16.dp))
                                    .padding(14.dp)
                            ) {
                                Text(if (isUser) "شما" else "SECRET", color = if (isUser) Green else Muted,
                                    fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(6.dp))
                                Text(msg.content, color = MainText, fontSize = 15.sp, lineHeight = 23.sp)
                            }
                        }
                    }
                    if (loading) {
                        item {
                            Text("Secret در حال نوشتن است…", color = Green, modifier = Modifier.padding(12.dp))
                        }
                    }
                }
            }

            error?.let {
                Text(it, color = Color(0xFFFF8A80), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth().background(Panel).padding(10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Box(
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                        .background(Color(0xFF1B211E), RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 13.dp)
                ) {
                    if (input.isEmpty()) Text("پیامت رو بنویس…", color = Muted, fontSize = 14.sp)
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(color = MainText, fontSize = 15.sp, lineHeight = 21.sp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { scope.launch { sendMessage(input) } }),
                        maxLines = 5
                    )
                }
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = { scope.launch { sendMessage(input) } },
                    enabled = !loading && input.isNotBlank(),
                    modifier = Modifier.size(48.dp).background(if (!loading && input.isNotBlank()) Green else Color(0xFF334139), RoundedCornerShape(15.dp))
                ) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "ارسال", tint = Bg)
                }
            }
            Text("برای استفاده، سرور API باید راه‌اندازی و در دسترس باشد.", color = Muted, fontSize = 10.sp,
                modifier = Modifier.fillMaxWidth().background(Panel).padding(bottom = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}
