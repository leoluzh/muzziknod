# Feature Specification: UI Compose Multiplatform do Host Modular

**Feature Branch**: `004-ui-compose`

**Created**: 2026-07-27

**Status**: Draft

**Input**: User description: "UI Compose Multiplatform para o host modular — interface visual para gerenciar módulos plugáveis (sequenciador, sampler, synth, efeitos, mixer), visualizar e editar o grafo de roteamento de áudio/MIDI, e controlar transporte/parâmetros dos módulos em tempo real."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Visualizar e editar o grafo de roteamento (Priority: P1)

Como usuário, eu vejo todos os módulos carregados no host e as conexões de áudio/MIDI
entre eles em uma tela única, e posso criar ou remover uma conexão entre a saída de um
módulo e a entrada de outro diretamente pela interface.

**Why this priority**: Sem visualizar e editar o grafo, a UI não substitui nenhuma
interação que hoje só existe via código/testes — é a capacidade que torna o host
utilizável por um usuário final, não apenas por um desenvolvedor.

**Independent Test**: Pode ser testado carregando dois ou mais módulos de referência
(ex.: oscillator e midi-logger) no host, abrindo a UI, criando uma conexão entre uma
saída e uma entrada compatíveis pela interface, e verificando que o grafo de roteamento
do host reflete a nova conexão (e que removê-la pela UI também reflete no host).

**Acceptance Scenarios**:

1. **Given** dois módulos carregados no host sem conexão entre si, **When** o usuário
   arrasta ou seleciona uma porta de saída de um módulo e uma porta de entrada compatível
   de outro na UI, **Then** o host registra a conexão e a UI exibe a nova conexão no
   grafo.
2. **Given** dois módulos conectados, **When** o usuário remove a conexão pela UI,
   **Then** o host remove a conexão do grafo de roteamento e a UI deixa de exibi-la.
3. **Given** duas portas incompatíveis (ex.: saída de áudio para entrada MIDI, ou taxas
   de amostragem diferentes), **When** o usuário tenta conectá-las pela UI, **Then** a UI
   recusa a conexão e comunica visualmente o motivo, sem alterar o grafo do host.

---

### User Story 2 - Controlar transporte e parâmetros dos módulos em tempo real (Priority: P1)

Como usuário, eu vejo os controles de transporte (play/stop) do sequenciador MIDI e os
parâmetros ajustáveis de cada módulo (ex.: mistura wet/dry e parâmetros específicos dos
efeitos de áudio) na UI, e ao alterá-los pela interface o comportamento do módulo em
execução muda de acordo, sem precisar reiniciar o host.

**Why this priority**: Mesma prioridade da US1 — sem controlar transporte/parâmetros
pela UI, o usuário ainda dependeria de código para operar o host, o que anula o
propósito de uma interface visual. As duas juntas (grafo + controles) formam o mínimo
utilizável desta feature.

**Independent Test**: Pode ser testado carregando um sequenciador MIDI e um módulo de
efeito de áudio, iniciando/parando o transporte pela UI e verificando que o estado de
execução do sequenciador muda de acordo, e ajustando um parâmetro do efeito pela UI e
verificando que os ciclos de processamento subsequentes refletem o novo valor.

**Acceptance Scenarios**:

1. **Given** um sequenciador MIDI carregado e parado, **When** o usuário aciona o
   controle de play na UI, **Then** o sequenciador inicia a reprodução e a UI reflete o
   estado "em execução".
2. **Given** um sequenciador MIDI em execução, **When** o usuário aciona o controle de
   stop na UI, **Then** o sequenciador para e a UI reflete o estado "parado".
3. **Given** um módulo de efeito de áudio carregado, **When** o usuário ajusta um
   parâmetro (ex.: mistura wet/dry ou tempo de delay) por um controle na UI, **Then** os
   ciclos de processamento subsequentes do módulo refletem o novo valor, sem interromper
   o processamento em andamento.
4. **Given** um controle de parâmetro na UI, **When** o usuário tenta ajustá-lo além do
   intervalo válido do parâmetro, **Then** a UI limita o controle ao intervalo válido,
   consistente com o comportamento de saturação (clamp) já definido pelos módulos.

---

### User Story 3 - Adicionar e remover módulos pela interface (Priority: P2)

Como usuário, eu escolho um tipo de módulo disponível (ex.: oscillator, midi-sequencer,
audio-effects) em um catálogo na UI, o adiciono ao host, e posso removê-lo quando não
precisar mais dele — sem editar código ou reiniciar a aplicação.

