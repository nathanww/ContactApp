package neurelectrics.contactapp;

import android.bluetooth.le.ScanResult;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

public class settingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        //sharedPref
        final SharedPreferences prefs = getSharedPreferences("com", MODE_PRIVATE);
        final SharedPreferences.Editor editor = prefs.edit();

        //handle starting and stopping the service
        final Button ignoreButton = (Button) findViewById(R.id.ignoredevices);

        ignoreButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String ignoreDevices = "";
                int devices = 0;
                //when the button is clicked, start scanning for devices, save their addresses, and updated a sharedPref with the devices to be ignored
                for (String i : scanData.getInstance().getData().keySet()) { //go through all the devices that have been found in the scan
                    ScanResult temp = scanData.getInstance().getData().get(i);
                    ignoreDevices = ignoreDevices + temp.getDevice().getAddress() + " ";
                    devices++;
                }
                editor.putString("ignoreDevices", ignoreDevices + prefs.getString("ignoreDevices", ""));
                editor.commit();
                Toast.makeText(getApplicationContext(), "Added " + devices + " beacons to ignore list", Toast.LENGTH_LONG);
                Log.i("Ignoredevices", ignoreDevices);
            }
        });
        //reset ignored devices
        final Button resetButton = (Button) findViewById(R.id.resetButton);
        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editor.putString("ignoreDevices", "");
                editor.commit();
                Toast.makeText(getApplicationContext(), "Ignored devices reset.", Toast.LENGTH_LONG);
            }
        });


        //leave the settings screen
        final Button exitButton = (Button) findViewById(R.id.exitButton);
        exitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

}
