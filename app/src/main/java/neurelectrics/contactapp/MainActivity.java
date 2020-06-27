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
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;


import com.github.javiersantos.appupdater.AppUpdater;
import com.github.javiersantos.appupdater.enums.UpdateFrom;

import org.w3c.dom.Text;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private Handler mHandler;

    int LIST_THRESH = -60; //minimal signal strength to show up in the list
    //hashMap that stores bt device info index by address
    HashMap<String, ScanResult> results = new HashMap<String, ScanResult>();
    boolean isVisible = false; //keep trck of whether this activity is in the foreground

    String generateChartString(int type) { //format the hourly exposure data into a format that can be sent to quickchart.. Type  1=hour, type 2=day, type 3 = week

        //chart fomratting data
        String BASE_REQUEST_HOUR = "https://quickchart.io/chart?c={type:%27line%27,%20options:%20{legend:%20{display:%20false}},data:{labels:[%2712%20AM%27,%271%20AM%27,%272%20AM%27,%273%20AM%27,%274%20AM%27,%275%20AM%27,%276%20AM%27,%277%20AM%27,%278%20AM%27,%279%20AM%27,%2710%20AM%27,%2711%20AM%27,%2712%20PM%27,%20%271%20PM%27,%272%20PM%27,%273%20PM%27,%274%20PM%27,%275%20PM%27,%276%20PM%27,%277%20PM%27,%278%20PM%27,%279%20PM%27,%2710%20PM%27,%2711%20PM%27],%20datasets:[{label:%27%27,%20data:%20[#CHARTDATA#],%20fill:false,borderColor:%27blue%27}]}}";
        String BASE_REQUEST_MINUTE = "https://quickchart.io/chart?c={type:%27line%27,%20options:%20{legend:%20{display:%20false}},data:{labels:[#LABELDATA#],%20datasets:[{label:%27%27,%20data:%20[#CHARTDATA#],%20fill:false,borderColor:%27blue%27}]}}";
        String BASE_REQUEST_DAILY = "https://quickchart.io/chart?c={type:'line', options: {legend: {display: false}},data:{labels:[#LABELDATA#], datasets:[{label:'', data: [#CHARTDATA#], fill:false,borderColor:'blue'}]}}";

        //Strings for obtained data
        String hourlyData = "";
        String dailyData = "";
        String minuteData = "";

        //formats and keys
        SimpleDateFormat todayFormat = new SimpleDateFormat("dd-MMM-yyyy");
        String todayKey = todayFormat.format(Calendar.getInstance(TimeZone.getDefault(), Locale.getDefault()).getTime());

        SimpleDateFormat minuteFormat = new SimpleDateFormat("-H-dd-MMM-yyyy");
        String minuteKey = minuteFormat.format(Calendar.getInstance(TimeZone.getDefault(), Locale.getDefault()).getTime());
        SimpleDateFormat hourFormat = new SimpleDateFormat("H");
        final SharedPreferences prefs = getSharedPreferences("com", MODE_PRIVATE);

        int thisHour = Integer.parseInt(hourFormat.format(Calendar.getInstance(TimeZone.getDefault(), Locale.getDefault()).getTime())); //todo: clearer way of getting current hour

        // /go through each hour of the day and append teh number of contacts to hourlydata.
        // We want to plot points for all the hours that have actually happened. Some might be missing data (if the app wasn't running), so show these as 0
        for (int time = 0; time <= thisHour; time++) {
            int contactNum = prefs.getInt(time + "-" + todayKey, -1);
            if (contactNum > -1) { //we actually have data for this slot
                hourlyData = hourlyData + contactNum + ",";
            } else {
                hourlyData = hourlyData + "0,";
            }
        }
        //now do the same thing for minute
        for (int min = 0; min <= 59; min++) {
            int contactNum = prefs.getInt("min-" + min + minuteKey, -1);


            if (contactNum > -1) { //we actually have data for this slot
                minuteData = minuteData + contactNum + ",";
            } else {
                minuteData = minuteData + "0,";
            }
        }


        //for this one we use a custom label since it is getting data for the last 7 days and we don't know up front which weekdays
        //Therefore, we find the day of the week for each data point and plug it in
        SimpleDateFormat weekdayFormat = new SimpleDateFormat("E");
        String WEEK_LABELS = "";
        //and for the day of the week
        for (int daysBehind = 6; daysBehind >= 0; daysBehind--) {
            Calendar thisDate = Calendar.getInstance(TimeZone.getDefault(), Locale.getDefault());
            thisDate.add(Calendar.DATE, 0 - daysBehind);
            String thisDayKey = todayFormat.format(thisDate.getTime());
            String thisDayLabel = weekdayFormat.format(thisDate.getTime());
            WEEK_LABELS = WEEK_LABELS + "'" + thisDayLabel + "'" + ",";


            int contactNum = prefs.getInt(thisDayKey, -1);
            if (contactNum > -1) { //we actually have data for this slot
                dailyData = dailyData + contactNum + ",";
            } else {
                dailyData = dailyData + "0,";
            }
        }

        //return the URL for the request chart. 1=current hour, 2=current day, 3=current week
        if (type == 1) {
            String minLabel = "";
            for (int i = 0; i < 60; i++) { //create labels from 1 to 60 minutes
                minLabel = minLabel + i + ",";
            }
            BASE_REQUEST_MINUTE = BASE_REQUEST_MINUTE.replace("#LABELDATA#", minLabel).replace("#CHARTDATA#", minuteData);
            return BASE_REQUEST_MINUTE;
        } else if (type == 2) {
            BASE_REQUEST_HOUR = BASE_REQUEST_HOUR.replace("#CHARTDATA#", hourlyData); //plug the data into the URL
            return BASE_REQUEST_HOUR;
        } else {
            BASE_REQUEST_DAILY = BASE_REQUEST_DAILY.replace("#CHARTDATA#", dailyData).replace("#LABELDATA#", WEEK_LABELS); //plug the data into the URL
            return BASE_REQUEST_DAILY;
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.e("mainactivity", "oncreate");
        // Bundle extras = getIntent().getExtras();


        //This condition is used to handle an automatic restart to fix background issues--currently NEVER USED
        if (false && getIntent().getBooleanExtra("btReset", false)) {
            this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN |
                            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN |
                            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
            //The activity is being restarted to fix Bluetooth issues, this should happen silently (i.e. not do anything in the UI)
            overridePendingTransition(0, 0);
            Intent intent = new Intent(MainActivity.this, MyForeGroundService.class);
            intent.setAction(MyForeGroundService.ACTION_START_FOREGROUND_SERVICE);
            startService(intent);
            finish();
        } else {
            //This is the part that always runs
            setContentView(R.layout.activity_main);


            //request to turn off battery optimization
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Intent intent = new Intent();
                String packageName = getPackageName();
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                    intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + packageName));
                    startActivity(intent);
                }
            }


            //start update checker
            AppUpdater update = new AppUpdater(this)
                    .setUpdateFrom(UpdateFrom.JSON)
                    .setUpdateJSON("https://raw.githubusercontent.com/nathanww/ContactApp/master/update.json");
            update.start();

            final SharedPreferences prefs = getSharedPreferences("com", MODE_PRIVATE);
            final SharedPreferences.Editor editor = prefs.edit();

            //have we configured data sharing yet?
            if (prefs.getInt("dataSharing", -1) == -1) {
                MainActivity.this.startActivity(new Intent(MainActivity.this, privacyOptIn.class));
            }

            //check to see if a background scan issue was detected, if it was display the warning
            if (prefs.getBoolean("backgroundIssue", false)) {
                final LinearLayout backgroundWarning = (LinearLayout) findViewById(R.id.backgroundWarning);
                backgroundWarning.setVisibility(View.VISIBLE);
                try {
                    editor.putBoolean("backgroundIssue", false);
                    editor.commit();
                } catch (ConcurrentModificationException e) {

                }
            }
            //set up the exposure chart using quickchart.io to make the chart and Webview to display it
            final WebView chartView = (WebView) findViewById(R.id.chartView);
            chartView.setInitialScale(30);
            chartView.setBackgroundColor(Color.WHITE);
            chartView.getSettings().setLoadWithOverviewMode(true); //set scaling to automatically fit the image returned by server
            chartView.setScrollBarStyle(WebView.SCROLLBARS_OUTSIDE_OVERLAY);
            chartView.setScrollbarFadingEnabled(false);
            chartView.getSettings().setUseWideViewPort(true);


            //button to quit the aplication
            final Button stopButton = (Button) findViewById(R.id.stopButton);
            stopButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finishActivity(1);
                    System.exit(0);
                }
            });
            //open the settings screen
            final Button settingsButton = (Button) findViewById(R.id.settingsButton);
            settingsButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    MainActivity.this.startActivity(new Intent(MainActivity.this, settingsActivity.class));
                }
            });


            //update the screen with list of detected devices
            final TextView status = (TextView) findViewById(R.id.scanResults);
            final TextView contactsToday = (TextView) findViewById(R.id.contactsList);
            final Handler handler = new Handler();
            final Runnable updateLoop = new Runnable() {
                @Override
                public void run() {
                    // first update the total number of contacts today
                    SimpleDateFormat todayFormat = new SimpleDateFormat("dd-MMM-yyyy");
                    String todayKey = todayFormat.format(Calendar.getInstance(TimeZone.getDefault(), Locale.getDefault()).getTime());
                    contactsToday.setText("Today's exposure score: " + prefs.getInt(todayKey, 0));
                    if (isVisible) {
                        chartView.loadUrl(generateChartString(prefs.getInt("chartMode", 2))); //update the chart
                    }

                    //show the devices contirbuting--this is not visible by default because the textView that holds it is set to GONE but can be turned pn
                    String dispResult = "";
                    for (String i : scanData.getInstance().getData().keySet()) {
                        ScanResult temp = scanData.getInstance().getData().get(i);
                        if (temp.getRssi() > LIST_THRESH) {
                            dispResult = dispResult + temp.getDevice().getAddress() + " : " + temp.getDevice().getName() + " " + temp.getRssi() + "\n";
                        }
                    }
                    status.setText(dispResult);

                    handler.postDelayed(this, 30000);

                }

            };
