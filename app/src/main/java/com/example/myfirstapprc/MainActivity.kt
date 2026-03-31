package com.example.myfirstapprc

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class RcDoc(val id: Int, val name: String)

data class RecentImport(val uri: String, val displayName: String, val timestamp: Long)

class MainActivity : ComponentActivity() {

    companion object {
        var allDocs: List<RcDoc> = emptyList()
            private set

        private const val PREFS_NAME = "rc_viewer_prefs"
        private const val KEY_RECENTS = "recent_imports"
        private const val MAX_RECENTS = 5
    }

    private var recentImports = mutableStateOf<List<RecentImport>>(emptyList())

    private val openFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            // Take persistable read permission so we can re-open later
            try {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Permission may not be persistable — still try to open now
            }

            val displayName = queryFileName(uri) ?: uri.lastPathSegment ?: "Imported file"
            addRecentImport(uri.toString(), displayName)

            startActivity(
                Intent(this, PlayerActivity::class.java)
                    .putExtra("uri", uri.toString())
                    .putExtra("displayName", displayName)
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        allDocs = discoverRcDocs()
        recentImports.value = loadRecentImports()

        setContent {
            MaterialTheme {
                BrowserScreen(
                    docs = allDocs,
                    recentImports = recentImports.value,
                    onDocSelected = { index ->
                        startActivity(
                            Intent(this, PlayerActivity::class.java)
                                .putExtra("index", index)
                        )
                    },
                    onOpenFile = {
                        openFileLauncher.launch(arrayOf("*/*"))
                    },
                    onRecentSelected = { recent ->
                        startActivity(
                            Intent(this, PlayerActivity::class.java)
                                .putExtra("uri", recent.uri)
                                .putExtra("displayName", recent.displayName)
                        )
                    },
                    onClearRecents = {
                        clearRecentImports()
                        recentImports.value = emptyList()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        recentImports.value = loadRecentImports()
    }

    private fun discoverRcDocs(): List<RcDoc> {
        val result = mutableListOf<RcDoc>()
        for (field in R.raw::class.java.fields) {
            result.add(RcDoc(field.getInt(null), field.name))
        }
        result.sortBy { it.name }
        return result
    }

    private fun queryFileName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
            }
        } catch (_: Exception) { null }
    }

    private fun loadRecentImports(): List<RecentImport> {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_RECENTS, null) ?: return emptyList()
        return raw.split("\n").mapNotNull { line ->
            val parts = line.split("\t", limit = 3)
            if (parts.size == 3) {
                RecentImport(parts[0], parts[1], parts[2].toLongOrNull() ?: 0L)
            } else null
        }
    }

    private fun addRecentImport(uri: String, displayName: String) {
        val current = loadRecentImports().toMutableList()
        current.removeAll { it.uri == uri }
        current.add(0, RecentImport(uri, displayName, System.currentTimeMillis()))
        val trimmed = current.take(MAX_RECENTS)
        saveRecentImports(trimmed)
        recentImports.value = trimmed
    }

    private fun saveRecentImports(imports: List<RecentImport>) {
        val raw = imports.joinToString("\n") { "${it.uri}\t${it.displayName}\t${it.timestamp}" }
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_RECENTS, raw).apply()
    }

    private fun clearRecentImports() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_RECENTS).apply()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    docs: List<RcDoc>,
    recentImports: List<RecentImport>,
    onDocSelected: (Int) -> Unit,
    onOpenFile: () -> Unit,
    onRecentSelected: (RecentImport) -> Unit,
    onClearRecents: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val grouped = remember(docs) { categorize(docs) }

    val filteredDocs = remember(docs, searchQuery) {
        if (searchQuery.isBlank()) null
        else docs.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("RC Viewer", fontWeight = FontWeight.Bold)
                        Text(
                            "${docs.size} built-in demos",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Search bar with clear button
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search demos\u2026") },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.outline
                    )
                },
                trailingIcon = {
                    AnimatedVisibility(
                        visible = searchQuery.isNotEmpty(),
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                Icons.Filled.Clear,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )

            if (filteredDocs != null) {
                // Flat filtered list
                if (filteredDocs.isEmpty()) {
                    EmptyState("No demos matching \"$searchQuery\"")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(filteredDocs, key = { it.name }) { doc ->
                            val index = docs.indexOf(doc)
                            DemoItem(doc.name) { onDocSelected(index) }
                        }
                    }
                }
            } else {
                // Main list: open file button + recent imports + grouped demos
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    // Open file button
                    item(key = "open_file") {
                        FilledTonalButton(
                            onClick = onOpenFile,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Open .rc file from storage\u2026")
                        }
                    }

                    // Recent imports section
                    if (recentImports.isNotEmpty()) {
                        item(key = "recent_header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Recent Imports",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = onClearRecents) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Clear recents",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                        items(recentImports, key = { "recent_${it.uri}" }) { recent ->
                            RecentImportItem(recent.displayName) { onRecentSelected(recent) }
                        }
                        item(key = "recent_divider") {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }

                    // Grouped built-in demos
                    grouped.forEach { (category, categoryDocs) ->
                        item(key = "header_$category") {
                            CategoryHeader(category, categoryDocs.size)
                        }
                        items(categoryDocs, key = { it.name }) { doc ->
                            val index = docs.indexOf(doc)
                            DemoItem(doc.name) { onDocSelected(index) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
fun CategoryHeader(name: String, count: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun DemoItem(name: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "\u203A",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
fun RecentImportItem(name: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "\uD83D\uDCC4",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "\u203A",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

fun categorize(docs: List<RcDoc>): List<Pair<String, List<RcDoc>>> {
    val groups = mutableMapOf<String, MutableList<RcDoc>>()
    for (doc in docs) {
        val cat = inferCategory(doc.name)
        groups.getOrPut(cat) { mutableListOf() }.add(doc)
    }
    return groups.toList().sortedBy { it.first }
}

fun inferCategory(name: String): String = when {
    name.startsWith("c_modifier_") -> "Modifiers"
    name.startsWith("c_") || name.startsWith("cm_") -> "Components"
    name.startsWith("clock") || name.startsWith("digital_clock") ||
        name.startsWith("fancy_clock") -> "Clocks"
    name.startsWith("color") -> "Color"
    name.startsWith("demo_") -> "Demos"
    name.startsWith("experimental_") -> "Experimental"
    name.startsWith("flow_control_") -> "Flow Control"
    name.startsWith("graph_") -> "Graphs"
    else -> "Other"
}
