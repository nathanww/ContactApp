package neurelectrics.contactapp;

import android.app.IntentService;
import android.bluetooth.le.ScanResult;
import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;
import android.widget.TextView;

/**
 * IntentService that is called to add device IDs to the ignore list
 */
public class scanAndIgnore extends IntentService {


    public scanAndIgnore() {
        super("scanAndIgnore");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        //sharedPref
        final SharedPreferences prefs = getSharedPreferences("com", MODE_PRIVATE);
        final SharedPreferences.Editor editor = prefs.edit();
        String ignoreDevices = prefs.getString("ignoreDevices", "");
        for (int run = 0; run < (60 * 20); run++) { //this makes it keep scanning for 20 minutes in order to capture devices that don't broadcast that frequently
            for (String i : scanData.getInstance().getData().keySet()) { //go through all the devices that have been found in the scan
                ScanResult temp = scanData.getInstance().getData().get(i);
                if (ignoreDevices.indexOf(temp.getDevice().getAddress() + " ") == -1) { //device is not already in the ignore list
                    ignoreDevices = ignoreDevices + temp.getDevice().getAddress() + " ";
                }


            }
            editor.putString("ignoreDevices", ignoreDevices);
            editor.commit();
            SystemClock.sleep(1000); //wait one second
        }

    }


}
