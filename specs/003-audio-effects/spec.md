# Feature Specification: Módulos de Efeitos de Áudio (Reverb/Delay/Distortion/EQ)

**Feature Branch**: `003-audio-effects`

**Created**: 2026-07-24

**Status**: Draft

**Input**: User description: "Reverb, delay, distortion, EQ — basic multi-effects chain on audio module"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Adicionar um efeito e ajustar a mistura wet/dry (Priority: P1)

Como usuário, eu adiciono um módulo de efeito (reverb, delay, distortion ou EQ) ao meu
projeto, conecto a saída de áudio de outro módulo à sua entrada, e ajusto o controle de
mistura wet/dry para equilibrar o quanto do sinal processado versus o sinal original chega
à saída do efeito.

**Why this priority**: É a capacidade fundamental de cada módulo — sem processar áudio e
misturar wet/dry, o efeito não tem propósito.

**Independent Test**: Pode ser testado carregando um módulo de efeito, conectando uma
fonte de áudio à sua entrada, definindo a mistura em 100% dry (totalmente seco) e
verificando que a saída é idêntica ao sinal de entrada; definindo 100% wet (totalmente
processado) e verificando que a saída reflete apenas o sinal processado pelo efeito.

**Acceptance Scenarios**:

1. **Given** um módulo de efeito conectado entre uma fonte de áudio e um destino, **When**
   a mistura é ajustada para 100% dry, **Then** o sinal de saída é idêntico ao sinal de
   entrada, sem processamento audível do efeito.
2. **Given** o mesmo módulo, **When** a mistura é ajustada para 100% wet, **Then** o sinal
   de saída reflete apenas o sinal processado, sem o sinal original não processado.
3. **Given** o mesmo módulo, **When** a mistura é ajustada para um valor intermediário
   (ex.: 50%), **Then** o sinal de saída é uma combinação proporcional do sinal original e
   do sinal processado.

---

### User Story 2 - Ajustar parâmetros específicos de cada tipo de efeito em tempo real (Priority: P1)

Como usuário, eu ajusto os parâmetros próprios de cada tipo de efeito (ex.: tempo e
feedback do delay; tempo de decaimento e tamanho da sala do reverb; drive/tom da
distortion; frequência, ganho e Q de cada banda do EQ) enquanto o áudio está sendo
processado, e as mudanças são aplicadas ao processamento subsequente sem interromper o
fluxo de áudio.

**Why this priority**: Moldar o caráter de cada efeito é o fluxo de trabalho real de
produção — mas o valor mínimo do módulo (US1: processar e misturar) já existe sem isso.
Mesma prioridade de US1 porque um efeito com parâmetros fixos tem utilidade musical muito
limitada.

**Independent Test**: Pode ser testado processando um sinal de áudio contínuo através de
um efeito, alterando um parâmetro específico (ex.: tempo de delay ou frequência de uma
banda de EQ) em tempo de execução, e verificando que os ciclos de processamento seguintes
refletem o novo valor sem interromper ou reiniciar o processamento.

**Acceptance Scenarios**:

1. **Given** um efeito de delay em processamento, **When** o usuário altera o tempo de
   delay ou o feedback, **Then** os ciclos seguintes usam o novo valor sem interromper o
   áudio já em trânsito.
2. **Given** um efeito de reverb em processamento, **When** o usuário altera o tempo de
   decaimento ou o tamanho da sala, **Then** os ciclos seguintes refletem a mudança sem
   interromper o processamento.
3. **Given** um efeito de distortion em processamento, **When** o usuário altera o drive
   ou o tom, **Then** os ciclos seguintes refletem a mudança sem interromper o
   processamento.
4. **Given** um efeito de EQ em processamento, **When** o usuário altera frequência, ganho
   ou Q de uma banda, **Then** os ciclos seguintes refletem a mudança sem interromper o
   processamento.
5. **Given** um parâmetro recebendo um valor fora da faixa válida, **When** o usuário tenta
   aplicá-lo, **Then** o módulo satura (clamp) o valor ao limite válido mais próximo, sem
   falhar.

---

### User Story 3 - Encadear múltiplos efeitos via grafo de roteamento (Priority: P1)

Como usuário, eu conecto vários módulos de efeito em sequência (ex.: EQ → distortion →
delay → reverb) usando o grafo de roteamento existente do Core Host Modular, entre uma
fonte de áudio e um destino, formando uma cadeia de multi-efeitos.

