# Feature Specification: Core Host Modular

**Feature Branch**: `001-core-host`

**Created**: 2026-07-22

**Status**: Draft

**Input**: User description: "Core do host modular: gerenciamento de ciclo de vida de módulos, grafo de roteamento de áudio/MIDI e contrato base de módulo"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Carregar e ativar um módulo no host (Priority: P1)

Como usuário do software, eu adiciono um módulo (ex.: um sintetizador ou um sequenciador) ao
meu projeto, e o host o inicializa, expõe suas entradas/saídas de áudio e MIDI, e o torna
disponível para ser conectado a outros módulos — sem que eu precise reiniciar a aplicação ou
que outros módulos já carregados sejam afetados.

**Why this priority**: É a capacidade fundamental que valida a proposta modular do projeto. Sem
isso, não existe produto — é o requisito mínimo para qualquer demonstração de valor.

**Independent Test**: Pode ser testado carregando um único módulo de referência (ex.: um
oscilador simples) no host vazio e verificando que ele aparece disponível, com suas portas de
entrada/saída corretamente expostas, sem nenhum outro módulo presente.

**Acceptance Scenarios**:

1. **Given** um host vazio (sem módulos carregados), **When** o usuário adiciona um módulo
   válido, **Then** o módulo aparece como ativo e suas portas de I/O ficam disponíveis para
   roteamento.
2. **Given** um host com módulos já carregados e conectados, **When** o usuário adiciona um novo
   módulo, **Then** os módulos existentes continuam funcionando sem interrupção ou perda de
   estado.
3. **Given** um módulo inválido ou incompatível com o contrato do host, **When** o usuário tenta
   carregá-lo, **Then** o host rejeita o carregamento e informa o motivo, sem afetar módulos já
   ativos.

---

### User Story 2 - Conectar módulos através de um grafo de roteamento (Priority: P1)

Como usuário, eu conecto a saída de um módulo (ex.: um sequenciador) à entrada de outro (ex.: um
sintetizador), formando um grafo de processamento de áudio/MIDI, e o host garante que os dados
fluam na ordem correta a cada ciclo de processamento.

**Why this priority**: Roteamento é o que transforma módulos isolados em um instrumento
funcional. Sem essa capacidade, os módulos carregados (US1) não produzem nenhum resultado útil
em conjunto.

**Independent Test**: Pode ser testado conectando dois módulos de referência (um gerador de
eventos MIDI e um módulo que apenas registra os eventos recebidos) e verificando que os eventos
emitidos pelo primeiro chegam ao segundo na ordem e no timing esperados.

**Acceptance Scenarios**:

1. **Given** dois módulos carregados com portas compatíveis, **When** o usuário conecta a saída
   de um à entrada do outro, **Then** o host atualiza o grafo de roteamento e passa a propagar
   dados entre eles a cada ciclo.
2. **Given** uma tentativa de conexão entre portas de tipos incompatíveis (ex.: saída de áudio a
   entrada MIDI), **When** o usuário tenta conectar, **Then** o host recusa a conexão e explica o
   motivo.
3. **Given** um grafo de roteamento existente, **When** o usuário desconecta um link, **Then** o
   fluxo de dados entre os módulos anteriormente conectados cessa imediatamente, sem afetar o
   restante do grafo.
4. **Given** uma tentativa de conexão que criaria um ciclo de realimentação não suportado no
   grafo, **When** o usuário tenta conectar, **Then** o host recusa a conexão e explica o motivo.

---

### User Story 3 - Remover um módulo sem afetar o restante do projeto (Priority: P2)

Como usuário, eu removo um módulo que não preciso mais, e o host desfaz suas conexões de forma
limpa, liberando os recursos associados, mantendo o restante do grafo intacto.

**Why this priority**: Importante para um ciclo de trabalho real (experimentar, descartar,
reorganizar), mas não bloqueia a demonstração inicial de valor das User Stories 1 e 2.

**Independent Test**: Pode ser testado carregando três módulos conectados em cadeia, removendo o
módulo do meio, e verificando que as conexões associadas a ele desaparecem enquanto os outros
dois módulos permanecem carregados (agora desconectados entre si).

**Acceptance Scenarios**:

1. **Given** um módulo conectado a outros no grafo, **When** o usuário o remove, **Then** todas
   as conexões que o envolviam são removidas e os módulos remanescentes continuam ativos.
2. **Given** um módulo em processo de remoção, **When** o host ainda está finalizando um ciclo de
   processamento em andamento, **Then** a remoção só é efetivada após o fim seguro do ciclo
   corrente (sem interromper um buffer em processamento).

---

### Edge Cases

- O que acontece quando dois módulos tentam se conectar formando um ciclo direto no grafo de
  áudio (realimentação) sem um módulo intermediário que introduza atraso/buffer?
- Como o host se comporta se um módulo trava ou lança um erro durante seu ciclo de
  processamento — o restante do grafo continua rodando ou o host pausa tudo?
