# Token Reduction (POC) — convenience wrappers around the Gradle `scan` CLI.
# All heavy lifting lives in scripts/*.sh; these targets just call them so the
# JDK activation logic stays in one place (scripts/lib.sh).

SHELL := /bin/bash
ROOT  := $(patsubst %/,%,$(dir $(abspath $(lastword $(MAKEFILE_LIST)))))

# Override on the command line, e.g. `make scan PROJECT=/path/to/app FRAMEWORK=spring-ai`.
PROJECT   ?= $(ROOT)/examples/spring-ai-support-assistant
FRAMEWORK ?= spring-ai

.DEFAULT_GOAL := help

.PHONY: help check test scan audit demo review clean tokens tools link-skills

help: ## Show this help
	@echo "Token Reduction (POC) — make targets:"
	@awk 'BEGIN{FS=":.*## "} /^[a-zA-Z0-9_-]+:.*## /{printf "  \033[36m%-12s\033[0m %s\n",$$1,$$2}' $(MAKEFILE_LIST)
	@echo
	@echo "Variables: PROJECT=<path> FRAMEWORK=<name> (defaults: canonical example, spring-ai)"

check: ## Compile all modules + run the full test suite (offline)
	@"$(ROOT)/scripts/check.sh"

test: ## Force-rerun the example scan regression test
	@"$(ROOT)/scripts/test.sh"

scan: ## Scan a project: make scan PROJECT=<path> FRAMEWORK=<name>
	@"$(ROOT)/scripts/scan.sh" "$(PROJECT)" "$(FRAMEWORK)"

audit: ## Scan the canonical example (expects 5 finding IDs)
	@"$(ROOT)/scripts/scan.sh"

demo: ## Run the offline example app, then audit it
	@"$(ROOT)/scripts/demo.sh"

review: ## Deterministic scan + OpenRouter semantic pass (needs OPENROUTER_API_KEY)
	@"$(ROOT)/scripts/review.sh" "$(PROJECT)" "$(FRAMEWORK)"

tokens: ## Heuristic token counts for the example (skill helper)
	@python3 "$(ROOT)/skills/token-efficiency-auditor/scripts/count_tokens.py" \
		"$(ROOT)/examples/spring-ai-support-assistant/src"

tools: ## Tool-surface summary for the example (skill helper)
	@python3 "$(ROOT)/skills/token-efficiency-auditor/scripts/analyze_tools.py" \
		"$(ROOT)/examples/spring-ai-support-assistant"

clean: ## Remove Gradle build outputs
	@"$(ROOT)/scripts/clean.sh"

link-skills: ## Recreate Cursor/Claude skill symlinks
	@"$(ROOT)/scripts/link-skill-targets.sh"
