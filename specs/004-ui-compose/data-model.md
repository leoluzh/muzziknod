# Phase 1 Data Model: UI Compose Multiplatform do Host Modular

Esta feature não introduz um modelo de persistência (sem storage, ver
Technical Context). As "entidades" abaixo são os tipos observáveis/de apresentação que
a UI consome ou introduz, mapeados às Key Entities do spec.

## Tipos observáveis aditivos (em `core-host` e módulos existentes)

### `ModuleRegistry.state: StateFlow<List<ManagedModule>>`
- Espelha o mesmo conteúdo hoje retornado por `all()`, mas como stream observável.
- Atualizado nos mesmos pontos em que `load()` e `removeImmediately()` já mutam o
  registro internamente.
- Sem novos campos em `ManagedModule` — reutiliza `instanceId`, `contract`, `ports`,
  `state: ModuleState`.

### `RoutingGraph.state: StateFlow<List<Connection>>`
- Espelha `connections()`, atualizado nos mesmos pontos em que `connect()`,
  `disconnect()`, `disconnectAllForModule()` e `removeModule()` já mutam o grafo.
- Reutiliza `Connection(id, sourceInstanceId, sourcePortId, targetInstanceId,
  targetPortId)` sem alteração.

### `TransportState` (novo, em `modules/midi-sequencer`)
```kotlin
data class TransportState(val isPlaying: Boolean, val currentStep: Int)
```
- Exposto como `MidiSequencerModule.transportState: StateFlow<TransportState>`.
- Substitui, para consumo pela UI, a leitura por polling de `isPlaying`/`currentStep`
  (que permanecem inalterados para os consumidores atuais — testes de contrato, etc.).

### Valor atual de parâmetro (novo, um `StateFlow<Double>` por parâmetro, em cada módulo de efeito)
- Ex.: `DelayModule.mix: StateFlow<Double>`, `.delayTimeMs`, `.feedback`;
  `DistortionModule.drive`, `.tone`; `ReverbModule.decayTime`, `.roomSize`;
  `EqModule.bandGain(index)`/`.bandFrequency(index)`/`.bandQ(index)` (uma coleção de
  `StateFlow<Double>`, uma por banda, já que o EQ tem N bandas dinâmicas).
- Fonte de verdade: o `current` já computado por `ParameterSmoother` a cada `process()`
  — passa a ser publicado em vez de ficar preso ao smoother privado.
- Validação: nenhuma nova — o clamping ao `ParameterSpec.range` já ocorre no
  `set...()` existente; o `StateFlow` só publica o valor pós-clamp.

## Tipos de apresentação (novos, em `ui-desktop`)

### `HostUiState` (agregado consumido pelos Composables de topo)
```kotlin
data class HostUiState(
    val modules: List<ModuleUiModel>,
    val connections: List<Connection>,
)

data class ModuleUiModel(
    val instanceId: String,
    val typeName: String,
    val ports: List<PortSpec>,
    val lifecycleState: ModuleState,
    val pendingRemoval: Boolean,
)
```
- `pendingRemoval`: true quando `removeModule()` foi solicitado mas ainda não
  efetivado (remoção diferida do host) — deriva de `ModuleState` mais um flag local
  de "removal in flight" mantido pelo `HostViewModel::class`, já que o host hoje não tem
  um estado explícito "removal pendente" separado de `Removed`. Resolve o Edge Case do
  spec (FR-015: desabilitar controles durante remoção pendente).

### `ModuleCatalogEntry` (para US3)
```kotlin
data class ModuleCatalogEntry(
    val typeName: String,
    val factory: () -> Module,
)
```
- Lista estática dos tipos de módulo disponíveis hoje: `OscillatorModule`,
  `MidiGeneratorModule`, `MidiLoggerModule`, `MidiSequencerModule`, e os quatro módulos
  de `audio-effects` (`DelayModule`, `ReverbModule`, `DistortionModule`, `EqModule`).
  Sampler/synth citados na descrição original ficam fora (não existem como módulos —
  ver Assumptions do spec).

## Regras de validação herdadas (não reimplementadas pela UI)

- Compatibilidade de porta (tipo Audio/Midi, direção, sample rate/formato) — decidida
  por `RoutingGraph.connect()`; a UI apenas exibe o `ConnectResult.Rejected(reason)`
  recebido, sem duplicar a regra (FR-005).
- Clamping de parâmetro ao `ParameterSpec.range` — decidido pelo `set...()` de cada
  módulo; a UI limita visualmente o controle ao mesmo `range` só para UX (evitar que o
  usuário arraste um slider além do intervalo), mas a fonte de verdade do clamp
  permanece no módulo (FR-009).
- Remoção diferida durante ciclo em andamento — decidida por
  `RoutingGraph.removeModule()`; a UI só reflete `pendingRemoval` a partir do resultado
  dessa chamada, sem lógica própria de quando um ciclo "está em andamento".
