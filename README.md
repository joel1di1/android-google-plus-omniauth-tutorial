##How to authenticate an Android application to a Rails server with [Omniauth](https://github.com/intridea/omniauth)

Lately, I have been in charge of the Android application development at elCurator. One of the new feature we wanted was to be able to sign in with a Google account. At first I thought it would be easy to implement it with the Google+ SDK, and theoritically, it was. But in practice I ran into several issues which made me think that it is not trivial at all !

I also noticed that the documentations and tutorials I could find on the web could be a little confusing, and couldn't find a tutorial for my specific need.

This publication is a tutorial which explains, step by step, how to make an Android application authenticate to a Rails server, using the gem [Omniauth](https://github.com/intridea/omniauth).

###Run the sample

I made a sample Android application and a sample Rails server to illustrate what I am talking about in this article.

Here are the instructions to run it on your machine.

First of all :

	$ git clone https://github.com/elcurator/android-google-plus-omniauth-tutorial
	$ cd android-google-plus-omniauth-tutorial

For the rails server :

	$ cd web
	$ echo "GOOGLE_KEY=your_google_key_here" >> .env
	$ echo "GOOGLE_SECRET=your_google_secret_here" >> .env
	$ bundle
	$ foreman start
	
For the android application :

In the `app.gradle` file

- replace the `BASE_URL` config field by your server url.
- replace the `GOOGLE_SERVER_CLIENT_ID` config field by your web application client id.

The project use gradle. You can either build it with gradle and run it on your android device manually, or open it with AndroidStudio and run it directly from there.

###The flow

Let's specify a little the flow we need to implement.

![Server side code flow](https://developers.google.com/+/images/server_side_code_flow.png)

On the above diagram, the **Client** is our **Android application**, and the **Server** is our **Rails server**.

From now on, we are going to follow this diagram step by step.

###User clicks the sign-in button

Obviously, this first part needs to be implemented in our Android application. Let's use the Google+ SDK to do so.

- To be able to use the Google+ SDK, follow [this documentation](https://developers.google.com/+/mobile/android/getting-started?hl=en).

> 	Notes : 
> 
>  - To register the installed application in the Google console, you need to specify a *SHA-1 fingerprint*. In the documentation they tell to use the default debug keystore. **Don't use it for a signed APK, it won't work !**. To generate a signed APK, you must use your own keystore. To work properly with Google+ OAuth, the *SHA-1 fingerprint* must be generated from the keystore you used to sign your APK.
> 
> - If you are using Gradle with plural flavors in your `build.gradle` file, be carefull to put the adequate flavor's `applicationId` in the `package name` field when creating the installed application in the Google console.
> 
> - If you are using Android Studio with Gradle, just add the play-services dependency in your `build.gradle` file:
>
		compile 'com.google.android.gms:play-services:7.0.0'

>	  [Note you can now use some specific parts of the play-services if you don't use every google service.](http://developer.android.com/google/play-services/setup.html?hl=en)

- Remember that we want to request a one-time code, which will then be used by our Rails server. To do so, we will need to specify which server we want to grant our server an access to the user's Google+ informations. That means we need to register a web application client in the [Google console](https://console.developers.google.com/project) under the same project than our installed application.

- You can now add the sign-in button by following [this documentation](https://developers.google.com/+/mobile/android/sign-in?hl=en). Be carefull, the first part of the documentation tells you how to connect your client directly to Google+, but what we want is to **enable our Rails server to access to our client Google+ informations**. You will need to follow what's said in the **last part of the documentation** (*Enable server-side API access for your app*)

> 	Note :
> 	To request the one-time code, you need to use a **scope**. In this scope, you need to specify a **server client id**. This id is actually the one you got from the Google console in the previous step.

If you correctly followed the documentation linked above, you should be able to successfully request a one-time code with the Google+ SDK, meaning we are now at the step 4 of the flow diagram.

###Client sends code to server

To implement this step, we need a route we can call from our client to post the one-time token, the access token and the client id.

Let's see what we need on the server side to implement this route.

- First, add this line in your `Gemfile` :

	`gem 'omniauth-google-oauth2', github: 'zquestz/omniauth-google-oauth2', branch: 'master'`
	
	I made a [pull request](https://github.com/zquestz/omniauth-google-oauth2/pull/165) to make omniauth-google-oauth2 respond to our needs. It has not been integrated in the last version yet, that's why we need to directly fetch the repository.
	
- Everything you need to setup `omniauth-google-oauth2` is described in [the gem's documentation](https://github.com/zquestz/omniauth-google-oauth2).

> 	Notes :
> 	
> 	- The scope used to setup omniauth must be the same than the one used to make the authorization request from the Android application.
> 	- The **google client id** and the **google client secret** used to configure Omniauth are from the web application client we created in the first part of this tutorial.

- Create an action in your `SessionController` like this:

		def create_from_google_oauth2
			// get the user data prealably fetched by omniauth-google-oauth2
			user_google_data = request_env['omniauth.auth']['info'].to_hash
			
			// do whatever you want with the user data
			
			
		end

- Create a callback route in your `SessionController` like this :
	
		get '/auth/:provider/callback', to: 'sessions#create_from_google_oauth2', as: :user_omniauth_callback 
	
		