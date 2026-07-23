package dev.muzziknod.host.contract

data class ParameterSpec(
    val id: String,
    val label: String,
    val range: ClosedFloatingPointRange<Double>,
    val default: Double,
)

data class ModuleContract(
    val typeId: String,
    val version: Int,
    val ports: List<PortSpec>,
    val parameters: List<ParameterSpec> = emptyList(),
)