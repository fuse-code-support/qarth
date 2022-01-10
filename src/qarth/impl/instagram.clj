(ns qarth.impl.instagram
  "A Instagram oauth impl. Type is :instagram."
  (require (qarth [oauth :as oauth])
           [qarth.oauth.lib :as lib]
           qarth.impl.oauth-v2
           cheshire.core))

(oauth/derive :instagram :oauth)

(defmethod oauth/build :instagram
  [service]
  (assoc service
         :request-url "https://api.instagram.com/oauth/authorize"
         :access-url "https://api.instagram.com/oauth/access_token"))

(defmethod oauth/id :instagram
  [requestor]
  (-> {:url "https://api.instagram.com/v1/users/self"}
      requestor 
      :body 
      cheshire.core/parse-string
      (get-in ["data" "id"])))

(defmethod oauth/activate :instagram
  [{access-url :access-url :as service} record auth-code]
  (lib/do-activate service record auth-code access-url lib/v2-json-parser))
