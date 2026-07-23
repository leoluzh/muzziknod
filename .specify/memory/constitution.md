# Modular DAW Constitution

## Core Principles

### I. Modularidade em Primeiro Lugar
Toda funcionalidade (sequenciador, sampler, synth, efeito, mixer) é um módulo plugável,
independente e testável isoladamente. O core do sistema é um host enxuto responsável apenas
pelo ciclo de vida dos módulos, roteamento de sinal (grafo de áudio/MIDI) e gerenciamento de
estado — nunca por lógica específica de um módulo. Nenhum módulo pode depender diretamente
da implementação interna de outro; toda comunicação passa por contratos/interfaces explícitos
definidos pelo host.

### II. Kotlin como Linguagem Principal, Java 26 como Runtime
O core, os módulos e a UI são escritos em Kotlin. No desktop/JVM, roda-se sobre Java 26,
aproveitando a Foreign Function & Memory API (`java.lang.foreign`) como mecanismo padrão de
interoperabilidade com engines nativas (C/C++/Rust) de DSP — JNI tradicional só é aceitável
onde FFM não estiver disponível (ex.: Android/ART).

### III. Separação Real-Time vs Not-Real-Time (NON-NEGOTIABLE)
Qualquer código no caminho crítico de áudio (processamento de buffer sample-accurate) deve ser
livre de alocação de objetos Java/Kotlin no hot path, livre de I/O bloqueante e livre de locks
que possam ser retidos pelo GC. Lógica que não é sample-accurate (UI, arranjo, gerenciamento de
projeto, sequenciamento MIDI de alto nível) pode rodar livremente na JVM gerenciada. Processamento
DSP crítico é delegado a uma engine nativa por trás da fronteira FFM/JNI, nunca implementado
diretamente em Kotlin puro no caminho de áudio.

### IV. Portabilidade via Kotlin Multiplatform
A lógica de domínio (modelo de módulos, estado, sequenciamento, contratos) vive em `commonMain`
e é 100% compartilhada entre plataformas. Diferenças de plataforma (FFM no JVM desktop, JNI/NDK
no Android) ficam isoladas atrás de `expect`/`actual` na fronteira de interop nativo. Android
não é requisito do MVP, mas nenhuma decisão de arquitetura no core pode inviabilizar essa
portabilidade futura.

### V. UI Declarativa Desacoplada do Core
A UI é construída em Compose Multiplatform, tratada como um consumidor externo do core via
contratos observáveis (state/streams) — nunca acessando estado interno de módulos diretamente.
Cada módulo pode expor sua própria UI (Composable) de forma plugável, sem que o host precise
conhecer detalhes visuais dos módulos.

### VI. Contratos Explícitos entre Módulos
Toda troca de áudio, MIDI, eventos e estado entre módulos e o host passa por interfaces
versionadas e documentadas. Mudanças de contrato exigem plano de migração explícito — não se
quebra um contrato de módulo silenciosamente.

### VII. Simplicidade Incremental (YAGNI)
Começa-se com a menor arquitetura que valida a proposta modular (host + 2-3 módulos de
referência). Otimizações de baixa latência, suporte a VST3/CLAP de terceiros e portabilidade
Android só entram quando houver necessidade real comprovada, não especulação antecipada.

## Restrições Técnicas

- **Linguagem**: Kotlin (JVM target: Java 26)
- **UI**: Compose Multiplatform
- **Interop nativo (desktop)**: Foreign Function & Memory API (`java.lang.foreign`) + jextract
  para geração de bindings
- **Interop nativo (Android, quando aplicável)**: JNI/NDK tradicional, escondido atrás do mesmo
  contrato Kotlin usado no desktop
- **Build**: Gradle com Kotlin Multiplatform plugin desde o início, mesmo que apenas o target
  JVM esteja ativo no MVP
- **Sem dependência de frameworks de aplicação genéricos** (ex.: Spring, Quarkus) no caminho de
  áudio — esses ecossistemas, se usados, ficam restritos a ferramentas auxiliares (ex.: serviço
  de licenciamento, sincronização em nuvem), nunca no core de processamento

## Fluxo de Desenvolvimento

- Cada módulo novo nasce com sua própria spec (`/speckit-specify`) antes de qualquer código
- Contratos entre host e módulos são desenhados e revisados antes da implementação do módulo
- Testes de contrato são obrigatórios para qualquer módulo que produza ou consuma áudio/MIDI
- Decisões de arquitetura que impactem a fronteira nativa (FFM/JNI) exigem justificativa
  explícita registrada no plano (`/speckit-plan`)

## Governance

Esta constitution tem precedência sobre qualquer decisão de implementação individual. Qualquer
plano (`/speckit-plan`) ou tarefa (`/speckit-tasks`) que viole um princípio aqui definido deve
justificar o desvio explicitamente ou ser revisado. Emendas a esta constitution exigem registro
de motivo e atualização da versão abaixo.

**Version**: 1.0.0 | **Ratified**: 2026-07-22 | **Last Amended**: 2026-07-22
