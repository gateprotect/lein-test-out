(ns leiningen.test-out
  (:use [leiningen.core.eval :only [eval-in-project]]

        [clojure.tools.namespace :only [find-namespaces-in-dir]]))

(try
 (use '[clojure.java.io :only [file]])
 (catch Throwable e
   (use '[clojure.contrib.io :only [file]])))

(defn require-all-test-namespaces
  "returns a form that when eval'd, requires all test namespaces"
  [project]
  `(do
     ~@(apply concat (for [p (:test-paths project)]
                       (for [ns (find-namespaces-in-dir (file p))]
                         `(require (quote ~ns)))))))

(defn require-clojure-test-form []
  `(try
    (require 'clojure.test)
    (require 'clojure.test.junit)
    (require 'clojure.test.tap)
    (catch Throwable e#
      (.printStackTrace e#)
      (System/exit 1))))

(defn run-form [project format filename]
  (let [format-fn (if (= format "tap")
                    'clojure.test.tap/with-tap-output
                    'clojure.test.junit/with-junit-output)]
    `(do
       (try
         ;; ignore :summary and reroute midje messages to :fail
         (defmethod clojure.test.junit/junit-report :summary [_#])
         (defmethod clojure.test.junit/junit-report :default [m#]
           (clojure.test.junit/junit-report (assoc m# :type :fail)))
         ~(require-all-test-namespaces project)
         (with-open [file-stream# (java.io.FileWriter. ~filename)]
           (binding [~'*out* file-stream#
                     clojure.test/*test-out* file-stream#]
             (if (not (clojure.test/successful? (~format-fn (clojure.test/run-all-tests))))
               (System/exit 1))
             (catch Throwable e#
               (clojure.test/is false (format "Uncaught exception: %s" e#))
               (System/exit 1)))))
       (System/exit 0))))

(defn test-out
  "runs all tests, and outputs results to a file in junitXML or TAP format.

Usage: lein test-out <format> <filename>

By default, outputs junit XML to testreports.xml."
  [project & [format filename]]
  (let [filename (or filename "testreports.xml")
        forms [(require-clojure-test-form)
               (run-form project format filename)]]
    (eval-in-project
     project
     (second forms) ;; form
     ;nil ;; handler
     ;nil ;; skip-auto-compile
     (first forms) ;; init
     )))