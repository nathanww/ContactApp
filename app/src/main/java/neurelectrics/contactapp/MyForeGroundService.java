package neurelectrics.contactapp;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;


public class MyForeGroundService extends Service {

    private static final String TAG_FOREGROUND_SERVICE = "FOREGROUND_SERVICE";

    public static final String ACTION_START_FOREGROUND_SERVICE = "ACTION_START_FOREGROUND_SERVICE";

    public static final String ACTION_STOP_FOREGROUND_SERVICE = "ACTION_STOP_FOREGROUND_SERVICE";

    private BluetoothLeScanner mLEScanner;
    private ScanSettings settings;
    private List<ScanFilter> filters;
    private BluetoothGatt mGatt;
    private BluetoothAdapter mBluetoothAdapter;
    HashMap<String, ScanResult> scanResults = new HashMap<String, ScanResult>();
    int CONTACT_THRESH = -65; //signals closer than this count as a close contact
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
    private void startForegroundService() {
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

        //start Bluetooth
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        mLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
        settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();
        filters = new ArrayList<ScanFilter>();
        scanLeDevice(true);
    }

    private void scanLeDevice(final boolean enable) {
        if (enable) { //start scanning process

            mLEScanner.startScan(filters, settings, mScanCallback);
            Log.e("scan", "Starting scan...");
        } else {

            mLEScanner.stopScan(mScanCallback);

        }
    }

    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            scanResults.put(result.getDevice().getAddress(), result);
            scanData.getInstance().setData(scanResults);

            //check to see if this is a contact
            if (result.getRssi() >= CONTACT_THRESH) {
                //check if it is ignored
                if (getSharedPreferences("com", MODE_PRIVATE).getString("ignoreDevices", "").indexOf(result.getDevice().getAddress()) == -1) {
                    //not in the ignore list!
                    SimpleDateFormat todayFormat = new SimpleDateFormat("dd-MMM-yyyy");
                    String todayKey = todayFormat.format(Calendar.getInstance().getTime());
                    final SharedPreferences.Editor editor = getSharedPreferences("com", MODE_PRIVATE).edit();
                    //get the total number of contacts today, add one, and write it back
                    editor.putInt(todayKey, getSharedPreferences("com", MODE_PRIVATE).getInt(todayKey, 0) + 1);
                    editor.apply();
                }

            }
        }

    };

    private void stopForegroundService()
    {


        // Stop foreground service and remove the notification.
        stopForeground(true);

        // Stop the foreground service.
        stopSelf();
    }


}