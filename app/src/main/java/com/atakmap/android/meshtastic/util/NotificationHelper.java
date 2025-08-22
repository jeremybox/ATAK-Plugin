package com.atakmap.android.meshtastic.util;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import androidx.core.app.NotificationCompat;
import com.atakmap.android.meshtastic.plugin.R;

public class NotificationHelper {
    private static final String TAG = "NotificationHelper";
    private static NotificationHelper instance;
    
    private final Context context;
    private final NotificationManager notificationManager;
    private NotificationCompat.Builder progressBuilder;
    
    private NotificationHelper(Context context) {
        this.context = context.getApplicationContext();
        this.notificationManager = (NotificationManager) this.context.getSystemService(Context.NOTIFICATION_SERVICE);
        initializeChannel();
        initializeProgressNotification();
    }
    
    public static synchronized NotificationHelper getInstance(Context context) {
        if (instance == null) {
            instance = new NotificationHelper(context);
        }
        return instance;
    }
    
    private void initializeChannel() {
        NotificationChannel channel = new NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_ID,
            Constants.NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setSound(null, null);
        notificationManager.createNotificationChannel(channel);
    }
    
    private void initializeProgressNotification() {
        Intent atakFrontIntent = new Intent();
        atakFrontIntent.setComponent(new ComponentName(
            Constants.ATAK_PACKAGE,
            Constants.ATAK_ACTIVITY
        ));
        atakFrontIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        atakFrontIntent.putExtra("internalIntent", new Intent(Constants.MESHTASTIC_SHOW_PLUGIN));
        
        PendingIntent appIntent = PendingIntent.getActivity(
            context, 
            0, 
            atakFrontIntent, 
            PendingIntent.FLAG_IMMUTABLE
        );
        
        progressBuilder = new NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Meshtastic File Transfer")
            .setContentText("Transfer in progress")
            .setSmallIcon(R.drawable.ic_launcher)
            .setAutoCancel(true)
            .setOngoing(false)
            .setContentIntent(appIntent);
    }
    
    public void showProgressNotification(int progress) {
        progressBuilder.setProgress(100, progress, false);
        notificationManager.notify(Constants.NOTIFICATION_ID, progressBuilder.build());
    }
    
    public void showCompletionNotification() {
        progressBuilder.setContentText("Transfer complete")
            .setProgress(0, 0, false);
        notificationManager.notify(Constants.NOTIFICATION_ID, progressBuilder.build());
    }
    
    public void showNotification(String title, String message) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher)
            .setAutoCancel(true);
        
        notificationManager.notify(Constants.NOTIFICATION_ID, builder.build());
    }
    
    public void cancelNotification() {
        notificationManager.cancel(Constants.NOTIFICATION_ID);
    }
}