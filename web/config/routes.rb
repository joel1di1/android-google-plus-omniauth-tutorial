Rails.application.routes.draw do

  post '/auth/:provider/callback', to: 'sessions#create_from_google_oauth2'

end
