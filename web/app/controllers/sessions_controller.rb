class SessionsController < ApplicationController
  skip_before_filter  :verify_authenticity_token, only: [:create_from_google_oauth2]


  def create_from_google_oauth2
    user_google_data = request.env['omniauth.auth']['info'].to_hash

    # You should use the session handler you prefer here to create a session
    @user = {
      email: user_google_data['email'],
      authentication_token: SecureRandom.hex
    }

    render 'create_success'
  end

  # Handles every oauth failures in one place
  def oauth_failure
    @error = request.env['omniauth.error']

    render 'create_fail', status: 401
  end
end