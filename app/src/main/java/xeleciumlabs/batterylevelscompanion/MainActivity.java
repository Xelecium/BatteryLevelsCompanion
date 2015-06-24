package xeleciumlabs.batterylevelscompanion;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.BatteryManager;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
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
    private static final UUID BatteryLevelsUUID = UUID.fromString("1e4990c7-8abe-4643-a0fd-1d86e26503b4");

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int NotificationID = 327;

    @InjectView(R.id.mainLayout) RelativeLayout mLayout;
    @InjectView(R.id.statusMessage) TextView mStatusMessageTextView;
    @InjectView(R.id.closeButton) Button mCloseButton;

    //Note: the frequency of when ACTION_BATTERY_CHANGED is determined by the
    // manufacturer and cannot be changed
    private final IntentFilter timeFilter = new IntentFilter(Intent.ACTION_TIME_TICK);
    private IntentFilter mBatteryFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
    private Intent mBatteryIntent;
    private NotificationManager mNotificationManager;

    private PebbleKit.PebbleDataReceiver mDataReceiver;
    private BroadcastReceiver mBatteryReceiver;
    private BroadcastReceiver mPebbleConnect;
    private BroadcastReceiver mPebbleDisconnect;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ButterKnife.inject(this);

        mCloseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        //Set up persistent notification
        PendingIntent notificationIntent = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), 0);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder.setSmallIcon(R.mipmap.icon_launcher)
                .setContentTitle("Battery Levels")
                .setOngoing(true)       //Ongoing notification
                .setContentIntent(notificationIntent)
                .setPriority(NotificationCompat.PRIORITY_MIN);  //Minimize priority so it doesn't appear in the notification bar, similar to Pebble
        mNotificationManager = (NotificationManager)getSystemService(this.NOTIFICATION_SERVICE);
        mNotificationManager.notify(NotificationID, builder.build());
    }

    @Override
    protected void onResume() {
        super.onResume();

        setupReceivers();
        registerReceivers();

        sendBatteryToPebble();

        updatePebbleConnection();
    }

    private void registerReceivers() {

        mBatteryIntent = registerReceiver(null, mBatteryFilter);
        registerReceiver(mBatteryReceiver, timeFilter);
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

        mDataReceiver = new PebbleKit.PebbleDataReceiver(BatteryLevelsUUID) {
            @Override
            public void receiveData(Context context, int transactionId, PebbleDictionary data) {
                Log.i(TAG, "Received data from Pebble: " + data.getInteger(SIGNAL_TO_PHONE_KEY));
                PebbleKit.sendAckToPebble(MainActivity.this, transactionId);
                Log.i(TAG, "Sending Ack to Pebble");
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
        mNotificationManager.cancel(NotificationID);
        Log.i(TAG, "Unregistered BroadcastReceiver");
    }
}
