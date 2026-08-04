# RAG rules

Look for retrieval that adds tokens without being used in the answer.

## Detect

- **Excessive `topK`** — default high K with no relevance threshold
- **Full documents** — chunk size too large or whole files injected
- **No filters** — tenant / type / date filters unused when available
- **Uncited retrieval** — docs retrieved but never referenced in the response path
- **Duplicate chunks** — same content from overlapping embeddings
- **Always-on RAG** — retrieval runs even for greetings or pure CRUD intents

## Suggest

- Lower `topK`; add similarity thresholds
- Prefer symbol / snippet level context over whole files
- Deduplicate retrieved chunks before prompt assembly
- Gate retrieval behind intent / tool need
- Measure citation rate when runtime telemetry exists

## Evidence to collect

- Vector store / retriever configuration
- `topK`, threshold, filters
- Call sites that always retrieve vs conditional retrieve
