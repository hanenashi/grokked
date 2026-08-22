package com.hanenashi.grokked

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.widget.VideoView
import android.widget.MediaController
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.text.DateFormat
import java.util.Date
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

private const val IMAGE_MODEL = "grok-imagine-image-2.0"
private const val VIDEO_MODEL = "grok-imagine-video-1.5"
private const val XAI_API = "https://api.x.ai/v1"
private const val TICKS_PER_USD = 10_000_000_000.0
private const val MAX_ACTIVITY = 50

private enum class CreationMode(val label: String, val needsImage: Boolean, val isVideo: Boolean) {
    TEXT_TO_IMAGE("Text to image", false, false),
    IMAGE_TO_IMAGE("Image to image", true, false),
    TEXT_TO_VIDEO("Text to video", false, true),
    IMAGE_TO_VIDEO("Image to video", true, true),
}

private data class SourceImage(val dataUri: String, val bitmap: Bitmap)
private data class ActivityItem(val createdAt: Long, val mode: String, val model: String, val settings: String, val costInUsdTicks: Long?)
private sealed interface GenerationResult { val costInUsdTicks: Long? }
private data class ImageResult(val bytes: ByteArray, override val costInUsdTicks: Long?) : GenerationResult
private data class VideoResult(val url: String, override val costInUsdTicks: Long?) : GenerationResult

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GrokkedTheme { GrokkedApp(applicationContext) } }
    }
}

@Composable
private fun GrokkedTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFD0BCFF),
            onPrimary = Color(0xFF381E72),
            background = Color.Black,
            surface = Color(0xFF111111),
            surfaceVariant = Color(0xFF1D1B20),
        ),
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GrokkedApp(context: Context) {
    val store = remember { LocalStore(context) }
    var apiKey by remember { mutableStateOf(store.loadApiKey()) }
    var rememberKey by remember { mutableStateOf(apiKey.isNotBlank()) }
    var mode by remember { mutableStateOf(CreationMode.TEXT_TO_IMAGE) }
    var prompt by remember { mutableStateOf("") }
    var sourceImage by remember { mutableStateOf<SourceImage?>(null) }
    var result by remember { mutableStateOf<GenerationResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var generating by remember { mutableStateOf(false) }
    var activitySelected by remember { mutableStateOf(false) }
    val activity = remember { mutableStateListOf<ActivityItem>().apply { addAll(store.loadActivity()) } }
    val scope = rememberCoroutineScope()
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        sourceImage = uri?.let { readSourceImage(context, it) }
        if (sourceImage == null && uri != null) error = "Could not read that image."
    }

    LaunchedEffect(apiKey, rememberKey) {
        if (rememberKey && apiKey.isNotBlank()) store.saveApiKey(apiKey) else if (!rememberKey) store.clearApiKey()
    }

    fun record(generated: GenerationResult) {
        activity.add(0, ActivityItem(
            createdAt = System.currentTimeMillis(),
            mode = mode.label,
            model = if (mode.isVideo) VIDEO_MODEL else IMAGE_MODEL,
            settings = if (mode.isVideo) "480p · 5 seconds" else if (mode.needsImage) "Source image edit" else "Image generation",
            costInUsdTicks = generated.costInUsdTicks,
        ))
        while (activity.size > MAX_ACTIVITY) activity.removeLast()
        store.saveActivity(activity)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Grokked") },
                actions = { if (apiKey.isNotBlank()) TextButton(onClick = { apiKey = ""; rememberKey = false }) { Text("Forget key") } },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = !activitySelected, onClick = { activitySelected = false }, icon = {}, label = { Text("Create") })
                NavigationBarItem(selected = activitySelected, onClick = { activitySelected = true }, icon = {}, label = { Text("Activity") })
            }
        },
    ) { padding ->
        if (activitySelected) {
            ActivityScreen(padding, activity) { activity.clear(); store.saveActivity(activity) }
        } else {
            GenerateScreen(
                padding = padding,
                apiKey = apiKey,
                onApiKeyChange = { apiKey = it },
                rememberKey = rememberKey,
                onRememberKeyChange = { rememberKey = it },
                mode = mode,
                onModeChange = { mode = it; result = null; error = null },
                prompt = prompt,
                onPromptChange = { prompt = it },
                sourceImage = sourceImage,
                onChooseImage = { imagePicker.launch("image/*") },
                onClearImage = { sourceImage = null },
                result = result,
                error = error,
                generating = generating,
                onGenerate = {
                    if (apiKey.isBlank() || prompt.isBlank() || (mode.needsImage && sourceImage == null) || generating) return@GenerateScreen
                    generating = true
                    error = null
                    result = null
                    scope.launch {
                        runCatching { create(apiKey.trim(), mode, prompt.trim(), sourceImage?.dataUri) }
                            .onSuccess { generated -> result = generated; record(generated) }
                            .onFailure { error = it.message ?: "Generation failed." }
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
    rememberKey: Boolean,
    onRememberKeyChange: (Boolean) -> Unit,
    mode: CreationMode,
    onModeChange: (CreationMode) -> Unit,
    prompt: String,
    onPromptChange: (String) -> Unit,
    sourceImage: SourceImage?,
    onChooseImage: () -> Unit,
    onClearImage: () -> Unit,
    result: GenerationResult?,
    error: String?,
    generating: Boolean,
    onGenerate: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text("Create", style = MaterialTheme.typography.headlineMedium) }
        item { Text("Your API key is encrypted with Android Keystore when saved on this device.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            OutlinedTextField(value = apiKey, onValueChange = onApiKeyChange, label = { Text("xAI API key") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        }
        item { Row { Checkbox(checked = rememberKey, onCheckedChange = onRememberKeyChange); Text("Remember encrypted key on this device", modifier = Modifier.padding(top = 12.dp)) } }
        item { ModePicker(mode, onModeChange) }
        if (mode.needsImage) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (sourceImage == null) OutlinedButton(onClick = onChooseImage, modifier = Modifier.fillMaxWidth()) { Text("Choose source image") }
                else {
                    Image(sourceImage.bitmap.asImageBitmap(), "Source image", Modifier.fillMaxWidth().height(190.dp), contentScale = ContentScale.Crop)
                    TextButton(onClick = onClearImage) { Text("Remove source image") }
                }
            }
        }
        item {
            OutlinedTextField(value = prompt, onValueChange = onPromptChange, label = { Text(if (mode.isVideo) "Describe the video" else "Describe the image") }, minLines = 4, modifier = Modifier.fillMaxWidth())
        }
        item {
            Button(onClick = onGenerate, enabled = apiKey.isNotBlank() && prompt.isNotBlank() && (!mode.needsImage || sourceImage != null) && !generating, modifier = Modifier.fillMaxWidth()) {
                Text(if (generating) "Generating…" else "Generate ${if (mode.isVideo) "video" else "image"}")
            }
        }
        if (error != null) item { Text(error, color = MaterialTheme.colorScheme.error) }
        if (result is ImageResult) item { ImageResultCard(result) }
        if (result is VideoResult) item { VideoResultCard(result) }
    }
}

@Composable
private fun ModePicker(selected: CreationMode, onSelect: (CreationMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeButton(CreationMode.TEXT_TO_IMAGE, selected, onSelect)
            ModeButton(CreationMode.IMAGE_TO_IMAGE, selected, onSelect)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeButton(CreationMode.TEXT_TO_VIDEO, selected, onSelect)
            ModeButton(CreationMode.IMAGE_TO_VIDEO, selected, onSelect)
        }
    }
}

@Composable
private fun ModeButton(mode: CreationMode, selected: CreationMode, onSelect: (CreationMode) -> Unit) {
    if (mode == selected) Button(onClick = { onSelect(mode) }) { Text(mode.label) }
    else OutlinedButton(onClick = { onSelect(mode) }) { Text(mode.label) }
}

@Composable
private fun ImageResultCard(result: ImageResult) {
    val bitmap = remember(result.bytes) { BitmapFactory.decodeByteArray(result.bytes, 0, result.bytes.size) }
    if (bitmap != null) Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Image(bitmap.asImageBitmap(), "Generated image", Modifier.fillMaxWidth(), contentScale = ContentScale.Fit)
            Spacer(Modifier.height(8.dp))
            Text("Actual xAI cost: ${formatCost(result.costInUsdTicks)}")
        }
    }
}

