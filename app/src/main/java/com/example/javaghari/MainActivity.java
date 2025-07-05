package com.example.javaghari;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {

    private TextView tvSunriseTime;
    private TextView tvSunsetTime;
    private Button btnToggleAlarm;
    private Button btnToggleAlarm1;
    private Button btnStopAlarm;
    private Spinner spinnerCities;

    private AlarmManager alarmManager;
    private PendingIntent sunriseAlarmIntent;
    private PendingIntent sunsetAlarmIntent;

    private boolean isSunriseAlarmActive = false;
    private boolean isSunsetAlarmActive = false;
    private String selectedCity = "";
    private long nextSunriseTimeMillis = 0L;
    private long nextSunsetTimeMillis = 0L;

    private static final String TAG = "MainActivity";
    private String API_KEY;
    private OkHttpClient client;

    // Constants for SharedPreferences and PendingIntents
    public static final int SUNRISE_ALARM_REQUEST_CODE = 123;
    public static final int SUNSET_ALARM_REQUEST_CODE = 124;
    public static final String PREFS_NAME = "SunriseAlarmPrefs";
    public static final String KEY_SUNRISE_ALARM_SET = "sunrise_alarm_set";
    public static final String KEY_SUNSET_ALARM_SET = "sunset_alarm_set";
    public static final String KEY_SELECTED_CITY = "selected_city";
    public static final String KEY_NEXT_SUNRISE_TIME_MILLIS = "next_sunrise_time_millis";
    public static final String KEY_NEXT_SUNSET_TIME_MILLIS = "next_next_sunset_time_millis";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvSunriseTime = findViewById(R.id.tvSunriseTime);
        tvSunsetTime = findViewById(R.id.tvSunsetTime);
        btnToggleAlarm = findViewById(R.id.btnToggleAlarm);
        btnToggleAlarm1 = findViewById(R.id.btnToggleAlarm1);
        btnStopAlarm = findViewById(R.id.btnStopAlarm);
        spinnerCities = findViewById(R.id.spinnerCities);

        alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        API_KEY = getString(R.string.openweathermap_api_key);
        client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        Intent sunriseIntent = new Intent(this, AlarmReceiver.class);
        sunriseIntent.putExtra("alarm_type", "sunrise");
        sunriseAlarmIntent = PendingIntent.getBroadcast(this, SUNRISE_ALARM_REQUEST_CODE, sunriseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent sunsetIntent = new Intent(this, AlarmReceiver.class);
        sunsetIntent.putExtra("alarm_type", "sunset");
        sunsetAlarmIntent = PendingIntent.getBroadcast(this, SUNSET_ALARM_REQUEST_CODE, sunsetIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        setupCitySpinner();
        loadAlarmState(); // Load state before setting listeners

        btnToggleAlarm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isSunriseAlarmActive) {
                    cancelSunriseAlarm();
                } else {
                    checkExactAlarmPermissionAndSet("sunrise");
                }
            }
        });

        btnToggleAlarm1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isSunsetAlarmActive) {
                    cancelSunsetAlarm();
                } else {
                    checkExactAlarmPermissionAndSet("sunset");
                }
            }
        });

        btnStopAlarm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopAlarmRingtone();
            }
        });

        updateUI(); // Initial UI state based on loaded preferences
    }

    private void setupCitySpinner() {
        String[] cities = getResources().getStringArray(R.array.indian_cities);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, cities);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCities.setAdapter(adapter);
        spinnerCities.setOnItemSelectedListener(this);

        // Set spinner to previously selected city or default
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String storedCity = prefs.getString(KEY_SELECTED_CITY, "Delhi, DL"); // Default to Delhi
        int defaultCityPosition = -1;
        for (int i = 0; i < cities.length; i++) {
            if (cities[i].equals(storedCity)) {
                defaultCityPosition = i;
                break;
            }
        }

        if (defaultCityPosition != -1) {
            spinnerCities.setSelection(defaultCityPosition);
            selectedCity = cities[defaultCityPosition];
        } else {
            selectedCity = cities[0]; // Fallback to first city if stored one not found
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        String newlySelectedCity = parent.getItemAtPosition(position).toString();
        if (!selectedCity.equals(newlySelectedCity)) {
            selectedCity = newlySelectedCity;
            Log.d(TAG, "Selected city: " + selectedCity);
            // If any alarm is active and city changes, cancel them and prompt re-enable
            if (isSunriseAlarmActive || isSunsetAlarmActive) {
                Toast.makeText(this, "Alarms will be recalculated for " + selectedCity + ". Please re-enable.", Toast.LENGTH_SHORT).show();
                cancelSunriseAlarm();
                cancelSunsetAlarm();
            }
            saveAlarmState(); // Save the new selected city immediately
            updateUI();
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // Do nothing
    }

    private void checkExactAlarmPermissionAndSet(final String alarmType) {
        boolean exactAlarmPermissionGranted;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            exactAlarmPermissionGranted = alarmManager.canScheduleExactAlarms();
        } else {
            exactAlarmPermissionGranted = true; // Not needed on older versions
        }

        if (!exactAlarmPermissionGranted) {
            Toast.makeText(this, "Please grant 'Alarms & reminders' permission in app settings.", Toast.LENGTH_LONG).show();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                startActivity(intent);
            }
        } else {
            fetchSunriseSunsetTimeAndSetAlarm(alarmType);
        }
    }

    private void fetchSunriseSunsetTimeAndSetAlarm(final String alarmType) {
        if (selectedCity.isEmpty()) {
            Toast.makeText(this, "Please select a city first.", Toast.LENGTH_SHORT).show();
            return;
        }

        final Button buttonToDisable = (alarmType.equals("sunrise")) ? btnToggleAlarm : btnToggleAlarm1;
        buttonToDisable.setEnabled(false); // Disable button during fetch

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String url = "https://api.openweathermap.org/data/2.5/weather?q=" + selectedCity + ",IN&appid=" + API_KEY + "&units=metric";
                    Request request = new Request.Builder().url(url).build();
                    Response response = client.newCall(request).execute();

                    if (response.isSuccessful() && response.body() != null) {
                        String responseBody = response.body().string();
                        JSONObject jsonObject = new JSONObject(responseBody);
                        JSONObject sys = jsonObject.getJSONObject("sys");
                        long sunriseUnix = sys.getLong("sunrise"); // Unix timestamp in UTC
                        long sunsetUnix = sys.getLong("sunset");   // Unix timestamp in UTC
                        long timezoneOffsetSeconds = jsonObject.getLong("timezone"); // Shift in seconds from UTC

                        // Calculate local sunrise time
                        Calendar sunriseCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                        sunriseCalendar.setTimeInMillis(sunriseUnix * 1000); // Convert to milliseconds

                        // Calculate local sunset time
                        Calendar sunsetCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                        sunsetCalendar.setTimeInMillis(sunsetUnix * 1000); // Convert to milliseconds

                        // Adjust for today vs tomorrow for Sunrise
                        long finalSunriseTimeMillis = sunriseCalendar.getTimeInMillis();
                        if (finalSunriseTimeMillis <= System.currentTimeMillis()) {
                            Log.d(TAG, "Today's sunrise has passed. Scheduling for tomorrow.");
                            sunriseCalendar.add(Calendar.DAY_OF_YEAR, 1);
                            finalSunriseTimeMillis = sunriseCalendar.getTimeInMillis();
                        }
                        nextSunriseTimeMillis = finalSunriseTimeMillis;

                        // Adjust for today vs tomorrow for Sunset
                        long finalSunsetTimeMillis = sunsetCalendar.getTimeInMillis();
                        if (finalSunsetTimeMillis <= System.currentTimeMillis()) {
                            Log.d(TAG, "Today's sunset has passed. Scheduling for tomorrow.");
                            sunsetCalendar.add(Calendar.DAY_OF_YEAR, 1);
                            finalSunsetTimeMillis = sunsetCalendar.getTimeInMillis();
                        }
                        nextSunsetTimeMillis = finalSunsetTimeMillis;

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (alarmType.equals("sunrise")) {
                                    setExactAlarm(nextSunriseTimeMillis, "sunrise");
                                    isSunriseAlarmActive = true;
                                } else { // alarmType == "sunset"
                                    setExactAlarm(nextSunsetTimeMillis, "sunset");
                                    isSunsetAlarmActive = true;
                                }
                                saveAlarmState(); // Save the updated alarm state and times
                                updateUI();
                            }
                        });
                    } else {
                        final String errorMessage = response.body() != null ? response.body().string() : response.message();
                        Log.e(TAG, "OpenWeatherMap API Error: " + response.code() + " - " + errorMessage);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "API Key not found", Toast.LENGTH_SHORT).show();
                                updateUI();
                            }
                        });
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Network error", e);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "Failed to get sunrise/sunset time : NETWORK ERROR", Toast.LENGTH_SHORT).show();
                            updateUI();
                        }
                    });
                } catch (Exception e) { // Catch JSONException and others
                    Log.e(TAG, "JSON parsing or other error", e);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "Failed to get sunrise/sunset time: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            updateUI();
                        }
                    });
                } finally {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            buttonToDisable.setEnabled(true); // Re-enable button
                        }
                    });
                }
            }
        }).start();
    }

    private void setExactAlarm(long timeInMillis, String type) {
        PendingIntent pendingIntent = (type.equals("sunrise")) ? sunriseAlarmIntent : sunsetAlarmIntent;
        String alarmTypeName = (type.equals("sunrise")) ? "Sunrise" : "Sunset";

        Log.d(TAG, "Setting " + alarmTypeName + " alarm for: " +
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.getDefault()).format(new Date(timeInMillis)));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent);
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent);
        }
        Toast.makeText(this, alarmTypeName + " alarm set successfully!", Toast.LENGTH_SHORT).show();
    }

    private void cancelSunriseAlarm() {
        alarmManager.cancel(sunriseAlarmIntent);
        isSunriseAlarmActive = false;
        nextSunriseTimeMillis = 0L; // Reset time
        saveAlarmState();
        updateUI();
        stopAlarmRingtone(); // Ensure ringtone stops if it was playing
        Toast.makeText(this, "Sunrise alarm cancelled.", Toast.LENGTH_SHORT).show();
    }

    private void cancelSunsetAlarm() {
        alarmManager.cancel(sunsetAlarmIntent);
        isSunsetAlarmActive = false;
        nextSunsetTimeMillis = 0L; // Reset time
        saveAlarmState();
        updateUI();
        stopAlarmRingtone(); // Ensure ringtone stops if it was playing
        Toast.makeText(this, "Sunset alarm cancelled.", Toast.LENGTH_SHORT).show();
    }

    private void stopAlarmRingtone() {
        Intent stopServiceIntent = new Intent(this, AlarmRingtoneService.class);
        stopService(stopServiceIntent);
        Log.d(TAG, "Stopped AlarmRingtoneService (if running).");
    }

    private void updateUI() {
        // Sunrise Alarm UI
        if (isSunriseAlarmActive && nextSunriseTimeMillis != 0L) {
            btnToggleAlarm.setText("Disable Sunrise Alarm");
            SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm (dd MMM)", Locale.getDefault());
            tvSunriseTime.setText("Next Sunrise: " + dateFormat.format(new Date(nextSunriseTimeMillis)));
        } else {
            btnToggleAlarm.setText("Enable Sunrise Alarm");
            tvSunriseTime.setText("Next Sunrise: --:--");
        }

        // Sunset Alarm UI
        if (isSunsetAlarmActive && nextSunsetTimeMillis != 0L) {
            btnToggleAlarm1.setText("Disable Sunset Alarm");
            SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm (dd MMM)", Locale.getDefault());
            tvSunsetTime.setText("Next Sunset: " + dateFormat.format(new Date(nextSunsetTimeMillis)));
        } else {
            btnToggleAlarm1.setText("Enable Sunset Alarm");
            tvSunsetTime.setText("Next Sunset: --:--");
        }

        // Stop Alarm Button visibility
        btnStopAlarm.setVisibility((isSunriseAlarmActive || isSunsetAlarmActive) ? View.VISIBLE : View.GONE);
    }

    private void saveAlarmState() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_SUNRISE_ALARM_SET, isSunriseAlarmActive);
        editor.putBoolean(KEY_SUNSET_ALARM_SET, isSunsetAlarmActive);
        editor.putString(KEY_SELECTED_CITY, selectedCity);
        editor.putLong(KEY_NEXT_SUNRISE_TIME_MILLIS, nextSunriseTimeMillis);
        editor.putLong(KEY_NEXT_SUNSET_TIME_MILLIS, nextSunsetTimeMillis);
        editor.apply();
        Log.d(TAG, "Alarm state saved: Sunrise Active=" + isSunriseAlarmActive + ", Sunset Active=" + isSunsetAlarmActive + ", City=" + selectedCity);
    }

    private void loadAlarmState() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        isSunriseAlarmActive = prefs.getBoolean(KEY_SUNRISE_ALARM_SET, false);
        isSunsetAlarmActive = prefs.getBoolean(KEY_SUNSET_ALARM_SET, false);
        selectedCity = prefs.getString(KEY_SELECTED_CITY, "Delhi, DL");
        nextSunriseTimeMillis = prefs.getLong(KEY_NEXT_SUNRISE_TIME_MILLIS, 0L);
        nextSunsetTimeMillis = prefs.getLong(KEY_NEXT_SUNSET_TIME_MILLIS, 0L);
        Log.d(TAG, "Alarm state loaded: Sunrise Active=" + isSunriseAlarmActive + ", Sunset Active=" + isSunsetAlarmActive + ", City=" + selectedCity);
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveAlarmState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Check if the exact alarm permission is granted every time the app comes to foreground
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Toast.makeText(this, "Please grant 'Alarms & reminders' permission for exact alarms.", Toast.LENGTH_LONG).show();
            isSunriseAlarmActive = false; // Mark as inactive if permission is revoked
            isSunsetAlarmActive = false;
            saveAlarmState(); // Save this state
            updateUI(); // Reflect the state if permission is missing
        } else {
            // Re-update UI on resume to reflect any potential changes or just refresh
            updateUI();
        }
    }
}