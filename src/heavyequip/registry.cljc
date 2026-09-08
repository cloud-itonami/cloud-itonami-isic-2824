(ns heavyequip.registry
  "Pure-function unit-dispatch + stability-certificate record
  construction -- an append-only mining/quarrying/construction-
  machinery-plant book-of-record draft.

  Like every sibling actor's registry, there is no single
  international check-digit standard for a unit-dispatch or
  stability-certificate reference number -- every manufacturer/
  jurisdiction assigns its own reference format. This namespace does
  NOT invent one; it builds a jurisdiction-scoped sequence number and
  validates the record's required fields, the same honest, non-
  fabricating discipline `heavyequip.facts` uses.

  `unit-stability-margin-out-of-range?` continues this fleet's two-
  sided range check family (`testlab.registry/within-tolerance?` /
  `conservation.registry/body-condition-out-of-range?` /
  `water.registry/contaminant-level-out-of-range?` /
  `steelworks.registry/heat-chemistry-out-of-range?` /
  `turbine.registry/unit-tolerance-out-of-range?` /
  `automotive.registry/vehicle-emissions-out-of-range?` established
  the priors), applying the SAME lo/hi bounds-comparison shape to a
  unit's own measured ISO 10262 stability-margin deviation against the
  unit's own recorded spec bounds.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real fab/assembly-line control system. It builds the
  RECORD a manufacturer would keep, not the act of dispatching the
  robot unit action or issuing the stability certificate itself
  (that is `heavyequip.operation`'s `:actuation/dispatch-unit`/
  `:actuation/issue-stability-certificate`, always human-gated -- see
  README `Actuation`)."
  (:require [kotoba.lang.text :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is the
  manufacturer's own act, not this actor's. See README `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn unit-stability-margin-out-of-range?
  "Does `unit`'s own `:stability-margin-actual` fall outside its own
  `[:stability-margin-min :stability-margin-max]` recorded spec-
  bounds? A pure ground-truth check against the unit's own permanent
  fields -- no upstream comparison needed. One of this fleet's two-
  sided range check family (see ns docstring)."
  [{:keys [stability-margin-actual stability-margin-min stability-margin-max]}]
  (and (number? stability-margin-actual) (number? stability-margin-min) (number? stability-margin-max)
       (or (< stability-margin-actual stability-margin-min)
           (> stability-margin-actual stability-margin-max))))

(defn register-unit-dispatch
  "Validate + construct the UNIT-DISPATCH registration DRAFT -- the
  manufacturer's own act of dispatching a real robot assembly/
  finishing action to complete an earth-moving machine unit. Pure
  function -- does not touch any real fab/assembly-line control
  system; it builds the RECORD a manufacturer would keep.
  `heavyequip.governor` independently re-verifies the unit's own
  stability-margin sufficiency against its own spec bounds, and a
  double-dispatch for the same unit, before this is ever allowed to
  commit."
  [unit-id jurisdiction sequence]
  (when-not (and unit-id (not= unit-id ""))
    (throw (ex-info "unit-dispatch: unit_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "unit-dispatch: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "unit-dispatch: sequence must be >= 0" {})))
  (let [dispatch-number (str (str/upper jurisdiction) "-HEQ-" (zero-pad sequence 6))
        record {"record_id" dispatch-number
                "kind" "unit-dispatch-draft"
                "unit_id" unit-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "dispatch_number" dispatch-number
     "certificate" (unsigned-certificate "UnitDispatch" dispatch-number dispatch-number)}))

(defn register-stability-certificate
  "Validate + construct the STABILITY-CERTIFICATE registration DRAFT
  -- the manufacturer's own act of issuing a real ISO 3450/10262
  stability-and-braking acceptance certificate certifying a unit as
  release-worthy. Pure function -- does not touch any real fab/
  assembly-line control system; it builds the RECORD a manufacturer
  would keep. `heavyequip.governor` independently re-verifies the
  unit's own stability/braking-test defect resolution status, and a
  double-issuance for the same unit, before this is ever allowed to
  commit."
  [unit-id jurisdiction sequence]
  (when-not (and unit-id (not= unit-id ""))
    (throw (ex-info "stability-certificate: unit_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "stability-certificate: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "stability-certificate: sequence must be >= 0" {})))
  (let [certificate-number (str (str/upper jurisdiction) "-STB-" (zero-pad sequence 6))
        record {"record_id" certificate-number
                "kind" "stability-certificate-draft"
                "unit_id" unit-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "certificate_number" certificate-number
     "certificate" (unsigned-certificate "StabilityCertificate" certificate-number certificate-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
