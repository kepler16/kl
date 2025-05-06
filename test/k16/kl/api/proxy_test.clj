(ns k16.kl.api.proxy-test
  (:require
   [matcher-combinators.test]
   [clojure.test :refer [deftest is]]
   [k16.kl.api.proxy :as api.proxy]))

(deftest routes-test
  (let [routes (#'api.proxy/build-routes
                "module-a"
                {:network {:services {:service-a {:endpoints {:container {:url "http://container-a"}
                                                              :host {:url "http://host.docker.internal:41003"}}
                                                  :default-endpoint :container}}

                           :routes {:route-a {:host "service-a.test"
                                              :service :service-a}

                                    :route-b {:host "api.test"
                                              :path-prefix "/service-a"
                                              :service :service-a}}}})]
    (is (match? {:http {:routers {"module-a-route-a" {:rule "HostRegexp(`service-a.test`)"
                                                      :service "module-a-service-a-container"}
                                  "module-a-route-b" {:rule "HostRegexp(`api.test`) && PathPrefix(`/service-a`)"
                                                      :service "module-a-service-a-container"}}
                        :services {"module-a-service-a-container" {:loadbalancer {:servers [{:url "http://container-a"}]}}}}}
                routes))))
