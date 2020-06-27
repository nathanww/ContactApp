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
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class settingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        //sharedPref
        final SharedPreferences prefs = getSharedPreferences("com", MODE_PRIVATE);
        final SharedPreferences.Editor editor = prefs.edit();


        //set up the sharing server
        EditText server = (EditText) findViewById(R.id.sharingServer);
        server.setText(prefs.getString("sharingServer", "https://biostream-1024.appspot.com/sd?"));
        //handle starting and stopping the service
        final Button ignoreButton = (Button) findViewById(R.id.ignoredevices);

        ignoreButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent ignoreIntent = new Intent(settingsActivity.this, scanAndIgnore.class);
                ignoreIntent.putExtra("timeToRun", 60);
                startService(ignoreIntent);
                ignoreButton.setText("Added nearby beacons to ignore list");
                ignoreButton.setEnabled(false);
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
                ignoreButton.setText("Add devices to ignore");
                ignoreButton.setEnabled(true);
            }
        });

        final Button sharingButton = (Button) findViewById(R.id.sharingSettings);

        sharingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                settingsActivity.this.startActivity(new Intent(settingsActivity.this, privacyOptIn.class));
                finish();
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

        //set the appriate text based on whether data sharing is enabled
        if (prefs.getInt("dataSharing", -1) == 1) { //sharing is on, this is is already configured in the xml file

        } else {
            TextView sharingOn = (TextView) findViewById(R.id.dataSharingExp);
            sharingOn.setVisibility(View.GONE);
            EditText text = (EditText) findViewById(R.id.sharingServer);
            text.setVisibility(View.GONE);

            TextView sharingOff = (TextView) findViewById(R.id.dataSharingOff);
            sharingOff.setVisibility(View.VISIBLE);
        }

    }

    protected void onDestroy() {
        super.onDestroy();
        final SharedPreferences prefs = getSharedPreferences("com", MODE_PRIVATE);
        final SharedPreferences.Editor editor = prefs.edit();
        EditText text = (EditText) findViewById(R.id.sharingServer);
        editor.putString("sharingServer", text.getText().toString());
        editor.commit();
    }

}
