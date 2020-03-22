package neurelectrics.contactapp;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;


import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    public static final int MY_PERMISSIONS_REQUEST_LOCATION = 99;
    private BluetoothAdapter mBluetoothAdapter;
    private int REQUEST_ENABLE_BT = 1;
    private Handler mHandler;
    private static final long SCAN_PERIOD = 10000;
    private BluetoothLeScanner mLEScanner;
    private ScanSettings settings;
    private List<ScanFilter> filters;
    private BluetoothGatt mGatt;

    //hashMap that stores bt device info index by address
    HashMap<String, ScanResult> results = new HashMap<String, ScanResult>();


    boolean isWearable(BluetoothDevice bd) {
        int btClass = bd.getBluetoothClass().getDeviceClass();
        int majorClass = bd.getBluetoothClass().getMajorDeviceClass();
        return (true || majorClass == BluetoothClass.Device.Major.UNCATEGORIZED || majorClass == BluetoothClass.Device.Major.PHONE || majorClass == BluetoothClass.Device.Major.WEARABLE || majorClass == BluetoothClass.Device.Major.HEALTH || btClass == BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES || btClass == BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET || btClass == BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE);
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //get the service status
        final SharedPreferences prefs = getSharedPreferences("com", MODE_PRIVATE);
        final SharedPreferences.Editor editor = prefs.edit();


        final boolean serviceStatus= prefs.getBoolean("serviceRunning", false);

        //handle starting and stopping the service
        final Button startServiceButton = (Button)findViewById(R.id.startstop);
        if (serviceStatus) {
            startServiceButton.setText("Stop");
        }

        startServiceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean localstatus= prefs.getBoolean("serviceRunning", false);
                Intent intent = new Intent(MainActivity.this, MyForeGroundService.class);
                if (localstatus) {//the thing is already running
                    intent.setAction(MyForeGroundService.ACTION_STOP_FOREGROUND_SERVICE);
                    startServiceButton.setText("Start");
                }
                else {
                    intent.setAction(MyForeGroundService.ACTION_START_FOREGROUND_SERVICE);
                    startServiceButton.setText("Stop");
                }
                startService(intent);
                //serviceStatus= !serviceStatus;
                startServiceButton.invalidate();
            }
        });

//set up bluetooth
         checkLocationPermission(); //get permissions to use bt



        //update the screen with list of detected devices
        final TextView status = (TextView) findViewById(R.id.scanResults);
        final Handler handler = new Handler();
        final Runnable updateLoop = new Runnable() {
            @Override
            public void run() {
                String dispResult = "";
                for (String i : results.keySet()) {
                    ScanResult temp = results.get(i);
                    if (temp.getRssi() > -65 && isWearable(temp.getDevice())) {
                        dispResult = dispResult + temp.getDevice().getAddress() + " : " + temp.getDevice().getName() + " " + temp.getRssi() + "\n";
                    }
                }
                status.setText(dispResult);

                handler.postDelayed(this, 5000);

            }

        };
// start
        handler.postDelayed(updateLoop, 5000);
    }

    /*
    @Override
    protected void onResume() {
        super.onResume();
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            if (Build.VERSION.SDK_INT >= 21) {
                mLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
                settings = new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build();
                filters = new ArrayList<ScanFilter>();
            }
            scanLeDevice(true);
        }
    }
    @Override
    protected void onPause() {
        super.onPause();
        if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {
            scanLeDevice(false);
        }
    }
    @Override
    protected void onDestroy() {
        if (mGatt == null) {
            return;
        }
        mGatt.close();
        mGatt = null;
        super.onDestroy();
    }*/
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_CANCELED) {
                //Bluetooth not enabled.
                finish();
                return;
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
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
            results.put(result.getDevice().getAddress(), result);

        }

    };


    public boolean checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_COARSE_LOCATION)) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
                new AlertDialog.Builder(this)
                        .setTitle("bt permission")
                        .setMessage("bt permission")
                        .setPositiveButton("ok", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                //Prompt the user once explanation has been shown
                                ActivityCompat.requestPermissions(MainActivity.this,
                                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                        MY_PERMISSIONS_REQUEST_LOCATION);
                            }
                        })
                        .create()
                        .show();


            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_PERMISSIONS_REQUEST_LOCATION);
            }
            return false;
        } else {
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {



                } else {

                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.

                }
                return;
            }

        }
    }
}






