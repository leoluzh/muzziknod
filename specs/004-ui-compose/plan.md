# Implementation Plan: UI Compose Multiplatform do Host Modular

**Branch**: `004-ui-compose` | **Date**: 2026-07-27 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/004-ui-compose/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Entregar uma UI Compose Multiplatform (desktop/JVM neste MVP) que observa e controla o
host modular existente: visualiza e edita o grafo de roteamento (`RoutingGraph`),
adiciona/remove módulos (`ModuleRegistry`), controla transporte do sequenciador MIDI e
parâmetros dos módulos de efeito. O core-host hoje só expõe estado via getters
síncronos (`ModuleRegistry.all()`, `RoutingGraph.connections()`, `MidiSequencerModule
.isPlaying`) e setters sem getter para parâmetros (`DelayModule.setMix()` etc., sem
`getMix()`) — nenhum `StateFlow`/`Flow`/listener existe hoje em lugar nenhum do
repositório. A abordagem técnica é: (1) adicionar uma camada fina de observabilidade
aditiva (`StateFlow`) em `core-host` e nos módulos existentes, sem quebrar nenhuma API
síncrona já usada pelos testes de contrato (Constitution VI); (2) construir a UI em um
novo módulo KMP (`ui-desktop`) que consome essa camada observável via `commonMain`
(lógica de apresentação) + `jvmMain` (entry point Compose Desktop), mantendo a UI restrita
a esses observables (Constitution V).

## Technical Context

**Language/Version**: Kotlin 2.4.10 (mesma versão do resto do repositório), JVM target 25 (`JVM_25`, consistente com os demais módulos — Java 26 real ainda não indexado pelo toolchain, ver `README.md`)

**Primary Dependencies**: Compose Multiplatform (Gradle plugin `org.jetbrains.compose` 1.11.1 + compilador `org.jetbrains.kotlin.plugin.compose`, compatível com Kotlin 2.4.10), `kotlinx-coroutines-core` (para `StateFlow`, ainda não usado no repositório — nova dependência aditiva em `core-host` e nos módulos)

**Storage**: N/A (sem persistência nesta feature — ver Assumptions do spec)

**Testing**: `kotlin.test` com binding JUnit5 (`kotlin-test-junit5`, mesmo padrão dos módulos existentes) para lógica de apresentação em `commonTest`; `runComposeUiTest` (Compose Multiplatform test API, também `kotlin.test`-based) para testes de Composable em `commonTest`

**Target Platform**: Desktop JVM (Windows/Linux/macOS via Compose Desktop) — Android/iOS permanecem portabilidade futura não bloqueada (Constitution IV), não entregues aqui

**Project Type**: Aplicação desktop consumindo uma biblioteca KMP existente (host modular) — novo módulo `ui-desktop` adicionado ao monorepo Gradle multi-módulo já existente

**Performance Goals**: UI permanece responsiva (sem travamentos perceptíveis) enquanto observa atualizações do host a cada ciclo de processamento; sem meta numérica de fps além do padrão de UI desktop (a Constitution III reserva metas de baixa latência ao caminho de áudio real-time, que esta feature não toca)

**Constraints**: A UI nunca acessa estado interno de módulo diretamente (Constitution V) — todo acesso passa pelos novos observables aditivos; nenhuma API síncrona existente pode ser removida ou ter assinatura alterada (Constitution VI, testes de contrato existentes não podem quebrar)

