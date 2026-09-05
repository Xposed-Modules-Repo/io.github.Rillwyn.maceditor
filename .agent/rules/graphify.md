---
trigger: always_on
glob:
description:
---

## graphify

Knowledge graph output: `graphify-out/`. **Do not run on repo root or full `backend/`** — use `.graphifyignore` and scoped paths.

Rules:
- Read `graphify-out/GRAPH_REPORT.md` before architecture questions (after a scoped build).
- Run `bash scripts/graphify-app.sh` or `graphify update backend/app` and `graphify update frontend/src` — never `graphify update backend` or `graphify update .`.
- MCP: `query_graph`, `get_node`, `shortest_path`. CLI: `graphify query`, `graphify path`, `graphify explain`.
- After code edits: `graphify update backend/app` (AST-only).
