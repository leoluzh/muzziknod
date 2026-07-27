ifeq ($(OS),Windows_NT)
SHELL := C:/Program Files/Git/usr/bin/sh.exe
endif
.SHELLFLAGS := -c

.DEFAULT_GOAL := help
.PHONY: help build check clean test jvmTest \
	test-core-host test-midi-sequencer test-audio-effects \
	test-oscillator test-midi-generator test-midi-logger test-ui-desktop \
	run-ui devbox-shell devbox-build devbox-test tasks

GRADLEW := ./gradlew

help: ## Lista todos os comandos disponiveis
	@echo "Comandos disponiveis:"
	@echo ""
	@echo "  make build                  - ./gradlew build (compila todos os modulos)"
	@echo "  make check                  - ./gradlew check (roda todos os testes/verificacoes)"
	@echo "  make test                   - alias de 'check' (nao existe task 'test' no root)"
	@echo "  make jvmTest                - ./gradlew jvmTest (roda testes JVM de todos os modulos)"
	@echo "  make clean                  - ./gradlew clean (limpa builds de todos os modulos)"
	@echo ""
	@echo "  make test-core-host         - testes JVM apenas de :core-host"
	@echo "  make test-midi-sequencer    - testes JVM apenas de :modules:midi-sequencer"
	@echo "  make test-audio-effects     - testes JVM apenas de :modules:audio-effects"
	@echo "  make test-oscillator        - testes JVM apenas de :reference-modules:oscillator"
	@echo "  make test-midi-generator    - testes JVM apenas de :reference-modules:midi-generator"
	@echo "  make test-midi-logger       - testes JVM apenas de :reference-modules:midi-logger"
	@echo "  make test-ui-desktop        - testes JVM apenas de :ui-desktop"
	@echo ""
	@echo "  make run-ui                 - ./gradlew :ui-desktop:run (abre a UI Compose Desktop)"
	@echo ""
	@echo "  make tasks                  - ./gradlew tasks --all (lista todas as tasks Gradle)"
	@echo ""
	@echo "  make devbox-shell           - devbox shell (entra no shell com Java 26)"
	@echo "  make devbox-build           - devbox run build"
	@echo "  make devbox-test            - devbox run test"

build: ## Compila core-host + reference-modules + modules
	$(GRADLEW) build;

check: ## Roda todos os testes/verificacoes de todos os modulos
	$(GRADLEW) check;

test: check ## Alias de 'check'

jvmTest: ## Roda apenas os testes JVM de todos os modulos
	$(GRADLEW) jvmTest;

clean: ## Remove os diretorios de build de todos os modulos
	$(GRADLEW) clean;

test-core-host: ## Testes JVM de :core-host
	$(GRADLEW) :core-host:jvmTest;

test-midi-sequencer: ## Testes JVM de :modules:midi-sequencer
	$(GRADLEW) :modules:midi-sequencer:jvmTest;

test-audio-effects: ## Testes JVM de :modules:audio-effects
	$(GRADLEW) :modules:audio-effects:jvmTest;

test-oscillator: ## Testes JVM de :reference-modules:oscillator
	$(GRADLEW) :reference-modules:oscillator:jvmTest;

test-midi-generator: ## Testes JVM de :reference-modules:midi-generator
	$(GRADLEW) :reference-modules:midi-generator:jvmTest;

test-midi-logger: ## Testes JVM de :reference-modules:midi-logger
	$(GRADLEW) :reference-modules:midi-logger:jvmTest;

test-ui-desktop: ## Testes JVM de :ui-desktop
	$(GRADLEW) :ui-desktop:jvmTest;

run-ui: ## Abre a UI Compose Desktop do host modular
	$(GRADLEW) :ui-desktop:run;

tasks: ## Lista todas as tasks Gradle disponiveis (todos os modulos)
	$(GRADLEW) tasks --all;

devbox-shell: ## Entra no devbox shell (Java 26)
	devbox shell;

devbox-build: ## devbox run build (equivalente a ./gradlew build)
	devbox run build;

devbox-test: ## devbox run test (equivalente a ./gradlew test)
	devbox run test;