@Composable
private fun VideoResultCard(result: VideoResult) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            AndroidView(
                factory = { context ->
                    VideoView(context).also { view ->
                        view.setMediaController(MediaController(context).also { it.setAnchorView(view) })
                        view.setVideoURI(Uri.parse(result.url))
                        view.setOnPreparedListener { it.start() }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(260.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text("Actual xAI cost: ${formatCost(result.costInUsdTicks)}")
        }
    }
}

@Composable
private fun ActivityScreen(padding: PaddingValues, activity: List<ActivityItem>, onClear: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Activity", style = MaterialTheme.typography.headlineMedium); if (activity.isNotEmpty()) TextButton(onClick = onClear) { Text("Clear") } } }
        item { Text("Stored locally on this device. Prompts, source images, media URLs, and API keys are not saved here.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (activity.isEmpty()) item { Text("No generations yet.") }
        items(activity) { entry ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(entry.mode, style = MaterialTheme.typography.titleMedium)
                    Text(entry.model, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(entry.settings, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(5.dp))
                    Text(DateFormat.getDateTimeInstance().format(Date(entry.createdAt)))
                    Text("Actual xAI cost: ${formatCost(entry.costInUsdTicks)}")
                }
            }
        }
    }
}

private suspend fun create(apiKey: String, mode: CreationMode, prompt: String, imageDataUri: String?): GenerationResult =
    if (mode.isVideo) generateVideo(apiKey, mode, prompt, imageDataUri) else generateImage(apiKey, mode, prompt, imageDataUri)

private suspend fun generateImage(apiKey: String, mode: CreationMode, prompt: String, imageDataUri: String?): ImageResult = withContext(Dispatchers.IO) {
    val body = JSONObject().apply {
        put("model", IMAGE_MODEL)
        put("prompt", prompt)
        put("response_format", "b64_json")
        if (mode.needsImage) put("image", JSONObject().put("url", imageDataUri).put("type", "image_url"))
    }
    val endpoint = if (mode.needsImage) "/images/edits" else "/images/generations"
    val json = requestJson(endpoint, "POST", apiKey, body)
    ImageResult(Base64.decode(json.getJSONArray("data").getJSONObject(0).getString("b64_json"), Base64.DEFAULT), json.cost())
}

private suspend fun generateVideo(apiKey: String, mode: CreationMode, prompt: String, imageDataUri: String?): VideoResult = withContext(Dispatchers.IO) {
    val body = JSONObject().apply {
        put("model", VIDEO_MODEL)
        put("prompt", prompt)
        put("duration", 5)
        put("resolution", "480p")
        if (mode.needsImage) put("image", JSONObject().put("url", imageDataUri))
    }
    val requestId = requestJson("/videos/generations", "POST", apiKey, body).getString("request_id")
    repeat(120) {
        delay(5_000)
        val result = requestJson("/videos/$requestId", "GET", apiKey)
        if (result.optString("status") == "done") return@withContext VideoResult(result.getJSONObject("video").getString("url"), result.cost())
        if (result.optString("status") in setOf("failed", "expired") || result.has("error")) throw IllegalStateException("xAI video generation failed.")
    }
    throw IllegalStateException("Timed out waiting for xAI video generation.")
}

private fun requestJson(path: String, method: String, apiKey: String, body: JSONObject? = null): JSONObject {
    val connection = (URL("$XAI_API$path").openConnection() as HttpURLConnection).apply {
        requestMethod = method
        doOutput = body != null
        connectTimeout = 30_000
        readTimeout = 90_000
        setRequestProperty("Authorization", "Bearer $apiKey")
        setRequestProperty("Content-Type", "application/json")
    }
    try {
        body?.toString()?.toByteArray(StandardCharsets.UTF_8)?.let { payload -> connection.outputStream.use { it.write(payload) } }
        val responseText = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream).readUtf8()
        if (connection.responseCode !in 200..299) throw IllegalStateException("xAI request failed (${connection.responseCode}).")
        return JSONObject(responseText)
    } finally {
        connection.disconnect()
    }
}

