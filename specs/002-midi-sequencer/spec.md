# Feature Specification: Módulo Sequenciador MIDI

**Feature Branch**: `002-midi-sequencer`

**Created**: 2026-07-24

**Status**: Implemented (see [tasks.md](./tasks.md) — all 24 tasks complete)

**Input**: User description: "MIDI sequencer module"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Programar e reproduzir um padrão de sequência (Priority: P1)

Como usuário, eu programo uma sequência de notas em um padrão de passos (steps) dentro do
módulo sequenciador, inicio a reprodução, e o módulo emite os eventos MIDI programados na
ordem e no tempo (timing) corretos, em loop contínuo, a cada ciclo de processamento do host,
até que eu pare a reprodução.

**Why this priority**: É a capacidade fundamental do módulo — sem programar e reproduzir um
padrão, o sequenciador não tem propósito. Junto ao Core Host Modular (001), esta é a primeira
demonstração de um módulo "produto" real (não apenas scaffolding de referência).

**Independent Test**: Pode ser testado carregando uma instância do módulo sequenciador no host,
programando um padrão simples (ex.: 4 passos com notas distintas), iniciando a reprodução, e
conectando sua saída MIDI a um módulo que registra os eventos recebidos — verificando que as
notas chegam na ordem e no tempo programados, repetindo em loop.

**Acceptance Scenarios**:

1. **Given** um módulo sequenciador carregado com um padrão vazio, **When** o usuário programa
   notas em passos específicos e inicia a reprodução, **Then** o módulo emite exatamente os
   eventos programados, na ordem dos passos, respeitando o tempo (BPM) configurado.
2. **Given** um padrão em reprodução, **When** o padrão alcança o último passo, **Then** a
   reprodução recomeça automaticamente do primeiro passo (loop), sem interrupção perceptível de
   timing.
3. **Given** um padrão em reprodução, **When** o usuário para a reprodução, **Then** o módulo
   para de emitir eventos e nenhuma nota é deixada "presa" (nenhum note-on sem seu note-off
   correspondente).

---

### User Story 2 - Editar o padrão em tempo real (Priority: P2)

Como usuário, eu edito o conteúdo de um padrão (adicionar, remover ou alterar notas em passos
específicos, e ajustar parâmetros como tempo/BPM e número de passos) enquanto o sequenciador
está parado ou em reprodução, e as mudanças passam a valer imediatamente (ou no próximo passo,
se estiver em reprodução), sem precisar recarregar o módulo.

**Why this priority**: Editar um padrão já em reprodução (ou entre execuções) é o fluxo de
trabalho real de composição — mas o valor mínimo do módulo (US1) já existe sem isso.

**Independent Test**: Pode ser testado programando um padrão inicial, iniciando a reprodução,
alterando uma nota em um passo ainda não alcançado no ciclo atual, e verificando que o próximo
loop reproduz a nota alterada (não a original).

**Acceptance Scenarios**:

1. **Given** um padrão parado, **When** o usuário adiciona, remove ou altera uma nota em um
   passo, **Then** a próxima reprodução usa o padrão atualizado.
2. **Given** um padrão em reprodução, **When** o usuário altera um passo que ainda não foi
   emitido no ciclo/loop atual, **Then** a alteração é refletida na próxima vez que aquele passo
   for alcançado, sem interromper os passos já em andamento.
3. **Given** um padrão em reprodução, **When** o usuário altera o tempo (BPM) ou o número de
   passos do padrão, **Then** a mudança é aplicada sem travar a reprodução ou perder a posição
   atual de forma abrupta.

---

### User Story 3 - Conectar o sequenciador a outros módulos via o grafo de roteamento (Priority: P1)

Como usuário, eu conecto a saída MIDI do sequenciador à entrada de outro módulo (ex.: um
sintetizador), usando o grafo de roteamento já existente do Core Host Modular, e os eventos
programados no sequenciador chegam ao módulo de destino a cada ciclo de reprodução.

**Why this priority**: Sem essa integração, o sequenciador é um módulo isolado sem utilidade
prática — ele só entrega valor quando alimenta outro módulo no grafo. Depende diretamente do
grafo de roteamento (Core Host Modular, feature 001).

**Independent Test**: Pode ser testado carregando o sequenciador e um módulo de referência que
registra eventos MIDI recebidos (ex.: `midi-logger` de 001), conectando a saída do sequenciador
à entrada do logger, iniciando a reprodução, e verificando que os eventos registrados
correspondem exatamente ao padrão programado.

**Acceptance Scenarios**:

1. **Given** o sequenciador carregado e conectado à entrada de outro módulo, **When** a
   reprodução está ativa, **Then** o módulo de destino recebe todos os eventos emitidos pelo
   sequenciador, na ordem e no ciclo em que foram gerados.
2. **Given** o sequenciador desconectado (sem conexões de saída), **When** a reprodução está
   ativa, **Then** o sequenciador continua avançando seu padrão normalmente (sem erro), apenas
   sem nenhum módulo recebendo os eventos.

---

### Edge Cases

- O que acontece se o usuário iniciar a reprodução de um padrão totalmente vazio (nenhuma nota
  programada)? O sequenciador deve avançar os passos normalmente, sem emitir nenhum evento MIDI.
- O que acontece se o usuário parar a reprodução no meio de uma nota sustentada (note-on já
  emitido, note-off ainda não alcançado)? O sequenciador deve emitir o note-off pendente
  imediatamente ao parar, para não deixar notas presas no(s) módulo(s) conectado(s).
