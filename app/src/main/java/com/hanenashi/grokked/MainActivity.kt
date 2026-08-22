package com.hanenashi.grokked

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.text.DateFormat
import java.util.Date
import android.util.Base64

private const val IMAGE_MODEL = "grok-imagine-image-2.0"
private const val TICKS_PER_USD = 10_000_000_000.0

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { GrokkedApp() } }
    }
}

private data class ActivityItem(
    val createdAt: Long,
    val model: String,
    val costInUsdTicks: Long?,
)

private data class GeneratedImage(val bytes: ByteArray, val costInUsdTicks: Long?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GrokkedApp() {
    var apiKey by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var generatedImage by remember { mutableStateOf<GeneratedImage?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var generating by remember { mutableStateOf(false) }
    var activitySelected by remember { mutableStateOf(false) }
    val activity = remember { mutableStateListOf<ActivityItem>() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Grokked") }) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = !activitySelected, onClick = { activitySelected = false }, icon = {}, label = { Text("Generate") })
                NavigationBarItem(selected = activitySelected, onClick = { activitySelected = true }, icon = {}, label = { Text("Activity") })
            }
        },
    ) { padding ->
        if (activitySelected) {
            ActivityScreen(padding, activity)
        } else {
            GenerateScreen(
                padding = padding,
                apiKey = apiKey,
                onApiKeyChange = { apiKey = it },
                prompt = prompt,
                onPromptChange = { prompt = it },
                generatedImage = generatedImage,
                error = error,
                generating = generating,
                onGenerate = {
                    if (apiKey.isBlank() || prompt.isBlank() || generating) return@GenerateScreen
                    generating = true
                    error = null
                    generatedImage = null
                    scope.launch {
                        runCatching { generateImage(apiKey.trim(), prompt.trim()) }
                            .onSuccess { result ->
                                generatedImage = result
                                activity.add(0, ActivityItem(System.currentTimeMillis(), IMAGE_MODEL, result.costInUsdTicks))
                            }
                            .onFailure { error = it.message ?: "Image generation failed." }
                        generating = false
                    }
                },
            )
        }
    }
}

@Composable
private fun GenerateScreen(
    padding: PaddingValues,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    prompt: String,
    onPromptChange: (String) -> Unit,
    generatedImage: GeneratedImage?,
    error: String?,
    generating: Boolean,
    onGenerate: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Text("Native Compose prototype", style = MaterialTheme.typography.titleLarge) }
        item { Text("Your xAI key stays only in memory for this basic build.") }
        item {
            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                label = { Text("xAI API key") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = prompt,
                onValueChange = onPromptChange,
                label = { Text("Describe an image") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Button(onClick = onGenerate, enabled = apiKey.isNotBlank() && prompt.isNotBlank() && !generating, modifier = Modifier.fillMaxWidth()) {
                Text(if (generating) "Generating…" else "Generate image")
            }
        }
        if (error != null) item { Text(error, color = MaterialTheme.colorScheme.error) }
        if (generatedImage != null) item {
            val bitmap = remember(generatedImage.bytes) { BitmapFactory.decodeByteArray(generatedImage.bytes, 0, generatedImage.bytes.size) }
            if (bitmap != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Image(bitmap = bitmap.asImageBitmap(), contentDescription = "Generated image", modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Text("Actual xAI cost: ${formatCost(generatedImage.costInUsdTicks)}")
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityScreen(padding: PaddingValues, activity: List<ActivityItem>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Activity", style = MaterialTheme.typography.titleLarge) }
        item { Text("This session only. Native persistent history comes next.") }
        if (activity.isEmpty()) item { Text("No generations yet.") }
        items(activity) { entry ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(entry.model, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(DateFormat.getDateTimeInstance().format(Date(entry.createdAt)))
                    Text("Actual xAI cost: ${formatCost(entry.costInUsdTicks)}")
                }
            }
        }
    }
}

private fun formatCost(costInUsdTicks: Long?): String =
    costInUsdTicks?.let { "$%.4f".format(it / TICKS_PER_USD) } ?: "Not returned"

private suspend fun generateImage(apiKey: String, prompt: String): GeneratedImage = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    val connection = (URL("https://api.x.ai/v1/images/generations").openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        doOutput = true
        connectTimeout = 30_000
        readTimeout = 90_000
        setRequestProperty("Authorization", "Bearer $apiKey")
        setRequestProperty("Content-Type", "application/json")
    }
    try {
        val payload = JSONObject().apply {
            put("model", IMAGE_MODEL)
            put("prompt", prompt)
            put("response_format", "b64_json")
        }.toString().toByteArray(StandardCharsets.UTF_8)
        connection.outputStream.use { it.write(payload) }
        val response = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val responseText = response.readUtf8()
        if (connection.responseCode !in 200..299) throw IllegalStateException("xAI request failed (${connection.responseCode}).")
        val json = JSONObject(responseText)
        val image = json.getJSONArray("data").getJSONObject(0).getString("b64_json")
        val cost = json.optJSONObject("usage")?.let { usage -> if (usage.has("cost_in_usd_ticks")) usage.getLong("cost_in_usd_ticks") else null }
        GeneratedImage(Base64.decode(image, Base64.DEFAULT), cost)
    } finally {
        connection.disconnect()
    }
}

private fun InputStream?.readUtf8(): String {
    if (this == null) return ""
    return use { input ->
        val output = ByteArrayOutputStream()
        input.copyTo(output)
        output.toString(StandardCharsets.UTF_8.name())
    }
}
