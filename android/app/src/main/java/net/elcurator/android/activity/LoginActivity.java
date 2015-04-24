package net.elcurator.android.activity;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.plus.Plus;
import com.google.gson.JsonObject;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.ion.Ion;

import net.elcurator.android.BuildConfig;
import net.elcurator.android.R;

import java.io.IOException;

public class LoginActivity extends Activity implements View.OnClickListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    /**
     * In order to be able to access the user login and email
     */
    private static final String LOGIN_SCOPES = "https://www.googleapis.com/auth/plus.login https://www.googleapis.com/auth/userinfo.email";

    private static final String SCOPES = "oauth2:server:client_id:" + BuildConfig.GOOGLE_SERVER_CLIENT_ID + ":api_scope:" + LOGIN_SCOPES;

    /**
     * Request code used to invoke sign in user interactions
     */
    private static final int SIGN_IN_REQUEST_CODE = 0;

    private static final int AUTH_CODE_REQUEST_CODE = 2000;

    private GoogleApiClient googleApiClient;

    /**
     * True if the sign-in button was clicked.  When true, we know to resolve all
     * issues preventing sign-in without waiting.
     */
    private boolean signInClicked;

    /**
     * True if we are in the process of resolving a ConnectionResult
     */
    private boolean intentInProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_login);

        findViewById(R.id.sign_in_button).setOnClickListener(this);

        googleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Plus.API)
                .addScope(Plus.SCOPE_PLUS_LOGIN)
                .build();
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (googleApiClient.isConnected()) {
            googleApiClient.disconnect();
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        if (!intentInProgress) {
            if (signInClicked && result.hasResolution()) {
                // The user has already clicked 'sign-in' so we attempt to resolve all
                // errors until the user is signed in, or they cancel.
                try {
                    result.startResolutionForResult(this, SIGN_IN_REQUEST_CODE);
                    intentInProgress = true;
                } catch (IntentSender.SendIntentException e) {
                    // The intent was canceled before it was sent.  Return to the default
                    // state and attempt to connect to get an updated ConnectionResult.
                    intentInProgress = false;
                    googleApiClient.connect();
                }
            }
        }
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.sign_in_button && !googleApiClient.isConnecting()) {
            if (googleApiClient.isConnected()) {
                Plus.AccountApi.clearDefaultAccount(googleApiClient);
                googleApiClient.disconnect();
                Toast.makeText(this, "User is disconnected!", Toast.LENGTH_LONG).show();
            } else {
                signInClicked = true;
                googleApiClient.connect();
            }
        }
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        signInClicked = false;

        new AsyncTask<Object, Void, Void>() {
            @Override
            protected Void doInBackground(Object... params) {
                final String code = requestOneTimeCodeFromGoogle();

                if (code != null)
                    sendAuthorizationToServer(code);

                return null;
            }
        }.execute();
    }

    @Override
    public void onConnectionSuspended(int i) {
        googleApiClient.connect();
    }

    @Override
    protected void onActivityResult(int requestCode, int responseCode, Intent intent) {
        switch (requestCode) {
            case SIGN_IN_REQUEST_CODE:
                if (responseCode != RESULT_OK) {
                    signInClicked = false;
                }

                intentInProgress = false;

                if (!googleApiClient.isConnected()) {
                    googleApiClient.reconnect();
                }
                break;
            case AUTH_CODE_REQUEST_CODE:
                if (responseCode == RESULT_OK)
                    // the authorization is granted, now we retry to connect
                    googleApiClient.connect();
                break;
        }
    }

    /**
     * This method must be called on a background thread
     */
    private String requestOneTimeCodeFromGoogle() {
        try {
            return GoogleAuthUtil.getToken(
                    this,
                    Plus.AccountApi.getAccountName(googleApiClient),
                    SCOPES
            );
        } catch (IOException transientEx) {
            // network or server error, the call is expected to succeed if you try again later.
            // Don't attempt to call again immediately - the request is likely to
            // fail, you'll hit quotas or back-off.
            Toast.makeText(this,
                    "Network or server error, the call is expected to succeed if you try again later",
                    Toast.LENGTH_LONG
            ).show();
            return null;
        } catch (UserRecoverableAuthException e) {
            // Requesting an authorization code will always throw
            // UserRecoverableAuthException on the first call to GoogleAuthUtil.getToken
            // because the user must consent to offline access to their data.  After
            // consent is granted control is returned to your activity in onActivityResult
            // and the second call to GoogleAuthUtil.getToken will succeed.
            startActivityForResult(e.getIntent(), AUTH_CODE_REQUEST_CODE);
            return null;
        } catch (GoogleAuthException authEx) {
            // Failure. The call is not expected to ever succeed so it should not be
            // retried.
            Toast.makeText(
                    this,
                    "Permanent error, something is wrong with your configuration.",
                    Toast.LENGTH_LONG
            ).show();
            return null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void sendAuthorizationToServer(final String code) {
        Ion.with(this)
                .load(BuildConfig.BASE_URL + "/auth/google_oauth2/callback")
                .setBodyParameter("code", code)
                .setBodyParameter("redirect_uri", BuildConfig.GOOGLE_REDIRECT_URI)
                .asJsonObject()
                .setCallback(new FutureCallback<JsonObject>() {
                    @Override
                    public void onCompleted(Exception e, JsonObject result) {
                        // Invalidate the token as soon as the server consumed it.
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
    }
}
