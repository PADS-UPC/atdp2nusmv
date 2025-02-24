(ns atd-reasoner.ltl
  (:require [clojure.spec.alpha :as spec]
            [clojure.string :as str]
            [instaparse.core :as insta]))

(spec/def :LTL/identifier
  #(re-matches #"[A-Za-z_][A-Za-z0-9_]*" (str %)))

(spec/def :LTL/atom :LTL/identifier)

(spec/def :LTL/atoms (spec/coll-of :LTL/atom))

(spec/def :LTL/formula
  (spec/or
   :atom :LTL/atom
   :G (spec/cat :op #(= % :G) :children :LTL/formula)
   :F (spec/cat :op #(= % :F) :children :LTL/formula)
   :X (spec/cat :op #(= % :X) :children :LTL/formula)
   :! (spec/cat :op #(= % :!) :children :LTL/formula)
   :& (spec/cat :op #(= % :&) :children (spec/* :LTL/formula))
   :| (spec/cat :op #(= % :|) :children (spec/* :LTL/formula))
   :U (spec/and (spec/cat :op #(= % :U) :children (spec/* :LTL/formula)) #(= (-> % :children count) 2))
   :-> (spec/and (spec/cat :op #(= % :->) :children (spec/* :LTL/formula)) #(= (-> % :children count) 2))
   :<-> (spec/and (spec/cat :op #(= % :<->) :children (spec/* :LTL/formula)) #(= (-> % :children count) 2))))

(spec/def :LTL/model
  (spec/keys :req [:LTL/states :LTL/formula]))

(spec/def :LTL/states (spec/coll-of :LTL/state))
(spec/def :LTL/formulas (spec/coll-of :LTL/formula))

(spec/def :LTL/atom-or-seq (spec/or :atom :LTL/atom
                                      :seq (spec/coll-of :LTL/atom)))

(spec/fdef precedence :args (spec/cat :st :LTL/atom :en :LTL/atom :a :LTL/atoms :b :LTL/atom))
(defn precedence [st, en, [_ :as as], b]
  (into [:|]
        (for [a_i as]
          [:G [:-> st [:U [:! b] [:| en a_i]]]]))
  #_[:G [:-> st [:U [:! b]
                  (into [:| en] as)]]])

(spec/fdef response :args (spec/cat :st :LTL/atom :en :LTL/atom :a :LTL/atom :b :LTL/atoms))
(defn response [st, en, a, [_ :as bs]]
  #_[:G [:-> st [:U (into [:&] (for [b_i bs] [:-> b_i [:U [:! en] [:& a [:! en]]]])) en]]]
  #_[:G [:-> st [:U (into [:|]
                        (for [b_i bs] [:-> a [:U [:! en] b_i]]))
                 en]]]
  (into [:|]
        (for [b_i bs]
          [:G [:-> st [:U [:-> a [:U [:! en] b_i]]
                       en]]])))

(spec/fdef sequence :args (spec/cat :st :LTL/atom :en :LTL/atom :a :LTL/atom :b :LTL/atom))
(defn sequence [st, en, a, b]
  [:&
   (precedence st, en, [a], b)
   (response st, en, a, [b])])

(defn all-pairs [xs]
  (for [i (range (count xs))
        j (range (count xs))
        :when (< i j)
        :let [x (xs i)
              y (xs j)]]
    [x y]))

(spec/fdef notCoOccurs :args (spec/cat :st :LTL/atom :en :LTL/atom :a :LTL/atom :b :LTL/atom))
(defn notCoOccurs
  ([st, en, a, b]
   [:&
    [:G [:-> st [:U [:-> a [:U [:! b] en]] en]]]
    [:G [:-> st [:U [:-> b [:U [:! a] en]] en]]]]
   #_[:G
    [:->
     st
     [:U
      [:&
       [:-> a [:U [:! b] en]]
       [:-> b [:U [:! a] en]]]
      en]]])
  ;; Multi-arity version just uses the cartesian product
  ([st, en, a, b & others]
   (into [:&]
         (for [[x, y] (all-pairs (into [a b] others))]
           (notCoOccurs st, en, x, y)))))

(spec/fdef alternatives :args (spec/cat :st :LTL/atom :en :LTL/atom :a :LTL/atom :b :LTL/atoms))
(defn alternatives [st, en, a, [_ :as bs]]
  (into [:&]
        (concat
         (for [b bs]
           (precedence st en [a] b))
         [(response st en a bs)]
         [(apply notCoOccurs st en bs)])))

(spec/fdef alternateResponse :args (spec/cat :st :LTL/atom :en :LTL/atom :a :LTL/atom :b :LTL/atom))
(defn alternateResponse [st, en, a, b]
  [:&
   (response st en a [b])
   [:G [:-> st [:U [:-> a [:X [:U [:! a] [:| b en]]]] en]]]])


(spec/fdef mandatory :args (spec/cat :st :LTL/atom :en :LTL/atom :a :LTL/atom :b :LTL/atom))
(defn mandatory [st, en, a, _a]
  ;;NOTE: Fourth arg because atdlib still doesn't support unary relations,
  ;; so we use reflexive ones instead.
  [:G [:-> st [:U [:! en] a]]])

(spec/fdef alternateSuccession :args (spec/cat :p :LTL/atom :q :LTL/atom))
(defn alternateSuccession [p, q]
  [:&
   [:-> [:F q] [:U [:! q] p]]
   [:G [:-> p [:F q]]]
   [:G [:-> p [:X [:-> [:F p] [:U [:! p] q]]]]]
   [:G [:-> q [:X [:-> [:F q] [:U [:! q] p]]]]]]
  #_(let [prec #(-> [:-> [:F %2] [:U [:! %2] %1]])
        resp #(-> [:G [:-> %1 [:F %2]]])
        always_btw #(-> [:G [:-> %1 [:X (prec %2 %1)]]])]
    [:& (always_btw p, q) (prec p, q) (resp p, q)]))

(spec/fdef weakUntil :args (spec/cat :a :LTL/atom :b :LTL/atom))
(defn weakUntil [a, b]
  [:| [:U a b] [:G a]])

(def parens-grammar
  (insta/parser
   "<S> = expr+;
    <expr> = parens | atom;
    parens = <'('> S <')'>;
    <atom> = #'[a-zA-Z!|&_=0-9->< ]*';"))

(defn simplify-parens [code]
  (let [f (fn f [t]
            (cond
              (and (coll? t) (= (first t) :parens) (= (count t) 2)
                   (coll? (second t)) (= (first (second t)) :parens))
              (f (second t))
              (and (coll? t) (= (first t) :parens))
              (apply str
                     (concat
                      ["("]
                      (map f (rest t))
                      [")"]))
              :else (str t)))]
    (f (first (parens-grammar code)))))

(spec/fdef compile-formula' :args (spec/cat :formula :LTL/formula))
(defn compile-formula' [formula]
  (if-not (coll? formula)
    (str "(activity="formula")")
    (case (first formula)
      :G   (str "(G (" (compile-formula' (second formula)) "))")
      :F   (str "(F (" (compile-formula' (second formula)) "))")
      :X   (str "(X (" (compile-formula' (second formula)) "))")
      :!   (str "(!" (compile-formula' (second formula)) ")")
      :&   (str "(" (str/join " & " (map compile-formula' (rest formula))) ")")
      :|   (str "(" (str/join " | " (map compile-formula' (rest formula))) ")")
      :U   (str "((" (compile-formula' (nth formula 1)) ") U (" (compile-formula' (nth formula 2)) "))")
      :->  (str "((" (compile-formula' (nth formula 1)) ") -> (" (compile-formula' (nth formula 2)) "))")
      :<-> (str "((" (compile-formula' (nth formula 1)) ") <-> (" (compile-formula' (nth formula 2)) "))")
      (throw (Exception. (format "Formula not recognised: %s" (str formula)))))))

(defn compile-formula [formula]
  (if (spec/valid? :LTL/formula formula)
    (simplify-parens (compile-formula' formula))
    (throw (Exception. (format "%s is not a valid LTL formula.\n Reason: %s" (str formula) (spec/explain-str :LTL/formula formula))))))
