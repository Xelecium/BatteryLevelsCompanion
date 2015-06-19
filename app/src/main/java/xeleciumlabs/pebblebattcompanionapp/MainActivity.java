package xeleciumlabs.pebblebattcompanionapp;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;

import java.util.UUID;


public class MainActivity extends Activity {

    //key for pushing battery info to the Pebble
    private static final int PHONE_BATTERY_DATA_KEY = 3;
    private static final int PHONE_CHARGE_STATE_KEY = 7;
    //key for signalling the phone to send back information
    private static final int SIGNAL_TO_PHONE_KEY = 11;
    private static final UUID PebbleBattUUID = UUID.fromString("1e4990c7-8abe-4643-a0fd-1d86e26503b4");

    private static final String TAG = MainActivity.class.getSimpleName();

    private Intent mBatteryIntent;

    //Note: the frequency of when ACTION_BATTERY_CHANGED is determined by the
    // manufacturer and cannot be changed
    private IntentFilter mBatteryFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);

    private BroadcastReceiver mReceiver;

    private TextView mPebbleBatteryTextView;
    private TextView mPhoneBatteryTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mPebbleBatteryTextView = (TextView) findViewById(R.id.pebbleBatteryTextView);
        mPhoneBatteryTextView = (TextView) findViewById(R.id.phoneBatteryTextView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        final IntentFilter filter = new IntentFilter(Intent.ACTION_TIME_TICK);

        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                //Update BatteryIntent with battery info
                mBatteryIntent = registerReceiver(null, mBatteryFilter);
                sendBatteryToPebble();
            }
        };

        mBatteryIntent = registerReceiver(null, mBatteryFilter);
        registerReceiver(mReceiver, filter);
        sendBatteryToPebble();

        //Receiver for data from the Pebble
        PebbleKit.registerReceivedDataHandler(this, new PebbleKit.PebbleDataReceiver(PebbleBattUUID) {
            @Override
            public void receiveData(Context context, int transactionId, PebbleDictionary data) {
                Log.i(TAG, "Received data from Pebble: " + data.getInteger(SIGNAL_TO_PHONE_KEY));
                PebbleKit.sendAckToPebble(getApplicationContext(), transactionId);
                Log.i(TAG, "Sending Ack to Pebble");
                sendBatteryToPebble();
            }
        });
    }

    private void sendBatteryToPebble() {
        PebbleDictionary dict = new PebbleDictionary();
        dict.addInt32(PHONE_BATTERY_DATA_KEY, calculateBattery());

        int chargeState = mBatteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        dict.addInt32(PHONE_CHARGE_STATE_KEY, chargeState);
        Log.d(TAG, "BatteryManager PLUGGED value: " + chargeState);
        PebbleKit.sendDataToPebble(MainActivity.this, PebbleBattUUID, dict);
        Log.i(TAG, "Sending battery and charge data to Pebble");
    }

    private int calculateBattery() {
        int batteryLevel = mBatteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        Log.d(TAG, "BatteryManager LEVEL value: " + batteryLevel);
        int batteryScale = mBatteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        Log.d(TAG, "BatteryManager SCALE value: " + batteryScale);

        float batteryPercent = batteryLevel / (float)batteryScale;
        int batteryValue = (int)(batteryPercent * 100);

        mPhoneBatteryTextView.setText(batteryValue + "%");
        return batteryValue;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
        Log.i(TAG, "Unregistered BroadcastReceiver");
    }
}
