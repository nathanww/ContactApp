package neurelectrics.contactapp;

import android.app.IntentService;
import android.bluetooth.le.ScanResult;
import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;
import android.widget.TextView;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ConcurrentModificationException;

/**
 * IntentService that is called to add device IDs to the ignore list
 */
public class scanAndIgnore extends IntentService {

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

    public scanAndIgnore() {
        super("scanAndIgnore");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        try {
            int timeToRun = intent.getIntExtra("timeToRun", -1);
            if (timeToRun == -1) {
                timeToRun = 20;
            }
            //sharedPref
            final SharedPreferences prefs = getSharedPreferences("com", MODE_PRIVATE);
            final SharedPreferences.Editor editor = prefs.edit();
            String ignoreDevices = prefs.getString("ignoreDevices", "");
            for (int run = 0; run < (60 * timeToRun); run++) { //this makes it keep scanning for 20 minutes in order to capture devices that don't broadcast that frequently
                try {
                    for (String i : scanData.getInstance().getData().keySet()) { //go through all the devices that have been found in the scan

                        ScanResult temp = scanData.getInstance().getData().get(i);
                        if (ignoreDevices.indexOf(fingerprint(temp) + " ") == -1) { //device is not already in the ignore list
                            ignoreDevices = ignoreDevices + fingerprint(temp) + " ";
                        }


                    }
                    editor.putString("ignoreDevices", ignoreDevices);
                    editor.commit();
                } catch (ConcurrentModificationException e) { //A CME will happen if this list got updated; in this case we just need to skip this run and try again on the next one.
                    Log.e("ignoreList", "CME");
                }
                SystemClock.sleep(1000); //wait one second
            }
        } catch (Exception e) {
            Log.e("ScanIgnoreError", e.getLocalizedMessage());
        }
    }


}
