# Quickstart: UI Compose Multiplatform do Host Modular

Guia de validação ponta a ponta desta feature. Pressupõe que `ui-desktop` e as mudanças
aditivas de `core-host`/`modules/*` (ver [contracts/](./contracts/)) já foram
implementadas conforme [tasks.md](./tasks.md) (gerado por `/speckit-tasks`).

## Pré-requisitos

- Java 26 (ou o JDK ativo do projeto — ver `README.md`), Gradle Wrapper (`./gradlew`),
  sem setup adicional além do já documentado no `README.md`.
- Branch `004-ui-compose` com o módulo `:ui-desktop` já registrado em
  `settings.gradle.kts`.

## 1. Testes automatizados (lógica de apresentação e Composables)

```bash
./gradlew :ui-desktop:jvmTest
```

**Esperado**: `HostViewModelTest`, `GraphViewTest`, `TransportControlsTest`,
`ModuleCatalogTest` passam — cobrindo os cenários de aceitação de US1/US2/US3 (ver
[contracts/ui-composables-contract.md](./contracts/ui-composables-contract.md)).

## 2. Testes de contrato do host (garantindo que nada quebrou)

```bash
./gradlew check
```

**Esperado**: `BUILD SUCCESSFUL` — os testes de contrato existentes de
001-core-host/002-midi-sequencer/003-audio-effects continuam passando inalterados,
confirmando que as novas APIs `StateFlow` são puramente aditivas (Constitution VI).

## 3. Cenário manual — US1 (grafo) + US2 (transporte/parâmetros)

```bash
./gradlew :ui-desktop:run
```

1. Com a janela aberta e o host vazio, o estado vazio (Edge Case do spec) deve orientar
   a adicionar um módulo.
2. Pelo catálogo (US3), adicionar um `OscillatorModule` e um `MidiLoggerModule`.
3. Conectar a saída de áudio do oscillator à entrada compatível do logger arrastando/
   selecionando as portas — a conexão deve aparecer na visualização do grafo
   imediatamente (SC-001, SC-003).
4. Tentar conectar uma porta de áudio a uma porta MIDI incompatível — a UI deve recusar
   visualmente sem alterar o grafo (SC-004).
5. Adicionar um `MidiSequencerModule`, acionar play pela UI, observar o estado
   "em execução" refletido nos controles de transporte (US2, Acceptance Scenario 1).
6. Adicionar um `DelayModule` de `audio-effects`, ajustar o slider de mix wet/dry e o
   tempo de delay — o comportamento de áudio observado (via, por exemplo, um
   `MidiLoggerModule`/log já existente ou inspeção do valor do `StateFlow`) deve refletir
   o novo valor no próximo ciclo, sem interromper o processamento (US2, Acceptance
   Scenario 3).
7. Remover o `DelayModule` enquanto conectado — a UI deve manter a conexão pendente até
   reconexão explícita (mesmo comportamento do host, FR-014) e desabilitar os controles
   do módulo durante a remoção diferida (FR-015).

## 4. Critério de sucesso da validação

- Todos os passos de 1–3 acima completam sem exceção não tratada e sem o processo
  travar/congelar.
- Nenhuma etapa exigiu editar código ou reiniciar o processo do host entre as ações
  (SC-001, SC-005).