**Why this priority**: É o que dá nome à feature ("multi-effects chain") — sem a
possibilidade de encadear os quatro tipos de efeito, cada módulo isolado (US1/US2) só
entrega valor parcial. Depende diretamente do grafo de roteamento (Core Host Modular,
feature 001).

**Independent Test**: Pode ser testado carregando um módulo gerador de áudio, os quatro
tipos de módulo de efeito e um módulo de destino, conectando-os em cadeia (gerador → EQ →
distortion → delay → reverb → destino) via o grafo de roteamento, e verificando que o
destino recebe o sinal processado por todos os efeitos habilitados, na ordem da conexão.

**Acceptance Scenarios**:

1. **Given** um gerador de áudio, quatro módulos de efeito (um de cada tipo) e um destino
   carregados, **When** eles são conectados em cadeia, **Then** o destino recebe o áudio
   processado por todos os efeitos, na ordem em que foram conectados.
2. **Given** essa mesma cadeia, **When** um dos módulos de efeito é removido do host,
   **Then** a conexão entre os módulos vizinhos não existe automaticamente (o usuário
   precisa reconectar explicitamente) — mesmo comportamento de remoção já definido pelo
   host (FR-009 de 001-core-host).
3. **Given** essa mesma cadeia, **When** o usuário reconecta a saída de um efeito a um
   ponto diferente do grafo (mudando a posição efetiva de um efeito na cadeia), **Then** o
   processamento subsequente reflete a nova ordem de conexão.

---

### Edge Cases

- O que acontece se a mistura wet/dry for ajustada para um valor fora do intervalo válido
  (ex.: negativo ou acima de 100%)? O valor é limitado (clamped) ao intervalo válido, sem
  erro.
- Como cada efeito se comporta ao ser carregado antes de qualquer sinal de entrada estar
  conectado? Processa silêncio (buffer vazio/zero) sem erro, produzindo saída silenciosa.
- O que acontece com sinal "wet" acumulado (ex.: cauda de reverb ou repetições de delay
  ainda soando) quando o módulo é removido? Segue o mesmo comportamento de remoção diferida
  já definido pelo host (FR-010 de 001-core-host) — a remoção só é efetivada após o ciclo em
  andamento; a cauda em si não precisa ser "drenada" antes da remoção.
- Um efeito conectado a uma fonte com taxa de amostragem ou formato de buffer diferente do
  seu próprio port de entrada segue a mesma regra geral já estabelecida pelo host: a conexão
  é recusada, sem conversão automática (FR-013 de 001-core-host).
- Como o EQ se comporta quando todas as bandas têm ganho zero? Deve se comportar como
  passthrough para propósitos de mistura wet/dry (sinal "wet" idêntico ao "dry").

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: O sistema DEVE fornecer quatro módulos de efeito de áudio como tipos
  separados — reverb, delay, distortion e EQ — cada um com uma entrada de áudio e uma
  saída de áudio, satisfazendo o mesmo contrato de módulo.
- **FR-002**: Cada módulo de efeito DEVE expor um controle de mistura wet/dry que
  determina a proporção entre o sinal original (dry) e o sinal processado (wet) presente
  na saída.
- **FR-003**: Em 100% dry, a saída de qualquer módulo de efeito DEVE ser idêntica ao sinal
  de entrada (nenhum processamento audível do efeito).
- **FR-004**: Em 100% wet, a saída de qualquer módulo de efeito DEVE refletir
  exclusivamente o sinal processado pelo efeito.
- **FR-005**: Entre 0% e 100%, a saída DEVE ser uma combinação proporcional entre o sinal
  original e o sinal processado, de acordo com o valor da mistura.
- **FR-006**: O módulo de delay DEVE expor, no mínimo, parâmetros de tempo de delay e
  quantidade de feedback (repetições).
- **FR-007**: O módulo de reverb DEVE expor, no mínimo, parâmetros de tempo de decaimento
  e tamanho da sala (ou parâmetro equivalente de densidade/difusão).
- **FR-008**: O módulo de distortion DEVE expor, no mínimo, parâmetros de drive
  (intensidade da saturação) e tom (filtragem do sinal saturado).
- **FR-009**: O módulo de EQ DEVE expor, no mínimo, três bandas ajustáveis, cada uma com
  frequência central, ganho e Q (largura de banda).
- **FR-010**: Alterações nos parâmetros de mistura ou específicos de cada efeito DEVEM ser
  aplicadas aos ciclos de processamento subsequentes sem interromper o processamento de
  áudio em andamento.
- **FR-011**: Os quatro módulos de efeito DEVEM satisfazer o contrato de módulo definido
  pelo Core Host Modular (001-core-host) — expõem portas de entrada/saída de áudio e são
  carregáveis/conectáveis através do grafo de roteamento existente, sem exigir mudanças no
  host.
