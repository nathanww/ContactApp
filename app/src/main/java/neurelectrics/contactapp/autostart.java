package neurelectrics.contactapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class autostart extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i("trackickingService", "attempting to start on boot");
        Intent startserv = new Intent(context, MyForeGroundService.class);
        startserv.setAction(MyForeGroundService.ACTION_START_FOREGROUND_SERVICE);
        context.startService(startserv);

    }
}
