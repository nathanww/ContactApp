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

    public static final String ACTION_PAUSE = "ACTION_PAUSE";

    public static final String ACTION_PLAY = "ACTION_PLAY";

    String appUuid="68a74ecc-5448-4c36-9edb-6ab70d5a8d67";
    int MOTION_THRESHOLD=4000;
    long MIN_MS=1000*60*5;
    long startTime=0;
    int thresholdExceed=0;
    Long lastMessageTime=System.currentTimeMillis()+30000;
    int[] sleepProb={0,0,70,75,86,90,95,100};
    PebbleKit.PebbleDataReceiver dataReceiver;
    public MyForeGroundService() {
    }

    void sendTel(String data) {
        String url ="http://biostream-1024.appspot.com/sleepdata?results=";
        StringRequest request = new StringRequest(Request.Method.GET, url+data, new Response.Listener<String>()
        {
            @Override
            public void onResponse(String response) {

            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {

            }
        }
        );
        RequestQueue rQueue = Volley.newRequestQueue(getApplicationContext());
        rQueue.add(request);
    }
    static void shutdownMedia(Context ctx) {
        AudioManager audioManager = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE);
        audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        // clickMedia(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,ctx.getApplicationContext());
        //clickMedia(KeyEvent.KEYCODE_MEDIA_PAUSE,ctx.getApplicationContext());
        //clickMedia(KeyEvent.KEYCODE_MEDIA_STOP,ctx.getApplicationContext());
        ActivityManager murder = (ActivityManager)ctx.getSystemService(Context.ACTIVITY_SERVICE);
        murder.killBackgroundProcesses("grit.storytel.app");
    }
    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG_FOREGROUND_SERVICE, "My foreground service onCreate().");
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
                    startTime=System.currentTimeMillis();
                    Toast.makeText(getApplicationContext(), "Esperando el sueño. Puedes cerrar la aplicación.", Toast.LENGTH_LONG).show();
                    break;
                case ACTION_STOP_FOREGROUND_SERVICE:
                    stopForegroundService();
                    Toast.makeText(getApplicationContext(), "He dejado de monitorear el sueño", Toast.LENGTH_LONG).show();
                    break;
                case ACTION_PLAY:
                    Toast.makeText(getApplicationContext(), "You click Play button.", Toast.LENGTH_LONG).show();
                    break;
                case ACTION_PAUSE:
                    Toast.makeText(getApplicationContext(), "You click Pause button.", Toast.LENGTH_LONG).show();
                    break;
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    /* Used to build and start foreground service. */
    private void startForegroundService()
    {
        sendTel("Startup1");
        sendTel("Startup2");
        final SharedPreferences prefs = getSharedPreferences("com", MODE_PRIVATE);
        final SharedPreferences.Editor editor = prefs.edit();

        editor.putBoolean("serviceRunning",true);
        editor.putLong("lastMessage",System.currentTimeMillis());
        editor.commit();
        Log.d(TAG_FOREGROUND_SERVICE, "Start foreground service.");
        //send telemetry



        // Create notification default intent.
        Intent intent = new Intent(this,MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);

        // Create notification builder.
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(this);

        // Make notification show big text.
        NotificationCompat.BigTextStyle bigTextStyle = new NotificationCompat.BigTextStyle();
        bigTextStyle.setBigContentTitle("Esperando el inicio del sueño");
        bigTextStyle.bigText("Los medios se detendrán cuando te duermas.");
        // Set big text style.
        builder.setStyle(bigTextStyle);
        builder.setContentTitle("Esperando el inicio del sueño");
        builder.setWhen(System.currentTimeMillis());
        builder.setSmallIcon(R.drawable.sleepnotif);
        //Bitmap largeIconBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.sleepnotif);
       // builder.setLargeIcon(largeIconBitmap);
        // Make the notification max priority.
        builder.setPriority(Notification.PRIORITY_MAX);
        builder.setContentIntent(pendingIntent);
        // Make head-up notification.
       // builder.setFullScreenIntent(pendingIntent, true);


        // Build the notification.
        Notification notification = builder.build();

        // Start foreground service.
        startForeground(1, notification);

    //set up the Pebblekit stuff
        PebbleKit.startAppOnPebble(this, UUID.fromString(appUuid));
        // Create a new receiver to get AppMessages from the C app
         dataReceiver = new PebbleKit.PebbleDataReceiver(UUID.fromString(appUuid)) {
            boolean isRunning=true;
            boolean shutdown=false;

            @Override
            public void receiveData(Context context, int transaction_id,
                                    PebbleDictionary dict) {
                if (isRunning) {
                    Long motion = dict.getInteger(49);
                    Log.e("pebble", "" + motion);


                    PebbleKit.sendAckToPebble(context, transaction_id);

                    if (motion > 50000) {
                        startTime=System.currentTimeMillis(); //reset the sleep onset timer
                    }

                    if (motion < MOTION_THRESHOLD) {
                        thresholdExceed++;
                    } else {
                        thresholdExceed = 0;
                    }

                    int psleep = 0;
                    if (thresholdExceed < 9) {
                        psleep = sleepProb[thresholdExceed];
                    } else {
                        psleep = 100;
                    }
                    sendTel(""+motion+","+prefs.getInt("sensitivity",95));
                    Log.e("psleep", "" + psleep);
                    Log.e("sensitivity:",""+prefs.getInt("sensitivity",95));
                    NotificationCompat.BigTextStyle bigTextStyle = new NotificationCompat.BigTextStyle();
                    bigTextStyle.setBigContentTitle("Esperando el inicio del sueño");
                    bigTextStyle.bigText("Los medios se detendrán cuando te duermas.\nProbabilidad de dormir:" + psleep + "%");
                    // Set big text style.
                    builder.setStyle(bigTextStyle);
                    builder.setContentTitle("Esperando el inicio del sueño");
                    NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                    nm.notify(1, builder.build());

                    if (psleep >= prefs.getInt("sensitivity",95) && System.currentTimeMillis() >= startTime+MIN_MS)
                    {

                        shutdownMedia(getApplicationContext());
                        NotificationCompat.BigTextStyle bigTextStyle2 = new NotificationCompat.BigTextStyle();
                        bigTextStyle2.setBigContentTitle("Stopped media");
                        bigTextStyle2.bigText("Media was stopped due to sleep detection. Tap to go to the app.");
                        // Set big text style.
                        builder.setStyle(bigTextStyle2);
                        nm.notify(1, builder.build());
                        isRunning=false;
                        Intent myIntent = new Intent(MyForeGroundService.this, MainActivity.class);
                        startActivity(myIntent);
                        stopForegroundService();
                    }


                    editor.putLong("lastMessage",System.currentTimeMillis());
                    editor.commit();
                }
            }
        };
        PebbleKit.registerReceivedDataHandler(this, dataReceiver);

        //set up the monitoring of last message
        final Handler handler = new Handler();
        final int delay = 5000; //milliseconds

        handler.postDelayed(new Runnable(){
            public void run(){
                if (System.currentTimeMillis()-prefs.getLong("lastMessage",System.currentTimeMillis())> 40000) { //no singal
                    PebbleKit.startAppOnPebble(getApplicationContext(), UUID.fromString(appUuid));

                }
                handler.postDelayed(this, delay);
            }
        }, delay);
    }

    private void stopForegroundService()
    {
        Log.d(TAG_FOREGROUND_SERVICE, "Stop foreground service.");
        SharedPreferences prefs = getSharedPreferences("com", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("serviceRunning",false);
        editor.commit();
        if (dataReceiver != null) {
            unregisterReceiver(dataReceiver);
        }
        // Stop foreground service and remove the notification.
        stopForeground(true);

        // Stop the foreground service.
        stopSelf();
    }
}