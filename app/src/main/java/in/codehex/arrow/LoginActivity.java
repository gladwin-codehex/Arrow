/*
 * Copyright 2016 Bobby
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package in.codehex.arrow;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.telephony.TelephonyManager;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import in.codehex.arrow.app.AppController;
import in.codehex.arrow.app.Config;

public class LoginActivity extends AppCompatActivity {

    EditText editName, editPhone;
    FloatingActionButton fab;
    SharedPreferences userPreferences;
    TelephonyManager telephonyManager;
    ProgressDialog progressDialog;
    Intent intent;
    String name, phone, imei;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        initObjects();
        prepareObjects();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],
                                           @NonNull int grantResults[]) {
        switch (requestCode) {
            case Config.PERMISSION_READ_PHONE_STATE:
                if (grantResults.length > 0 &&
                        grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    imei = telephonyManager.getDeviceId();
                } else {
                    Toast.makeText(this, "Permission request denied", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    /**
     * Initialize the objects
     */
    private void initObjects() {
        editName = (EditText) findViewById(R.id.name);
        editPhone = (EditText) findViewById(R.id.phone);
        fab = (FloatingActionButton) findViewById(R.id.fab);

        userPreferences = getSharedPreferences(Config.PREF_USER, MODE_PRIVATE);
        telephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        progressDialog = new ProgressDialog(this);
    }

    /**
     * Implement and manipulate the objects
     */
    private void prepareObjects() {
        progressDialog.setMessage("Authenticating..");
        progressDialog.setCancelable(false);

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                name = editName.getText().toString().trim();
                phone = editPhone.getText().toString().trim();

                // marshmallow runtime Telephone permission
                if (ContextCompat.checkSelfPermission(getApplicationContext(),
                        Manifest.permission.READ_PHONE_STATE) ==
                        PackageManager.PERMISSION_GRANTED) {
                    imei = telephonyManager.getDeviceId();
                    processData();
                } else if (ContextCompat.checkSelfPermission(getApplicationContext(),
                        Manifest.permission.READ_PHONE_STATE) !=
                        PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(LoginActivity.this,
                            new String[]{Manifest.permission.READ_PHONE_STATE},
                            Config.PERMISSION_READ_PHONE_STATE);
                }
            }
        });
    }

    /**
     * Check whether the name is empty or length and then set the error if the conditions
     * are satisfied.
     */
    private void processData() {
        if (name.isEmpty())
            editName.setError(getResources().getString(R.string.error_name_empty));
        else if (name.length() < 3)
            editName.setError(getResources().getString(R.string.error_name_length));
        else if (phone.isEmpty())
            editPhone.setError(getResources().getString(R.string.error_phone_empty));
        else if (phone.length() < 10)
            editPhone.setError(getResources().getString(R.string.error_phone_length));
        else processLogin();
    }

    /**
     * Authenticates the user. Sends HTTP POST request to the server.
     */
    private void processLogin() {
        showProgressDialog();

        StringRequest stringRequest = new StringRequest(Request.Method.POST,
                Config.URL_API, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                hideProgressDialog();
                try {
                    JSONObject jsonObject = new JSONObject(response);
                    int error = jsonObject.getInt("error");
                    String message = jsonObject.getString("message");

                    Toast.makeText(LoginActivity.this, message, Toast.LENGTH_SHORT).show();

                    if (error == 0) {
                        SharedPreferences.Editor editor = userPreferences.edit();
                        editor.putString(Config.KEY_PREF_NAME, name);
                        editor.putString(Config.KEY_PREF_PHONE, phone);
                        editor.putString(Config.KEY_PREF_IMEI, imei);
                        editor.apply();

                        intent = new Intent(LoginActivity.this, MainActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK
                                | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                hideProgressDialog();
                Toast.makeText(LoginActivity.this,
                        "Network error! Please check your internet connection!",
                        Toast.LENGTH_SHORT).show();
            }
        }) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("tag", "user");
                params.put("name", name);
                params.put("imei", imei);
                return params;
            }
        };
        AppController.getInstance().addToRequestQueue(stringRequest, "user");
    }

    /**
     * displays progress dialog if dialog is not shown
     */
    private void showProgressDialog() {
        if (!progressDialog.isShowing()) {
            progressDialog.show();
        }
    }

    /**
     * hides progress dialog if dialog is shown
     */
    private void hideProgressDialog() {
        if (progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }
}
