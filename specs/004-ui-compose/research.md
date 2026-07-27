# Phase 0 Research: UI Compose Multiplatform do Host Modular

## 1. Como observar o host reativamente sem quebrar contratos existentes

**Decision**: Adicionar `StateFlow` aditivo ao lado das APIs síncronas existentes —
`ModuleRegistry` e `RoutingGraph` ganham uma propriedade `state: StateFlow<...>`
atualizada nos mesmos pontos onde `all()`/`connections()` já mudam (load/remove/connect/
disconnect/processCycle), sem remover ou alterar `all()`/`connections()`/`get()`.
`MidiSequencerModule` ganha `transportState: StateFlow<TransportState>` ao lado de
`isPlaying`/`currentStep`. Cada módulo de efeito ganha um getter de valor atual por
parâmetro (ex.: `mix: StateFlow<Double>` em vez de só `setMix()`), usando o `current` já
mantido internamente por `ParameterSmoother` — hoje computado mas nunca exposto.

**Rationale**:
- Constitution VI proíbe quebrar contrato existente silenciosamente — aditivo é a única
  forma de atender FR-010 (observação reativa) sem violar isso.
- Constitution III exige que o caminho de áudio sample-accurate não aloque/bloqueie —
  emitir em um `StateFlow` faz sentido apenas no ponto em que um ciclo já terminou
  (`processCycle()` de `RoutingGraph`, ou o próprio `process()` de cada módulo após
  aplicar o novo valor suavizado), não dentro do laço de amostra. `StateFlow.value = x`
  é uma escrita atômica sem alocação por emissão (ao contrário de `SharedFlow` com
  buffer), o que a mantém aceitável mesmo se eventualmente chamada perto do hot path.
- `kotlinx-coroutines-core` já é a escolha natural para `StateFlow` em Kotlin
  Multiplatform e não introduz nenhuma dependência de framework de aplicação (não viola
  a restrição técnica contra Spring/Quarkus etc.).

**Alternatives considered**:
- *Callback/Listener simples (sem coroutines)*: rejeitado — reinventa o que `StateFlow`
  já resolve (valor atual + stream de mudanças + multicast), e Compose já tem suporte de
  primeira classe para coletar `StateFlow` (`collectAsState()`), reduzindo código de
  ponte.
- *Polling pela UI (sem observable novo nenhum)*: rejeitado — violaria FR-010 e a
  Constitution V explicitamente, que exige contratos observáveis, não que a UI leia
  estado por conta própria em loop.
- *Barramento de eventos genérico (event bus compartilhado entre todos os módulos)*:
  rejeitado por YAGNI (Constitution VII) — nenhuma feature hoje precisa de pub/sub
  cross-module além do que a UI consome; adicionar isso agora seria especulação.

## 2. Framework de UI e integração Gradle

**Decision**: Plugin Gradle `org.jetbrains.compose` versão `1.11.1` + plugin compilador
`org.jetbrains.kotlin.plugin.compose` (obrigatório desde Kotlin 2.0, que separou o
compilador Compose do Kotlin compiler embarcado) na versão do Kotlin do projeto
(2.4.10). Dependência `implementation(compose.desktop.currentOS)` no novo módulo
`ui-desktop`, com `compose.desktop.application { mainClass = "dev.muzziknod.ui.desktop.MainKt" }`.

**Rationale**: É o stack oficial já fixado pela Constitution (Restrições Técnicas — "UI:
Compose Multiplatform") desde antes desta feature existir; a versão 1.11.1 é compatível
com Kotlin 2.4.10 (linha de suporte corrente do Compose Multiplatform para Kotlin 2.3+).

**Alternatives considered**:
- *Skiko/Compose para Web ou outros alvos além de desktop*: fora de escopo — spec
  Assumptions restringe esta feature a desktop/JVM.
- *Outro framework de UI declarativa não-Compose*: rejeitado — contradiria a Constitution
  (Princípio V e Restrições Técnicas fixam Compose Multiplatform, não é uma decisão em
  aberto desta feature).

## 3. Estrutura do novo módulo e testes de Composable

**Decision**: Novo módulo `:ui-desktop`, KMP com único alvo `jvm()` (mesmo padrão dos
demais módulos), lógica de apresentação e Composables em `commonMain`/`commonTest`
(portável), `main()` do Compose Desktop em `jvmMain` (primeiro uso real de `jvmMain` no
repo). Testes de Composable usam `runComposeUiTest` (API `kotlin.test`-based do próprio
Compose Multiplatform, com `onNodeWithTag`/`onNodeWithText`/`assertTextEquals`),
consistente com o padrão `kotlin.test` já usado em todo o resto do projeto.

**Rationale**: Mantém o precedente arquitetural dos módulos existentes (nomenclatura
`build.gradle.kts` idêntica, único alvo `jvm()` por ora) e cumpre a Constitution IV
(lógica compartilhável em `commonMain`) sem esforço extra de portabilidade especulativa
(Android/iOS ficam possíveis depois, mas não são construídos agora).

**Alternatives considered**:
- *JUnit5 + testes de instância de Composable sem `runComposeUiTest`*: rejeitado —
  perderia a árvore semântica (`onNodeWithTag` etc.), tornando testes de interação
  (clicar, arrastar) difíceis de expressar e verificar.
- *Colocar tudo em `jvmMain` (sem separar lógica de apresentação em `commonMain`)*:
  rejeitado — fecha a porta de portabilidade que a Constitution IV protege, sem
  necessidade real (o esforço de manter a separação é baixo: só o `main()`/`Window`
  ficam presos ao desktop).

## 4. Nomenclatura e localização das novas APIs observáveis

**Decision**: `ModuleRegistry.state: StateFlow<List<ManagedModule>>`,
`RoutingGraph.state: StateFlow<List<Connection>>`,
`MidiSequencerModule.transportState: StateFlow<TransportState>` (novo `data class
TransportState(val isPlaying: Boolean, val currentStep: Int)`), e em cada módulo de
efeito uma propriedade por parâmetro nomeada igual ao setter sem o prefixo `set` (ex.:
`setMix()` ganha `mix: StateFlow<Double>`).

**Rationale**: Segue a convenção de nomenclatura já usada no restante do host
(`ParameterSpec.id`/`label` em minúsculo, setters `setX`) e evita ambiguidade sobre qual
propriedade corresponde a qual setter.

**Alternatives considered**:
- *Um único `StateFlow<ModuleSnapshot>` por módulo agregando todos os parâmetros*:
  considerado, mas rejeitado para o MVP — obrigaria a UI a sempre recompor ao mudar
  qualquer parâmetro, mesmo quando só um controle está sendo observado; propriedades
  `StateFlow` por parâmetro permitem recomposição granular no Compose (`collectAsState()`
  por controle).
