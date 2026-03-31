package com.example.myfirstapprc

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.remote.player.view.RemoteComposePlayer

class PlayerActivity : ComponentActivity() {

    private val TAG = "RcViewer"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uriString = intent.getStringExtra("uri")

        if (uriString != null) {
            // --- Imported file mode ---
            val displayName = intent.getStringExtra("displayName") ?: "Imported file"
            val bytes = loadBytesFromUri(Uri.parse(uriString))
            if (bytes == null) {
                Toast.makeText(this, "Cannot read file: $displayName", Toast.LENGTH_LONG).show()
                finish()
                return
            }
            setContent {
                MaterialTheme {
                    ImportedPlayerScreen(
                        displayName = displayName,
                        bytes = bytes,
                        onBack = { finish() }
                    )
                }
            }
        } else {
            // --- Built-in demo mode ---
            val docs = MainActivity.allDocs
            val initialIndex = intent.getIntExtra("index", 0)

            if (docs.isEmpty()) {
                finish()
                return
            }

            setContent {
                MaterialTheme {
                    DemoPlayerScreen(
                        docs = docs,
                        initialIndex = initialIndex,
                        onBack = { finish() },
                        loadDocument = ::loadDoc
                    )
                }
            }
        }
    }

    private fun loadBytesFromUri(uri: Uri): ByteArray? {
        return try {
            contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read URI: $uri", e)
            null
        }
    }

    private fun loadDoc(player: RemoteComposePlayer, doc: RcDoc) {
        try {
            val bytes = resources.openRawResource(doc.id).use { it.readBytes() }
            player.setDocument(bytes)
            Log.d(TAG, "setDocument OK for ${doc.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load ${doc.name}", e)
        }
    }
}

// --- Imported file player (no prev/next) ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportedPlayerScreen(
    displayName: String,
    bytes: ByteArray,
    onBack: () -> Unit
) {
    var player by remember { mutableStateOf<RemoteComposePlayer?>(null) }
    var playerReady by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf(false) }

    LaunchedEffect(playerReady) {
        if (playerReady && player != null) {
            try {
                player!!.setDocument(bytes)
            } catch (_: Exception) {
                loadError = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            displayName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "Imported file",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        if (loadError) {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Failed to render this file.\nIt may not be a valid .rc document.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }
        } else {
            PlayerView(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxWidth()
                    .fillMaxSize(),
                onPlayerReady = { p ->
                    player = p
                    playerReady = true
                }
            )
        }
    }
}

// --- Built-in demo player (with prev/next) ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoPlayerScreen(
    docs: List<RcDoc>,
    initialIndex: Int,
    onBack: () -> Unit,
    loadDocument: (RemoteComposePlayer, RcDoc) -> Unit
) {
    var currentIndex by remember { mutableIntStateOf(initialIndex) }
    val doc = docs[currentIndex]
    val hasPrev = currentIndex > 0
    val hasNext = currentIndex < docs.size - 1

    var player by remember { mutableStateOf<RemoteComposePlayer?>(null) }
    var playerReady by remember { mutableStateOf(false) }

    LaunchedEffect(currentIndex, playerReady) {
        if (playerReady && player != null) {
            loadDocument(player!!, docs[currentIndex])
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            doc.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${currentIndex + 1} / ${docs.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { if (hasPrev) currentIndex-- },
                        enabled = hasPrev
                    ) { Text("\u25C0") }
                    IconButton(
                        onClick = { if (hasNext) currentIndex++ },
                        enabled = hasNext
                    ) { Text("\u25B6") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        PlayerView(
            modifier = Modifier
                .padding(padding)
                .fillMaxWidth()
                .fillMaxSize(),
            onPlayerReady = { p ->
                player = p
                playerReady = true
            }
        )
    }
}

// --- Shared player view factory ---

@Composable
fun PlayerView(
    modifier: Modifier = Modifier,
    onPlayerReady: (RemoteComposePlayer) -> Unit
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            FrameLayout(context).also { container ->
                val p = RemoteComposePlayer(context)
                container.addView(
                    p,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                container.viewTreeObserver.addOnGlobalLayoutListener(object :
                    ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        container.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        onPlayerReady(p)
                    }
                })
            }
        }
    )
}
