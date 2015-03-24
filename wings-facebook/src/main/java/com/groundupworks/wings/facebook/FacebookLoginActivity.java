package com.groundupworks.wings.facebook;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;

import com.facebook.widget.LoginButton;

/**
 * {@link android.app.Activity} with Facebook login button.
 */
public class FacebookLoginActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.facebook_activity_login);

        LoginButton loginButton = (LoginButton) findViewById(R.id.login_button);
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setResult(Activity.RESULT_OK);
                finish();
            }
        });
    }
}