- **FR-012**: O sistema DEVE permitir conectar os quatro tipos de efeito em qualquer ordem,
  em sequência, através do grafo de roteamento existente, formando uma cadeia de
  multi-efeitos.
- **FR-013**: Valores de mistura wet/dry ou de parâmetros específicos fora do intervalo
  válido DEVEM ser limitados (clamped) ao intervalo válido, sem lançar erro.
- **FR-014**: Um módulo de efeito sem sinal de entrada conectado DEVE processar silêncio e
  produzir saída silenciosa, sem erro.
- **FR-015**: Esta feature entrega o contrato dos módulos de efeito e o controle de
  wet/dry/parâmetros com processamento de áudio como passthrough (identidade — o sinal
  "processado" é, por ora, o próprio sinal de entrada, sem DSP real de reverb/delay/
  distortion/EQ). O algoritmo de DSP real fica para uma feature futura dedicada à ponte
  nativa FFM/engine DSP, cumprindo a Constitution (Princípio III — DSP crítico nunca
  implementado diretamente em Kotlin puro no caminho de áudio) ao não implementar nenhum
  DSP real ainda, em vez de violar o princípio com uma implementação provisória em Kotlin
  puro. *(Resolvido — decisão do usuário na especificação; ver research.md quando a feature
  for planejada.)*
- **FR-016**: Os quatro tipos de efeito (reverb, delay, distortion, EQ) entram nesta
  feature como quatro tipos de módulo separados, não um único módulo configurável, e não um
  módulo único com cadeia interna — consistente com como oscillator/midi-generator/
  midi-logger foram modelados como tipos distintos em 001-core-host, e com a Constitution I
  (módulos plugáveis independentes). *(Resolvido — decisão do usuário na especificação.)*

### Key Entities

- **Módulo de Efeito**: Módulo que recebe áudio em uma entrada, aplica um processamento
  (reverb, delay, distortion ou EQ) e produz áudio em uma saída, com um controle de mistura
  wet/dry e parâmetros específicos do tipo de efeito.
- **Mistura Wet/Dry**: Parâmetro percentual (0-100%) que determina a proporção entre o
  sinal original (dry) e o sinal processado (wet) na saída do efeito.
- **Parâmetros do Efeito**: Conjunto específico por tipo — delay (tempo de delay,
  feedback); reverb (tempo de decaimento, tamanho da sala/densidade); distortion (drive,
  tom); EQ (bandas, cada uma com frequência, ganho, Q).
- **Cadeia de Efeitos**: Sequência de módulos de efeito conectados em série através do
  grafo de roteamento do host, entre uma fonte de áudio e um destino.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Com a mistura em 100% dry, a saída de qualquer módulo de efeito é
  indistinguível do sinal de entrada em 100% dos testes automatizados dedicados a esse
  cenário.
- **SC-002**: Com a mistura em 100% wet, a saída de qualquer módulo de efeito não contém
  nenhuma componente do sinal original não processado, em 100% dos testes automatizados
  dedicados a esse cenário.
- **SC-003**: Uma alteração de parâmetro (mistura ou específico do efeito) aplicada durante
  o processamento é refletida corretamente no próximo ciclo de processamento, em 100% dos
  casos testados, sem interromper o processamento em andamento.
- **SC-004**: Uma cadeia com os quatro tipos de efeito conectados (gerador → EQ →
  distortion → delay → reverb → destino) processa e entrega áudio ao destino em um fluxo de
  teste automatizado completo, sem reiniciar a aplicação.

## Assumptions

- Segue as mesmas suposições de plataforma das features 001 e 002 (desktop/JVM, Java 26,
  sem persistência de projeto em disco nesta feature, sem UI).
- Cada tipo de efeito (reverb, delay, distortion, EQ) é um módulo de tipo separado (não um
  único módulo configurável que troca de algoritmo, nem um módulo único com cadeia
  interna) — consistente com como oscillator/midi-generator/midi-logger foram modelados
  como tipos distintos em 001-core-host, e com a Constitution I (módulos plugáveis
  independentes).
- Não há automação gravada de parâmetros (envelope/curva ao longo do tempo) nesta
  feature — apenas ajuste direto de valor, como já é o padrão para `ParameterSpec` no
  contrato do host (definição/valor atual, sem timeline).
- O EQ desta feature é um EQ paramétrico simples de 3 bandas; EQs gráficos multibanda ou
  formatos de filtro alternativos ficam fora de escopo.
