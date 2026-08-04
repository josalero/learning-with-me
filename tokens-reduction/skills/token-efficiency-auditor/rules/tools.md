# Tool rules

Tool schemas are often the largest avoidable input cost.

## Detect

- **All tools on every request** — `.tools(allTools)` or global tool lists
- **Large tool schemas** — wide parameter objects, deep nesting, long descriptions
- **Unused tools** — registered but never called on a path
- **Duplicate tool definitions** — same capability exposed under multiple names
- **Tools for admin paths on user paths** — privileged tools exposed to every session

## Suggest

- Dynamic / path-scoped tool selection
- Tree-shake tools that cannot apply to the current intent
- Shorten descriptions; remove unused parameters
- Split admin vs user tool sets
- Add regression tests that assert tool sets per operation

## Evidence to collect

- Run `python3 scripts/analyze_tools.py <project-root>`
- Count tools registered per call site
- Note which tools are reachable from each service / agent
