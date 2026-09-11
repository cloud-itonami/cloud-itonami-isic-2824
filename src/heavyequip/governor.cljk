(ns heavyequip.governor
  "Heavy Equipment Governor -- the independent compliance layer
  that earns the Heavy Equipment Advisor the right to commit. The LLM has
  no notion of machinery design-rules law, whether a unit's own
  measured stability-margin deviation actually stays within its own
  recorded spec bounds, whether a stability/braking-test-detected
  defect against the unit has actually stayed unresolved, or when an
  act stops being a draft and becomes a real-world robot unit dispatch
  or stability-certificate issuance, so this MUST be a separate system
  able to *reject* a proposal and fall back to HOLD -- the mining/
  quarrying/construction-machinery-plant analog of
  `cloud-itonami-isic-6512`'s CasualtyGovernor.

  Six checks, in priority order, ALL HARD violations: a human approver
  CANNOT override them (you don't get to approve your way past a
  fabricated design-rules spec-basis, incomplete evidence, an out-of-
  spec unit, an unresolved stability/braking-test defect, or a double
  dispatch/certificate-issuance). The confidence/actuation gate is
  SOFT: it asks a human to look (low confidence / actuation), and the
  human may approve -- but see `heavyequip.phase`: for `:stake
  :actuation/dispatch-unit`/`:actuation/issue-stability-certificate`
  (a real safety-critical act) NO phase ever allows auto-commit
  either. Two independent layers agree that actuation is always a
  human call.

    1. Spec-basis                  -- did the requirements proposal cite
                                       an OFFICIAL source (`heavyequip.
                                       facts`), or invent one?
    2. Evidence incomplete         -- for `:actuation/dispatch-
                                       unit`/`:actuation/issue-
                                       stability-certificate`, has the
                                       unit actually been verified
                                       with a full CAE-simulation-
                                       report/ISO-10262-stability-test-
                                       report/ISO-3450-braking-test-
                                       report/material-certification-
                                       record evidence checklist on file?
    3. Unit stability-margin out of
       range                         -- for `:actuation/dispatch-
                                       unit`, INDEPENDENTLY
                                       recompute whether the
                                       unit's own measured
                                       stability-margin deviation
                                       falls outside its own recorded
                                       spec bounds (`heavyequip.
                                       registry/unit-stability-margin-
                                       out-of-range?`) -- needs no
                                       proposal inspection or stored-
                                       verdict lookup at all. One of
                                       this fleet's two-sided range
                                       check family (`testlab.
                                       governor/within-tolerance-
                                       violations`/`conservation.
                                       governor/body-condition-out-of-
                                       range-violations`/`water.
                                       governor/contaminant-level-out-
                                       of-range-violations`/
                                       `steelworks.governor`/`turbine.
                                       governor`/`automotive.governor`
                                       established the priors).
    4. Stability/braking-test defect
       unresolved                    -- reported by THIS proposal
                                       itself (a `:stability-brake-
                                       test/screen` that just found an
                                       unresolved defect), or already
                                       on file for the unit
                                       (`:stability-brake-test/
                                       screen`/`:actuation/issue-
                                       stability-certificate`).
                                       Evaluated UNCONDITIONALLY (not
                                       scoped to a specific op), the
                                       SAME discipline `casualty.
                                       governor/sanctions-violations`/
                                       ...(prior siblings)...
                                       established -- exercised in
                                       tests/demo via `:stability-
                                       brake-test/screen` DIRECTLY, not
                                       via an actuation op against an
                                       unscreened unit -- see this ns's
                                       own test suite.
    5. Confidence floor / actuation
       gate                          -- LLM confidence below threshold,
                                       OR the op is `:actuation/
                                       dispatch-unit`/`:actuation/
                                       issue-stability-certificate`
                                       (REAL safety-critical acts) ->
                                       escalate.

  Two more guards, double-dispatch/double-certificate-issuance
  prevention, are enforced but NOT listed as numbered HARD checks
  above because they need no upstream comparison at all --
  `already-dispatched-violations`/`already-certified-violations`
  refuse to dispatch a unit action/issue a stability certificate
  for the SAME unit twice, off dedicated `:unit-dispatched?`/
  `:stability-certified?` facts (never a `:status` value) -- the
  SAME 'check a dedicated boolean, not status' discipline every prior
  sibling governor's guards establish, informed by `cloud-itonami-
  isic-6492`'s status-lifecycle bug (ADR-2607071320)."
  (:require [heavyequip.facts :as facts]
            [heavyequip.registry :as registry]
            [heavyequip.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Dispatching a real robot unit action on a safety-critical earth-
  moving machine and issuing a real stability certificate are the two
  real-world actuation events this actor performs -- a two-member set,
  matching every prior dual-actuation sibling's shape."
  #{:actuation/dispatch-unit :actuation/issue-stability-certificate})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:design-rules/verify` (or actuation) proposal with no spec-basis
  citation is a HARD violation -- never invent a jurisdiction's
  design-rules requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:design-rules/verify :actuation/dispatch-unit :actuation/issue-stability-certificate} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案は設計規則要件として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:actuation/dispatch-unit`/`:actuation/issue-stability-
  certificate`, the jurisdiction's required CAE-simulation-report/
  ISO-10262-stability-test-report/ISO-3450-braking-test-report/
  material-certification-record evidence must actually be satisfied
  -- do not trust the advisor's self-reported confidence alone."
  [{:keys [op subject]} st]
  (when (contains? #{:actuation/dispatch-unit :actuation/issue-stability-certificate} op)
    (let [a (store/unit st subject)
          verification (store/requirements-verification-of st subject)]
      (when-not (and verification
                     (facts/required-evidence-satisfied?
                      (:jurisdiction a) (:checklist verification)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(CAEシミュレーション報告書/ISO10262安定性試験報告書/ISO3450制動性能試験報告書/材料証明記録等)が充足していない状態での提案"}]))))

(defn- unit-stability-margin-out-of-range-violations
  "For `:actuation/dispatch-unit`, INDEPENDENTLY recompute whether the
  unit's own stability-margin deviation falls outside its own
  recorded spec bounds via `heavyequip.registry/unit-stability-
  margin-out-of-range?` -- needs no proposal inspection or stored-
  verdict lookup at all, since its inputs are permanent ground-truth
  fields already on the unit."
  [{:keys [op subject]} st]
  (when (= op :actuation/dispatch-unit)
    (let [a (store/unit st subject)]
      (when (registry/unit-stability-margin-out-of-range? a)
        [{:rule :unit-stability-margin-out-of-range
          :detail (str subject " の実測安定性マージン偏差(" (:stability-margin-actual a)
                      ")が仕様範囲[" (:stability-margin-min a) "," (:stability-margin-max a) "]を逸脱")}]))))

(defn- stability-brake-test-defect-unresolved-violations
  "An unresolved stability/braking-test-detected defect -- reported by
  THIS proposal (e.g. a `:stability-brake-test/screen` that itself
  just found one), or already on file in the store for the unit
  (`:stability-brake-test/screen`/`:actuation/issue-stability-
  certificate`) -- is a HARD, un-overridable hold. Evaluated
  UNCONDITIONALLY (not scoped to a specific op) so the screening op
  itself can HARD-hold on its own finding."
  [{:keys [op subject]} proposal st]
  (let [hit-in-proposal? (= :unresolved (get-in proposal [:value :verdict]))
        unit-id (when (contains? #{:stability-brake-test/screen :actuation/issue-stability-certificate} op) subject)
        hit-on-file? (and unit-id (= :unresolved (:verdict (store/stability-brake-screen-of st unit-id))))]
    (when (or hit-in-proposal? hit-on-file?)
      [{:rule :stability-brake-test-defect-unresolved
        :detail "未解決の安定性/制動試験欠陥がある状態での安定性証明書発行提案は進められない"}])))

(defn- already-dispatched-violations
  "For `:actuation/dispatch-unit`, refuses to dispatch a unit
  action for the SAME unit twice, off a dedicated `:unit-
  dispatched?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :actuation/dispatch-unit)
    (when (store/unit-already-dispatched? st subject)
      [{:rule :already-dispatched
        :detail (str subject " は既に実行済み")}])))

(defn- already-certified-violations
  "For `:actuation/issue-stability-certificate`, refuses to issue a
  stability certificate for the SAME unit twice, off a dedicated
  `:stability-certified?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :actuation/issue-stability-certificate)
    (when (store/unit-already-certified? st subject)
      [{:rule :already-certified
        :detail (str subject " は既に安定性証明書発行済み")}])))

(defn check
  "Censors a Heavy Equipment Advisor proposal against the governor
  rules. Returns {:ok? bool :violations [..] :confidence c :escalate?
  bool :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (unit-stability-margin-out-of-range-violations request st)
                           (stability-brake-test-defect-unresolved-violations request proposal st)
                           (already-dispatched-violations request st)
                           (already-certified-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
