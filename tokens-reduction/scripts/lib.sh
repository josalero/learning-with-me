#!/usr/bin/env bash
# Shared helpers for the token-audit convenience scripts.
set -euo pipefail

# Repo root (parent of this scripts/ dir).
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENGINE="${ROOT}/engine"
EXAMPLE="${ROOT}/examples/spring-ai-support-assistant"

# Align the JDK with the project's pinned major (see engine/.java-version) when jenv exists.
activate_jdk() {
	if command -v jenv >/dev/null 2>&1; then
		export JENV_VERSION="${JENV_VERSION:-25}"
	fi
}

# Run a Gradle task from the engine dir with the right JDK active.
gradlew() {
	activate_jdk
	(cd "${ENGINE}" && ./gradlew "$@")
}
