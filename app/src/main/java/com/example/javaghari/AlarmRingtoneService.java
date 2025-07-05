package com.example.javaghari;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class AlarmRingtoneService extends Service {

    private MediaPlayer mediaPlayer;
    private static final String TAG = "AlarmRingtoneService";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private static final long ALARM_DURATION_MS = 60 * 1000L; // 60 seconds in milliseconds

    // Notification Channel ID
    private static final String CHANNEL_ID = "sunrise_alarm_channel";
    private static final int NOTIFICATION_ID = 1; // Unique ID for the notification

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String alarmType = intent != null ? intent.getStringExtra("alarm_type") : "unknown";
        Log.d(TAG, "AlarmRingtoneService started for type: " + alarmType);

        // Create notification channel (required for Android 8.0+)
        createNotificationChannel();

        // Build the notification
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.outline_alarm_24) // Use a suitable icon (e.g., a bell icon)
                .setContentTitle(alarmType.substring(0, 1).toUpperCase() + alarmType.substring(1) + " Alarm!") // "Sunrise Alarm!" or "Sunset Alarm!"
                .setContentText("It's time for " + alarmType + "!")
                .setPriority(NotificationCompat.PRIORITY_HIGH) // Make it a high-priority notification
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setContentIntent(pendingIntent) // Tapping notification opens MainActivity
                .setAutoCancel(true) // Notification disappears when tapped
                .setDefaults(NotificationCompat.DEFAULT_ALL); // Vibrate, sound, etc. as per system settings

        // Start the service in the foreground with the notification
        startForeground(NOTIFICATION_ID, builder.build());

        // Stop any existing media player just in case
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }

        // Play default alarm sound
        mediaPlayer = MediaPlayer.create(this, Settings.System.DEFAULT_ALARM_ALERT_URI);
        if (mediaPlayer != null) {
            // Set the audio stream type to ALARM
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // For API 21+ using AudioAttributes is preferred
                android.media.AudioAttributes audioAttributes = new android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                mediaPlayer.setAudioAttributes(audioAttributes);
            } else {
                // For older APIs, use setAudioStreamType
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
            }

            mediaPlayer.setLooping(true); // Loop the sound
            mediaPlayer.start();
        } else {
            Log.e(TAG, "Failed to create MediaPlayer for default ringtone.");
        }

        // Schedule to stop the alarm after ALARM_DURATION_MS
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Alarm duration ended. Stopping service.");
                stopSelf(); // Stops the service, which will trigger onDestroy()
            }
        }, ALARM_DURATION_MS);

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "AlarmRingtoneService destroyed.");
        // Remove any pending callbacks to prevent memory leaks if service is destroyed prematurely
        handler.removeCallbacksAndMessages(null);
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        // Stop the foreground service and remove the notification
        stopForeground(true);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // This is a started service, not a bound service
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.channel_name); // Define in strings.xml
            String description = getString(R.string.channel_description); // Define in strings.xml
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            // Optionally, configure vibration, sound etc for the channel
            channel.enableVibration(true);
            // For channels, setting the sound here helps with system-level control
            // However, MediaPlayer's stream type takes precedence for the actual playback.
            // channel.setSound(Settings.System.DEFAULT_RINGTONE_URI, null); // Not strictly needed for MediaPlayer control, but good practice for the channel itself

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
}