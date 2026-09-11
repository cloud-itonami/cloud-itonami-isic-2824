(ns heavyequip.facts
  "Per-jurisdiction mining/quarrying/construction-machinery design-rules
  catalog -- the G2-style spec-basis table the Heavy Equipment Governor
  checks every `:design-rules/verify` proposal against.

  Coverage is reported HONESTLY: a jurisdiction not in this table has
  NO spec-basis. Seed values cite official machinery-safety / earth-
  moving-machinery authorities; this is a starting catalog, not a
  survey of every market.")

(def catalog
  {"JPN" {:name "Japan"
          :owner-authority "経済産業省 (METI) / 国土交通省 (MLIT) / 日本産業規格 (JIS) 建設機械関連"
          :legal-basis "労働安全衛生法 (建設機械) / JIS A 8403 (ブルドーザ) / JIS A 8411 (油圧ショベル) (参考)"
          :national-spec "建設機械（ブルドーザ・油圧ショベル・ホイールローダ・ダンプトラック・掘削機）の型式・安定性・制動性能要件"
          :provenance "https://www.jisc.go.jp/"
          :required-evidence ["CAEシミュレーション報告書 (CAE-simulation-report)"
                              "安定性試験報告書 ISO 10262 (ISO-10262-stability-test-report)"
                              "制動性能試験報告書 ISO 3450 (ISO-3450-braking-test-report)"
                              "材料証明記録 (material-certification-record)"]}
   "USA" {:name "United States"
          :owner-authority "OSHA / SAE International (ROPS & earth-moving machinery safety)"
          :legal-basis "29 CFR 1926 (Construction) / ANSI/SAE J1040 (ROPS certification) (reference)"
          :national-spec "Earth-moving machinery (excavators, dozers, wheel loaders, dump trucks, drill rigs) rollover-protection, stability and braking requirements"
          :provenance "https://www.osha.gov/"
          :required-evidence ["CAE-simulation-report"
                              "ISO-10262-stability-test-report"
                              "ISO-3450-braking-test-report"
                              "Material-certification-record"]}
   "GBR" {:name "United Kingdom"
          :owner-authority "HSE / UKCA earthmoving-machinery framework (BS EN 474 series)"
          :legal-basis "Supply of Machinery (Safety) Regulations (UKCA) / BS EN 474 (reference)"
          :national-spec "UK earthmoving machinery conformity, stability and braking requirements"
          :provenance "https://www.hse.gov.uk/"
          :required-evidence ["CAE-simulation-report"
                              "ISO-10262-stability-test-report"
                              "ISO-3450-braking-test-report"
                              "Material-certification-record"]}
   "DEU" {:name "Germany"
          :owner-authority "DGUV / DIN / EU-Maschinenrichtlinie (Erdbaumaschinen)"
          :legal-basis "Maschinenrichtlinie 2006/42/EG (künftig Maschinenverordnung (EU) 2023/1230) / EN 474 (Referenz)"
          :national-spec "DE Erdbaumaschinen (Bagger, Planierraupen, Radlader, Muldenkipper, Bohrgeräte) Sicherheits-, Standsicherheits- und Bremsanforderungen"
          :provenance "https://www.din.de/"
          :required-evidence ["CAE-Simulationsbericht (CAE-simulation-report)"
                              "Standsicherheitsprüfbericht ISO 10262 (ISO-10262-stability-test-report)"
                              "Bremsprüfbericht ISO 3450 (ISO-3450-braking-test-report)"
                              "Werkstoffzertifikat (material-certification-record)"]}})

(defn spec-basis [iso3] (get catalog iso3))

(defn coverage
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-2824 R0: " (count catalog)
                 " jurisdictions seeded. Extend `heavyequip.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn required-evidence-satisfied?
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))
