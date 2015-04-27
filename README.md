#How to authenticate an android application to a rails server with [Omniauth](https://github.com/intridea/omniauth)

At elCuraotr, I have been in charge of the android application development lately. One of the new features we wanted was to be able to sign in with a Google account. At first I thought it would be easy to implement it with the Google+ SDK, and theoritically, it was. But in practice I ran into several issues which made me think that it is not trivial at all!

I also noticed that the documentations and tutorials I could find on the web could be a little confusing, and I couldn't find a tutorial for my specific need.

That's why I decided to write a tutorial to explain step by step, how to make an android application authenticate to a rails server, using the gem [Omniauth](https://github.com/intridea/omniauth).

##Run the sample

I made a sample android application and a sample rails server to illustrate what I am talking about in this article.

Here are the instructions to run it on your machine.

First of all:
	
	$ git clone https://github.com/elcurator/android-google-plus-omniauth-tutorial
	$ cd android-google-plus-omniauth-tutorial

For the rails server:

	$ cd web
	$ echo "GOOGLE_KEY=your_google_key_here" >> .env
	$ echo "GOOGLE_SECRET=your_google_secret_here" >> .env
	$ bundle
	$ foreman start
	
For the android application:

In the `app.gradle` file:

- replace the `BASE_URL` config field by your server url.
- replace the `GOOGLE_SERVER_CLIENT_ID` config field by your web application client id.

The project uses gradle. You can either build it with gradle and run it on your android device manually, or open it with AndroidStudio and run it directly from there.

##The flow

Let's specify a little the flow we need to implement.

