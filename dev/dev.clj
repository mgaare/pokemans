(ns dev
  (:require [clojure.tools.namespace.repl :refer :all]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as str]
            [datomic.api :as d])
  (:import datomic.Util))

(def dev-db-uri
  "datomic:mem://dev-db")

(def schema
  (io/resource "schema.edn"))

(def init-data
  (io/resource "init-data.edn"))

(defn read-txs
  [tx-resource]
  (with-open [tf (io/reader tx-resource)]
    (Util/readAll tf)))

(defn transact-all
  ([conn txs]
   (transact-all conn txs nil))
  ([conn txs res]
   (if (seq txs)
     (transact-all conn (rest txs) @(d/transact conn (first txs)))
     res)))

(defn initialize-db
  "Creates db, connects, transacts schema and example data, returns conn."
  []
  (d/create-database dev-db-uri)
  (let [conn (d/connect dev-db-uri)]
    (transact-all conn (read-txs schema))
    (transact-all conn (read-txs init-data))
    conn))

(defonce conn nil)

(defn go
  []
  (alter-var-root #'conn (constantly (initialize-db))))

(defn stop
  []
  (alter-var-root #'conn
                  (fn [c] (when c (d/release c)))))

(defn restart
  []
  (stop)
  (go))

(def rating-multiples
  {:species.rating/low 3
   :species.rating/medium 5
   :species.rating/high 7
   :species.rating/omfgwtf 9})

(defn roll-stat
  [rating]
  (-> (rand)
      (inc)
      (* (get rating-multiples rating))
      (long)))

(defn new-pokeman
  "Takes a species map (or entity map), and returns a tx to create a
   new pokeman."
  [species]
  (let [{strength :species/strength-rating
         agility :species/agility-rating
         stamina :species/stamina-rating
         magic :species/magic-rating
         id :db/id} species]
    {:db/id (d/tempid :db.part/user)
     :pokeman/species id
     :pokeman/strength (roll-stat strength)
     :pokeman/agility (roll-stat agility)
     :pokeman/stamina (roll-stat stamina)
     :pokeman/magic (roll-stat magic)
     :pokeman/morale :morale/normal}))

(defn generate-pokemans
  "Generates n pokemans and saves to db."
  [conn n]
  (let [db (d/db conn)
        specii (->> (d/q '[:find [?e ...]
                          :where [?e :species/name ?n]]
                         db)
                    (map (partial d/entity db)))
        pokemans (map (fn [_] (new-pokeman (rand-nth specii)))
                      (range n))]
    (d/transact conn pokemans)))

(defn traineurs
  "Returns entity maps of all traineurs."
  [db]
  (->> (d/q '[:find [?t ...]
              :where [?t :traineur/name]]
            db)
       (map (partial d/entity db))))

(defn unclaimed-pokemans
  "Returns entity maps of unclaimed pokemans."
  [db]
  (->> (d/q '[:find [?p ...]
              :where [?p :pokeman/species]
              (not [_ :traineur/pokemans ?p])]
            db)
       (map (partial d/entity db))))

(defn everyone-capture-n-pokemans
  "Randomly distributes n pokemans to each traineur (until we run out.)"
  [conn n]
  (let [db (d/db conn)
        ts (shuffle (traineurs db))
        ps (partition-all n (shuffle (unclaimed-pokemans db)))]
    (dorun
     (map (fn [t tps]
            (d/transact conn
                        (mapv (fn [p]
                                [:pokeman/capture (:db/id t) (:db/id p)])
                              tps)))
          ts ps))))

(def friends-and-gym
  '[[(friends ?t1 ?t2)
     [?t1 :traineur/friends ?t2]]
    [(friends ?t1 ?t2)
     [?t2 :traineur/friends ?t1]]
    [(friends ?t1 ?t2)
     [?t1 :traineur/gym ?g]
     [?t2 :traineur/gym ?g]]])

(def recursive-friends
  '[[(friends ?t1 ?t2)
     [?t1 :traineur/friends ?t2]]
    [(friends ?t1 ?t2)
     [?t1 :traineur/friends ?ti]
     (friends ?ti ?t2)]])

