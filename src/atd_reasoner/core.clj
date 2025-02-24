(ns atd-reasoner.core
  (:require [clojure.java.io :as io]
            [clojure.spec.alpha :as spec]
            [atd-reasoner.nusmv :as nusmv])
  (:gen-class))


(defn -main
  "Parses an ATDP spec from the command line and outputs the NuSMV model specification."
  [path query start out & _]
  (assert (and path query start out) "Three arguments expected")
  (assert (io/file path) "First argument must be a valid ATDP file")
  (assert (spec/conform :LTL/formula (read-string query)) "Second argument must be a well-formed LTLf formula")
  (assert start "Third argument must be the starting activity id")
  (assert (.getParent (io/file out)) "Fourth argument must be a valid path to output NuSMV spec")
  (let [atdp (read-string (slurp path))
        query (read-string query)]
    (spit out
          (nusmv/compile-model atdp, query, start))))

