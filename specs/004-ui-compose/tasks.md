# Tasks: UI Compose Multiplatform do Host Modular

**Input**: Design documents from `/specs/004-ui-compose/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md,
contracts/host-observability-contract.md, contracts/ui-composables-contract.md,
quickstart.md

**Tests**: Included and REQUIRED — Constitution "Fluxo de Desenvolvimento" mandata
testes de contrato para qualquer módulo que produza/consuma áudio/MIDI, e FR-010/SC-003
exigem que a observabilidade nunca divirja do estado real do host; a UI em si (produto
real, não scaffolding) também recebe cobertura de Composable por user story via
`runComposeUiTest`.

**Organization**: US1 e US2 são P1 (grafo e transporte/parâmetros — MVP mínimo
utilizável); US3 é P2 (catálogo de módulos). Ordenado US1 → US2 → US3: US1 entrega a
casca do `HostViewModel`/`GraphView` que US2 e US3 reutilizam; US2 depende dos novos
observables de transporte/parâmetro (não compartilhados com US1); US3 depende apenas do
que já existe em Foundational (`ModuleRegistry.load`/`removeImmediately`).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Pode rodar em paralelo (arquivos diferentes, sem dependência pendente)
- **[Story]**: Mapeia a tarefa para US1, US2 ou US3
- Caminhos de arquivo são exatos, relativos à raiz do repositório

---

## Phase 1: Setup (Shared Infrastructure)

- [X] T001 Add `:ui-desktop` to root `settings.gradle.kts`; create directory skeleton
      (`ui-desktop/src/commonMain/kotlin/dev/muzziknod/ui/`,
      `ui-desktop/src/commonTest/kotlin/dev/muzziknod/ui/`,
      `ui-desktop/src/jvmMain/kotlin/dev/muzziknod/ui/desktop/`)
- [X] T002 Add `org.jetbrains.compose` (1.11.1) and `org.jetbrains.kotlin.plugin.compose`
      plugin coordinates to `pluginManagement` in root `settings.gradle.kts`; add
      `kotlinx-coroutines-core` version/library entry to `gradle/libs.versions.toml`
      (per research.md §1–2)
- [X] T003 [P] Create `ui-desktop/build.gradle.kts` — KMP plugin + `org.jetbrains
      .compose` + `org.jetbrains.kotlin.plugin.compose`, single `jvm()` target (same
      JVM target as other modules), depends on `core-host`,
      `modules:midi-sequencer`, `modules:audio-effects`,
      `implementation(compose.desktop.currentOS)` on `jvmMain`,
      `implementation(libs.kotlinx.coroutines.core)` on `commonMain`, `kotlin.test` +
      `kotlin-test-junit5` on `commonTest`, `compose.desktop.application { mainClass =
      "dev.muzziknod.ui.desktop.MainKt" }`

**Checkpoint**: `./gradlew build` runs (empty `:ui-desktop` module compiles alongside
existing ones; Compose plugin resolves)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Observabilidade aditiva do grafo/registro e a casca do
`HostViewModel`/`GraphView` que toda user story consome. Nenhuma user story começa antes
desta fase.

- [X] T004 [P] Add `kotlinx-coroutines-core` as an `api` dependency on `commonMain` of
      `core-host/build.gradle.kts`, `modules/midi-sequencer/build.gradle.kts`, and
      `modules/audio-effects/build.gradle.kts` (per research.md §1 — additive,
      `StateFlow` types must cross module boundaries as public API)
- [X] T005 [P] Add `ModuleRegistry.state: StateFlow<List<ManagedModule>>` to
      `core-host/src/commonMain/kotlin/dev/muzziknod/host/lifecycle/ModuleRegistry.kt`,
      backed by a `MutableStateFlow` updated at the same points `load()`/
      `removeImmediately()` already mutate the registry (contracts/host-observability-
      contract.md); `all()` unchanged
- [X] T006 [P] Add `RoutingGraph.state: StateFlow<List<Connection>>` to
      `core-host/src/commonMain/kotlin/dev/muzziknod/host/graph/RoutingGraph.kt`, backed
      by a `MutableStateFlow` updated at the same points `connect()`/`disconnect()`/
      `disconnectAllForModule()`/`removeModule()` already mutate the graph;
      `connections()` unchanged
- [X] T007 [P] Contract test: `ModuleRegistry.state.value` always equals `all().toList()`
      immediately after `load()` and `removeImmediately()` (contracts/host-
      observability-contract.md invariant) in
      `core-host/src/commonTest/kotlin/dev/muzziknod/host/lifecycle/ModuleRegistryStateTest.kt`
      (depends on T005)
- [X] T008 [P] Contract test: `RoutingGraph.state.value` always equals
      `connections().toList()` immediately after `connect()`/`disconnect()`/
      `disconnectAllForModule()`/`removeModule()` in
      `core-host/src/commonTest/kotlin/dev/muzziknod/host/graph/RoutingGraphStateTest.kt`
      (depends on T006)
- [X] T009 Run the existing `001-core-host`/`002-midi-sequencer`/`003-audio-effects`
      test suites (`./gradlew check`) to confirm T004-T006 introduced zero regressions
      (Constitution VI — additive-only)
- [X] T010 [P] Create `HostUiState`/`ModuleUiModel`/`ModuleCatalogEntry` data classes
      (data-model.md) in
      `ui-desktop/src/commonMain/kotlin/dev/muzziknod/ui/state/HostUiState.kt`
- [X] T011 `HostViewModel` skeleton — combines `ModuleRegistry.state` and
      `RoutingGraph.state` (via `combine`) into `uiState: StateFlow<HostUiState>`;
      `connect`/`disconnect` delegate to `RoutingGraph`; `addModule`/`removeModule`
      delegate to `ModuleRegistry` (contracts/ui-composables-contract.md) in
      `ui-desktop/src/commonMain/kotlin/dev/muzziknod/ui/state/HostViewModel.kt`
      (depends on T005, T006, T010)
- [X] T012 [P] Unit test: `HostViewModel.uiState` reflects a `load()`/`connect()` made
      directly on the underlying `ModuleRegistry`/`RoutingGraph` within one `StateFlow`
      emission (SC-003) in
      `ui-desktop/src/commonTest/kotlin/dev/muzziknod/ui/state/HostViewModelTest.kt`
      (depends on T011)
- [X] T013 `Main.kt` entry point — `application { Window { ... } }` wiring a real
      `ModuleRegistry`/`RoutingGraph`/`HostViewModel` (contracts/ui-composables-
      contract.md) in
      `ui-desktop/src/jvmMain/kotlin/dev/muzziknod/ui/desktop/Main.kt` (depends on T011)

**Checkpoint**: `:ui-desktop` compiles, `HostViewModel` observes a live host, `./gradlew
:ui-desktop:run` opens an empty window. Ready for all user stories.

---

## Phase 3: User Story 1 - Visualizar e editar o grafo de roteamento (Priority: P1) 🎯 MVP

**Goal**: A UI exibe todos os módulos e conexões carregados no host, permite criar uma
conexão entre portas compatíveis e remover uma existente, e recusa visualmente conexões
incompatíveis sem alterar o host.

**Independent Test**: Carregar dois módulos de referência (oscillator, midi-logger) no
host, abrir a UI, criar uma conexão pela interface, verificar que `RoutingGraph
.connections()` reflete a nova conexão; remover pela UI e verificar que desaparece de
ambos.

### Tests for User Story 1

- [X] T014 [P] [US1] Composable test: `GraphView` renderiza os módulos/portas/conexões
      de um `HostUiState` de entrada (US1 AC1 setup) in
      `ui-desktop/src/commonTest/kotlin/dev/muzziknod/ui/graph/GraphViewTest.kt`
      (implemented as `rendersModulesPortsAndConnectionsFromState` — T014-T017 landed
      together in one `GraphViewTest.kt` file rather than four, since they share the
      same fixtures)
- [X] T015 [P] [US1] Composable test: selecionar uma porta de saída e uma porta de
      entrada compatíveis invoca `onConnect` com os IDs corretos (US1 AC1) in
      `ui-desktop/src/commonTest/kotlin/dev/muzziknod/ui/graph/GraphViewTest.kt`
      (`selectingOutputThenInputPortInvokesOnConnectWithCorrectIds`)
- [X] T016 [P] [US1] Composable test: acionar remoção de uma conexão exibida invoca
      `onDisconnect` com o `connectionId` correto (US1 AC2) in
      `ui-desktop/src/commonTest/kotlin/dev/muzziknod/ui/graph/GraphViewTest.kt`
      (`clickingDisconnectInvokesOnDisconnectWithCorrectConnectionId`)
- [X] T017 [P] [US1] Composable test: quando `onConnect` é invocado com um retorno
      `ConnectResult.Rejected(reason)` simulado, a view exibe o motivo e o `HostUiState`
      de entrada permanece inalterado (FR-005, SC-004; US1 AC3) in
      `ui-desktop/src/commonTest/kotlin/dev/muzziknod/ui/graph/GraphViewTest.kt`
      (`rejectedConnectShowsReasonWithoutMutatingInputState`)

### Implementation for User Story 1

- [X] T018 [US1] Implement `GraphView` Composable — renderiza módulos (nome, tipo,
      portas) e conexões a partir de `HostUiState`, seleção explícita de porta origem →
      porta destino (per research.md §3 mecanismo mais simples), chama `onConnect`/
      `onDisconnect` (contracts/ui-composables-contract.md) in
      `ui-desktop/src/commonMain/kotlin/dev/muzziknod/ui/graph/GraphView.kt` (depends on
      T010; satisfies T014-T016). Note: `onConnect`'s signature was corrected from
      `-> Unit` to `-> ConnectResult` in contracts/ui-composables-contract.md during
      implementation — `GraphView` needs the result synchronously to show a rejection
      reason, and `HostViewModel.connect` already returns `ConnectResult`.
- [X] T019 [US1] Add rejection-reason display state to `GraphView` — ao receber
      `ConnectResult.Rejected` do callback `onConnect`, exibe a mensagem sem mutar a
      visualização do grafo (FR-005) in `GraphView.kt` (depends on T018; satisfies T017)
- [X] T020 [US1] Wire `GraphView` into `Main.kt`, passing `HostViewModel.uiState
      .collectAsState()` and `HostViewModel::connect`/`HostViewModel::disconnect` as
      callbacks in
      `ui-desktop/src/jvmMain/kotlin/dev/muzziknod/ui/desktop/Main.kt` (depends on T011,
      T013, T018)
- [X] T021 [US1] Implement the empty-state view (Edge Case do spec — host sem módulos
      orienta a adicionar um pelo catálogo) in `GraphView.kt` (depends on T018)

**Checkpoint**: US1 totalmente funcional e testável de forma independente —
`./gradlew :ui-desktop:jvmTest --tests "*GraphView*"`.

---

## Phase 4: User Story 2 - Controlar transporte e parâmetros dos módulos em tempo real (Priority: P1)

**Goal**: A UI exibe e permite acionar play/stop de um sequenciador MIDI e ajustar
parâmetros de módulos de efeito de áudio em tempo real, sem interromper o processamento.

**Independent Test**: Carregar um `MidiSequencerModule` e um módulo de efeito, acionar
play/stop pela UI e verificar `isPlaying`, ajustar um parâmetro pela UI e verificar que
o próximo `process()` reflete o novo valor.

### Tests for User Story 2

- [X] T022 [P] [US2] Add `TransportState` data class + `MidiSequencerModule
      .transportState: StateFlow<TransportState>` (contracts/host-observability-
      contract.md), backed by updates at the same points `play()`/`stop()`/step advance
      already mutate `Transport` in
      `modules/midi-sequencer/src/commonMain/kotlin/dev/muzziknod/modules/midisequencer/MidiSequencerModule.kt`
- [X] T023 [P] [US2] Contract test: `transportState.value.isPlaying`/`.currentStep`
      always match `isPlaying`/`currentStep` immediately after `play()`/`stop()`/a step
      advance in
      `modules/midi-sequencer/src/commonTest/kotlin/dev/muzziknod/modules/midisequencer/TransportStateTest.kt`
      (depends on T022)
- [X] T024 [P] [US2] Add a `StateFlow<Double>` per existing parameter (`mix`,
      `delayTimeMs`, `feedback`) to `DelayModule`, publishing `ParameterSmoother
      .current` after each `process()` (contracts/host-observability-contract.md) in
      `modules/audio-effects/src/commonMain/kotlin/dev/muzziknod/modules/audioeffects/DelayModule.kt`
- [X] T025 [P] [US2] Same pattern as T024 for `ReverbModule` (`mix`, `decayMs`,
      `roomSize` — corrected from `decayTime` to match the module's actual field name)
      in
      `modules/audio-effects/src/commonMain/kotlin/dev/muzziknod/modules/audioeffects/ReverbModule.kt`
- [X] T026 [P] [US2] Same pattern as T024 for `DistortionModule` (`mix`, `drive`,
      `tone`) in
      `modules/audio-effects/src/commonMain/kotlin/dev/muzziknod/modules/audioeffects/DistortionModule.kt`
- [X] T027 [P] [US2] Same pattern as T024 for `EqModule` (`mix`, `bandGain(index)`,
      `bandFrequency(index)`, `bandQ(index)` — implemented as
      `bandGain(band: EqBand)`/etc. using the real `EqBand` enum parameter the module's
      existing setters already take, not a raw `Int` index; `EqModule` has no `mix`
      parameter at all — corrected to match the real contract, which has 9
      per-band parameters and no mix) in
      `modules/audio-effects/src/commonMain/kotlin/dev/muzziknod/modules/audioeffects/EqModule.kt`
- [X] T028 [P] [US2] Contract test: for each of the four effect modules, the
      published `StateFlow<Double>` value matches the smoothed value after calling the
      corresponding `set...()` and running one `process()` cycle in
      `modules/audio-effects/src/commonTest/kotlin/dev/muzziknod/modules/audioeffects/ParameterStateFlowTest.kt`
      (depends on T024-T027)
- [X] T029 [P] [US2] Composable test: `TransportControls` renderiza estado play/stop e
      invoca `onPlay`/`onStop` (US2 AC1-2) in
      `ui-desktop/src/commonTest/kotlin/dev/muzziknod/ui/transport/TransportControlsTest.kt`
- [X] T030 [P] [US2] Composable test: `ParameterControl` limita o valor exibido ao
      `spec.range` e nunca invoca `onValueChange` fora desse intervalo, mesmo que o
      usuário arraste além do limite (FR-009; US2 AC3-4) in
      `ui-desktop/src/commonTest/kotlin/dev/muzziknod/ui/parameters/ParameterControlTest.kt`

### Implementation for User Story 2

- [X] T031 [US2] Implement `TransportControls` Composable per contracts/ui-composables-
      contract.md in
      `ui-desktop/src/commonMain/kotlin/dev/muzziknod/ui/transport/TransportControls.kt`
      (depends on T022; satisfies T029)
- [X] T032 [US2] Implement `ParameterControl` Composable (slider clamped to `spec
      .range`) per contracts/ui-composables-contract.md in
      `ui-desktop/src/commonMain/kotlin/dev/muzziknod/ui/parameters/ParameterControls.kt`
      (depends on T024-T027; satisfies T030)
- [X] T033 [US2] Added `HostViewModel.moduleInstance<T>(instanceId)` (an
      `inline`/`reified` escape hatch returning the concrete `Module` instance) instead
      of extending `ModuleUiModel`/`HostUiState` — the per-module `StateFlow` shapes
      (`transportState`, `mix`/`delayTimeMs`/..., per-band EQ lookups) are too
      heterogeneous to fit one uniform field set without speculative over-abstraction
      (Constitution VII); a new `ui/controls/ModuleControls.kt` Composable dispatches
      on `typeName` and binds each module's own `StateFlow`s directly, in
      `ui-desktop/src/commonMain/kotlin/dev/muzziknod/ui/state/HostViewModel.kt` and
      `ui-desktop/src/commonMain/kotlin/dev/muzziknod/ui/controls/ModuleControls.kt`
      (depends on T011, T022, T024-T027)
- [X] T034 [US2] Wire `ModuleControls` (which internally uses `TransportControls`/
      `ParameterControl`) into `Main.kt` for every loaded module in
      `ui-desktop/src/jvmMain/kotlin/dev/muzziknod/ui/desktop/Main.kt` (depends on T020,
      T031, T032, T033)

**Checkpoint**: US1 + US2 ambos funcionais de forma independente.

---

## Phase 5: User Story 3 - Adicionar e remover módulos pela interface (Priority: P2)

**Goal**: A UI oferece um catálogo dos tipos de módulo disponíveis para adicionar ao
host, e permite remover um módulo carregado, respeitando a remoção diferida do host.

**Independent Test**: Abrir a UI com o host vazio, adicionar um módulo do catálogo,
verificar que aparece no grafo; removê-lo pela UI e verificar que desaparece de ambos.

### Tests for User Story 3

- [X] T035 [P] [US3] Composable test: `ModuleCatalog` lista as entradas fornecidas e
      invoca `onAdd` com a entrada selecionada (US3 AC1) in
      `ui-desktop/src/commonTest/kotlin/dev/muzziknod/ui/catalog/ModuleCatalogTest.kt`
- [X] T036 [P] [US3] Unit test: `HostViewModel.removeModule` marca o módulo como
      `pendingRemoval = true` em `HostUiState` até a remoção ser efetivada pelo host
      (FR-015; Edge Cases) in
      `ui-desktop/src/commonTest/kotlin/dev/muzziknod/ui/state/HostViewModelRemovalTest.kt`
      (uses a `TriggerModule` test double that calls back into `viewModel.removeModule`
      from inside an active `processCycle()`, so the deferred-removal window is
      genuinely observed, not just asserted around it)

### Implementation for User Story 3

- [X] T037 [P] [US3] Build the static `List<ModuleCatalogEntry>` — oscillator,
      midi-generator, midi-logger, midi-sequencer, e os quatro tipos de
      `audio-effects` (data-model.md; sampler/synth fora de escopo) in
      `ui-desktop/src/commonMain/kotlin/dev/muzziknod/ui/catalog/ModuleCatalog.kt`
      (also implements the `ModuleCatalog` Composable per contracts/ui-composables-
      contract.md; satisfies T035)
- [X] T038 [US3] `pendingRemoval` tracking was already added to `HostViewModel
      .removeModule`/`uiState` derivation back in Foundational (T011) — no further
      change needed here in
      `ui-desktop/src/commonMain/kotlin/dev/muzziknod/ui/state/HostViewModel.kt`
      (satisfies T036)
- [X] T039 [US3] Disable a module's controls while `pendingRemoval == true` (FR-015):
      `GraphView.kt`'s port `clickable` already had `enabled = !module.pendingRemoval`
      since T018; added an `enabled: Boolean = true` parameter to `TransportControls`/
      `ParameterControl` and a `pendingRemoval` parameter to the new `ModuleControls`
      dispatcher, which passes `enabled = !pendingRemoval` down to every control it
      renders in
      `ui-desktop/src/commonMain/kotlin/dev/muzziknod/ui/transport/TransportControls.kt`,
      `parameters/ParameterControls.kt`, `controls/ModuleControls.kt`
- [X] T040 [US3] Wire `ModuleCatalog` into `Main.kt` — `onAdd` calls `HostViewModel
      .addModule`; added a "remover módulo" action per module row calling
      `HostViewModel.removeModule` in
      `ui-desktop/src/jvmMain/kotlin/dev/muzziknod/ui/desktop/Main.kt` (depends on T020,
      T037, T038)

**Checkpoint**: US1 + US2 + US3 todos independentemente funcionais.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T041 Run `quickstart.md` scenarios 1-4 end-to-end; correct any command drift
      (actual Gradle task/test-class names), same practice as prior features' final
      polish task
- [ ] T042 [P] README: add a `004-ui-compose` row to the "Features especificadas" table
      and a `./gradlew :ui-desktop:run` mention in the "Build & testes" section, in
      `README.md`
- [ ] T043 [P] Update the Makefile (`make` target `run-ui` or similar) to launch
      `:ui-desktop:run`, consistent with existing `make build`/`make test-*` targets,
      in `Makefile`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sem dependências
- **Foundational (Phase 2)**: depende de Setup — bloqueia todas as user stories
- **US1 (Phase 3)**: depende apenas de Foundational
- **US2 (Phase 4)**: depende de Foundational; independente de US1 nos arquivos que
  toca (`transport/`, `parameters/`, módulos de `midi-sequencer`/`audio-effects`), mas
  reutiliza `HostViewModel`/`Main.kt` já criados em Foundational/US1
- **US3 (Phase 5)**: depende apenas de Foundational (usa `ModuleRegistry.load`/
  `removeImmediately`, já existentes) — não depende de US1/US2 para funcionar, embora
  T039 desabilite controles definidos em US1/US2 quando presentes
- **Polish (Phase 6)**: depende de US1 + US2 + US3 completos

### Within Each Story

- Testes (T014-T017, T022-T023+T028-T030, T035-T036) escritos primeiro, devem falhar
  antes da implementação correspondente
- Observables do host → Composables → wiring em `Main.kt`

### Parallel Opportunities

- T001-T003 (Setup) — T002/T003 após T001 (diretório precisa existir)
- T004-T013 (Foundational) — T004-T008 em paralelo entre si; T009 depende de
  T004-T006; T010 em paralelo com T004-T009; T011 depende de T005, T006, T010; T012 em
  paralelo com T013 (ambos dependem de T011)
- Within US1: T014-T017 em paralelo entre si; T018 depende de T010; T019-T021
  sequenciais em `GraphView.kt`
- Within US2: T022-T027 em paralelo entre si (arquivos diferentes); T028-T030 em
  paralelo entre si; T031-T032 em paralelo entre si
- Within US3: T035-T036 em paralelo entre si; T037-T038 em paralelo entre si

---

## Parallel Example: User Story 2

```bash
# Launch the four per-module observability additions together (independent files):
Task: "TransportState + transportState StateFlow in MidiSequencerModule.kt"
Task: "Parameter StateFlows in DelayModule.kt"
Task: "Parameter StateFlows in ReverbModule.kt"
Task: "Parameter StateFlows in DistortionModule.kt"
Task: "Parameter StateFlows in EqModule.kt"

# Launch both new Composables together:
Task: "TransportControls Composable in transport/TransportControls.kt"
Task: "ParameterControl Composable in parameters/ParameterControls.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 (Setup) → Phase 2 (Foundational) → Phase 3 (US1)
2. **STOP and VALIDATE**: montar dois módulos e conectá-los/desconectá-los
   inteiramente pela UI — corresponde a SC-001/SC-004.

### Incremental Delivery

1. Setup + Foundational → fundação pronta (`HostViewModel` observando um host real,
   janela vazia abrindo)
2. US1 → validar independentemente (MVP: grafo visível e editável)
3. US2 → validar independentemente (adiciona controle de transporte/parâmetros em
   tempo real, SC-002)
4. US3 → validar independentemente (adiciona catálogo/remoção, SC-005)
5. Polish → quickstart completo + README + Makefile

## Notes

- Tarefas [P] tocam arquivos diferentes sem dependência pendente entre elas
- Commit após cada tarefa ou grupo lógico (workflow worktree-por-feature — tudo isso
  acontece na branch/worktree `004-ui-compose`, nunca em `main`)
- Verificar que os testes falham antes de implementar
- Evitar: tarefas vagas, conflitos no mesmo arquivo, dependências cross-story que
  quebrem a independência
