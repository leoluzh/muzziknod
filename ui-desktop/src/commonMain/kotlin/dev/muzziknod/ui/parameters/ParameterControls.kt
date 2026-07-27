package dev.muzziknod.ui.parameters

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.muzziknod.host.contract.ParameterSpec

/**
 * A single parameter's control (slider), clamped to [ParameterSpec.range]
 * (contracts/ui-composables-contract.md; US2 AC3-4, FR-009).
 */
@Composable
fun ParameterControl(
    spec: ParameterSpec,
    currentValue: Double,
    onValueChange: (Double) -> Unit,
) {
    Column(modifier = Modifier.testTag("parameter-${spec.id}")) {
        Text(text = "${spec.label}: $currentValue")
        Slider(
            value = currentValue.toFloat(),
            onValueChange = { raw ->
                onValueChange(raw.toDouble().coerceIn(spec.range))
            },
            valueRange = spec.range.start.toFloat()..spec.range.endInclusive.toFloat(),
            modifier = Modifier.testTag("parameter-${spec.id}-slider"),
        )
    }
}
