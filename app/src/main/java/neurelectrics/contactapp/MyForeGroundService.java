package neurelectrics.contactapp;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.support.v4.app.NotificationCompat;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;

import java.util.UUID;


public class MyForeGroundService extends Service {

    private static final String TAG_FOREGROUND_SERVICE = "FOREGROUND_SERVICE";

    public static final String ACTION_START_FOREGROUND_SERVICE = "ACTION_START_FOREGROUND_SERVICE";

    public static final String ACTION_STOP_FOREGROUND_SERVICE = "ACTION_STOP_FOREGROUND_SERVICE";



    public MyForeGroundService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG_FOREGROUND_SERVICE, "foreground service onCreate().");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(intent != null)
        {
            String action = intent.getAction();

            switch (action)
            {
                case ACTION_START_FOREGROUND_SERVICE:
                    startForegroundService();
                    break;
                case ACTION_STOP_FOREGROUND_SERVICE:
                    stopForegroundService();
                    break;
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    /* Used to build and start foreground service. */
    private void startForegroundService()
    {
        final SharedPreferences prefs = getSharedPreferences("com", MODE_PRIVATE);
        final SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("serviceRunning",true);
        editor.commit();
        Log.d(TAG_FOREGROUND_SERVICE, "Start foreground service.");
        // Create notification default intent.
        Intent intent = new Intent(this,MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
        // Create notification builder.
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(this);

        // Make notification show big text.
        NotificationCompat.BigTextStyle bigTextStyle = new NotificationCompat.BigTextStyle();
        bigTextStyle.setBigContentTitle("Tracking social distancing");
        bigTextStyle.bigText("ContactApp is tracking your contacts.");
        // Set big text style.
        builder.setStyle(bigTextStyle);
        builder.setContentTitle("ContactApp is tracking your contacts.");
        builder.setWhen(System.currentTimeMillis());
        builder.setSmallIcon(R.drawable.sleepnotif);
        // Make the notification max priority.
        builder.setPriority(Notification.PRIORITY_MAX);
        builder.setContentIntent(pendingIntent);
        // Build the notification.
        Notification notification = builder.build();
        // Start foreground service.
        startForeground(1, notification);


    }

    private void stopForegroundService()
    {


        // Stop foreground service and remove the notification.
        stopForeground(true);

        // Stop the foreground service.
        stopSelf();
    }


}