**Why this priority**: Complementa US1/US2 fechando o ciclo de vida do módulo pela UI.
Tem prioridade menor porque o MVP da UI já entrega valor real conectando e controlando
módulos pré-carregados (US1+US2); montar o projeto do zero pela UI é o próximo passo
natural, não o mínimo indispensável.

**Independent Test**: Pode ser testado abrindo a UI com o host vazio, adicionando um
módulo do catálogo, verificando que ele aparece no host e na visualização do grafo, e
removendo-o pela UI, verificando que ele desaparece de ambos.

**Acceptance Scenarios**:

1. **Given** o host sem módulos carregados, **When** o usuário seleciona um tipo de
   módulo no catálogo da UI e confirma a adição, **Then** o módulo é carregado no host e
   passa a aparecer na visualização do grafo.
2. **Given** um módulo carregado sem conexões, **When** o usuário o remove pela UI,
   **Then** o módulo é removido do host (respeitando a remoção diferida já definida pelo
   host, se um ciclo estiver em andamento) e desaparece da UI assim que a remoção é
   efetivada.

---

### Edge Cases

- O que a UI exibe quando o host não tem nenhum módulo carregado? Um estado vazio
  orientando o usuário a adicionar um módulo pelo catálogo (US3).
- Como a UI reflete uma mudança de estado do host que não se originou de uma ação do
  próprio usuário na UI (ex.: outro processo ou teste alterando o grafo)? A UI observa o
  estado do host via os contratos observáveis (state/streams) definidos pela Constitution
  (Princípio V) e atualiza a visualização automaticamente, sem exigir ação do usuário.
- O que acontece se o usuário tentar remover um módulo que ainda tem conexões ativas?
  Segue o mesmo comportamento já definido pelo host — as conexões não são removidas
  automaticamente; a UI comunica isso e a remoção do módulo segue a regra de remoção
  diferida do host.
- Como a UI se comporta se um módulo carregado não expõe uma UI própria (Composable)?
  A UI do host exibe uma representação genérica mínima (nome, tipo, portas) suficiente
  para posicioná-lo no grafo e vê-lo, mesmo sem controles específicos.
- O que acontece com controles de parâmetro durante a remoção diferida de um módulo
  (ciclo em andamento)? Os controles ficam desabilitados assim que a remoção é
  solicitada, refletindo que o módulo está saindo, mesmo antes de a remoção ser
  efetivada.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema DEVE prover uma interface visual, construída em Compose
  Multiplatform, que exibe todos os módulos atualmente carregados no host.
- **FR-002**: A UI DEVE exibir as conexões de áudio/MIDI existentes no grafo de
  roteamento do host entre os módulos carregados.
- **FR-003**: A UI DEVE permitir criar uma nova conexão entre uma porta de saída e uma
  porta de entrada compatíveis, refletindo a criação no grafo de roteamento do host.
- **FR-004**: A UI DEVE permitir remover uma conexão existente, refletindo a remoção no
  grafo de roteamento do host.
- **FR-005**: A UI DEVE recusar visualmente uma tentativa de conexão entre portas
  incompatíveis, sem alterar o estado do host, consistente com as regras de conexão já
  definidas pelo Core Host Modular (001-core-host).
- **FR-006**: A UI DEVE exibir controles de transporte (play/stop) para módulos que
  expõem esse tipo de controle (ex.: sequenciador MIDI) e refletir o estado atual de
  execução.
- **FR-007**: A UI DEVE exibir controles para os parâmetros ajustáveis expostos por cada
  módulo (`ParameterSpec`), permitindo alterá-los em tempo real.
- **FR-008**: Uma alteração de parâmetro ou transporte feita pela UI DEVE ser aplicada ao
  módulo correspondente sem interromper o processamento de áudio/MIDI em andamento.
- **FR-009**: A UI DEVE limitar (clamp) o valor de um controle de parâmetro ao intervalo
  válido definido pelo módulo, refletindo o mesmo comportamento de saturação já aplicado
  pelo host/módulos.
- **FR-010**: A UI DEVE observar o estado do host exclusivamente através de contratos
  observáveis (state/streams) expostos pelo core, nunca acessando estado interno de
  módulo diretamente, conforme a Constitution (Princípio V).
- **FR-011**: Cada módulo PODE expor sua própria UI (Composable) de forma plugável; a UI
  do host DEVE renderizá-la quando disponível, sem que o host precise conhecer detalhes
  visuais do módulo.
