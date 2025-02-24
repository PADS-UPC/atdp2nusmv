;; This file contains examples from the paper

;; Example 1: The "false" query. For Use Case 1. Note that a false literal
;; cannot currently be encoded, so we need to use any contradiction. To be
;; used with ATDP specs hosp-1 and hosp-1-bad.



[:& "T0" [:! "T0"]]

;; Example 2: A business rule: "A contaminated trace can never be used". To be
;; used with ATDP specs hosp-2-i{1,2,3}.

[:G [:-> "T20" [:U [:! "T23"] "T19"]]]

;; Example 3: Two encoded partial traces. To be used with ATDP spec hosp-3.

;; < ... T11, T12, T13 ... >

[:U [:| "START_P0" "START_P1" "START_P23" "START_P2" "START_P3" "START_P4"
     "END_P0" "END_P1" "END_P23" "END_P2" "END_P3" "END_P4"]
 [:& "T11"
  [:X [:U [:| "START_P0" "START_P1" "START_P23" "START_P2" "START_P3" "START_P4"
           "END_P0" "END_P1" "END_P23" "END_P2" "END_P3" "END_P4"]
       [:& "T12"
        [:X [:U [:| "START_P0" "START_P1" "START_P23" "START_P2" "START_P3" "START_P4"
                 "END_P0" "END_P1" "END_P23" "END_P2" "END_P3" "END_P4"]
             "T13"]]]]]]]

;; < ... T11, T13, T12 ... >

[:U [:| "START_P0" "START_P1" "START_P23" "START_P2" "START_P3" "START_P4"
     "END_P0" "END_P1" "END_P23" "END_P2" "END_P3" "END_P4"]
 [:& "T11"
  [:X [:U [:| "START_P0" "START_P1" "START_P23" "START_P2" "START_P3" "START_P4"
           "END_P0" "END_P1" "END_P23" "END_P2" "END_P3" "END_P4"]
       [:& "T13"
        [:X [:U [:| "START_P0" "START_P1" "START_P23" "START_P2" "START_P3" "START_P4"
                 "END_P0" "END_P1" "END_P23" "END_P2" "END_P3" "END_P4"]
             "T12"]]]]]]]
