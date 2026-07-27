package dev.muzziknod.ui.parameters

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runComposeUiTest
import dev.muzziknod.host.contract.ParameterSpec
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ParameterControlTest {
    @Test
    fun onValueChangeNeverReceivesAValueOutsideSpecRangeEvenWhenDraggedPastTheLimit() = runComposeUiTest {
        val spec = ParameterSpec(id = "mix", label = "Mix", range = 0.0..1.0, default = 0.5)
        val receivedValues = mutableListOf<Double>()

        setContent {
            ParameterControl(spec = spec, currentValue = 0.5, onValueChange = { receivedValues += it })
        }

        onNodeWithTag("parameter-mix-slider")
            .performSemanticsAction(SemanticsActions.SetProgress) { action -> action(2.0f) }

        assertTrue(receivedValues.isNotEmpty())
        assertTrue(receivedValues.all { it in spec.range }, "onValueChange must never emit a value outside ${spec.range}, got $receivedValues")
    }
}