- **FR-012**: Para um módulo que não expõe uma UI própria, a UI do host DEVE exibir uma
  representação genérica mínima (nome, tipo, portas) para permitir posicioná-lo e
  visualizá-lo no grafo.
- **FR-013**: A UI DEVE prover um catálogo dos tipos de módulo disponíveis
  (oscillator, midi-generator, midi-logger, midi-sequencer, audio-effects) a partir do
  qual o usuário pode adicionar uma nova instância ao host.
- **FR-014**: A UI DEVE permitir remover um módulo carregado do host, respeitando a
  regra de remoção diferida já definida pelo host (FR-010 de 001-core-host) quando um
  ciclo de processamento estiver em andamento.
- **FR-015**: Enquanto a remoção de um módulo estiver pendente (diferida), a UI DEVE
  desabilitar os controles desse módulo, sinalizando visualmente que ele está saindo.
- **FR-016**: A UI DEVE exibir um estado vazio, com orientação para adicionar um módulo
  pelo catálogo, quando o host não tiver nenhum módulo carregado.

### Key Entities

- **Visualização de Grafo**: Representação visual dos módulos carregados e das conexões
  de áudio/MIDI entre eles, espelhando o grafo de roteamento do host.
- **Controle de Parâmetro**: Elemento de UI vinculado a um `ParameterSpec` de um módulo,
  que exibe e permite alterar seu valor atual dentro do intervalo válido.
- **Controle de Transporte**: Elemento de UI vinculado ao estado de execução (play/stop)
  de um módulo que expõe esse contrato (ex.: sequenciador MIDI).
- **Catálogo de Módulos**: Lista dos tipos de módulo disponíveis para adição ao host pela
  UI.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Um usuário consegue montar uma cadeia funcional de dois ou mais módulos
  (conectar portas compatíveis) inteiramente pela UI, sem editar código, em um fluxo de
  teste manual ou automatizado de interface.
- **SC-002**: Uma alteração de parâmetro ou de transporte feita na UI é refletida no
  comportamento do módulo correspondente no ciclo de processamento seguinte, em 100% dos
  casos verificados em teste automatizado de UI.
- **SC-003**: O estado exibido pela UI (módulos, conexões, valores de parâmetro) nunca
  diverge do estado real do host por mais de um ciclo de atualização de observação,
  verificável comparando o estado exposto pelo host com o estado renderizado pela UI em
  teste automatizado.
- **SC-004**: Uma tentativa de conexão incompatível é recusada pela UI sem alterar o
  grafo do host em 100% dos casos testados.
- **SC-005**: Um usuário consegue adicionar um módulo do catálogo e vê-lo aparecer no
  grafo em uma única interação (sem etapas intermediárias de configuração obrigatórias).

## Assumptions

- Escopo desktop/JVM apenas nesta feature — portabilidade Android via Compose
  Multiplatform permanece uma possibilidade futura não bloqueada, mas não é entregue
  aqui (Constitution IV, YAGNI).
- A UI consome os módulos e o grafo já existentes (001-core-host, 002-midi-sequencer,
  003-audio-effects); nenhuma mudança de contrato de módulo é necessária para esta
  feature (Constitution VI) — se a UI expuser uma necessidade de novo contrato
  observável que ainda não existe no core, a adição desse contrato é parte desta
  feature, mas sem quebrar os contratos existentes.
- Não há persistência de projeto em disco nesta feature (salvar/carregar um layout de
  módulos e conexões) — cada sessão da UI começa com o host vazio ou com o estado que o
  processo host já tiver em memória. Persistência é uma feature futura separada,
  sugerida no backlog do README.
- Edição do grafo (criar/remover conexão) é feita por seleção explícita de portas na UI
  (ex.: clicar origem, depois destino); um editor drag-and-drop completo é um
  refinamento de UX que pode evoluir depois, desde que a US1 já funcione com o mecanismo
  mais simples.
- A UI de cada módulo (Composable plugável, FR-011) é responsabilidade do próprio
  módulo; esta feature entrega a UI do host (grafo, catálogo, casca) e as Composables
  específicas dos módulos de referência já existentes (oscillator, midi-generator,
  midi-logger) e dos módulos atuais (midi-sequencer, audio-effects) — sampler e synth
  mencionados na descrição original ainda não existem como módulos e ficam fora de
  escopo até serem especificados.
