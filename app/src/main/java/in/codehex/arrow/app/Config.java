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

package in.codehex.arrow.app;

/**
 * Created by Bobby on 13-04-2016
 */
public class Config {

    public static final String URL_API = "http://arrow.zordec.com/api.php";
    public static final String URL_API_DIRECTIONS = "https://maps.googleapis.com/" +
            "maps/api/directions/json?";

    public static final String PREF_USER = "user";
    public static final String PREF_DATA = "data";
    public static final String KEY_PREF_DATA = "data";
    public static final String KEY_PREF_NAME = "name";
    public static final String KEY_PREF_PHONE = "phone";
    public static final String KEY_PREF_IMEI = "imei";
    public static final String PREF_DEFAULT_NAME = "codehex";

    public static final int REQUEST_PLAY_SERVICES = 4;
    public static final int REQUEST_SPEECH_INPUT = 6;
    public static final int REQUEST_CHECK_TTS = 9;

    public static final int UPDATE_INTERVAL = 15000;
    public static final int FASTEST_INTERVAL = 10000;

    public static final int PERMISSION_ACCESS_FINE_LOCATION = 13;
    public static final int PERMISSION_READ_PHONE_STATE = 30;

    public static final String UTTERANCE_ID_INITIAL = "initial";
    public static final String UTTERANCE_ID_CONFIRMATION = "confirmation";

    public static final String BROWSER_KEY = "AIzaSyAdz0K5C3qbDBS53EyfxIXRtqeleVMPOXg";
}