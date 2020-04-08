package neurelectrics.contactapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class pluggedIn extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        Intent ignoreIntent = new Intent(context, scanAndIgnore.class);
        ignoreIntent.putExtra("timeToRun", 15);
        context.startService(ignoreIntent);
    }
}
