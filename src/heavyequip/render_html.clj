(ns heavyequip.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 for this repo: `docs/samples/
  operator-console.html` previously existed as a HAND-TYPED snapshot
  with no generator behind it, so nothing kept it honest. This
  namespace drives the REAL actor stack (`heavyequip.operation` ->
  `heavyequip.governor` -> `heavyequip.phase` -> `heavyequip.store`)
  through a scenario built on this repo's own seeded unit ids
  (`unit-1`..`unit-4`, `heavyequip.store/demo-data`), then renders the
  page from what the run actually produced.

  Everything on the page is derived:
    - unit rows            <- `store/all-units` + the run's ledger
    - governor holds       <- the `:governor-hold` facts the run emitted
    - phase gate rows      <- `phase/gate` and `governor/high-stakes`
                              CALLED at render time, not transcribed
    - jurisdiction rows    <- `facts/catalog` + `facts/coverage`
    - registry drafts      <- `store/dispatch-history` /
                              `store/evidence-history`
    - approval attribution <- a render-time SCAN of the committed store
                              surfaces for an approver key (see
                              `approval-attribution`)
    - audit ledger         <- `store/ledger`

  No timestamps, no invented numbers, no hand-typed rows: the output is
  byte-identical across reruns against the same seed.

  `-main` REFUSES to write the page when the run produced zero
  `:governor-hold` facts. A console that cannot show the governor
  refusing something is not evidence of a governor, so the HARD-hold
  requirement is a build-time invariant here rather than a convention.

  Styling is a small self-contained stylesheet inlined below: no
  vendored CSS and no extra dependency, so the build stays offline and
  reproducible with only the deps this repo already had.

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [clojure.string :as str]
            [heavyequip.facts :as facts]
            [heavyequip.governor :as governor]
            [heavyequip.operation :as op]
            [heavyequip.phase :as phase]
            [heavyequip.registry :as registry]
            [heavyequip.store :as store]
            [langgraph.graph :as g]))

(def ^:private operator
  {:actor-id "op-1" :actor-role :heavyequip-engineer :phase phase/default-phase})

(def ^:private approver-id "op-1")

;; ----------------------------- the real run -----------------------------

(defn run-demo!
  "Runs a fresh seeded store through a scenario that reaches every
  disposition this actor can produce, and returns
  `{:db store :approvals [..]}`.

  `unit-1` (JPN, measured stability-margin deviation 0.05 inside its
  own recorded [-0.10,0.10] spec bounds, no stability/braking-test
  defect) clears a FULL lifecycle: intake (auto-commits at phase 3 --
  the only op any phase ever lets through unattended), design-rules
  verification (phase-gated, human-approved), stability/braking-test
  screening (approved), the unit-dispatch actuation (ALWAYS escalates
  -- never auto at any phase -- approved) and the stability-certificate
  actuation (same posture, approved).

  The other units drive each of the Heavy Equipment Governor's six HARD
  checks to a hold, none of which a human is ever offered the chance to
  override:
    unit-2  design-rules verification for a jurisdiction with no
            official spec-basis on file  -> :no-spec-basis
    unit-2  unit dispatch with no verified evidence checklist on file
                                         -> :evidence-incomplete
    unit-3  unit dispatch, 0.35 measured stability-margin deviation
            outside its own recorded [-0.10,0.10] bounds
                                         -> :unit-stability-margin-out-of-range
    unit-4  stability/braking-test screening that itself finds an
            unresolved defect
                              -> :stability-brake-test-defect-unresolved
    unit-1  a SECOND unit dispatch       -> :already-dispatched
    unit-1  a SECOND stability certificate -> :already-certified

  `:approvals` records, per resumed run, what the human actually
  approved (op / subject / approver / the effect the commit wrote).
  That is the ground truth the attribution scan below is compared
  against -- the page never assumes an approval happened."
  []
  (let [db       (store/seed-db)
        actor    (op/build db)
        approved (atom [])
        exec!    (fn [tid request]
                   (g/run* actor {:request request :context operator}
                           {:thread-id tid}))
        approve! (fn [tid]
                   (let [r  (g/run* actor
                                    {:approval {:status :approved :by approver-id}}
                                    {:thread-id tid :resume? true})
                         st (:state r)]
                     (swap! approved conj
                            {:op      (get-in st [:request :op])
                             :subject (get-in st [:request :subject])
                             :by      (some :by (filter #(= :approval-granted (:t %))
                                                        (:audit st)))
                             :effect  (get-in st [:record :effect])
                             :path    (get-in st [:record :path])})
                     r))]

    ;; -- unit-1: full lifecycle, every write a human signed off on -----
    (exec! "t1-intake" {:op      :unit/intake
                        :subject "unit-1"
                        :patch   {:id "unit-1"
                                  :unit-name "Sakura Hydraulic Excavator EX-220"}})

    (exec!    "t1-verify" {:op :design-rules/verify :subject "unit-1"})
    (approve! "t1-verify")

    (exec!    "t1-screen" {:op :stability-brake-test/screen :subject "unit-1"})
    (approve! "t1-screen")

    (exec!    "t1-dispatch" {:op :actuation/dispatch-unit :subject "unit-1"})
    (approve! "t1-dispatch")

    (exec!    "t1-certificate" {:op :actuation/issue-stability-certificate
                                :subject "unit-1"})
    (approve! "t1-certificate")

    ;; -- unit-2: no official spec-basis, then no evidence on file ------
    (exec! "t2-verify"   {:op :design-rules/verify :subject "unit-2" :no-spec? true})
    (exec! "t2-dispatch" {:op :actuation/dispatch-unit :subject "unit-2"})

    ;; -- unit-3: evidence IS on file, but the unit is out of spec ------
    (exec!    "t3-verify" {:op :design-rules/verify :subject "unit-3"})
    (approve! "t3-verify")
    (exec!    "t3-dispatch" {:op :actuation/dispatch-unit :subject "unit-3"})

    ;; -- unit-4: the screening op HARD-holds on its own finding --------
    (exec! "t4-screen" {:op :stability-brake-test/screen :subject "unit-4"})

    ;; -- unit-1 again: both double-actuation guards -------------------
    (exec! "t1-dispatch-again"    {:op :actuation/dispatch-unit :subject "unit-1"})
    (exec! "t1-certificate-again" {:op :actuation/issue-stability-certificate
                                   :subject "unit-1"})

    {:db db :approvals @approved}))

