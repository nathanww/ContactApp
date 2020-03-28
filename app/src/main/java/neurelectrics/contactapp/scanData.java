package neurelectrics.contactapp;

import android.bluetooth.le.ScanResult;

import java.util.HashMap;

//singlton class for sharing BT scan data between UI and scan thread
public class scanData {
    HashMap<String, ScanResult> results = new HashMap<String, ScanResult>();

    public HashMap<String, ScanResult> getData() {
        return results;
    }

    public void setData(HashMap<String, ScanResult> data) {
        this.results = data;
    }

    private static final scanData holder = new scanData();

    public static scanData getInstance() {
        return holder;
    }
}