- O que acontece se o usuário tentar carregar dois módulos com o mesmo identificador único?
- Como o sistema lida com um módulo que expõe um número de portas de I/O diferente do declarado
  em seu contrato?
- O que acontece se uma conexão for feita entre módulos com taxas de amostragem ou formatos de
  buffer diferentes?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O host DEVE permitir carregar um módulo em tempo de execução sem reiniciar a
  aplicação e sem interromper módulos já carregados.
- **FR-002**: O host DEVE expor, para cada módulo carregado, a lista de suas portas de entrada e
  saída (áudio e/ou MIDI) declaradas em seu contrato.
- **FR-003**: O host DEVE validar a compatibilidade de um módulo com o contrato base antes de
  aceitar seu carregamento, rejeitando módulos incompatíveis com uma mensagem de erro clara.
- **FR-004**: O host DEVE permitir a criação de conexões entre a porta de saída de um módulo e a
  porta de entrada de outro, validando a compatibilidade de tipo (áudio ↔ áudio, MIDI ↔ MIDI)
  antes de efetivar a conexão.
- **FR-005**: O host DEVE recusar conexões entre portas de tipos incompatíveis, informando o
  motivo da recusa.
- **FR-006**: O host DEVE detectar e recusar conexões que criem ciclos de realimentação não
  suportados no grafo de roteamento.
- **FR-007**: O host DEVE processar o grafo de roteamento respeitando a ordem de dependência
  entre módulos a cada ciclo de processamento.
- **FR-008**: O host DEVE permitir a remoção de uma conexão individual sem afetar outras conexões
  do grafo.
- **FR-009**: O host DEVE permitir a remoção de um módulo, desfazendo automaticamente todas as
  conexões associadas a ele, sem afetar módulos remanescentes.
- **FR-010**: O host DEVE garantir que a remoção de um módulo só seja efetivada após a conclusão
  segura do ciclo de processamento em andamento.
- **FR-011**: O host DEVE isolar falhas de um módulo individual (erro ou travamento durante o
  processamento) [NEEDS CLARIFICATION: comportamento esperado — o host deve desativar apenas o
  módulo com falha e continuar o restante do grafo, ou pausar o processamento inteiro por
  segurança?].
- **FR-012**: O host DEVE impedir o carregamento de dois módulos com o mesmo identificador único
  simultaneamente.
- **FR-013**: O host DEVE validar que módulos conectados operam com taxa de amostragem e formato
  de buffer compatíveis [NEEDS CLARIFICATION: o host deve realizar conversão automática de
  formato/taxa entre módulos incompatíveis, ou a incompatibilidade deve simplesmente impedir a
  conexão?].

### Key Entities

- **Módulo**: Unidade funcional plugável (ex.: sintetizador, sequenciador, efeito). Possui um
  identificador único, um contrato declarado (portas de entrada/saída de áudio e/ou MIDI,
  parâmetros configuráveis) e um ciclo de vida (carregado, ativo, removido).
- **Porta**: Ponto de entrada ou saída de um módulo, tipado como áudio ou MIDI, usado como
  extremidade de uma conexão.
- **Conexão**: Ligação entre a porta de saída de um módulo e a porta de entrada de outro,
  representando um fluxo de dados no grafo de roteamento.
- **Grafo de Roteamento**: Estrutura que representa o conjunto de módulos carregados e suas
  conexões, determinando a ordem de processamento a cada ciclo.
- **Contrato de Módulo**: Especificação que todo módulo deve satisfazer para ser aceito pelo
  host — inclui as portas expostas e as garantias de comportamento esperadas pelo host.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Um módulo de referência pode ser carregado, conectado a outro módulo e removido do
  host sem reiniciar a aplicação, em um fluxo de teste automatizado.
- **SC-002**: O host rejeita 100% das tentativas de conexão entre portas de tipos incompatíveis
  em testes automatizados dedicados a esse cenário.
- **SC-003**: A remoção de um módulo do meio de uma cadeia de três módulos conectados preserva a
  atividade dos outros dois módulos em 100% dos casos testados.
- **SC-004**: O host processa um grafo de referência com pelo menos 5 módulos conectados sem
  travamentos ou erros de ordenação de processamento ao longo de uma sessão de teste contínua.

## Assumptions

- O MVP do host roda em desktop (JVM/Java 26), sem suporte a Android neste primeiro incremento —
  conforme a Constitution do projeto, a portabilidade via Kotlin Multiplatform é preparada
  estruturalmente, mas não validada nesta feature.
- Os módulos de referência usados para validar esta feature são módulos simples, sem
  processamento DSP nativo real (ex.: geradores de eventos MIDI e osciladores básicos em
  Kotlin puro) — a integração com engine DSP nativa via FFM API é tratada como uma feature
  separada e futura.
- Um único usuário opera o host por vez (sem colaboração multiusuário simultânea nesta fase).
- O host não possui, nesta feature, persistência de projeto em disco (salvar/carregar sessão) —
  isso é uma feature futura que dependerá deste core estar estável.