;; ------------------------- approver attribution -------------------------
;;
;; Deliberately a RENDER-TIME MEASUREMENT, not a hard-coded sentence.
;; `heavyequip.operation` attaches the approver to the committed
;; record's `:payload`; whether that survives into the SSoT is up to
;; each `commit-record!` effect branch. Scanning for the key here means
;; the page reports whatever is true of the code it was built from --
;; if the store is ever changed to keep the approver, this section
;; starts saying so on the next build with no edit to this file.

(defn- approver-key-in
  "Any key on `m` that names an approver, keyword or string, `-` or `_`.
  Returns [k v] or nil. Deterministic: lowest key name wins."
  [m]
  (when (map? m)
    (->> m
         (filter (fn [[k v]]
                   (and (some? v)
                        (str/includes? (str/lower-case (str/replace (str k) "_" "-"))
                                       "approv"))))
         (sort-by (comp str key))
         first)))

(defn- surfaces-written-by
  "The store surfaces an approved commit of `effect` on `subject`
  actually wrote, as [label map] pairs, looked up back out of the live
  store. These are the places an approver identity could have landed."
  [db effect subject]
  (case effect
    :unit/upsert
    [["unit record" (store/unit db subject)]]

    :verification/set
    [["requirements verification" (store/requirements-verification-of db subject)]]

    :stability-brake-screen/set
    [["stability/brake screening" (store/stability-brake-screen-of db subject)]]

    :unit/mark-dispatched
    [["unit record" (store/unit db subject)]
     ["unit-dispatch draft"
      (first (filter #(= subject (get % "unit_id")) (store/dispatch-history db)))]]

    :unit/mark-certified
    [["unit record" (store/unit db subject)]
     ["stability-certificate draft"
      (first (filter #(= subject (get % "unit_id")) (store/evidence-history db)))]]

    []))

(defn- ledger-approver-for
  "Does the append-only ledger itself carry the approver for this
  op/subject? Scanned, not assumed."
  [db op subject]
  (some (fn [f]
          (when (and (= op (:op f)) (= subject (:subject f)))
            (approver-key-in f)))
        (store/ledger db)))

(defn approval-attribution
  "For every approval the run actually granted, measure whether the
  approver identity can still be answered from the store afterwards.
  Returns a seq of rows; `:retained?` is measured, never assumed."
  [db approvals]
  (for [{:keys [op subject by effect]} approvals]
    (let [surfaces (remove (comp nil? second) (surfaces-written-by db effect subject))
          hits     (keep (fn [[label m]]
                           (when-let [[k v] (approver-key-in m)]
                             {:surface label :key k :value v}))
                         surfaces)
          ledger   (ledger-approver-for db op subject)]
      {:op        op
       :subject   subject
       :granted-to by
       :effect    effect
       :surfaces  (mapv first surfaces)
       :hits      (vec hits)
       :ledger?   (boolean ledger)
       :retained? (boolean (seq hits))})))

;; ----------------------------- html helpers -----------------------------
;;
;; Convention, so nothing is ever escaped twice: `esc` is applied to RAW
;; TEXT exactly once, at the point the text enters the document. Helpers
;; named `*-cell` return finished HTML and are interpolated verbatim.
;; Literal UTF-8 characters are used instead of named entities (the
;; document declares charset=utf-8), which keeps `&` almost absent from
;; the output and makes a double-escape regression visible.

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")))

(defn- kw->s
  "Full keyword including namespace -- `:actuation/dispatch-unit`, not
  the `name`-only `dispatch-unit` a reader could not act on."
  [k]
  (if (keyword? k) (subs (str k) 1) (str k)))

(defn- code [v] (str "<code>" (esc v) "</code>"))
(defn- span [cls v] (str "<span class=\"" cls "\">" (esc v) "</span>"))
(defn- row [& cells] (str "        <tr>" (str/join (map #(str "<td>" % "</td>") cells)) "</tr>"))
(defn- yes-no [b] (if b (span "ok" "yes") (span "muted" "no")))

(defn- section [title lede headers rows]
  (str "  <section class=\"card\">\n"
       "    <h2>" (esc title) "</h2>\n"
       "    <p class=\"muted\">" lede "</p>\n"
       "    <table>\n"
       "      <thead><tr>"
       (str/join (map #(str "<th>" (esc %) "</th>") headers))
       "</tr></thead>\n"
       "      <tbody>\n"
       (str/join "\n" rows) "\n"
       "      </tbody>\n"
       "    </table>\n"
       "  </section>\n"))

;; ----------------------------- sections -----------------------------

(defn- last-fact-for [ledger unit-id]
  (last (filter #(= unit-id (:subject %)) ledger)))

(defn- status-cell [ledger unit-id]
  (let [f (last-fact-for ledger unit-id)]
    (cond
      (nil? f) (span "muted" "no activity")
      (= :governor-hold (:t f))
      (str (span "critical" "HARD hold")
           " " (code (kw->s (or (-> f :violations first :rule) :unknown))))
      (= :committed (:t f)) (span "ok" "committed")
      :else (span "muted" "in progress"))))

(defn- margin-cell [{:keys [stability-margin-actual stability-margin-min stability-margin-max]
                     :as unit}]
  (let [txt (str stability-margin-actual
                 " ∈ [" stability-margin-min "," stability-margin-max "]")]
    ;; The SAME predicate the governor uses, called here -- not a copy
    ;; of its conclusion.
    (if (registry/unit-stability-margin-out-of-range? unit)
      (span "critical" (str stability-margin-actual
                            " ∉ [" stability-margin-min "," stability-margin-max "]"))
      (span "ok" txt))))

(defn- lifecycle-cell [{:keys [unit-dispatched? stability-certified?
                               dispatch-number evidence-number]}]
  (cond
    (and unit-dispatched? stability-certified?)
    (str (span "ok" "dispatched + certified") " " (code (str dispatch-number " / " evidence-number)))
    unit-dispatched? (str (span "warn" "dispatched, not certified") " " (code dispatch-number))
    stability-certified? (str (span "warn" "certified, not dispatched") " " (code evidence-number))
    :else (span "muted" "not actuated")))

(defn- unit-rows [db ledger]
  (for [{:keys [id unit-name jurisdiction stability-brake-test-defect-unresolved?] :as u}
        (store/all-units db)]
    (row (code id)
         (esc unit-name)
         (esc jurisdiction)
         (margin-cell u)
         (if stability-brake-test-defect-unresolved?
           (span "critical" "unresolved")
           (span "ok" "none on record"))
         (lifecycle-cell u)
         (status-cell ledger id))))

(defn- hold-rows [ledger]
  (for [{:keys [op subject violations confidence]}
        (filter #(= :governor-hold (:t %)) ledger)
        {:keys [rule detail]} violations]
    (row (code (kw->s op))
         (code subject)
         (span "critical" (kw->s rule))
         (esc detail)
         (esc confidence))))

(defn- gate-rows []
  (let [ph phase/default-phase
        {:keys [label]} (get phase/phases ph)]
    (for [op (sort-by str phase/write-ops)]
      ;; `phase/gate` is CALLED, so this table cannot drift from the
      ;; rollout policy the actor actually enforces.
      (let [clean (phase/gate ph {:op op} :commit)
            held  (phase/gate ph {:op op} :hold)
            stakes? (contains? governor/high-stakes op)]
        (row (code (kw->s op))
             (esc label)
             (case (:disposition clean)
               :commit   (span "ok" "auto-commit")
               :escalate (span "warn" (str "human approval"
                                           (when-let [r (:reason clean)]
                                             (str " · " (kw->s r)))))
               (span "critical" (str "hold"
                                     (when-let [r (:reason clean)]
                                       (str " · " (kw->s r))))))
             (span "critical" (kw->s (:disposition held)))
             (if stakes?
               (span "critical" "always human · never auto at any phase")
               (span "muted" "—")))))))

(defn- jurisdiction-rows [db]
  (let [units (store/all-units db)
        used  (frequencies (keep :jurisdiction units))
        iso3s (sort (distinct (concat (keys facts/catalog) (keys used))))]
    (for [iso3 iso3s]
      (let [sb (facts/spec-basis iso3)]
        (row (code iso3)
             (if sb (esc (:name sb)) (span "critical" "no spec-basis on file"))
             (if sb (esc (:owner-authority sb)) (span "muted" "—"))
             (if sb (esc (count (:required-evidence sb))) (span "muted" "0"))
             (esc (get used iso3 0)))))))

(defn- registry-rows [db]
  (for [r (concat (store/dispatch-history db) (store/evidence-history db))]
    (row (code (get r "record_id"))
         (esc (get r "kind"))
         (code (get r "unit_id"))
         (esc (get r "jurisdiction"))
         (yes-no (get r "immutable")))))

(defn- attribution-rows [rows]
  (for [{:keys [op subject granted-to surfaces hits ledger? retained?]} rows]
    (row (code (kw->s op))
         (code subject)
         (esc granted-to)
         (esc (str/join ", " surfaces))
         (if retained?
           (span "ok" (str/join ", " (map #(str (kw->s (:key %)) "=" (:value %)) hits)))
           (span "critical" "not retained by any surface"))
         (if ledger? (span "ok" "yes") (span "critical" "no")))))

(defn- attribution-lede
  "The disclosure sentence, DERIVED from the scan above. If the store is
  ever changed to keep the approver, this text changes by itself."
  [rows]
  (let [n     (count rows)
        kept  (count (filter :retained? rows))
        lost  (remove :retained? rows)
        ledg  (count (filter :ledger? rows))]
    (str "This run granted " n " human approval"
         (when (not= 1 n) "s") ", each by "
         (code approver-id) ". Measured at render time by scanning the committed store "
         "surfaces for a key naming an approver: "
         (esc kept) " of " (esc n)
         " kept the approver identity in the SSoT, and "
         (esc ledg) " of " (esc n)
         " can be answered from the append-only ledger."
         (when (seq lost)
           (str " For "
                (str/join ", " (map #(code (kw->s (:effect %))) (distinct (map #(select-keys % [:effect]) lost))))
                " the committed record does <strong>not</strong> retain who approved it — "
                "<code>heavyequip.operation</code> does attach "
                (code ":approved-by") " to the record's " (code ":payload")
                ", but those <code>commit-record!</code> branches re-draft the record "
                "from <code>heavyequip.registry</code> and read neither "
                (code ":payload") " nor " (code ":value")
                ". The ledger does not close the gap either: the <code>:approval-granted</code> "
                "fact exists only in the graph run's <code>:audit</code> channel and is never "
                "appended by the <code>:commit</code> node, so after the run ends "
                "&quot;who approved this dispatch&quot; is unanswerable from this store. "
                "Stated rather than patched: changing commit semantics is a governance "
                "decision with its own contract tests."))
         (when (empty? lost)
           " Every approval is attributable from the store."))))

;; ----------------------------- document -----------------------------

(def ^:private stylesheet "
:root { --ink:#1a1a1a; --muted:#6b6b6b; --line:#e5e5e5; --bg:#fafafa;
        --ok:#137a3f; --ok-bg:#e8f5ec; --warn:#8a5300; --warn-bg:#fff8e1;
        --crit:#b3261e; --crit-bg:#fbe9e7; }
* { box-sizing: border-box; }
body { font-family: system-ui,-apple-system,'Hiragino Sans','Noto Sans JP',sans-serif;
       margin:0; color:var(--ink); background:var(--bg); line-height:1.55; }
header.bar { display:flex; align-items:baseline; gap:12px; flex-wrap:wrap;
             padding:14px 20px; background:#fff; border-bottom:1px solid var(--line); }
header.bar h1 { font-size:18px; margin:0; font-weight:650; letter-spacing:.01em; }
header.bar .badge { margin-left:auto; font-size:12px; color:var(--muted); }
main { max-width:1100px; margin:24px auto; padding:0 20px; }
.card { background:#fff; border:1px solid var(--line); border-radius:10px;
        padding:16px 18px; margin-bottom:16px; }
h2 { margin:0 0 6px; font-size:15px; font-weight:650; }
p.muted { color:var(--muted); font-size:13px; margin:0 0 12px; }
table { width:100%; border-collapse:collapse; font-size:13px; }
th { text-align:left; padding:6px 10px; border-bottom:1px solid var(--line);
     font-size:11px; font-weight:650; color:var(--muted);
     text-transform:uppercase; letter-spacing:.05em; white-space:nowrap; }
td { text-align:left; padding:7px 10px; border-bottom:1px solid #f2f2f2;
     vertical-align:top; }
tr:last-child td { border-bottom:none; }
code { font-family:ui-monospace,SFMono-Regular,Menlo,monospace; font-size:12px;
       background:#f4f4f5; padding:1px 5px; border-radius:4px; white-space:nowrap; }
.ok { color:var(--ok); }
.muted { color:var(--muted); }
.warn { color:var(--warn); background:var(--warn-bg); padding:1px 6px; border-radius:4px; }
.critical { color:#fff; background:var(--crit); padding:1px 6px; border-radius:4px;
            font-weight:600; }
footer { max-width:1100px; margin:0 auto 40px; padding:0 20px;
         color:var(--muted); font-size:12px; }
")

(defn render
  "Renders the whole document from `{:db .. :approvals ..}` as returned
  by `run-demo!` (or any other real scenario against this actor)."
  [{:keys [db approvals]}]
  (let [ledger (vec (store/ledger db))
        holds  (filter #(= :governor-hold (:t %)) ledger)
        rules  (sort (distinct (map :rule (mapcat :violations holds))))
        attrib (approval-attribution db approvals)
        cov    (facts/coverage (distinct (keep :jurisdiction (store/all-units db))))]
    (str
     "<!doctype html>\n"
     "<html lang=\"en\"><head><meta charset=\"utf-8\">\n"
     "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
     "<title>cloud-itonami-isic-2824 · heavy-equipment plant · Operator Console</title>\n"
     "<style>" stylesheet "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Mining, quarrying &amp; construction machinery manufacture (ISIC 2824) — Operator Console</h1>\n"
     "  <span class=\"badge\">generated · governor-gated · never dispatches hardware</span>\n"
     "</header>\n"
     "<main>\n"

     (section "Units"
              (str "Every row is the live record in <code>heavyequip.store</code> after the run. "
                   "The stability-margin column calls "
                   "<code>heavyequip.registry/unit-stability-margin-out-of-range?</code> — the same "
                   "predicate the governor uses — rather than restating its conclusion.")
              ["Unit" "Name" "Jurisdiction" "Stability margin vs spec"
               "Stability/brake defect" "Actuation lifecycle" "Last decision"]
              (unit-rows db ledger))

     (section (str "Governor HARD holds (" (count holds) " in this run, "
                   (count rules) " distinct rules)")
              (str "Un-overridable refusals: a human approver is never offered these. "
                   "Each row is a <code>:governor-hold</code> fact the run actually emitted, "
                   "with the detail string the governor itself wrote.")
              ["Op" "Unit" "Rule" "Detail" "Advisor confidence"]
              (hold-rows ledger))

     (section (str "Action gate — phase " phase/default-phase
                   " (" (:label (get phase/phases phase/default-phase)) ")")
              (str "Derived by calling <code>heavyequip.phase/gate</code> for each op in "
                   "<code>phase/write-ops</code>, so this table cannot drift from the policy "
                   "the actor enforces. The confidence floor is "
                   (code governor/confidence-floor)
                   "; the high-stakes set is <code>heavyequip.governor/high-stakes</code>.")
              ["Op" "Phase" "If governor is clean" "If governor holds" "High stakes"]
              (gate-rows))

     (section "Jurisdiction spec-basis coverage"
              (str "From <code>heavyequip.facts/catalog</code>. Coverage is reported honestly: "
                   "a jurisdiction absent from the catalog has NO spec-basis and the governor "
                   "refuses to invent one. Of the "
                   (esc (:requested cov)) " jurisdiction(s) these units actually use, "
                   (esc (:covered cov)) " are covered"
                   (when (seq (:missing-jurisdictions cov))
                     (str " (missing: "
                          (str/join ", " (map code (:missing-jurisdictions cov))) ")"))
                   ".")
              ["ISO3" "Name" "Owning authority" "Required evidence items" "Seeded units"]
              (jurisdiction-rows db))

     (section "Registry drafts produced"
              (str "Built by <code>heavyequip.registry</code> from the jurisdiction-scoped "
                   "sequence counter. These are DRAFT book-of-record entries — the attached "
                   "credentials are unsigned and <code>issued_by_registry</code> is false. "
                   "Nothing here dispatched real hardware or signed a real certificate.")
              ["Record id" "Kind" "Unit" "Jurisdiction" "Immutable"]
              (registry-rows db))

     (section "Approver attribution (measured, not asserted)"
              (attribution-lede attrib)
              ["Op" "Unit" "Approved by (in-run)" "Store surfaces written"
               "Approver found in SSoT" "In ledger"]
              (attribution-rows attrib))

     (section (str "Audit ledger — " (count ledger) " facts")
              (str "The append-only decision log exactly as the run left it: "
                   (esc (count (filter #(= :committed (:t %)) ledger))) " commits and "
                   (esc (count holds)) " holds.")
              ["#" "Fact" "Op" "Unit" "Disposition" "Basis"]
              (map-indexed
               (fn [i {:keys [t op subject disposition basis]}]
                 (row (esc (inc i))
                      (if (= :governor-hold t) (span "critical" (kw->s t)) (span "ok" (kw->s t)))
                      (code (kw->s op))
                      (code subject)
                      (esc (kw->s disposition))
                      (esc (str/join ", " (map #(if (keyword? %) (kw->s %) (str %)) basis)))))
               ledger))

     "</main>\n"
     "<footer>Generated by <code>heavyequip.render-html</code> "
     "(<code>clojure -M:dev:render-html</code>) from a single deterministic run of the real "
     "actor graph against <code>heavyequip.store/demo-data</code>. No timestamps, no sampling: "
     "byte-identical on every rebuild from the same source.</footer>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out    (or (first args) "docs/samples/operator-console.html")
        result (run-demo!)
        ledger (vec (store/ledger (:db result)))
        holds  (filter #(= :governor-hold (:t %)) ledger)
        rules  (distinct (map :rule (mapcat :violations holds)))]
    ;; Build-time invariant, not a convention: a console that cannot
    ;; show the governor refusing something is not evidence of a
    ;; governor. Refuse to write the page rather than ship a page that
    ;; only ever shows the happy path.
    (when (empty? holds)
      (throw (ex-info
              (str "refusing to write " out
                   ": the scenario produced ZERO :governor-hold facts. "
                   "An operator console that never shows a refusal is not evidence "
                   "that the governor can refuse. Fix the scenario (or the governor) "
                   "before regenerating this page.")
              {:out out :ledger-facts (count ledger) :holds 0})))
    (spit out (render result))
    (println "wrote" out
             (str "(" (count ledger) " ledger facts, "
                  (count holds) " HARD holds over " (count rules) " distinct rules: "
                  (str/join ", " (map kw->s (sort rules))) ", "
                  (count (:approvals result)) " approvals, "
                  (count (store/dispatch-history (:db result))) " dispatch drafts, "
                  (count (store/evidence-history (:db result))) " certificate drafts)"))))
