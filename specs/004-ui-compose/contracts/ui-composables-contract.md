# Contract: Composables Públicos do Módulo `ui-desktop`

Superfície pública do novo módulo, testável independentemente via `runComposeUiTest`
(ver quickstart.md). Composables internos de layout/estilo não fazem parte deste
contrato — apenas os pontos de entrada usados pelos testes e pelo `Main.kt` desktop.

## `dev.muzziknod.ui.state.HostViewModel`

```kotlin
class HostViewModel(
    private val registry: ModuleRegistry,
    private val graph: RoutingGraph,
) {
    val uiState: StateFlow<HostUiState>

    fun connect(sourceInstanceId: String, sourcePortId: String, targetInstanceId: String, targetPortId: String): ConnectResult
    fun disconnect(connectionId: String)
    fun addModule(entry: ModuleCatalogEntry)
    fun removeModule(instanceId: String)
}
```
- `uiState` combina `registry.state` e `graph.state` (via `combine`) em um único
  `HostUiState` pronto para renderização — a UI nunca lê `registry`/`graph` diretamente.
- `connect`/`disconnect`/`addModule`/`removeModule` são as únicas formas pelas quais um
  Composable muta o host — nenhum Composable chama `ModuleRegistry`/`RoutingGraph`
  diretamente (Constitution V).

## `dev.muzziknod.ui.graph.GraphView`

```kotlin
@Composable
fun GraphView(
    state: HostUiState,
    onConnect: (sourceInstanceId: String, sourcePortId: String, targetInstanceId: String, targetPortId: String) -> ConnectResult,
    onDisconnect: (connectionId: String) -> Unit,
)
```
`onConnect` returns `ConnectResult` (not `Unit`) so `GraphView` can synchronously display a
`Rejected` reason without needing a second observable round-trip — `HostViewModel.connect`
already returns `ConnectResult` for exactly this reason.
- Satisfaz US1 (Acceptance Scenarios 1–3): renderiza módulos/portas/conexões a partir de
  `state`, invoca `onConnect`/`onDisconnect` em resposta à interação do usuário — nunca
  chama o host diretamente.
- Teste de contrato: dado um `HostUiState` com uma tentativa de conexão rejeitada
  (simulada passando um `onConnect` que devolve `ConnectResult.Rejected`), a view exibe o
  motivo sem que o `state` de entrada mude (FR-005, SC-004).

## `dev.muzziknod.ui.transport.TransportControls`

```kotlin
@Composable
fun TransportControls(
    transportState: TransportState,
    onPlay: () -> Unit,
    onStop: () -> Unit,
)
```
- Satisfaz US2 (Acceptance Scenarios 1–2): exibe estado play/stop e aciona os callbacks
  correspondentes.

## `dev.muzziknod.ui.parameters.ParameterControls`

```kotlin
@Composable
fun ParameterControl(
    spec: ParameterSpec,
    currentValue: Double,
    onValueChange: (Double) -> Unit,
)
```
- Satisfaz US2 (Acceptance Scenarios 3–4): renderiza um controle (ex.: slider) limitado
  a `spec.range`, chama `onValueChange` com o valor já limitado ao intervalo — nunca
  emite um valor fora de `spec.range` para `onValueChange`, mesmo que o clamp final
  também ocorra no módulo.

## `dev.muzziknod.ui.catalog.ModuleCatalog`

```kotlin
@Composable
fun ModuleCatalog(
    entries: List<ModuleCatalogEntry>,
    onAdd: (ModuleCatalogEntry) -> Unit,
)
```
- Satisfaz US3 (Acceptance Scenarios 1–2): lista os tipos de módulo disponíveis, aciona
  `onAdd` na seleção do usuário.

## `dev.muzziknod.ui.desktop.Main` (`jvmMain`, fora do contrato de teste em `commonTest`)

```kotlin
fun main() // application { Window(...) { App(hostViewModel) } }
```
- Único ponto do módulo que não é portável (`jvmMain`); monta a `Window` Compose Desktop
  e injeta um `HostViewModel` real. Não testado por `runComposeUiTest` (isso testaria o
  framework do SO); coberto apenas pela verificação manual do `quickstart.md`.
