package neurelectrics.contactapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class privacyOptIn extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_privacy_opt_in);
        final SharedPreferences prefs = getSharedPreferences("com", MODE_PRIVATE);
        final SharedPreferences.Editor editor = prefs.edit();
//create buttons to opt in/opt out of data sharing
        final Button yesButton = (Button) findViewById(R.id.yesbutton);
        yesButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editor.putInt("dataSharing", 1);
                editor.apply();
                privacyOptIn.this.startActivity(new Intent(privacyOptIn.this, MainActivity.class));
            }
        });
        final Button noButton = (Button) findViewById(R.id.nobutton);
        noButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editor.putInt("dataSharing", 2);
                editor.apply();
                privacyOptIn.this.startActivity(new Intent(privacyOptIn.this, MainActivity.class));
            }
        });
    }


    protected void onPause() {
        super.onPause();
        finish();
    }
}