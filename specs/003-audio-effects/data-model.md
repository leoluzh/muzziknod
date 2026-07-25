# Phase 1 Data Model: Módulos de Efeitos de Áudio (Reverb/Delay/Distortion/EQ)

## Shared primitives (`dev.muzziknod.modules.audioeffects`)

### ParameterSmoother

Ramps one `Double` value toward a target instead of jumping instantly
(research.md "Parameter smoothing").

| Field | Type | Notes |
|---|---|---|
| `current` | `Double` | Value used by `process()` this cycle |
| `target` | `Double` | Set by the module's public setter, clamped to the declared `ParameterSpec.range` first |
| `rampSamples` | `Int` | Fixed window (e.g. 64) over which `current` linearly approaches `target` |

**Behavior**: `advance()` moves `current` one step closer to `target` each
sample; once within one step, `current == target` exactly (no oscillation).

### WetDryMixer

Stateless helper applying the FR-002–FR-005 crossfade.

| Function | Signature | Notes |
|---|---|---|
| `mix` | `(dry: Float, wet: Float, mix: Double) -> Float` | `dry * (1 - mix) + wet * mix`; `mix` pre-clamped to `0.0..1.0` by the caller's `ParameterSmoother` |

### DelayLine (internal to `DelayModule`)

Circular buffer backing the single-tap delay.

| Field | Type | Notes |
|---|---|---|
| `buffer` | `FloatArray` | Sized for max delay (2000 ms) at construction `sampleRate`; allocated once in `onLoad()` |
| `writeIndex` | `Int` | Advances every sample, wraps via modulo |

### CombFilter / AllpassFilter (internal to `ReverbModule`)

Each a small fixed-size circular buffer + feedback coefficient, same
allocate-once-in-`onLoad()` shape as `DelayLine`. Four `CombFilter`s run in
parallel and sum into two series `AllpassFilter`s (research.md "Per-effect
algorithm choice").

### Biquad (internal to `EqModule`)

RBJ peaking-EQ biquad: 5 coefficients (`b0,b1,b2,a1,a2`) recomputed whenever a
band's frequency/gain/Q setter is called, plus 4 state samples
(`x1,x2,y1,y2`) carried across `process()` calls.

## ReverbModule

| Aspect | Value |
|---|---|
| `contract.typeId` | `"reverb"` |
| Ports | `in` (Audio, Input), `out` (Audio, Output) — same `sampleRate`/`BufferFormat.Float32` |
| `ParameterSpec`s | `mix` (`0.0..1.0`, default `0.5`), `decayMs` (`50.0..5000.0`, default `1500.0`), `roomSize` (`0.0..1.0`, default `0.5`) |
| Public setters | `setMix`, `setDecayMs`, `setRoomSize` |
| Internal state | 4× `CombFilter`, 2× `AllpassFilter`, 3× `ParameterSmoother` |

## DelayModule

| Aspect | Value |
|---|---|
| `contract.typeId` | `"delay"` |
| Ports | `in` (Audio, Input), `out` (Audio, Output) |
| `ParameterSpec`s | `mix` (`0.0..1.0`, default `0.5`), `delayTimeMs` (`1.0..2000.0`, default `375.0`), `feedback` (`0.0..0.95`, default `0.3`) |
| Public setters | `setMix`, `setDelayTimeMs`, `setFeedback` |
| Internal state | 1× `DelayLine`, 3× `ParameterSmoother` |

## DistortionModule

| Aspect | Value |
|---|---|
| `contract.typeId` | `"distortion"` |
| Ports | `in` (Audio, Input), `out` (Audio, Output) |
| `ParameterSpec`s | `mix` (`0.0..1.0`, default `1.0`), `drive` (`1.0..20.0`, default `4.0`), `tone` (`200.0..12000.0` Hz lowpass cutoff, default `6000.0`) |
| Public setters | `setMix`, `setDrive`, `setTone` |
| Internal state | one-pole lowpass state sample, 3× `ParameterSmoother` |

## EqModule

| Aspect | Value |
|---|---|
| `contract.typeId` | `"eq"` |
| Ports | `in` (Audio, Input), `out` (Audio, Output) |
| `ParameterSpec`s | Per band (`low`, `mid`, `high`) × (`freqHz`, `gainDb`, `q`) = 9 total; ranges: `freqHz` `20.0..20000.0`, `gainDb` `-24.0..24.0`, `q` `0.1..10.0`. Defaults: low `100.0`/`0.0`/`0.7`; mid `1000.0`/`0.0`/`0.7`; high `8000.0`/`0.0`/`0.7`. No `mix` parameter — EQ has no separate dry/wet concept beyond each band's own gain (Edge Cases: all bands at 0 dB gain ⇒ passthrough already) |
| Public setters | `setBandFrequency(band, hz)`, `setBandGain(band, db)`, `setBandQ(band, q)` |
| Internal state | 3× `Biquad` (one per band), 9× `ParameterSmoother` |

## Relationships

```text
ReverbModule      1 ── 4 CombFilter, 1 ── 2 AllpassFilter, 1 ── 3 ParameterSmoother
DelayModule       1 ── 1 DelayLine, 1 ── 3 ParameterSmoother
DistortionModule  1 ── 3 ParameterSmoother
EqModule          1 ── 3 Biquad, 1 ── 9 ParameterSmoother
(all four)        1 ── 1 WetDryMixer (stateless, shared instance/function — except EqModule, see above)
```

No "Cadeia de Efeitos" entity in this module's own data model — the chain
(spec Key Entities) is realized entirely by `core-host`'s existing
`RoutingGraph`, connecting instances of the four types above in sequence
(FR-012); nothing here represents the chain itself.