private fun JSONObject.cost(): Long? = optJSONObject("usage")?.let { if (it.has("cost_in_usd_ticks")) it.getLong("cost_in_usd_ticks") else null }
private fun formatCost(costInUsdTicks: Long?): String = costInUsdTicks?.let { "$%.4f".format(it / TICKS_PER_USD) } ?: "Not returned"

private fun readSourceImage(context: Context, uri: Uri): SourceImage? = runCatching {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("No image data")
    val mime = context.contentResolver.getType(uri)?.takeIf { it.startsWith("image/") } ?: "image/png"
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("Invalid image")
    SourceImage("data:$mime;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}", bitmap)
}.getOrNull()

private fun InputStream?.readUtf8(): String = this?.use { input ->
    val output = ByteArrayOutputStream()
    input.copyTo(output)
    output.toString(StandardCharsets.UTF_8.name())
} ?: ""

private class LocalStore(context: Context) {
    private val preferences = context.getSharedPreferences("grokked", Context.MODE_PRIVATE)

    fun saveApiKey(apiKey: String) { preferences.edit().putString("encrypted_api_key", encrypt(apiKey)).apply() }
    fun loadApiKey(): String = preferences.getString("encrypted_api_key", null)?.let(::decrypt).orEmpty()
    fun clearApiKey() { preferences.edit().remove("encrypted_api_key").apply() }

    fun loadActivity(): List<ActivityItem> = runCatching {
        val records = JSONArray(preferences.getString("activity", "[]"))
        List(records.length()) { index -> records.getJSONObject(index).let { ActivityItem(it.getLong("createdAt"), it.getString("mode"), it.getString("model"), it.getString("settings"), if (it.has("cost")) it.getLong("cost") else null) } }
    }.getOrDefault(emptyList())

    fun saveActivity(activity: List<ActivityItem>) {
        val records = JSONArray()
        activity.take(MAX_ACTIVITY).forEach { entry -> records.put(JSONObject().apply { put("createdAt", entry.createdAt); put("mode", entry.mode); put("model", entry.model); put("settings", entry.settings); entry.costInUsdTicks?.let { put("cost", it) } }) }
        preferences.edit().putString("activity", records.toString()).apply()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        return "${Base64.encodeToString(cipher.iv, Base64.NO_WRAP)}:${Base64.encodeToString(cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8)), Base64.NO_WRAP)}"
    }

    private fun decrypt(value: String): String = runCatching {
        val parts = value.split(":", limit = 2)
        require(parts.size == 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, secretKey(), javax.crypto.spec.GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP))) }
        String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), StandardCharsets.UTF_8)
    }.getOrElse { clearApiKey(); "" }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey("grokked-api-key", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("grokked-api-key", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
}
