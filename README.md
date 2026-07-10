# cloud-itonami-isic-2824

Open Business Blueprint for **ISIC Rev.5 2824**: manufacture of
machinery for mining, quarrying and construction -- earth-moving-
machine unit assembly, stability/braking-test screening and
stability-certificate issuance for a community mining/construction-
machinery plant.

This repository publishes a mining/quarrying/construction-machinery-
manufacturing actor -- unit intake, per-jurisdiction machinery
design-rules verification, stability/braking-test-defect screening,
robot unit-dispatch and stability-certificate finalization -- as an
OSS business that any qualified heavy-equipment plant can fork,
deploy, run, improve and sell, so a plant keeps its own construction
and design-rules history instead of renting a closed MES / quality
SaaS.

Built on this workspace's
[`langgraph`](https://github.com/kotoba-lang/langgraph)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
every prior actor in this fleet -- here it is **Heavy Equipment
Advisor ⊣ Heavy Equipment Governor**.

## Scope note: manufacturing, not machine operation

This repository is scoped to **building** mining/quarrying/
construction machinery (unit assembly, stability/braking testing,
design-rules evidence). It is not a fleet-operator, rental or
job-site vertical (equipment rental, jobsite dispatch, maintenance
contracting). Distinct from:

- `cloud-itonami-isic-2410` — basic iron and steel **manufacturing**
- `cloud-itonami-isic-2811` — engines and turbines **manufacturing**
- `cloud-itonami-isic-2822` — metal-forming machinery and machine tools **manufacturing**
- `cloud-itonami-isic-2910` — motor vehicles **manufacturing**
- `cloud-itonami-isic-3011` — ships and floating structures **manufacturing**

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here robots (assembly, fit-up,
finishing, stability/braking-test scan) operate under an actor that
proposes actions and an independent **Heavy Equipment Governor** that
gates them. The governor never issues a stability certificate itself;
`:high`/`:safety-critical` actions (`:actuation/dispatch-unit`,
`:actuation/issue-stability-certificate`) require human sign-off.

## Core contract

```text
unit intake + design-rules verify + stability/brake-test screen
  -> Heavy Equipment Advisor proposal
  -> Heavy Equipment Governor (HARD holds un-overridable)
  -> phase gate (actuation always escalates)
  -> human approval for high stakes
  -> append-only ledger + draft records
```

## Actuation honesty

Dispatching an assembly/finishing robot and issuing a stability
certificate produce **unsigned draft records and ledger facts only**.
This actor does not talk to real plant control systems or
type-approval portals. Signature and hardware dispatch are the
heavy-equipment plant's own acts.

## Ops

| Op | Effect |
|---|---|
| `:unit/intake` | normalize unit directory patch (phase 3 may auto-commit when clean) |
| `:design-rules/verify` | per-jurisdiction stability-certificate evidence checklist (always human) |
| `:stability-brake-test/screen` | ISO 3450/10262 stability/braking defect screen (HARD hold if unresolved) |
| `:actuation/dispatch-unit` | draft unit-dispatch record (always human) |
| `:actuation/issue-stability-certificate` | draft stability-certificate record (always human) |

## Social / regulatory hand-off

```clojure
(require '[heavyequip.store :as store]
         '[heavyequip.export :as export])

(def db (store/seed-db))
(export/audit-package db)           ;; EDN maps for type-approval/flag hand-off
(export/package->csv-bundle db)     ;; CSV bundle (units/ledger/dispatches/stability-certificates)
```

Operator console (static sample): `docs/samples/operator-console.html`.

## Develop

```bash
clojure -M:dev:test
clojure -M:lint
clojure -M:dev:run
```

## License

AGPL-3.0-or-later — see `LICENSE`.

## Operator console (Pages)

After enabling GitHub Pages (Settings → Pages → GitHub Actions), the
static console is at:

https://cloud-itonami.github.io/cloud-itonami-isic-2824/

Local: open `docs/index.html` or `docs/samples/operator-console.html`.

## Export audit package (CLI)

```bash
clojure -M:dev:export
# or: clojure -M:dev:export /tmp/audit-2824
```

Writes CSV files under `out/audit-package/` (or the given directory).
