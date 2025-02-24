(ns atd-reasoner.nusmv
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.java.shell :as sh :refer [sh]]
            [me.raynes.conch.low-level :as clow]
            [atd-reasoner.ltl :as ltl]
            [edu.upc.atdlib.relation :as rel]
            [edu.upc.atdlib.span :as span]
            [edu.upc.atdlib.scope-relation :as srel]
            [edu.upc.atdlib.annotation :as atdp]))


(def relation-impls
  {::rel/Precedence ltl/precedence
   ::rel/Response ltl/response
   ::rel/NoCoOccurs ltl/notCoOccurs
   ::rel/Sequence ltl/sequence
   ::rel/Alternatives ltl/alternatives
   ::rel/LoopBack ltl/alternateResponse
   ::rel/Mandatory ltl/mandatory})

(defn get-scopes [atd]
  (->>
   (:scopes atd)
   (map (fn [scope]
          (let [start (str "START_" (:id scope))
                end (str "END_" (:id scope))]
            (assoc scope
                   :start start
                   :end end))))
   (map (fn [scope] [(:id scope) scope]))
   (into {})))

(defn compute-scope-of-activity
  "Returns the innermost scope containing an activity"
  [scopes]
  (reduce
   (fn [scope-of-activity scope]
     (reduce
      (fn [scope-of-activity activity]
        (update scope-of-activity activity (fnil conj #{}) (:id scope)))
      scope-of-activity
      (:activities scope)))
   {}
   (vals scopes)))

(defn compute-parent-scope
  [scopes]
  (->>
   (map
    (fn [scope]
      (let [superset-scopes (filter
                             #(and (set/subset? (:activities scope) (:activities %))
                                   (not= scope %))
                             scopes)]
        [(:id scope)
         (when (seq superset-scopes)
           #_"Get the smallest superset scope (i.e. the parent)"
           (:id (apply min-key #(count (:activities %)) superset-scopes)))]))
    scopes)
   (into {})))


(defn compile-model [atd query start-activity]
  (let [activities (map :id (concat (atdp/get-spans-of-type atd ::span/Action)
                                    (atdp/get-spans-of-type atd ::span/Condition)))
        scopes (get-scopes atd)
        scope-of-activity-raw (compute-scope-of-activity scopes)
        scope-of-activity (fn [activity]
                            (let [scopes (scope-of-activity-raw activity)]
                              (when (seq scopes)
                                (apply min-key #(count (scopes %)) scopes))))
        parent-scope (compute-parent-scope (vals scopes))
        relations (:relations atd)
        scope-relations (:scope-relations atd)

        __ (def --scope-of-activity scope-of-activity)
        __ (def --activities activities)

        constraints
        (->> relations
             (map
              ;; We assume that all the src/dst in a relation share the same scope
              (fn [{:keys [src dst] :as rel}]
                (let [x (if (coll? src) (first src) src)
                      scope (scopes (scope-of-activity x))
                      impl (relation-impls (:type rel))]
                  (when impl (impl (:start scope) (:end scope) src dst)))))
             (remove nil?))

        axioms
        (concat
         ;; 1. An activity inside a scope can only be executed
         ;;    between the scope's start and end activities.
         (for [a activities
               :let [P (scopes (scope-of-activity a))
                     st (:start P)
                     en (:end P)
                     __ (assert (and (-> st nil? not) (-> en nil? not)))]]
           [:&
            [:G [:-> en
                 (ltl/weakUntil [:! a] st)]]
            (ltl/weakUntil [:! a] st)])
         ;; 2. A child scope's activities can only be executed
         ;;    between its parent's start and end activities
         (for [P (vals scopes)
               :when (parent-scope (:id P))
               :let [[st en] [(:start P) (:end P)]
                     P_parent (scopes (parent-scope (:id P)))
                     [st_parent, en_parent] [(:start P_parent) (:end P_parent)]]]
           [:&
            [:G [:-> en_parent
                    (ltl/weakUntil [:! st] st_parent)]]
            [:G [:-> en_parent
                 (ltl/weakUntil [:! en] st_parent)]]
            (ltl/weakUntil [:! st] st_parent)
            (ltl/weakUntil [:! en] st_parent)])
         ;; 3. Every scope start has a single corresponding scope end, and vice-versa
         (for [P (vals scopes)
               :let [st (:start P)
                     en (:end P)]]
           (ltl/alternateSuccession st en))
         ;; 5. The trap state STOP is eventually reached (i.e. the process ends)
         [[:F "STOP"]]
         ;; 6 Executing a terminating activity ends the iteration of an iterating scope
         (let [terminating-activities (map :src (filter
                                                 #(= (:type %) ::rel/Terminating)
                                                 (:relations atd)))]
           (for [a terminating-activities
                 :let [P (scopes (scope-of-activity a))
                       [st en] [(:start P) (:end P)]
                       P_parent (scopes (parent-scope (:id P)))
                       [st_parent, en_parent] [(:start P_parent) (:end P_parent)]]
                 :when (parent-scope (:id P))]
             [:&
              [:G [:-> a [:X en]]]
              [:G [:-> st_parent [:U [:-> a [:U [:! st] en_parent]] en_parent]]]]))
         ;; 6.1 Non-Iterating scopes cannot be repeated inside their parent
         (let [iterating-scopes (set (map :src (filter
                                                #(= (:type %) ::srel/Iterating)
                                                (:scope-relations atd))))
               non-iterating-scopes (filter
                                     #(not (iterating-scopes (:id %)))
                                     (vals scopes))]
           (for [P non-iterating-scopes
                 :when (parent-scope (:id P))
                 :let [[st en] [(:start P) (:end P)]
                       P_parent (scopes (parent-scope (:id P)))
                       [st_parent, en_parent] [(:start P_parent) (:end P_parent)]]]
             [:G [:-> st_parent [:U
                                 [:-> st [:X [:U [:! st] en_parent]]]
                                 en_parent]]]
             ))

         )

        model
        (->> scope-relations
             (map
              (fn [s-rel]
                (let [Pi (scopes (:src s-rel))
                      [st_i, en_i] [(:start Pi) (:end Pi)]
                      Pj (scopes (:dst s-rel))
                      [st_j, en_j] [(:start Pj) (:end Pj)]
                      P_parent (scopes (parent-scope (:id Pi)))
                      [st_parent, en_parent] [(:start P_parent) (:end P_parent)]
                      ]
                  (assert (= (:id P_parent) (parent-scope (:id Pj)))
                          (format "For two scopes to be related, they must share the same parent! In relation %s, parent(%s) = %s but parent(%s) = %s"
                                  (str s-rel) (:id Pi), (:id P_parent),
                                  (:id Pj), (parent-scope (:id Pj))))
                  (case (:type s-rel)
                    ::srel/Sequential (ltl/sequence st_parent, en_parent, en_i, st_j)
                    ::srel/Exclusive (ltl/notCoOccurs st_parent, en_parent, st_i, st_j)
                    ::srel/Mandatory (ltl/mandatory st_parent, en_parent, st_i, st_i)
                    ::srel/Concurrent nil
                    ::srel/Iterating nil #_"Defined in axiom 6.2"))))
             (remove nil?))

        all-activities
        (concat
         activities
         (mapcat
          #(-> [(:start %) (:end %)])
          (vals scopes)))

        nusmv-code
        (str/join "\n"
                  ["MODULE main"
                   "VAR"
                   (str "    activity : {"
                        (str/join "," (concat (sort all-activities) ["STOP"]))
                        "};")
                   "ASSIGN"
                   (str "  init(activity) := " start-activity ";")
                   "  next(activity) :="
                   "    case"
                   (str/join "\n"
                             (for [a all-activities]
                               (str "      activity = " a 
                                    ": {" (str/join "," (concat (sort all-activities) ["STOP"])) "};")))
                   "      activity = STOP : {STOP};"
                   "    esac;"
                   "LTLSPEC"
                   #_(ltl/compile-formula
                      [:->
                       (into [:&] (concat axioms model constraints))
                       query])
                   "("
                   (str/join " & \n" (map ltl/compile-formula (concat axioms model constraints)))
                   ") -> "
                   (ltl/compile-formula query)

                   ])]
    nusmv-code))

(def nusmv-path
  "The path to the NuSMV executable"
  "/home/josep/Repositories/NuSMV-2.6.0/NuSMV/build/bin/NuSMV")

(defn run-model [atd query start-activity]
  (let [compiled (compile-model atd query start-activity)
        tmpfile (io/file (str "/tmp/atd-" (str (Math/abs (hash compiled))) ".nusmv"))]
    (spit tmpfile compiled)
    (clow/proc
     "unbuffer" nusmv-path "-bmc" "-bmc_length" "50" (.getAbsolutePath tmpfile)
     :buffer-size (* 1024 10))))

(defn parse-nusmv-output [output]
  (let [[spec & rest]
        (drop-while
         #(re-matches #"-- no counterexample found with bound [0-9]+" %)
         (drop-while
          (complement #(re-matches #"-- no counterexample found with bound [0-9]+" %))
          output))
        truth-value (not (re-matches #".* is false$" spec))
        trace (when-not truth-value
                (let [states (->> rest
                                  (drop-while #(re-matches #"-- as demonstrated by the following.*" %))
                                  (drop-while #(re-matches #"Trace Description:.*" %))
                                  (drop-while #(re-matches #"Trace Type:.*" %)))]
                  (loop [trace []
                         input states]
                    (if (seq input)
                      (let [state-regex #".* State: ([0-9]+\.[0-9]+).*"
                            input (drop-while #(not (re-matches state-regex %))
                                              input)
                            state (second (re-matches state-regex (first input)))
                            maybe-activity (when-not (re-matches state-regex (second input))
                                             (second (re-matches #".*activity = (.*)$" (second input))))]
                        (if maybe-activity
                          (recur (conj trace maybe-activity)
                                 (drop 2 input))
                          (recur (conj trace (last trace))
                                 (drop 1 input))))
                      trace))))]
    {:spec-true? truth-value
     :counter-example trace}))

(defn nusmv-run [atd query start-activity]
  (let [nusmv-process (run-model atd query start-activity)
        lines (loop [lines []]
                (let [line (clow/read-line nusmv-process :out)]
                  (if line
                    (do (print line)
                        (recur (conj lines (.replace line "\n" ""))))
                    lines)))]
    (parse-nusmv-output lines)))


