# Memory rules

Unbounded conversation history is a common production cost leak.

## Detect

- **Unbounded chat memory** — append-only lists with no window
- **No summarize / evict policy** — long sessions keep full transcripts
- **Full tool results retained** — large JSON stored and re-sent every turn
- **Duplicate memory + RAG** — same facts in history and retrieved docs
- **Missing max messages / max tokens** on memory beans or advisors

## Suggest

- Token-window or message-window memory
- Summarize-and-evict for long sessions
- Store tool-result references / hashes instead of full payloads
- Cap memory contribution in the prompt assembler
- Separate short-term vs long-term memory stores

## Evidence to collect

- Memory type (`MessageWindowChatMemory`, custom lists, Redis, etc.)
- Window size or lack thereof
- Whether tool outputs are persisted verbatim
