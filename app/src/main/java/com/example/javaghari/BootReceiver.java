package com.example.javaghari;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(intent.getAction()))) {
            Log.d(TAG, "Device booted, attempting to re-schedule alarms.");

            SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);

            // Sunrise Alarm re-scheduling
            boolean isSunriseAlarmSet = prefs.getBoolean(MainActivity.KEY_SUNRISE_ALARM_SET, false);
            long storedSunriseTimeMillis = prefs.getLong(MainActivity.KEY_NEXT_SUNRISE_TIME_MILLIS, 0L);

            if (isSunriseAlarmSet && storedSunriseTimeMillis != 0L) {
                Log.d(TAG, "Found previously set sunrise alarm at " +
                        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.getDefault()).format(new Date(storedSunriseTimeMillis)));
                scheduleAlarmOnBoot(context, storedSunriseTimeMillis, MainActivity.SUNRISE_ALARM_REQUEST_CODE, "sunrise");
            } else {
                Log.d(TAG, "No previously set sunrise alarm found or data incomplete.");
            }

            // Sunset Alarm re-scheduling
            boolean isSunsetAlarmSet = prefs.getBoolean(MainActivity.KEY_SUNSET_ALARM_SET, false);
            long storedSunsetTimeMillis = prefs.getLong(MainActivity.KEY_NEXT_SUNSET_TIME_MILLIS, 0L);

            if (isSunsetAlarmSet && storedSunsetTimeMillis != 0L) {
                Log.d(TAG, "Found previously set sunset alarm at " +
                        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.getDefault()).format(new Date(storedSunsetTimeMillis)));
                scheduleAlarmOnBoot(context, storedSunsetTimeMillis, MainActivity.SUNSET_ALARM_REQUEST_CODE, "sunset");
            } else {
                Log.d(TAG, "No previously set sunset alarm found or data incomplete.");
            }
        }
    }

    private void scheduleAlarmOnBoot(Context context, long storedAlarmTimeMillis, int requestCode, String alarmType) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent alarmIntent = new Intent(context, AlarmReceiver.class);
        alarmIntent.putExtra("alarm_type", alarmType);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        long finalScheduleTime = storedAlarmTimeMillis;
        long currentTime = System.currentTimeMillis();

        if (finalScheduleTime <= currentTime) {
            Log.d(TAG, "Stored " + alarmType + " alarm time is in the past. Adjusting for tomorrow.");
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(storedAlarmTimeMillis);
            calendar.add(Calendar.DAY_OF_YEAR, 1);
            finalScheduleTime = calendar.getTimeInMillis();
            Log.d(TAG, "Re-scheduled " + alarmType + " for: " +
                    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.getDefault()).format(new Date(finalScheduleTime)));

            // Update the stored time in SharedPreferences immediately for consistency
            SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            if (alarmType.equals("sunrise")) {
                editor.putLong(MainActivity.KEY_NEXT_SUNRISE_TIME_MILLIS, finalScheduleTime);
            } else {
                editor.putLong(MainActivity.KEY_NEXT_SUNSET_TIME_MILLIS, finalScheduleTime);
            }
            editor.apply();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.w(TAG, "Exact alarm permission not granted. Cannot re-schedule " + alarmType + " alarm precisely on boot.");
                // Optionally, send a simple notification to the user here to inform them that the alarm couldn't be re-scheduled.
                return;
            }
        }

        alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                finalScheduleTime,
                pendingIntent
        );
        Log.d(TAG, alarmType + " alarm re-scheduled on boot.");
    }
}