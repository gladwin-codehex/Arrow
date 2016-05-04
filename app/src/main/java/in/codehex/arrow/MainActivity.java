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

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;
import com.google.maps.android.PolyUtil;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.text.Html;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import in.codehex.arrow.app.AppController;
import in.codehex.arrow.app.Config;
import in.codehex.arrow.model.DirectionItem;

public class MainActivity extends AppCompatActivity implements TextToSpeech.OnInitListener,
        LocationListener, GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    SharedPreferences userPreferences, dataPreferences;
    RelativeLayout mainLayout;
    Intent intent;
    String name;
    GoogleApiClient googleApiClient;
    LocationRequest locationRequest;
    TextToSpeech textToSpeech;
    String destination;
    TextView textInstruction;
    TelephonyManager telephonyManager;
    double lat, lng;
    List<DirectionItem> directionItemList;
    boolean isDestinationAvailable = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initObjects();
        prepareObjects();
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech.setLanguage(Locale.getDefault());
            textToSpeech.setPitch(0.8f);
            textToSpeech.setSpeechRate(0.8f);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("data", dataPreferences.getString(Config.KEY_PREF_DATA, null));
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedState) {
        super.onRestoreInstanceState(savedState);
        processDirectionData(savedState.getString("data"));
    }

    @Override
    public void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case Config.REQUEST_SPEECH_INPUT:
                if (resultCode == RESULT_OK && data != null) {
                    ArrayList<String> result =
                            data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (result.get(0).equalsIgnoreCase("emergency")) {
                        String phone = userPreferences.getString(Config.KEY_PREF_PHONE, null);
                        String message = "Emergency!\n"
                                + userPreferences.getString(Config.KEY_PREF_NAME, null)
                                + " is at http://maps.google.com/maps?q=" + lat + "," + lng;
                        try {
                            SmsManager smsManager = SmsManager.getDefault();
                            smsManager.sendTextMessage(phone, null, message, null, null);
                            textToSpeech.speak("Emergency alert sent!",
                                    TextToSpeech.QUEUE_FLUSH, null, null);
                        } catch (Exception e) {
                            textToSpeech.speak("Unable to send alert message!",
                                    TextToSpeech.QUEUE_FLUSH, null, null);
                        }
                    } else if (isDestinationAvailable) {
                        if (result.get(0).equalsIgnoreCase("yes")) {
                            processDirection();
                            isDestinationAvailable = false;
                        } else {
                            textToSpeech.speak(getString(R.string.prompt_speech_input_initial),
                                    TextToSpeech.QUEUE_FLUSH, null, Config.UTTERANCE_ID_INITIAL);
                            isDestinationAvailable = false;
                        }
                    } else {
                        speakData(result.get(0));
                    }
                }
                break;
            case Config.REQUEST_CHECK_TTS:
                if (resultCode != TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
                    intent = new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                    startActivity(intent);
                }
                break;
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (googleApiClient != null)
            if (!googleApiClient.isConnected())
                googleApiClient.connect();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (googleApiClient != null)
            if (googleApiClient.isConnected())
                googleApiClient.disconnect();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (googleApiClient != null)
            if (googleApiClient.isConnected())
                stopLocationUpdates();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (googleApiClient != null)
            if (googleApiClient.isConnected())
                startLocationUpdates();
    }

    @Override
    public void onConnected(Bundle bundle) {
        startLocationUpdates();
    }

    @Override
    public void onConnectionSuspended(int i) {
        googleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
    }

    @Override
    public void onLocationChanged(Location loc) {
        lat = loc.getLatitude();
        lng = loc.getLongitude();
        LatLng latLng = new LatLng(lat, lng);

        Location userLocation = new Location("user");
        userLocation.setLatitude(lat);
        userLocation.setLongitude(lng);

        if (getBatteryLevel() < 20) {
            textToSpeech.speak("Your battery is at " + (int) getBatteryLevel()
                    + "%", TextToSpeech.QUEUE_FLUSH, null, null);
        } else if (!directionItemList.isEmpty() && !isDestinationAvailable) {
            for (int i = 0; i < directionItemList.size(); i++) {
                String polyline = directionItemList.get(i).getPolyline();
                List<LatLng> decodedPath = PolyUtil.decode(polyline);
                if (PolyUtil.isLocationOnPath(latLng, decodedPath, true, 40)) {
                    double endLat = directionItemList.get(i).getEndLat();
                    double endLng = directionItemList.get(i).getEndLng();
                    Location endLocation = new Location("end");
                    endLocation.setLatitude(endLat);
                    endLocation.setLongitude(endLng);

                    float distance = userLocation.distanceTo(endLocation);

                    if (distance <= 20)
                        updateLocation();

                    if (distance <= 20 && (i + 1) == directionItemList.size()) {
                        String data = "You have reached your final destination.";
                        textToSpeech.speak(data, TextToSpeech.QUEUE_FLUSH, null, null);
                        textInstruction.setText(data);
                        updateLocation();
                        dataPreferences.edit().clear().apply();
                        directionItemList.clear();
                    } else if (!directionItemList.get(i).isPathInstruction()) {
                        String data = directionItemList.get(i).getData();
                        textToSpeech.speak(data, TextToSpeech.QUEUE_FLUSH, null, null);
                        textInstruction.setText(data);
                        directionItemList.get(i).setPathInstruction(true);
                    } else {
                        textToSpeech.speak("Walk straight for the distance of "
                                        + (int) distance + " meters.",
                                TextToSpeech.QUEUE_FLUSH, null, null);
                    }
                } /*else {
                    List<Float> list = new ArrayList<>();
                    for (int j = 0; j < decodedPath.size(); j++) {
                        Location endLocation = new Location("end");
                        endLocation.setLatitude(decodedPath.get(j).latitude);
                        endLocation.setLongitude(decodedPath.get(j).longitude);

                        list.add(userLocation.distanceTo(endLocation));
                    }
                    if (!list.isEmpty()) {
                        Collections.sort(list);
                        textToSpeech.speak("You are " +
                                        (int) list.get(0).floatValue() +
                                        " meters away from the correct path.",
                                TextToSpeech.QUEUE_FLUSH, null, null);
                    }
                }*/
            }
        }
    }

    /**
     * Initialize the objects
     */
    private void initObjects() {
        mainLayout = (RelativeLayout) findViewById(R.id.main);
        textInstruction = (TextView) findViewById(R.id.instruction);

        directionItemList = new ArrayList<>();
        userPreferences = getSharedPreferences(Config.PREF_USER, MODE_PRIVATE);
        dataPreferences = getSharedPreferences(Config.PREF_DATA, MODE_PRIVATE);
        telephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
    }

    /**
     * Implement and manipulate the objects
     */
    private void prepareObjects() {
        if (loginCheck()) {
            name = userPreferences.getString(Config.KEY_PREF_NAME, Config.PREF_DEFAULT_NAME);

            checkTts();

            textToSpeech = new TextToSpeech(this, this);
            textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {

                }

                @Override
                public void onDone(String utteranceId) {
                    switch (utteranceId) {
                        case Config.UTTERANCE_ID_INITIAL:
                            promptSpeechInput();
                            break;
                        case Config.UTTERANCE_ID_CONFIRMATION:
                            promptSpeechInput();
                            break;
                    }
                }

                @Override
                public void onError(String utteranceId) {

                }
            });

            mainLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    textToSpeech.speak(getString(R.string.prompt_speech_input_initial),
                            TextToSpeech.QUEUE_FLUSH, null, Config.UTTERANCE_ID_INITIAL);
                }
            });

            if (checkPlayServices())
                buildGoogleApiClient();

            createLocationRequest();

            if (!isGPSEnabled(getApplicationContext()))
                showAlertGPS();
        }
    }

    /**
     * Check whether Text to Speech is available. If not, request user to download and install it.
     */
    private void checkTts() {
        intent = new Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        startActivityForResult(intent, Config.REQUEST_CHECK_TTS);
    }

    /**
     * Prompt the user to say something
     */
    private void promptSpeechInput() {
        textToSpeech.stop();
        intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        try {
            startActivityForResult(intent, Config.REQUEST_SPEECH_INPUT);
        } catch (ActivityNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * Speak the data passed from the activity result for speech input.
     *
     * @param data the text to speak
     */
    private void speakData(String data) {
        destination = data;
        isDestinationAvailable = true;
        String mData = "Do you want to go to " + data + "?";
        textToSpeech.speak(mData, TextToSpeech.QUEUE_ADD, null, Config.UTTERANCE_ID_CONFIRMATION);
        Toast.makeText(MainActivity.this, data, Toast.LENGTH_SHORT).show();
    }

    /**
     * Obtain the direction from the google directions API
     */
    private void processDirection() {
        textToSpeech.speak("Obtaining direction. Please wait.",
                TextToSpeech.QUEUE_FLUSH, null, null);
        String url = null;
        try {
            url = Config.URL_API_DIRECTIONS + "origin=" + lat + "," + lng +
                    "&destination=" + URLEncoder.encode(destination, "utf-8")
                    + "&mode=walking" + "&key=" + Config.BROWSER_KEY;
            System.out.println(url);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        final StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        dataPreferences.edit().putString(Config.KEY_PREF_DATA, response).apply();
                        processDirectionData(response);
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                textToSpeech.speak("Network error! Please check your internet connection!",
                        TextToSpeech.QUEUE_FLUSH, null, null);
                error.printStackTrace();
            }
        });
        AppController.getInstance().addToRequestQueue(stringRequest, "direction");
    }

    /**
     * Parse the JSON object and create an array list of the instructions
     *
     * @param response the json response from the google directions API
     */
    private void processDirectionData(String response) {
        directionItemList.clear();
        try {
            JSONObject json = new JSONObject(response);
            JSONArray array = json.getJSONArray("routes");
            JSONObject routes = array.getJSONObject(0);
            JSONArray legs = routes.getJSONArray("legs");
            JSONObject jsonObject = legs.getJSONObject(0);
            String source = jsonObject.getString("start_address");
            String destination = jsonObject.getString("end_address");
            JSONArray steps = jsonObject.getJSONArray("steps");
            for (int i = 0; i < steps.length(); i++) {
                JSONObject object = steps.getJSONObject(i);
                JSONObject distance = object.getJSONObject("distance");
                String mDistance = distance.getString("text");
                JSONObject duration = object.getJSONObject("duration");
                String mDuration = duration.getString("text");
                String mInstruction = object.getString("html_instructions");
                String data = "Instruction: " + Html.fromHtml(mInstruction)
                        + ". Distance: " + mDistance + ". Duration: " + mDuration + ".";
                JSONObject startLocation = object.getJSONObject("start_location");
                double startLat = startLocation.getDouble("lat");
                double startLng = startLocation.getDouble("lng");
                JSONObject endLocation = object.getJSONObject("end_location");
                double endLat = endLocation.getDouble("lat");
                double endLng = endLocation.getDouble("lng");
                JSONObject polyline = object.getJSONObject("polyline");
                String points = polyline.getString("points");
                directionItemList.add(new DirectionItem(data, startLat, startLng,
                        endLat, endLng, points, false));
                updateRecentSearch(source, destination);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (directionItemList.isEmpty())
            textToSpeech.speak("No direction instruction is available",
                    TextToSpeech.QUEUE_FLUSH, null, null);
    }

    /**
     * Updates the current location of the user to the database
     */
    private void updateLocation() {
        StringRequest stringRequest = new StringRequest(Request.Method.POST,
                Config.URL_API, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                try {
                    JSONObject jsonObject = new JSONObject(response);
                    String message = jsonObject.getString("message");
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                updateLocation();
                Toast.makeText(MainActivity.this,
                        "Network error! Please check your internet connection!",
                        Toast.LENGTH_SHORT).show();
            }
        }) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("tag", "location");
                params.put("imei", telephonyManager.getDeviceId());
                params.put("latitude", String.valueOf(lat));
                params.put("longitude", String.valueOf(lng));
                return params;
            }
        };
        AppController.getInstance().addToRequestQueue(stringRequest, "location");
    }

    /**
     * Updates the recent directions search to the database
     */
    private void updateRecentSearch(final String source, final String destination) {
        StringRequest stringRequest = new StringRequest(Request.Method.POST,
                Config.URL_API, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                try {
                    JSONObject jsonObject = new JSONObject(response);
                    String message = jsonObject.getString("message");
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                updateRecentSearch(source, destination);
                Toast.makeText(MainActivity.this,
                        "Network error! Please check your internet connection!",
                        Toast.LENGTH_SHORT).show();
            }
        }) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("tag", "arrow");
                params.put("imei", telephonyManager.getDeviceId());
                params.put("source", source);
                params.put("destination", destination);
                return params;
            }
        };
        AppController.getInstance().addToRequestQueue(stringRequest, "arrow");
    }

    /**
     * Checks whether the user has logged in. If not, Login Activity is started.
     */
    private boolean loginCheck() {
        if (!userPreferences.contains(Config.KEY_PREF_IMEI)) {
            intent = new Intent(this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return false;
        } else return true;
    }

    /**
     * display an alert to notify the user that GPS has to be enabled
     */
    private void showAlertGPS() {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
        alertDialog.setTitle("Enable GPS");
        alertDialog.setMessage("GPS service is not enabled." +
                " Do you want to go to location settings?");
        alertDialog.setPositiveButton("Settings", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                startActivity(intent);
            }
        });
        alertDialog.show();
    }

    /**
     * @param context context of the MainActivity class
     * @return true if GPS is enabled else false
     */
    private boolean isGPSEnabled(Context context) {
        LocationManager locationManager = (LocationManager)
                context.getSystemService(Context.LOCATION_SERVICE);
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

    /**
     * initializes and implements location request object
     */
    private void createLocationRequest() {
        locationRequest = new LocationRequest();
        locationRequest.setInterval(Config.UPDATE_INTERVAL);
        locationRequest.setFastestInterval(Config.FASTEST_INTERVAL);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    /**
     * initializes the google api client
     */
    protected synchronized void buildGoogleApiClient() {
        googleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    /**
     * checks for the availability of google play services functionality
     *
     * @return true if play services is enabled else false
     */
    private boolean checkPlayServices() {
        GoogleApiAvailability googleApiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = googleApiAvailability
                .isGooglePlayServicesAvailable(this);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (googleApiAvailability.isUserResolvableError(resultCode)) {
                googleApiAvailability.getErrorDialog(this, resultCode,
                        Config.REQUEST_PLAY_SERVICES).show();
            } else {
                Toast.makeText(getApplicationContext(),
                        "This device is not supported.", Toast.LENGTH_LONG).show();
                finish();
            }
            return false;
        }
        return true;
    }

    /**
     * start receiving location updates
     */
    private void startLocationUpdates() {
        // marshmallow runtime location permission
        if (ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED) {
            LocationServices.FusedLocationApi
                    .requestLocationUpdates(googleApiClient, locationRequest, this);
        } else if (ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    Config.PERMISSION_ACCESS_FINE_LOCATION);
        }
    }

    /**
     * stop receiving location updates
     */
    private void stopLocationUpdates() {
        LocationServices.FusedLocationApi.removeLocationUpdates(googleApiClient, this);
    }

    /**
     * Get the battery status of the device
     *
     * @return the battery level of the device
     */
    private float getBatteryLevel() {
        Intent batteryIntent = registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = -1, scale = -1;
        if (batteryIntent != null) {
            level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        }

        // Error checking that probably isn't needed but I added just in case.
        if (level == -1 || scale == -1) {
            return 50.0f;
        }

        return ((float) level / (float) scale) * 100.0f;
    }
}
