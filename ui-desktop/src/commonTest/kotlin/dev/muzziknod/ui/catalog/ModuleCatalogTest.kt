package dev.muzziknod.ui.catalog

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import dev.muzziknod.refmodules.oscillator.OscillatorModule
import dev.muzziknod.ui.state.ModuleCatalogEntry
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ModuleCatalogTest {
    @Test
    fun listsEntriesAndInvokesOnAddWithTheSelectedEntry() = runComposeUiTest {
        val entries = listOf(ModuleCatalogEntry("oscillator") { OscillatorModule(instanceId = "osc-x") })
        var added: ModuleCatalogEntry? = null

        setContent {
            ModuleCatalog(entries = entries, onAdd = { added = it })
        }

        onNodeWithTag("catalog-entry-oscillator").assertExists()
        onNodeWithTag("catalog-entry-oscillator").performClick()

        assertEquals("oscillator", added?.typeName)
    }

    @Test
    fun defaultModuleCatalogListsAllExistingModuleTypesExceptUnbuiltSamplerAndSynth() {
        val typeNames = defaultModuleCatalog().map { it.typeName }.toSet()

        assertEquals(
            setOf("oscillator", "midi-generator", "midi-logger", "midi-sequencer", "delay", "reverb", "distortion", "eq"),
            typeNames,
        )
    }
}
