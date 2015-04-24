# Setup the google oauth2 provider
Rails.application.config.middleware.use OmniAuth::Builder do
  provider :google_oauth2, ENV['GOOGLE_KEY'], ENV['GOOGLE_SECRET'], provider_ignores_state: true
end

# Set the action to call in case of failure
OmniAuth.config.on_failure = SessionsController.action(:oauth_failure)
