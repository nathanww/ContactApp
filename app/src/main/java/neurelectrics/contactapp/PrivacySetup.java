package neurelectrics.contactapp;

import android.Manifest;
import android.bluetooth.le.ScanResult;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class PrivacySetup extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_privacy_setup);
        int ignoredDevices = 0;
        final Button locationButton = (Button) findViewById(R.id.locationButton);
        locationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i("permissionRequest", "done");
                ActivityCompat.requestPermissions(PrivacySetup.this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        50);

            }
        });


        final Button scanButton = (Button) findViewById(R.id.ignorebutton);
        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent ignoreIntent = new Intent(PrivacySetup.this, scanAndIgnore.class);
                startService(ignoreIntent);
                scanButton.setText("Added nearby beacons to ignore list");
                scanButton.setEnabled(false);
                TextView ask3 = (TextView) findViewById(R.id.ask3);
                ask3.setVisibility(View.VISIBLE);
                Button doneButton = (Button) findViewById(R.id.completebutton);
                doneButton.setVisibility(View.VISIBLE);

            }
        });


        final Button completeButton = (Button) findViewById(R.id.completebutton);
        completeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();

            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) { //permission was granted
            Button locationButton = (Button) findViewById(R.id.locationButton);
            locationButton.setEnabled(false);
            locationButton.setText("Permission granted");
            //start searching for devices for the next step
            Intent startserv = new Intent(PrivacySetup.this, MyForeGroundService.class);
            startserv.setAction(MyForeGroundService.ACTION_START_FOREGROUND_SERVICE);
            startService(startserv);
            //enable UI for the next step
            TextView header2 = (TextView) findViewById(R.id.header2);
            header2.setVisibility(View.VISIBLE);
            TextView ask2 = (TextView) findViewById(R.id.ask2);
            ask2.setVisibility(View.VISIBLE);
            Button scanButton = (Button) findViewById(R.id.ignorebutton);
            scanButton.setVisibility(View.VISIBLE);
        }
    }


}

