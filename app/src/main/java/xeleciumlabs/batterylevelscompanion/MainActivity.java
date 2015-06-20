package xeleciumlabs.batterylevelscompanion;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.BatteryManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;

import java.util.UUID;

import butterknife.ButterKnife;
import butterknife.InjectView;

public class MainActivity extends Activity {

    //key for pushing battery info to the Pebble
    private static final int PHONE_BATTERY_DATA_KEY = 3;
    private static final int PHONE_CHARGE_STATE_KEY = 7;
    //key for signalling the phone to send back information
    private static final int SIGNAL_TO_PHONE_KEY = 11;
    //UUID to connect to corresponding Pebble watchface
    private static final UUID BatteryLevelsUUID = UUID.fromString("1e4990c7-8abe-4643-a0fd-1bd86e26503b4");
    //Tag for log events
    private static final String TAG = MainActivity.class.getSimpleName();

    //Note: the frequency of when ACTION_BATTERY_CHANGED is determined by the
    // manufacturer and cannot be changed
    private IntentFilter mBatteryFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
    private Intent mBatteryIntent;

    //Event Handlers
    private PebbleKit.PebbleDataReceiver mDataReceiver;
    private BroadcastReceiver mBatteryReceiver;
    private BroadcastReceiver mPebbleConnect;
    private BroadcastReceiver mPebbleDisconnect;

    //View elements
    private RelativeLayout mLayout;
    private TextView mStatusMessageTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mLayout = (RelativeLayout)findViewById(R.id.mainLayout);
        mStatusMessageTextView = (TextView)findViewById(R.id.statusMessage);
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        final IntentFilter filter = new IntentFilter(Intent.ACTION_TIME_TICK);
        registerReceiver(mBatteryReceiver, filter);

        setupReceivers();
        registerReceivers();

        updatePebbleConnection();
    }

    private void registerReceivers() {
        //Receiver for updating info on the minute
        //Intent to grab battery info for updating.
        //Do not register to battery change events, because it will basically
        //fire nonstop.
        mBatteryIntent = registerReceiver(null, mBatteryFilter);

        //Receiver for data from the Pebble
        PebbleKit.registerReceivedDataHandler(this, mDataReceiver);
        PebbleKit.registerPebbleConnectedReceiver(this, mPebbleConnect);
        PebbleKit.registerPebbleDisconnectedReceiver(this, mPebbleDisconnect);
    }

    private void setupReceivers() {
        mBatteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                //Update BatteryIntent with battery info
                mBatteryIntent = registerReceiver(null, mBatteryFilter);
                sendBatteryToPebble();
            }
        };

        mPebbleConnect = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.i(TAG, "Pebble connected!");
                updatePebbleConnection();
            }
        };

        mPebbleDisconnect = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.i(TAG, "Pebble disconnected!");
                updatePebbleConnection();
            }
        };

        mDataReceiver = new PebbleKit.PebbleDataReceiver(BatteryLevelsUUID) {
            @Override
            public void receiveData(Context context, int transactionId, PebbleDictionary data) {
                Log.i(TAG, "Received data from Pebble: " + data.getInteger(SIGNAL_TO_PHONE_KEY));
                PebbleKit.sendAckToPebble(MainActivity.this, transactionId);
                Log.i(TAG, "Sending Ack to Pebble");
                sendBatteryToPebble();
            }
        };
    }

    private void updatePebbleConnection() {
        boolean connected = PebbleKit.isWatchConnected(this);
        if (connected) {
            //update layout with message
            mLayout.setBackgroundColor(Color.parseColor("#BBFFBB"));
            mStatusMessageTextView.setText("Connected to Pebble, sending battery info!");
            sendBatteryToPebble();
        }
        else {
            //update layout with message
            mLayout.setBackgroundColor(Color.parseColor("#FFBBBB"));
            mStatusMessageTextView.setText("Not connected to Pebble, can't send battery info!");
        }
    }

    private void sendBatteryToPebble() {
        PebbleDictionary dict = new PebbleDictionary();
        dict.addInt32(PHONE_BATTERY_DATA_KEY, calculateBattery());

        int chargeState = mBatteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        dict.addInt32(PHONE_CHARGE_STATE_KEY, chargeState);
        Log.d(TAG, "BatteryManager PLUGGED value: " + chargeState);
        PebbleKit.sendDataToPebble(MainActivity.this, BatteryLevelsUUID, dict);
        Log.i(TAG, "Sending battery and charge data to Pebble");
    }

    private int calculateBattery() {
        int batteryLevel = mBatteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        Log.d(TAG, "BatteryManager LEVEL value: " + batteryLevel);
        int batteryScale = mBatteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        Log.d(TAG, "BatteryManager SCALE value: " + batteryScale);

        float batteryPercent = batteryLevel / (float)batteryScale;

        return (int)(batteryPercent * 100);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mBatteryReceiver);
        unregisterReceiver(mDataReceiver);
        unregisterReceiver(mPebbleConnect);
        unregisterReceiver(mPebbleDisconnect);
        Log.i(TAG, "Unregistered BroadcastReceiver");
    }
}
