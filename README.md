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

## Ambiente de desenvolvimento (Devbox)

Este projeto tem um [`devbox.json`](devbox.json) pinando Java 26 via Nix, para um shell
reprodutível sem instalar JDK manualmente:

```bash
devbox shell        # entra no shell com Java 26 disponível
devbox run build    # ./gradlew build
devbox run test     # ./gradlew test
```

**Notas**:
- Devbox depende do Nix, que não roda nativamente no Windows — use WSL2.
- O pacote `openjdk@26` no `devbox.json` **não resolve ainda**: confirmado via
  `devbox search openjdk` que o Nixhub só indexa até `openjdk25`
  (`javaPackages.compiler.openjdk25`, ex.: `25.0.4+1`). O pin em `26` é intencional/
  aspiracional — atualize para a versão real quando o Nixhub indexar o JDK 26.
- O wrapper do Gradle (`./gradlew`) baixa sua própria distribuição do Gradle e continua
  sendo a fonte de verdade da versão (`gradle-wrapper.properties` pina `9.6.1`). O pacote
  `gradle_9` no `devbox.json` é só um fallback de CLI para uso offline/sem rede — o
  Nixhub ainda não indexa exatamente `9.6.1` (`gradle_9@latest` resolve para `9.5.1`).

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
