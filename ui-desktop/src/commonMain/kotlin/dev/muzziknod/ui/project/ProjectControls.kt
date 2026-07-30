package dev.muzziknod.ui.project

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * Save/Save As/Load project-file controls (006-project-persistence; contracts/
 * project-file-schema.md). A single path field drives all three actions — this UI
 * doesn't track "the currently open file" separately, so Save and Save As only differ
 * in which path the user typed.
 */
@Composable
fun ProjectControls(
    projectMessage: String?,
    onSave: (path: String) -> Unit,
    onSaveAs: (path: String) -> Unit,
    onLoad: (path: String) -> Unit,
) {
    var path by remember { mutableStateOf("") }

    Column(modifier = Modifier.testTag("project-controls")) {
        TextField(
            value = path,
            onValueChange = { path = it },
            label = { Text("Caminho do arquivo de projeto") },
            modifier = Modifier.testTag("project-path-field"),
        )
        Row {
            Text(
                text = "Salvar",
                modifier = Modifier.testTag("project-save").clickable(enabled = path.isNotBlank()) { onSave(path) },
            )
            Text(
                text = "Salvar como",
                modifier = Modifier.testTag("project-save-as").clickable(enabled = path.isNotBlank()) { onSaveAs(path) },
            )
            Text(
                text = "Carregar",
                modifier = Modifier.testTag("project-load").clickable(enabled = path.isNotBlank()) { onLoad(path) },
            )
        }
        projectMessage?.let {
            Text(text = it, modifier = Modifier.testTag("project-message"))
        }
    }
}
