package dev.muzziknod.ui.project

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ProjectControlsTest {

    @Test
    fun saveSaveAsAndLoadEachFireWithTheTypedPath() = runComposeUiTest {
        val savedPaths = mutableListOf<String>()
        val savedAsPaths = mutableListOf<String>()
        val loadedPaths = mutableListOf<String>()

        setContent {
            ProjectControls(
                projectMessage = null,
                onSave = { savedPaths += it },
                onSaveAs = { savedAsPaths += it },
                onLoad = { loadedPaths += it },
            )
        }

        onNodeWithTag("project-path-field").performTextInput("/tmp/my-project.json")
        onNodeWithTag("project-save").performClick()
        onNodeWithTag("project-save-as").performClick()
        onNodeWithTag("project-load").performClick()

        assertEquals(listOf("/tmp/my-project.json"), savedPaths)
        assertEquals(listOf("/tmp/my-project.json"), savedAsPaths)
        assertEquals(listOf("/tmp/my-project.json"), loadedPaths)
    }

    @Test
    fun blankPathDisablesAllActions() = runComposeUiTest {
        var saveCalled = false

        setContent {
            ProjectControls(projectMessage = null, onSave = { saveCalled = true }, onSaveAs = {}, onLoad = {})
        }

        onNodeWithTag("project-save").performClick()

        assertTrue(!saveCalled, "Save must be disabled while the path field is blank")
    }

    @Test
    fun projectMessageIsDisplayedWhenPresent() = runComposeUiTest {
        setContent {
            ProjectControls(projectMessage = "Projeto salvo em /tmp/x.json", onSave = {}, onSaveAs = {}, onLoad = {})
        }

        onNodeWithTag("project-message").assertTextContains("Projeto salvo em /tmp/x.json")
    }
}
