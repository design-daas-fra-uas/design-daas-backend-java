---
description: 'Generate a complete technical + user + thesis documentation set for one backend package (e.g. auth, permission, user). Use when the user asks to "document package X", "create docs for the X package", or wants architecture/DB/workflow diagrams plus a master-thesis chapter for a specific module.'
---

# Package Documentation Skill

You are acting as a **technical writer + software architect** producing a
complete documentation set for a single, named backend package (e.g.
`auth`, `permission`, `user`, `desktop`). This skill was authored while
documenting the `auth` package in this repository
(`docs/auth-package/`) — treat that folder as the reference template for
structure, depth, and tone.

## 0. Determine the target package and output location

1. Identify the package name/path the user wants documented (e.g.
   `de.frauas.design.backend.<package>`).
2. Output goes to `docs\<package-name>-package\` (kebab-case, suffixed
   `-package`), created if missing, sibling to `docs\auth-package\`.
3. If the user hasn't specified which diagram format or thesis style they
   want, ask (don't assume) — unless they've already stated a preference
   earlier in the conversation:
   - Diagram format: Mermaid (default/recommended, renders in
     Markdown/GitHub) vs PlantUML vs ASCII.
   - Thesis depth: full ready-to-use prose vs structured outline.

## 1. Explore the package thoroughly before writing anything

**Do not write documentation from assumptions.** Read the actual code:

1. `glob` for all files under the package's controller/service/dto/model/
   repository/exception sub-packages (or equivalent structure).
2. `view` every controller, every service, every entity/model, every
   repository, and every exception class. Pay special attention to
   Javadoc/docstrings already in the code — they often explain *why*, not
   just *what*, and should be mined for architectural rationale rather than
   re-derived from scratch.
3. `grep` for where the package's beans/classes are referenced from
   *outside* the package (e.g. security config, other services) — this
   reveals integration points that belong in the architecture doc.
4. Find and read relevant DB migrations (Flyway/Liquibase) touching the
   package's tables, in chronological order — migration history is a rich
   source of "why does the schema look like this" narrative for both the
   architecture doc and the thesis chapter.
5. Check for an existing test suite for the package; test class names/
   structure reveal how the maintainers think about the package's
   sub-responsibilities (useful for the architecture doc's "design
   decisions" section).
6. If the package integrates with global config (security filter chains,
   scheduled jobs, cross-cutting validators), read those files too — don't
   describe the package in isolation if it plugs into shared infrastructure.

Use parallel `view`/`grep`/`glob` calls wherever possible to keep this
phase fast.

## 2. Produce this exact document set (adapt names to the package)

Create these files in `docs\<package-name>-package\`:

1. **`README.md`** — index with a table of contents, one-paragraph
   description, and a "quick facts" section (endpoints, storage, key
   external dependencies).
2. **`01-overview.md`** — what the package does / does not do, its
   directory layout (as a tree), and a table of what's owned elsewhere but
   related.
3. **`02-architecture.md`** — a Mermaid `flowchart` of the layered
   architecture (controller → service → repository → cross-cutting
   config), a Mermaid `classDiagram` of the core types, and a numbered
   "Key design decisions" section explaining *why* the code is structured
   the way it is (extract this from Javadoc/comments plus your own
   analysis of the code's actual behavior — cite concrete class/method
   names).
4. **`03-database-design.md`** — a Mermaid `erDiagram`, a table per
   database table (column, type, notes), and a migration-history table
   summarizing each migration file chronologically with a one-line
   rationale. Include a "lessons visible in the migration history" section
   if the schema evolved in an interesting way (columns added/removed/
   renamed, tables dropped).
5. **`04-workflows.md`** — a Mermaid `sequenceDiagram` for every distinct
   flow the package supports (e.g. create/read/update/delete, or
   domain-specific flows like login/refresh/logout). Diagrams must reflect
   actual method/class names from the code, not generic placeholders. Add
   a `stateDiagram-v2` for any stateful policy (e.g. lockout, approval
   workflows) if applicable.
6. **`05-api-reference.md`** — every REST endpoint: method, path, auth
   requirement, request params/body schema, response schema, and a
   complete error-code table (HTTP status + machine-readable error code +
   trigger condition), plus a table of relevant configuration properties.
7. **`06-security-design.md`** (only if the package has security-relevant
   behavior — auth, permissions, PII handling, etc.) — a threat/mitigation
   table, cryptographic or hashing choices with rationale, and a "known
   trade-offs / limitations" section useful for a thesis "limitations"
   discussion.
8. **`07-user-guide.md`** — plain-language explanation for a non-technical
   project owner: what problem it solves, what they get "out of the box",
   a table of configurable knobs relevant to business decisions (not code),
   and operational notes (what breaks if X, who to involve for changes).
9. **`08-thesis-chapter.md`** — a ready-to-use, numbered thesis chapter
   (unless the user asked for an outline instead) covering: motivation/
   requirements, architectural overview, the 2-4 most interesting design
   decisions explained with academic rigor and rationale, notable
   algorithms/data structures, limitations and future work, and a
   suggested references list (relevant RFCs/standards/OWASP guides). Write
   in formal academic prose, third person, past/present tense as
   appropriate — not bullet-point notes. Ground every claim in the actual
   code you read; do not invent behavior.

Match the tone, structural depth, and Mermaid diagram conventions used in
`docs\auth-package\` — that folder is the style reference for this skill.

## 3. Accuracy rules (non-negotiable)

- Every class name, method name, config property, table name, and column
  referenced in the docs **must** exist in the code you actually read.
  Never invent plausible-sounding but unverified details.
- If the package has no security-relevant behavior, skip
  `06-security-design.md` entirely rather than padding it with generic
  advice.
- If something is genuinely unclear from the code (e.g. an undocumented
  magic number), say so explicitly rather than guessing at intent.
- Prefer quoting/paraphrasing existing Javadoc rationale over inventing new
  rationale — the code's own comments are the most authoritative source for
  "why".

## 4. Wrap-up

After creating all files, give the user a concise summary (not a
re-print of the docs) listing: files created, and 2-3 headline
architectural/security insights worth highlighting. Do not dump full file
contents back into the chat — the files themselves are the deliverable.
