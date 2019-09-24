(ns qarth.friend
  "Friend workflows for Qarth."
  (:require [qarth.oauth :as oauth]
            [qarth.ring :as qarth-ring]
            [cemerick.friend :as friend]
            [ring.util.request :as request]
            [ring.util.response :as response]
            [clojure.tools.logging :as log]))

(defn- credential-map
                                        ;Adds Friend meta-information to the auth record, and uses that
                                        ;as the credential map.
  [credential-fn record redirect-on-auth?]
  (if-let [r (credential-fn record)]
    (vary-meta r assoc
               :cemerick.friend/workflow ::qarth
               :cemerick.friend/redirect-on-auth? redirect-on-auth?
               :type :cemerick.friend/auth)))

(defn auth-record
  "Looks for a qarth auth record in the Friend authentications.
  Returns it, or nil if not found."
  [req]
  (->> req
       :session :cemerick.friend/identity :authentications
       vals
       (filter #(-> % meta :cemerick.friend/workflow (= ::qarth)))
       first
       :qarth.oauth/record))

(defn requestor
  "Get an auth requestor from a Friend-authenticated request and a service."
  [req service]
  (if-let [r (auth-record req)]
    (oauth/requestor service r)))

(defn oauth-workflow
  "Creates a Friend  using a Qarth service.

  Required arguments:
  service -- the auth service
  auth-url -- A dual purpose URL. This starts both the OAuth workflow
  (so a login button, for example, should redirect here)
  and serves as the auth callback.
  It should be the same as the callback in your auth service.

  Optional arguments:
  login-url or login-uri -- a URL to redirect to if a user is not logged in.
  (The default Friend :login-uri is /login.)
  key -- for multi-services. Can also be passed as a query param, \"service\".
  credential-fn -- override the Friend credential fn.
  The default Friend credential-fn, for some reason, returns nil.
  The credential map supplied is of the form
  {:qarth.oauth/record auth-record, :identity :qarth.oauth/anonymous}.
  redirect-on-auth? -- the Friend redirect on auth setting, default true
  login-failure-handler -- the login failure handler.
  The default is to redirect to the configured login-url.
  Failure also logs an exception and clears the current auth record.

  Exceptions are logged and treated as auth failures."
  [{:keys [service key auth-url credential-fn redirect-on-auth?
           login-url login-uri login-failure-handler] :as params}]
  (fn [{ring-sesh :session :as req}]
    (let [auth-config (merge (:cemerick.friend/auth-config req) params)
          auth-url (or auth-url (:auth-url auth-config))]
      (if (= (request/path-info req) auth-url)
        (let [
                                        ; Friend-specific configuration
              redirect-on-auth? (or redirect-on-auth?
                                    (:redirect-on-auth? auth-config) true)
              credential-fn (or credential-fn (:credential-fn auth-config))
              success-handler (fn [{{record :qarth.oauth/record} :session}]
                                (credential-map credential-fn
                                                {:qarth.oauth/record record
                                                 :identity :qarth.oauth/anonymous}
                                                redirect-on-auth?))
              login-failure-handler (or login-failure-handler
                                        (get auth-config :login-failure-handler)
                                        (fn [req]
                                          (assoc
                                           (response/redirect
                                            (or login-url
                                                login-uri
                                        ; Honor our promise to look up
                                        ; 'the configured login-url'
                                                (:login-url auth-config)
                                                (:login-uri auth-config)))
                                           :session (:session req))))
                                        ; Our configuration
              key (or key (:key auth-config))
              req (if key
                    (assoc-in req [:query-params "service"] key)
                    req)]
          (log/debug "Reached Friend OAuth workflow at uri" (:uri req))
          (let [resp
                ((qarth-ring/omni-handler {:service service
                                           :success-handler success-handler
                                           :failure-handler login-failure-handler})
                 req)]
            (log/trace "Workflow returning" (pr-str resp))
            resp))))))
