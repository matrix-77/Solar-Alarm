package com.example.javaghari;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class AlarmReceiver extends BroadcastReceiver {
    private static final String TAG = "AlarmReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String alarmType = intent.getStringExtra("alarm_type");
        if (alarmType == null) {
            alarmType = "unknown";
        }
        Log.d(TAG, "Alarm received for type: " + alarmType + "! Starting AlarmRingtoneService.");

        // Start the service to play the alarm sound
        Intent serviceIntent = new Intent(context, AlarmRingtoneService.class);
        serviceIntent.putExtra("alarm_type", alarmType); // Pass alarm type to service
        context.startService(serviceIntent);
    }
}