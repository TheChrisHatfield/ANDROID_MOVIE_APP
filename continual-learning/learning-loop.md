# Learning Loop (agent playbook)

**Hook:** `learning.loop`  
**Audience:** coding agents in this repo  
**Contract:** [`docs/cl-spec-integration.md`](../docs/cl-spec-integration.md)

Process-only doc: **how** to learn, validate, and promote memory. **What** the product does lives in Spec Kit — never duplicate behavioral requirements here.

---

## Before you work

1. Read [`START_HERE.md`](../START_HERE.md) → [`docs/planning-rosetta-stone.md`](../docs/planning-rosetta-stone.md) (`planning.rosetta`).
2. Read **active feature** from [`AGENTS.md`](../AGENTS.md) → load `docs/specs/<feature-id>/` (spec, plan, tasks) + `docs/delta/20_micro/<feature-id>.md`.
3. Skim this file + [`gap-backlog.md`](./gap-backlog.md) for open gaps and last loot shift.

---

## Truth lanes

| Lane | Owns | Pattern |
|------|------|---------|
| Navigation | Where to look | `docs/planning-rosetta-stone.md` |
| Behavior | Requirements, acceptance | `docs/specs/<feature-id>/` |
| Context | Mission, architecture, scope | `docs/delta/` |
| Execution | Running code | `src/`, `services/`, `android/`, `tests/` |
| Operations | Commands, paths, conventions | `AGENTS.md` |
| Process synthesis | Gaps, cycle notes, → Pattern | `continual-learning/` |

**Conflict rule:** execution + Spec Kit win. Update spec or gap-backlog; do not “fix” reference clones in place.

---

## One loop (repeat every slice)

```
LOAD  → rosetta + active spec/plan/tasks + gap-backlog
SLICE → one task / one hook (spec.flow, integration.wiring_audit, …)
PROVE → integration-level command (not docs-only)
AUDIT → tasks.md wiring checklist + wiring-audit record
SPLIT → behavioral → spec · operational → proposals → agents-propose
CLOSE → ci-check · loot-shift → gap-backlog
```

| Step | Agent action |
|------|----------------|
| **LOAD** | Identify `<feature-id>` from `AGENTS.md`; read spec trio. |
| **SLICE** | One micro task; minimal diff. |
| **PROVE** | Run a real command (test, API smoke, build). Capture output. |
| **AUDIT** | Trace UI/CLI → handler → core → persistence; check tasks.md boxes. |
| **RECORD** | `py -3.11 -m hive_planner wiring-audit record --feature <feature-id> --evidence "<cmd>" --summary "<chain traced>"` |
| **SPLIT** | Behavioral learnings → `spec.md` / contracts. Operational → `docs/proposals/*.md`. |
| **PROMOTE** | `agents-propose` / `agents-apply` only after audit passes (operational bullets only). |
| **CLOSE** | `spec-drift-check` + `ci-check`. Append one line to [`loop-log.md`](./loop-log.md). |
| **LOOT SHIFT** | Update [`gap-backlog.md`](./gap-backlog.md): what’s done, what’s next. |

---

## Promotion rules (required)

| Type | Goes to | Never goes to |
|------|---------|----------------|
| Behavioral | `docs/specs/<feature-id>/spec.md` (+ plan/tasks/contracts) | `AGENTS.md`, this file |
| Operational | `AGENTS.md` via `agents-propose --apply` | spec.md |
| Process → Pattern | This file or gap-backlog | spec.md (unless it’s a requirement) |
| User secrets / URLs | App Settings only | repo, AGENTS, spec examples as hardcoded defaults |

---

## Hive commands (repo root)

```powershell
# From repository root (see AGENTS.md)
py -3.11 -m hive_planner spec-drift-check
py -3.11 -m hive_planner wiring-audit record --feature <feature-id> --evidence "<prove-cmd>" --summary "<short audit>"
py -3.11 -m hive_planner wiring-audit check
py -3.11 -m hive_planner agents-propose --candidates docs/proposals/<file>.md
py -3.11 -m hive_planner agents-propose --candidates docs/proposals/<file>.md --apply
py -3.11 -m hive_planner ci-check
```

**Blocked promotion is normal** when: no implementation yet, wiring checklist unchecked, or no integration evidence. Fix the slice; do not `--skip-wiring-audit` unless explicitly directed.

---

## Wiring audit (agent checklist)

Copy per slice; evidence must match a real command:

- [ ] Traced caller → handler → core (no layer break)
- [ ] No false success when a sub-step failed or was skipped
- [ ] Config/flags have read sites on the enabled path
- [ ] Integration-level validation attached (not mock-only)
- [ ] Matching boxes checked in `docs/specs/<feature-id>/tasks.md`

---

## → Pattern (process only)

- Rosetta before Delta before spec before code.
- One task per slice; prove before promote.
- Behavioral in Spec Kit; operational in `AGENTS.md` after audit.
- Reference upstream repos are **read-only** — vendor/refactor into this repo’s execution tree.
- Gap state lives in `gap-backlog.md`; session history in `loop-log.md` — not in spec.

---

## Optional cross-repo patterns (same shape, different product)

If another Hive project exists on the machine, its `continual-learning/learning-loop.md` is interchangeable for **process**. Do not copy product behavior across repos.

| Pattern | When to use |
|---------|-------------|
| Standard loop (this file) | Default for spec + implement + CL |
| Cartridge mine loop | Large reverse-engineer from PDFs/Rosetta (only if `context-library/` ingest exists) |

---

## Related

- [`.hive/spec-kit/WORKFLOW.md`](../.hive/spec-kit/WORKFLOW.md)
- [`.cursor/plugins/hive-continual-learning/WORKFLOW.md`](../.cursor/plugins/hive-continual-learning/WORKFLOW.md)
- [`gap-backlog.md`](./gap-backlog.md) — feature-specific open work (agent-maintained)
- [`loop-log.md`](./loop-log.md) — append-only cycle log