![Server side code flow](https://developers.google.com/+/images/server_side_code_flow.png)

On the above diagram, the **client** is our **android application**, and the **server** is our **rails server**.

From now on, we are going to follow this diagram step by step.

###User clicks the sign-in button

Obviously, this first part needs to be implemented in our android application. Let's use the Google+ SDK to do so.

- To be able to use the Google+ SDK, follow [this documentation](https://developers.google.com/+/mobile/android/getting-started?hl=en).

> 	Notes: 
> 
>  - To register the installed application in the Google console, you need to specify a *SHA-1 fingerprint*. In the documentation they tell us to use the default debug keystore. **Don't use it for a signed APK, it won't work**! To generate a signed APK, you must use your own keystore. To work properly with Google+ OAuth, the *SHA-1 fingerprint* must be generated from the keystore you used to sign your APK.
> 
> - If you are using Gradle with plural flavors in your `build.gradle` file, be carefull to put the adequate flavor's `applicationId` in the `package name` field when creating the installed application in the Google console.
> 
> - If you are using android Studio with Gradle, just add the play-services dependency in your `build.gradle` file:
>
		compile 'com.google.android.gms:play-services:7.0.0'

>	  [Note you can now use some specific parts of the play-services if you don't use every google service.](http://developer.android.com/google/play-services/setup.html?hl=en)

- Remember that we want to request a one-time code, which will then be used by our rails server. To do so, we will need to specify which server we want to grant our server an access to the user's Google+ informations. That means we need to register a web application client in the [Google console](https://console.developers.google.com) under the same project than our installed application.

- You can now add the sign-in button by following [this documentation](https://developers.google.com/+/mobile/android/sign-in?hl=en). Be carefull, the first part of the documentation tells you how to connect your client directly to Google+, but what we want is to **enable our rails server to access to our client Google+ informations**. You will need to follow what's said in the **last part of the documentation** (*Enable server-side API access for your app*)

> 	Note:
> 	To request the one-time code, you need to use a **scope**. In this scope, you need to specify a **server client id**. This id is actually the one you got from the Google console in the previous step.

If you correctly followed the documentation linked above, you should be able to successfully request a one-time code with the Google+ SDK, meaning we are now at the step 4 of the flow diagram.

###Client sends code to server

To implement this step, we need a route we can call from our client to post a one-time code and a redirect uri. 

Let's see what we need on the server side to implement this route.

- First, add this line in your `Gemfile`:

		gem 'omniauth-google-oauth2', github: 'zquestz/omniauth-google-oauth2', branch: 'master'
	
	I made a [pull request](https://github.com/zquestz/omniauth-google-oauth2/pull/165) to make omniauth-google-oauth2 respond to our needs. It has not been integrated in the last version yet, that's why we need to directly fetch the repository.

- Create a `SessionsController` and add an action like this:
	```ruby
	def create_from_google_oauth2
		# Retrieve the user informations that omniauth fetched for us 
		user_google_data = request.env['omniauth.auth']['info'].to_hash

		# You should use the session handler you prefer here to create a session
		@user = {
			email: user_google_data['email'],
			authentication_token: SecureRandom.hex
		}
    		
		# Render a json success template
		render 'create_success'
  	end
  	```
  	    		
- Create the file `config/initializers/omniauth.rb` with this code:

	```ruby
	# Setup the google oauth2 provider
	Rails.application.config.middleware.use OmniAuth::Builder do
  		provider :google_oauth2, 'your web client id', 'your web client secret', provider_ignores_state: true
	end
	```

> 	Notes: 
> 
> - You can find the web client id and secret in the [Google console](https://console.developers.google.com).
> 
> - The web client id must be the same than the one used in our android application.

- Add this to the `routes.rb` file:
	```ruby
	post '/auth/:provider/callback', to: 'sessions#create_from_google_oauth2'	
	```
At this point, when we post on the route `/auth/google_oauth/callback` with a `code` and a `redirect_uri` in set in the body, omniauth fetches the user informations then calls our `create_from_google_oauth2` action.

- If the one-time code is already used, or if it is expired, Google responds with an `invalid_grant` error. Since we don't want to have to parse a big html error page in our client, we need to catch this error, and respond with a json formatted error.

	To do so, let's add an action in our `SessionsController`

	```ruby
	def oauth_failure
		# Retrieve the error
		@error = request.env['omniauth.error']
		
		# Render a json error template
		render 'create_fail', status: 401
  	end
  	```

	Then, add this code to our `config/initializers/omniauth.rb` file so omniauth knows which action to call when an error occurs:
	
		OmniAuth.config.on_failure = SessionsController.action(:oauth_failure)

- We are ready to call the authentication route from our client. Using the library Ion to perform the request, here is the code we can add to our login activity, right after having fetched the one-time code from Google:

	```java
	Ion.with(this)
		.load(BuildConfig.BASE_URL + "/auth/google_oauth2/callback")
		.setBodyParameter("code", code)
		.setBodyParameter("redirect_uri", BuildConfig.GOOGLE_REDIRECT_URI);
	```

### Server exchange one-time code for access and id tokens

It's actually what is `omniauth-google-oauth2` doing for us. Don't bother with this step.

### Server should confirm "fully logged in" to client

In the android application, replace the authentication request code we already used by this:

```java
Ion.with(this)
	.load(BuildConfig.BASE_URL + "/auth/google_oauth2/callback")
	.setBodyParameter("code", code)
	.setBodyParameter("redirect_uri", BuildConfig.GOOGLE_REDIRECT_URI)
	.asJsonObject()
	.setCallback(new FutureCallback<JsonObject>() {
		@Override
		public void onCompleted(Exception e, JsonObject result) {
			// Invalidate the code as soon as the server consumed it.
			GoogleAuthUtil.invalidateToken(getApplicationContext(), code);				
			Toast.makeText(
				LoginActivity.this,
				result.get("error") != null ?
					"error : " + result.get("description").getAsString() :
					"connected as : " + result.get("authentication_email").getAsString(),
				Toast.LENGTH_LONG
			).show();
		}
	});
```
	
> Note : we invalidate the one-time code as soon as it has been consumed by the server. Otherwise, the next time the user will try to authenticate, he will get the same code from Google, try to use it, and get an `invalid_grant` error.

## Your feedbacks are welcome !

Feel free to make a pull request if you want to add or clarify some informations.