**Scale/Scope**: Um host local em processo único; número de módulos/conexões da ordem de dezenas (uso interativo de um único usuário), sem requisito de múltiplos hosts remotos ou colaboração

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Princípio | Avaliação | Justificativa |
|---|---|---|
| I. Modularidade em Primeiro Lugar | PASS | UI vive em módulo próprio (`ui-desktop`), consome host apenas via contratos públicos (observables novos + APIs existentes); não implementa lógica de módulo. |
| II. Kotlin / Java 26 runtime | PASS | `ui-desktop` é Kotlin puro, mesmo toolchain JVM dos demais módulos. |
| III. Separação Real-Time vs Not-Real-Time | PASS | UI e a nova camada observável rodam fora do caminho de áudio sample-accurate; `StateFlow` é atualizado a partir do resultado de um ciclo (`processCycle()`), nunca dentro do hot loop de processamento de buffer. Nenhuma alocação nova é introduzida em `Module.process()`. |
| IV. Portabilidade via Kotlin Multiplatform | PASS | Lógica de apresentação (view-models/observables) fica em `commonMain` de `ui-desktop`; apenas o entry point Compose Desktop (`main()`, janela) fica em `jvmMain`. Isso não fecha a porta para um alvo Android futuro. |
| V. UI Declarativa Desacoplada do Core | PASS (com adição de contrato) | A UI só observa `StateFlow`s expostos pelo core/módulos — nunca campos internos. Como esses observables não existiam, esta feature os cria como parte do design (Phase 1), não como violação. |
| VI. Contratos Explícitos entre Módulos | PASS (aditivo) | Todas as novas APIs são aditivas (novos métodos/propriedades `StateFlow`), nenhuma assinatura existente muda. Testes de contrato existentes (`ModuleContractComplianceTests`) continuam válidos sem alteração. |
| VII. Simplicidade Incremental (YAGNI) | PASS | Observabilidade é limitada ao que a UI realmente precisa (estado de módulos/conexões/parâmetros/transporte) — sem barramento de eventos genérico ou undo/redo especulativo. |

Nenhuma violação — Complexity Tracking não se aplica.

## Project Structure

### Documentation (this feature)

```text
specs/004-ui-compose/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
core-host/
└── src/commonMain/kotlin/dev/muzziknod/host/
    ├── lifecycle/ModuleRegistry.kt      # + StateFlow<List<ManagedModule>> aditivo (observableState)
    ├── graph/RoutingGraph.kt            # + StateFlow<List<Connection>> aditivo
    └── observability/                   # NOVO: wrappers StateFlow reutilizáveis (HostObservable)

modules/midi-sequencer/
└── src/commonMain/kotlin/dev/muzziknod/modules/midisequencer/
    └── MidiSequencerModule.kt           # + StateFlow<TransportState> aditivo (isPlaying/currentStep)

modules/audio-effects/
└── src/commonMain/kotlin/dev/muzziknod/modules/audioeffects/
    ├── DelayModule.kt                   # + getter/StateFlow de valor atual por parâmetro
    ├── DistortionModule.kt              # idem
    ├── EqModule.kt                      # idem
    └── ReverbModule.kt                  # idem

ui-desktop/                              # NOVO módulo KMP
├── build.gradle.kts                    # kotlin-multiplatform + org.jetbrains.compose + org.jetbrains.kotlin.plugin.compose
├── src/commonMain/kotlin/dev/muzziknod/ui/
│   ├── state/HostViewModel.kt          # observa StateFlows do host, expõe UiState combinado
│   ├── graph/GraphView.kt              # Composable: visualização/edição do grafo (US1)
│   ├── transport/TransportControls.kt  # Composable: play/stop (US2)
│   ├── parameters/ParameterControls.kt # Composable: controles de ParameterSpec (US2)
│   └── catalog/ModuleCatalog.kt        # Composable: catálogo de módulos (US3)
├── src/commonTest/kotlin/dev/muzziknod/ui/
│   ├── state/HostViewModelTest.kt
│   ├── graph/GraphViewTest.kt          # runComposeUiTest
│   ├── transport/TransportControlsTest.kt
│   └── catalog/ModuleCatalogTest.kt
└── src/jvmMain/kotlin/dev/muzziknod/ui/desktop/
    └── Main.kt                          # entry point Compose Desktop (application { Window { ... } })
```

**Structure Decision**: Novo módulo Gradle `:ui-desktop` (KMP, alvo `jvm()` único neste MVP,
mesmo padrão dos demais módulos) adicionado a `settings.gradle.kts`. Lógica de
apresentação e Composables ficam em `commonMain`/`commonTest` (portáveis); apenas o
`main()` do Compose Desktop fica em `jvmMain` — primeiro uso de um source set `jvmMain`
real no repositório, mas sem violar a Constitution IV. A camada de observabilidade é
aditiva dentro dos módulos existentes (`core-host`, `midi-sequencer`, `audio-effects`),
não um módulo novo, porque pertence ao domínio de cada um (Constitution I — o host não
deve reimplementar o que já vive no módulo).

## Complexity Tracking

Nenhuma violação da Constitution Check — seção não aplicável.
