# Operator Guide

## First Deployment
1. Register heavy-equipment manufacturing engineers, plants, units, personnel and robots.
2. Import historical unit / stability-brake-test / design-rules records.
3. Run read-only validation and robot mission dry-runs.
4. Configure design-rules evidence checklists and human sign-off paths.
5. Publish a dry-run audit export.

## Minimum Production Controls
- governor gate on every robot action before dispatch
- human sign-off for `:high`/`:safety-critical` robot actions (e.g. assembly/finishing on safety-critical units, stability-certificate issuance)
- audit export for every dispatch, sign-off and disclosure
- backup manual process

## Certification
Certified operators must prove robot-safety integrity, evidence-backed
records and human review for safety-affecting actions.

## Operating states
intake : design-rules-verify : stability-brake-test-screen : approve : dispatch-unit : issue-stability-certificate : audit

## Audit export (social operation)

After a production session, export the append-only package for
type-approval inspectors or internal compliance:

```clojure
(require '[heavyequip.store :as store]
         '[heavyequip.export :as export])
(export/audit-package store)        ; EDN maps
(export/package->csv-bundle store)  ; CSV files as string map
```

Drafts remain **unsigned** — signing and submission to a type-approval
authority are the heavy-equipment manufacturer's own acts (see README
Actuation honesty).

Static UI sample: `docs/samples/operator-console.html`.