(defn find-pokemon-with-friends-rule
  "Finds all the friends with a given pokeman species name, using one
   of the friend-finding rules. (Eg, `friends-and-gym` or
   `recursive-friends`)"
  [traineur species-name friends-rule]
  (d/q '[:find ?f ?p
         :in $ % ?traineur ?species
         :where (friends ?traineur ?f)
         [?f :traineur/pokemans ?p]
         [?p :pokeman/species ?s]
         [?s :species/name ?species]]
       (d/db conn) friends-rule traineur species-name))

(defn duplicates
  [db]
  (d/q '[:find ?tn ?sn
         :where
         [?t :traineur/pokemans ?p1]
         [?t :traineur/pokemans ?p2]
         [?p1 :pokeman/species ?s]
         [?p2 :pokeman/species ?s]
         [?t :traineur/name ?tn]
         [?s :species/name ?sn]
         [(!= ?p1 ?p2)]]
       db))

;;;; play with duplicates

;;; Get reference to Bleakachu species

;; (def bleakachu (d/entity (d/db conn) [:species/name "Bleakachu]))

;;; Create 2 Bleakachu, grab their ids

;; (def bleakachu-ids (-> (d/transact conn (repeatedly 2 (fn [] (new-pokeman bleakachu))))
;;                        deref :tempids vals))

;;; Get reference to Jellypuff species

;; (def jellypuff (d/entity (d/db conn) [:species/name "Jellypuff"]))

;;; Create 1 Jellypuff, grab id

;; (def jellypuff-id (-> (d/transact conn [(new-pokeman jellypuff)])
;;                       deref :tempids vals first))

;;; Have Cody capture all 3

;; (d/transact conn (mapv (fn [id] [:pokeman/capture [:traineur/name "Cody"] id])
;;                        (conj bleakachu-ids jellypuff-id)))

;;; duplicates should list Cody and Bleakachu


;;; also of note: you can use Datomic's query API on lists of vectors!

(def fake-db
  [[:Jenny :has-toy :MalibuCarrie]
   [:Jenny :has-toy :DoctorCarrie]
   [:Jenny :has-toy :PoliticalConsultantCarrie]
   [:Jenny :has-toy :Bleakachu]
   [:Jenny :has-toy :Blobosaur]
   [:Karen :has-toy :Ariel]
   [:Karen :has-toy :Belle]
   [:Karen :has-toy :RockerBarbie]
   [:MalibuCarrie :type :Carrie]
   [:DoctorCarrie :type :Carrie]
   [:PoliticalConsultantCarrie :type :Carrie]
   [:RockerCarrie :type :Carrie]
   [:Bleakachu :type :Pokeman]
   [:Blobosaur :type :Pokeman]
   [:Ariel :type :Princess]
   [:Belle :type :Princess]])

;; this doesn't work - returns too high count

(d/q '[:find ?child ?type (count ?toy)
       :where
       [?child :has-toy ?toy1]
       [?toy1 :type ?type]
       [?child :has-toy ?toy2]
       [?toy2 :type ?type]
       [(!= ?toy1 ?toy2)]
       [?child :has-toy ?toy]]
     fake-db)

;; this works!

(d/q '[:find ?child ?type (count ?toy)
       :where
       [?child :has-toy ?toy1]
       [?toy1 :type ?type]
       [?child :has-toy ?duplicate]
       [?duplicate :type ?type]
       [(!= ?toy1 ?duplicate)]
       [?child :has-toy ?toy]
       [?toy :type ?type]]
     fake-db)

;; this works too!

(d/q '[:find ?child ?type (count ?toy)
       :where
       [?child :has-toy ?toy]
       [?toy :type ?type]
       [?child :has-toy ?duplicate]
       [?duplicate :type ?type]
       [(!= ?toy ?duplicate)]]
     fake-db)

;; and this will give you the list of the toys

(d/q '[:find ?child ?type (distinct ?toy)
       :where
       [?child :has-toy ?toy]
       [?toy :type ?type]
       [?child :has-toy ?duplicate]
       [?duplicate :type ?type]
       [(!= ?toy ?duplicate)]]
     fake-db)