- Como o módulo se comporta se for removido do host (ver FR-009/FR-010 de 001-core-host)
  enquanto está em reprodução? Aplica-se o mesmo comportamento de remoção diferida já definido
  pelo host — a remoção só é efetivada após o ciclo em andamento, e qualquer nota sustentada
  deve receber seu note-off antes da remoção.
- O que acontece se o usuário alterar o número de passos do padrão para um valor menor que a
  posição de reprodução atual? A posição de reprodução é ajustada (wrap) para dentro do novo
  intervalo válido, sem crash.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O módulo sequenciador DEVE permitir a criação e edição de um padrão organizado em
  passos (steps) numerados, cada passo podendo conter zero ou mais eventos de nota MIDI (nota,
  velocidade, duração/gate).
- **FR-002**: O módulo DEVE permitir configurar o número de passos do padrão (comprimento) e o
  tempo de reprodução (BPM), com valores padrão razoáveis quando não especificados pelo usuário.
- **FR-003**: O módulo DEVE expor um controle de transporte com, no mínimo, os estados
  "reproduzindo" e "parado", iniciando e parando a emissão de eventos MIDI de acordo.
- **FR-004**: Durante a reprodução, o módulo DEVE emitir os eventos MIDI programados em cada
  passo na ordem correta e no tempo correspondente ao BPM configurado, avançando um passo por
  ciclo de processamento do host conforme a resolução configurada.
- **FR-005**: Ao alcançar o último passo do padrão durante a reprodução, o módulo DEVE retornar
  automaticamente ao primeiro passo (loop contínuo), sem exigir intervenção do usuário.
- **FR-006**: O módulo DEVE permitir editar o padrão (notas, BPM, número de passos) tanto quando
  parado quanto durante a reprodução; edições em passos ainda não alcançados no ciclo/loop atual
  DEVEM refletir na próxima vez que esses passos forem processados, sem interromper passos já em
  andamento.
- **FR-007**: Ao parar a reprodução, o módulo DEVE emitir imediatamente o evento de finalização
  (note-off) de qualquer nota atualmente sustentada, para não deixar notas presas em módulos
  conectados.
- **FR-008**: O módulo DEVE satisfazer o contrato de módulo definido pelo Core Host Modular
  (001-core-host) — expõe uma porta de saída MIDI e é carregável/conectável através do grafo de
  roteamento existente, sem exigir mudanças no host.
- **FR-009**: O módulo DEVE continuar avançando seu padrão de reprodução normalmente mesmo sem
  nenhuma conexão de saída ativa (nenhum erro por ausência de destino conectado).
- **FR-010**: Se o número de passos do padrão for reduzido enquanto a posição de reprodução
  atual está além do novo limite, o módulo DEVE ajustar a posição de reprodução para dentro do
  novo intervalo válido, sem falhar.

### Key Entities

- **Padrão (Pattern)**: Sequência ordenada de passos que define o que o módulo reproduz;
  possui um comprimento (número de passos) e um tempo (BPM) associado.
- **Passo (Step)**: Posição individual dentro do padrão; contém zero ou mais eventos de nota
  MIDI (nota, velocidade, duração/gate) a serem emitidos quando o passo é alcançado durante a
  reprodução.
- **Evento de Nota**: Par nota-ligada/nota-desligada (note-on/note-off) com velocidade e duração
  associadas, emitido pela porta de saída MIDI do módulo.
- **Transporte**: Estado de reprodução do módulo (reproduzindo/parado) e a posição atual (passo
  corrente) dentro do padrão.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Um padrão de 16 passos com notas programadas reproduz em loop contínuo por pelo
  menos 20 repetições sem desvio de timing perceptível (ordem/tempo dos eventos) e sem notas
  presas, em um teste automatizado.
- **SC-002**: 100% das notas sustentadas recebem seu evento de finalização (note-off) dentro do
  mesmo ciclo em que a reprodução é parada, em testes automatizados dedicados a esse cenário.
- **SC-003**: Uma edição de padrão (nota, BPM ou número de passos) aplicada durante a reprodução
  é refletida corretamente na próxima passagem pelo passo alterado, em 100% dos casos testados,
  sem interromper a reprodução em andamento.
- **SC-004**: O módulo sequenciador carrega, conecta-se a outro módulo via o grafo de roteamento
  existente, reproduz um padrão completo e é removido do host, tudo em um único fluxo de teste
  automatizado, sem reiniciar a aplicação.

## Assumptions

- O sequenciador desta feature é monofônico por trilha/instância (um padrão por instância do
  módulo) — múltiplas trilhas simultâneas dentro de uma mesma instância ficam fora de escopo;
  para tocar múltiplas partes em paralelo, o usuário carrega múltiplas instâncias do módulo
  (habilitado pelo Core Host Modular).
- O padrão é editado de forma programática/estruturada (dados de passos), não via gravação
  ao vivo a partir de um controlador MIDI externo — captura de performance ao vivo é uma feature
  futura separada, dependente de I/O de dispositivo MIDI ainda não implementado no host.
- Não há sincronização de transporte entre múltiplas instâncias de sequenciadores nesta feature
  (cada instância tem seu próprio play/stop independente) — um "clock" ou transporte global
  compartilhado entre módulos é uma feature futura separada.
- Groove/swing, probabilidade por passo, e automação de parâmetros não-nota (ex.: CC, pitch
  bend) ficam fora de escopo desta feature — o padrão programado contém apenas eventos de nota
  (note-on/note-off com velocidade e duração).
- Segue as mesmas suposições de plataforma da feature 001 (desktop/JVM, Java 26, sem
  persistência de projeto em disco nesta feature).