// start
            handler.post(updateLoop);

            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
//start the bluetooth search service, if we have the required location permission
                Intent intent = new Intent(MainActivity.this, MyForeGroundService.class);
                intent.setAction(MyForeGroundService.ACTION_START_FOREGROUND_SERVICE);
                startService(intent);
            } else { //otherwise this is probably the first run, so open the intro window
                MainActivity.this.startActivity(new Intent(MainActivity.this, PrivacySetup.class));
            }


            //buttons for controlling the time view
            final Button viewHour = (Button) findViewById(R.id.viewHour);
            viewHour.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    editor.putInt("chartMode", 1);
                    chartView.loadUrl(generateChartString(1));
                    editor.apply();
                }
            });


            final Button viewDay = (Button) findViewById(R.id.viewDay);
            viewDay.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    editor.putInt("chartMode", 2);
                    chartView.loadUrl(generateChartString(2));
                    editor.apply();
                }
            });

            final Button viewWeek = (Button) findViewById(R.id.viewWeek);
            viewWeek.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    editor.putInt("chartMode", 3);
                    chartView.loadUrl(generateChartString(3));
                    editor.apply();
                }
            });
        }
    }


    @Override
    protected void onResume() { //start the scan when the application starts
        super.onResume();
        isVisible = true; //the app is visible
    }

    @Override
    protected void onPause() {
        super.onPause();
        isVisible = false; //the app is no longer visible
        finishAffinity();
    }


    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();

    }

}





