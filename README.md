# Modular DAW

Software modular de criação de música eletrônica: um host enxuto orquestra módulos plugáveis
(sequenciador, sampler, synth, efeitos, mixer), cada um independente e testável isoladamente.

## Stack

- **Linguagem**: Kotlin
- **Runtime desktop**: Java 26 (JVM), usando `java.lang.foreign` (FFM API) como ponte para
  engines DSP nativas (C/C++/Rust) quando necessário
- **UI**: Compose Multiplatform
- **Portabilidade**: estrutura Kotlin Multiplatform (`commonMain` / `jvmMain` / futuro
  `androidMain`), com `expect`/`actual` isolando a fronteira de interop nativo (FFM no
  desktop, JNI/NDK no Android)

Todas as decisões de arquitetura acima estão registradas e justificadas em
[`.specify/memory/constitution.md`](.specify/memory/constitution.md).

## Desenvolvimento guiado por specs (Spec Kit + Claude Code)

Este projeto usa o [GitHub Spec Kit](https://github.com/github/spec-kit) para desenvolvimento
guiado por especificação, integrado como **skills do Claude Code** (`.claude/skills/`) — abra
esta pasta no Claude Code (Claude Desktop → aba Code, ou terminal) e os comandos abaixo ficam
disponíveis automaticamente:

1. `/speckit-constitution` — princípios do projeto (já estabelecidos, ver acima)
2. `/speckit-specify` — cria a especificação de uma nova feature em `specs/NNN-nome/spec.md`
3. `/speckit-clarify` — resolve ambiguidades marcadas como `[NEEDS CLARIFICATION]` na spec
4. `/speckit-plan` — cria o plano técnico de implementação a partir da spec
5. `/speckit-tasks` — quebra o plano em tarefas acionáveis
6. `/speckit-implement` — executa a implementação

Comandos opcionais de reforço de qualidade: `/speckit-analyze`, `/speckit-checklist`.

## Features especificadas

| # | Feature | Status |
|---|---------|--------|
| [001](specs/001-core-host/spec.md) | Core Host Modular — ciclo de vida de módulos, grafo de roteamento de áudio/MIDI, contrato base de módulo | Implementado — ver [tasks.md](specs/001-core-host/tasks.md) e [quickstart.md](specs/001-core-host/quickstart.md) |

## Build & testes

```bash
./gradlew build   # compila core-host + reference-modules (oscillator, midi-generator, midi-logger)
./gradlew check   # roda todos os testes de todos os módulos (não existe task `test` no root — é `check`, ou `jvmTest` por módulo)
```

Veja [`specs/001-core-host/quickstart.md`](specs/001-core-host/quickstart.md) para os
comandos de validação cenário-a-cenário (US1/US2/US3, SC-001–SC-004).

## Próximos passos sugeridos

1. Rodar `/speckit-analyze` sobre `001-core-host` para uma checagem de consistência
   cross-artifact agora que spec/plan/tasks/implementação existem
2. Rodar `/speckit-specify` para a próxima feature (ex.: persistência de projeto, UI
   Compose, ou integração DSP nativa via FFM) — cada feature nova nasce em seu próprio
   worktree/branch, nunca direto em `main`
