# ADR-0001: Heavy Equipment Advisor ⊣ Heavy Equipment Governor architecture

- Status: Accepted (2026-07-10)
- Repository: `cloud-itonami-isic-2824` (ISIC Rev.5 `2824`)

## Context

Mining/quarrying/construction-machinery manufacturing (unit assembly,
stability/braking-test inspection, machinery design-rules conformity,
stability-certificate issuance) needs the same governed-actor pattern
as the rest of the cloud-itonami fleet: an untrusted advisor proposes;
an independent governor may HOLD; high-stakes actuation never
auto-commits.

This vertical continues the classic heavy-industry manufacturing
cluster alongside `cloud-itonami-isic-2410` (basic iron and steel),
`cloud-itonami-isic-2811` (engines and turbines) and
`cloud-itonami-isic-2910` (motor vehicles), all descending from
`cloud-itonami-isic-3030` (aerospace), the first manufacturing-sector
full actor in this fleet. Within the capital-equipment sub-cluster of
ISIC division C28 "machinery" it sits alongside
`cloud-itonami-isic-2822` (metal-forming machinery and machine
tools), distinct from the transport-equipment sub-cluster
(`2811`/`2910`/`3011` shipbuilding/`3030`) and from
`cloud-itonami-isic-2511` (structural metal products, a fabrication
rather than capital-equipment vertical).

## Decision

1. Namespaces live under `heavyequip.*` with the standard
   facts / registry / store / governor / phase / advisor / operation / sim
   shape.
2. Entity is a **unit** (an excavator, bulldozer, wheel loader, dump
   truck or drilling rig), not a vehicle, hull block or steel heat.
3. Dual actuation on the same entity:
   - `:actuation/dispatch-unit` (robot assembly/finishing dispatch draft)
   - `:actuation/issue-stability-certificate` (ISO 3450/10262
     stability-and-braking acceptance certificate draft)
4. Double-actuation guards use dedicated booleans
   (`:unit-dispatched?`, `:stability-certified?`), never a status
   lifecycle (ADR-2607071320 / 6492 lesson).
5. `unit-stability-margin-out-of-range?` continues the fleet two-sided
   range check family (after testlab / conservation / water /
   steelworks / turbine / automotive), applied here to a unit's own
   measured ISO 10262 stability-margin deviation against its own
   recorded spec bounds.
6. Stability/braking-test defect unresolved is evaluated
   unconditionally so `:stability-brake-test/screen` itself can
   HARD-hold (parksafety ADR-2607071922 Decision 5 discipline).
7. Spec-basis catalog seeds JPN (METI/MLIT/JIS A 8403 · A 8411) / USA
   (OSHA 29 CFR 1926 / ANSI-SAE J1040 ROPS) / GBR (HSE/UKCA BS EN 474)
   / DEU (EU Machinery Directive 2006/42/EC, EN 474) only; missing
   jurisdictions are uncovered, never fabricated.

## Consequences

(+) Mining/quarrying/construction-machinery manufacturing gains a
forkable OSS operating stack with auditable governor holds.
(+) Reuses langgraph + store dual-backend parity without new physics.
(−) No physical plant digital-twin tick in this repo (follow-up domain
data, e.g. giemon-factory style layout, is out of scope here).
(−) Design-rules-authority coverage is a starting catalog, not
exhaustive.

## Related

- Superproject fleet ADR for this promotion (heavy-industry-2824-coverage)
- Sibling architecture: `cloud-itonami-isic-2410` docs/adr/0001,
  `cloud-itonami-isic-2811` docs/adr/0001,
  `cloud-itonami-isic-2910` docs/adr/0001
