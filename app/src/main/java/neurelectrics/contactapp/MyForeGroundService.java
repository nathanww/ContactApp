package neurelectrics.contactapp;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;

import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.WindowManager;
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
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

import static android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION;


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
    HashMap<Long, ScanResult> contactList = new HashMap<Long, ScanResult>(); //stores contacts indexed by time, for suppressing contacts after they've been detected too much
    String contactsThisCycle = ""; //contacts that have been observed in a certain period of time
    int CONTACT_THRESH = -65; //signals closer than this count as a close contact
    BroadcastReceiver plugged = new pluggedIn();
    boolean isRunning = false; //flag for whether the service si running or not
    //these settings control how contacts stop "counting" once they have been observed for a certain period of time.
    //This makes the score more interpretable because long lasting contacts are often things that just happen to be in the vicinity and don't represent "real" contacts
    //contact_list time is how long the system keeps track of contacts, and contact list max in the number of 30-second periods in which they must be
    //observed before they stop counting.
    long CONTACT_LIST_TIME = 1000 * 60 * (60); //number of ms contacts on the list should be kept for
    int CONTACT_LIST_MAX = 1; //start disregarding signals if they appear in more than this many scans


    String signalsThisCycle = ""; //signals of any strength that have already been encountered in the current scan
    int totalSignals = 0; //total BT contacts detected
    int BACKGROUND_ISSUE_THRESHOLD = 60; //If we see no contacts for this many consecutive periods, assume that there is something wrong with our backround scan
    int noContactCount = 0; //current # of times we have had zero contacts in a scan
    long nextWindow = 0;

    int totalPeriods = 0;

    //if we have more than SEND_AT scans and data sharing is turned on, upload the data to the server
    int sendScanCount = 0;
    int SEND_AT = 30;
    int hourly_total = 0;

    void makeNetRequest(String URL) {
        RequestQueue netQ = Volley.newRequestQueue(getApplicationContext());
        StringRequest stringRequest = new StringRequest(Request.Method.POST, URL,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        //Nothing in here, because we don't (yet) care about what the outcome of sending the data was
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e("netError", error.toString());
            }
        });

// Add the request to the RequestQueue.
        netQ.add(stringRequest);
        Log.i("netRequest", "sent " + URL);
    }

    String fingerprint(ScanResult result) {
        String temp = result.getDevice().getName() + ":" + result.getDevice().getType() + ":" + result.getAdvertisingSid() + ":" + result.getDevice().getBluetoothClass() + ":" + result.getDevice().getUuids() + ":" + result.getTxPower() + ":" + result.getPeriodicAdvertisingInterval() + ":" + result.getPrimaryPhy() + ":" + result.getSecondaryPhy();
        try {
            MessageDigest m = MessageDigest.getInstance("MD5");
            m.update(temp.getBytes());
            byte[] digest = m.digest();
            BigInteger bigInt = new BigInteger(1, digest);
            String hashtext = bigInt.toString(16);
            return hashtext;
        } catch (NoSuchAlgorithmException e) { //if for some reason we can't do md5, just return the original
            return temp;
        }
    }

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

        IntentFilter filter = new IntentFilter(Intent.ACTION_POWER_CONNECTED);
        this.registerReceiver(plugged, filter);

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        this.unregisterReceiver(plugged);

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if(intent != null)
        {
            String action = intent.getAction();

            switch (action)
            {
                case ACTION_START_FOREGROUND_SERVICE:
                    if (!isRunning) {
                        startForegroundService();
                        isRunning = true;
                    }
                    break;

                case ACTION_STOP_FOREGROUND_SERVICE:
                    isRunning = false;
                    stopForegroundService();

                    break;
            }
        }
        return START_REDELIVER_INTENT;
    }

    /* Used to build and start foreground service. */
    private void startForegroundService() {
        createNotificationChannel(); //creates the required channel on newer platforms

        final SharedPreferences prefs = getSharedPreferences("com", MODE_PRIVATE);
        final SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("serviceRunning",true);
        editor.commit();
        Log.d(TAG_FOREGROUND_SERVICE, "Start foreground service.");
        // Create notification default intent.
        Intent intent = new Intent(this,MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
        // Create notification builder.
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "contactapp");

        // Make notification show big text.
        NotificationCompat.BigTextStyle bigTextStyle = new NotificationCompat.BigTextStyle();
        bigTextStyle.setBigContentTitle("Tracking exposure");
        bigTextStyle.bigText("ContactApp is monitoring your exposure.");
        // Set big text style.
        builder.setStyle(bigTextStyle);
        builder.setContentTitle("ContactApp is monitoring your exposure.");
        builder.setWhen(System.currentTimeMillis());
        builder.setSmallIcon(R.drawable.servicenotif);
        // Make the notification max priority.
        builder.setPriority(Notification.PRIORITY_MAX);
        builder.setContentIntent(pendingIntent);
        // Build the notification.
        Notification notification = builder.build();
        // Start foreground service.
        startForeground(1, notification);

        //check to see if we have a device ID and generate one if not
        if (prefs.getInt("deviceID", -1) == -1) {
            int randomID = (int) (Math.random() * 100000);
            editor.putInt("deviceID", randomID);
            editor.commit();
        }
        //ContactList stores contacts over the last 24 hours for "adaptation" (i.e. so persistent contacts like refrigerators don't keep
        //inflating the score
        //if a contactList already exists load it from storage, otherwise we can use the blank one

        if (prefs.getBoolean("hasContacts", false)) {
            Gson gson = new Gson();
            String json = prefs.getString("contactList", "");
            Type collectionType = new TypeToken<HashMap<Long, ScanResult>>() {
            }.getType();
            contactList = gson.fromJson(json, collectionType);

        }
        //start Bluetooth
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) { //check to make sure the Bluetooth is working, if not try to enable it.
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            enableBtIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK); //Request Bluetooth on
            startActivity(enableBtIntent);
        }
        int waitTime = 60;
        while (waitTime > 0 && (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled())) { //wait one minute for user to turn on buetooth, if it is not on by then abort.
            waitTime--;
            SystemClock.sleep(1000);
        }
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) { //permission was not granted, abort the service
            isRunning = false;
            stopForegroundService();
        }


        scanLeDevice(true);

        //tell the system not to go to sleep
        final PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        final PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK | PowerManager.LOCATION_MODE_NO_CHANGE,
                "ContactApp::BluetoothScan");
        wakeLock.acquire();

        //Some devices (i.e. Redmi note 8) seem to stop returning scan results after a while. We can check if this is happening, and, if so, silently restart the main activity to fix it.
        final Handler btCheck = new Handler();
        final Runnable btLoop = new Runnable() {
            @Override
            public void run() {


                btCheck.postDelayed(this, 15000);

            }

        };
