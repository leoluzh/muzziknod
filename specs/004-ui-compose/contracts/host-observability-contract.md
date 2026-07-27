# Contract: Observabilidade Aditiva do Host

Estas são as únicas mudanças de API em `core-host` e nos módulos existentes exigidas por
esta feature. Todas são **aditivas** — nenhuma assinatura existente é removida ou
alterada. Testes de contrato existentes (`ModuleContractComplianceTests` e os testes de
002/003) continuam passando sem modificação.

## `core-host` — `dev.muzziknod.host.lifecycle.ModuleRegistry`

```kotlin
val state: StateFlow<List<ManagedModule>>
```
- Emite um novo valor sempre que `load()` ou `removeImmediately()` mutam o registro.
- `state.value` no momento da leitura é sempre igual a `all().toList()` no mesmo
  instante (invariante de consistência).

## `core-host` — `dev.muzziknod.host.graph.RoutingGraph`

```kotlin
val state: StateFlow<List<Connection>>
```
- Emite um novo valor sempre que `connect()`, `disconnect()`,
  `disconnectAllForModule()` ou `removeModule()` mutam o grafo.
- `state.value` é sempre igual a `connections().toList()` no mesmo instante.

## `modules/midi-sequencer` — `dev.muzziknod.modules.midisequencer.MidiSequencerModule`

```kotlin
data class TransportState(val isPlaying: Boolean, val currentStep: Int)

val transportState: StateFlow<TransportState>
```
- Emite um novo valor sempre que `play()`/`stop()` mudam `isPlaying`, e a cada avanço de
  `currentStep` durante a reprodução.
- `isPlaying`/`currentStep` (propriedades existentes) permanecem inalteradas.

## `modules/audio-effects` — um `StateFlow<Double>` por parâmetro existente

```kotlin
// DelayModule
val mix: StateFlow<Double>
val delayTimeMs: StateFlow<Double>
val feedback: StateFlow<Double>

// ReverbModule
val mix: StateFlow<Double>
val decayTime: StateFlow<Double>
val roomSize: StateFlow<Double>

// DistortionModule
val mix: StateFlow<Double>
val drive: StateFlow<Double>
val tone: StateFlow<Double>

// EqModule
val mix: StateFlow<Double>
fun bandGain(index: Int): StateFlow<Double>
fun bandFrequency(index: Int): StateFlow<Double>
fun bandQ(index: Int): StateFlow<Double>
```
- Cada `StateFlow` reflete o valor `current` já suavizado por `ParameterSmoother`,
  atualizado a cada `process()`.
- Todos os `set...()` existentes permanecem inalterados, incluindo o comportamento de
  clamp ao `ParameterSpec.range`.

## Invariante geral

Para qualquer um dos `StateFlow` acima: o valor emitido nunca reflete um estado
intermediário inconsistente do buffer de áudio em processamento — a emissão ocorre
depois que a mutação correspondente (load/connect/set/step) já foi aplicada de forma
completa e visível a um leitor síncrono equivalente (`all()`, `connections()`,
`isPlaying`, ou o efeito do `set...()` no próximo `process()`).