// start
        btCheck.postDelayed(btLoop, 30000);

        //If the Bluetooth system has not registered at least one signal in a minute, it's a sign that
        //compute the number of contacts every 30 seconds. This compensates for things like differences in bluetooth advertising rate.
        final Handler handler = new Handler();
        final Runnable updateLoop = new Runnable() {
            @Override
            public void run() {
                try {
                    long startTime = System.currentTimeMillis();
                    int contactCount = contactsThisCycle.length() - contactsThisCycle.replace(" ", "").length(); //count the number of space-seperated addresses in the contact list
                    contactsThisCycle = ""; //reset the counter
                    signalsThisCycle = ""; //same for all signals
                    SimpleDateFormat todayFormat = new SimpleDateFormat("dd-MMM-yyyy");
                    SimpleDateFormat hourFormat = new SimpleDateFormat("H-dd-MMM-yyyy");

                    String todayKey = todayFormat.format(Calendar.getInstance(TimeZone.getDefault(), Locale.getDefault()).getTime());
                    String hourKey = hourFormat.format(Calendar.getInstance(TimeZone.getDefault(), Locale.getDefault()).getTime());


                    //formats and keys for weekly and hourly graphs

                    SimpleDateFormat minuteFormat = new SimpleDateFormat("m-H-dd-MMM-yyyy");
                    String minuteKey = "min-" + minuteFormat.format(Calendar.getInstance(TimeZone.getDefault(), Locale.getDefault()).getTime());


                    final SharedPreferences.Editor editor = getSharedPreferences("com", MODE_PRIVATE).edit();
                    //get the total number of contacts today, add one, and write it back
                    editor.putInt(todayKey, getSharedPreferences("com", MODE_PRIVATE).getInt(todayKey, 0) + contactCount);
                    //also update the contacts this hour
                    editor.putInt(hourKey, getSharedPreferences("com", MODE_PRIVATE).getInt(hourKey, 0) + contactCount);


                    //update contacts for this minute and contacts for this day
                    editor.putInt(minuteKey, getSharedPreferences("com", MODE_PRIVATE).getInt(minuteKey, 0) + contactCount);
                    editor.apply();


                    cleanContactList(); //clean out old contacts from the contact list once they expire

                    //every 3 runs (1.5 minutes) restart the scan to avoid running into long running scan restrictions
                    if (totalPeriods >= 3) {
                        scanLeDevice(false);
                        scanLeDevice(true);
                        totalPeriods = 0;
                    }
                    totalPeriods++;
                    sendScanCount++;
                    hourly_total = hourly_total + contactCount;
                    if (sendScanCount >= SEND_AT && (prefs.getInt("dataSharing", -1) == 1)) { //we are configured to share data and also it's time to do so!
                        makeNetRequest(prefs.getString("sharingServer", "https://biostream-1024.appspot.com/sd?") + "deviceid=" + prefs.getInt("deviceID", 1) + "&contacts=" + hourly_total + "_noContact=" + noContactCount);
                        hourly_total = 0;
                        sendScanCount = 0;
                    }


                    //checks for if the Bluetooth scan has failed

                    if ((totalSignals == 0)) {
                        //nothing detected since last time this fired, if this happens a lot it means we probably encountered a background process limitation
                        noContactCount++;
                        if (noContactCount > BACKGROUND_ISSUE_THRESHOLD) {
                            if (!prefs.getBoolean("backgroundIssue", false)) {
                                editor.putBoolean("backgroundIssue", true);
                                editor.commit();
                            }
                        }

                        //BACKUP MODE--CURRENTLY DISABLED
                        // We can do some things to try and restart the background scan, but it causes issues (screen turning on at random times). So currently we'll just ask the user to fix their power settings

                    /*
                    // The way to fix this is to reinitilaize the main activity (and then the service which is started by main)
                    //Currently, this only runs with the system in sleep mode--this is because restarting the activity while the user is using the phone can give some weird animations

                    //First, we need to wake the sytem up to start our new scan
                    PowerManager.WakeLock restart = powerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,"ContactApp::BluetoothRestart");
                    restart.acquire();
                    restart.release();

                    Intent i = new Intent(getApplicationContext(), MainActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|FLAG_ACTIVITY_NO_ANIMATION);
                    i.putExtra("btReset", true);
                    getApplicationContext().startActivity(i);
                    Log.e("scan", "Restarting app...");

                    stopForegroundService();*/


                    } else {
                        noContactCount = 0; //we had contacts, so reset this
                    }


                    totalSignals = 0;
                } catch (Exception e) {
                    Log.e("loopError", e.getLocalizedMessage());
                }
                handler.postDelayed(this, 30000);
                // Log.e("totalComputationTime",""+(System.currentTimeMillis()-startTime));
            }

        };
// start
        handler.postDelayed(updateLoop, 30000);


    }

    private void scanLeDevice(final boolean enable) {
        if (enable) { //start scanning process
            //mBluetoothAdapter.disable();
            mBluetoothAdapter.enable();
            mLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
            settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
            filters = new ArrayList<ScanFilter>();
            ScanFilter.Builder builder = new ScanFilter.Builder(); //reate a scan filter that matches anything to allow scans to run with screen off
            ScanFilter filter = builder.build();
            filters.add(filter);
            mLEScanner.startScan(filters, settings, mScanCallback);
            //Log.e("scan", "Starting scan...");
        } else {

            mLEScanner.stopScan(mScanCallback);

        }
    }

    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            try {
                if (System.currentTimeMillis() >= nextWindow) {
                    scanResults.put(fingerprint(result), result);
                    scanData.getInstance().setData(scanResults);
                    //Log.e("contact", result.getDevice().getName() + ":" + result.getDevice().getType() + ":" + result.getAdvertisingSid() + ":" + result.getDevice().getBluetoothClass() + ":" + result.getDevice().getUuids() + ":" + result.getTxPower() + ":" + result.getPeriodicAdvertisingInterval() + ":" + result.getPrimaryPhy() + ":" + result.getSecondaryPhy());
                    totalSignals++;
                    //check to see if this is a contact
                    if (result.getRssi() >= CONTACT_THRESH) {
                        long startTime = System.currentTimeMillis();
                        result.getDevice().fetchUuidsWithSdp(); //update the list of services offered by this device
                        //check the ignore list, and also the number of times this contact has been observed in the contact list. If it's not in the ignore list and hasn't been observed too much, add it to the contact list
                        if (contactsThisCycle.indexOf(fingerprint(result)) == -1 && countContacts(fingerprint(result)) < CONTACT_LIST_MAX) {
                            contactsThisCycle = contactsThisCycle + fingerprint(result) + " ";

                        }
                        if (signalsThisCycle.indexOf(fingerprint(result)) == -1) { //if this device has not been seen this cycle, add it to the list regardless of signal strength
                            signalsThisCycle = signalsThisCycle + fingerprint(result) + " ";
                            contactList.put(new Long(System.currentTimeMillis()), result);
                        }
                        nextWindow = System.currentTimeMillis() + (System.currentTimeMillis() - startTime); //assume that processing this signal will take as long as processing the last one and don't process anything new until that timeout--avoid CPU leaks

                    }
                }
            } catch (Exception e) {
                Log.e("scanerror", e.getLocalizedMessage());
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

    private void cleanContactList() { //remove any entries in contact list that are too old as defined by the CONTACT_LIST_TIME variable

        HashMap<Long, ScanResult> contactCopy = (HashMap) contactList.clone();
        for (Long i : contactCopy.keySet()) {
            if (i < System.currentTimeMillis() - CONTACT_LIST_TIME) {
                contactList.remove(i);
            }
        }
        //write the contactList back to storage
        final SharedPreferences prefs = getSharedPreferences("com", MODE_PRIVATE); //local sharedprefs
        final SharedPreferences.Editor editor = prefs.edit();
        //now convert contactList to a josn
        Gson gson = new Gson();
        String json = gson.toJson(contactList);

            editor.putBoolean("hasContacts", true);
            editor.putString("contactList", json);
            editor.commit();

    }


    private int countContacts(String deviceid) { //count the number of contacts from this device
        int hits = 0;
        for (Long i : contactList.keySet()) {
            try {
                if (fingerprint(contactList.get(i)).equals(deviceid)) {
                    hits++;

                }
            } catch (NullPointerException e) { //sometimes we will encounter a 'null' device, in that case just skip it

            }
        }
        return hits;
    }

    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel("contactapp", "ContactApp notification", importance);
            channel.setDescription("Contactapp persistent notification");
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }



}