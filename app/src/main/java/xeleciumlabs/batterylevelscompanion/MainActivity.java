package xeleciumlabs.batterylevelscompanion;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.BatteryManager;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RelativeLayout;
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
    private static final UUID BatteryLevelsUUID = UUID.fromString("1e4990c7-8abe-4643-a0fd-1d86e26503b4");
    private static final String UPDATE_ACTION = "xeleciumlabs.alarmupdate";

    private SharedPreferences.Editor mSettingsEditor;

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int NotificationID = 327;

    private RelativeLayout mLayout;
    private TextView mStatusMessageTextView;
    private Button mCloseButton;

    private RelativeLayout mBackgroundPreferenceItem;
    private CheckBox mBackgroundCheckBox;
    private boolean mBackgroundPreference;

    private RelativeLayout mNotificationPreferenceItem;
    private CheckBox mNotificationCheckBox;
    private boolean mNotificationPreference;

    //Note: the frequency of when ACTION_BATTERY_CHANGED is determined by the
    // manufacturer and cannot be changed
    private final IntentFilter timeFilter = new IntentFilter(Intent.ACTION_TIME_TICK);
    private IntentFilter mBatteryFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
    private Intent mBatteryIntent;

    private AlarmManager mAlarmManager;
    private PendingIntent mBackgroundIntent;
    private NotificationManager mNotificationManager;
    private Notification mNotification;

    private PebbleKit.PebbleDataReceiver mDataReceiver;
    private BroadcastReceiver mBatteryReceiver;
    private BroadcastReceiver mBackgroundReceiver;
    private BroadcastReceiver mPebbleConnect;
    private BroadcastReceiver mPebbleDisconnect;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mLayout = (RelativeLayout)findViewById(R.id.mainLayout);
        mStatusMessageTextView = (TextView)findViewById(R.id.statusMessage);
        mCloseButton = (Button)findViewById(R.id.closeButton);

        mBackgroundPreference = getPreferences(MODE_PRIVATE).getBoolean("backgroundAlarm", true);
        mNotificationPreference = getPreferences(MODE_PRIVATE).getBoolean("notificationIcon", false);
        mSettingsEditor = getPreferences(MODE_PRIVATE).edit();

        setupReceivers();
        registerReceivers();

        mAlarmManager = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
        //setupBackground();
        setupNotification();

        mBackgroundPreferenceItem = (RelativeLayout)findViewById(R.id.backgroundPreferenceItem);
        mBackgroundCheckBox = (CheckBox)findViewById(R.id.backgroundPreferenceCheckBox);
        updateBackgroundPreference();

        mNotificationPreferenceItem = (RelativeLayout)findViewById(R.id.notificationPreferenceItem);
        mNotificationCheckBox = (CheckBox)findViewById(R.id.notificationPreferenceCheckBox);
        updateNotificationPreference();

        mCloseButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        mBackgroundPreferenceItem.setOnClickListener(backgroundClickListener);
        mBackgroundCheckBox.setOnClickListener(backgroundClickListener);
        mNotificationPreferenceItem.setOnClickListener(notificationIconClickListener);
        mNotificationCheckBox.setOnClickListener(notificationIconClickListener);

    }

    OnClickListener backgroundClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            //Toggle preference between true and false
            mBackgroundPreference = !mBackgroundPreference;

            updateBackgroundPreference();
        }
    };

    private void setupBackground() {
        //Continue battery updates to Pebble in the background

    }

    private void updateBackgroundPreference() {
        //set checkbox accordingly
        mBackgroundCheckBox.setChecked(mBackgroundPreference);

        //if user wants to continue running in background
        if (mBackgroundPreference) {
            mAlarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME,
                    1 * 1000,
                    60 * 1000,
                    mBackgroundIntent);
            mSettingsEditor.putBoolean("backgroundAlarm", true);
            Log.d(TAG, "Background Alarm enabled!");
        }

        //if user wants to stop running in the background
        else {
            mAlarmManager.cancel(mBackgroundIntent);
            mSettingsEditor.putBoolean("backgroundAlarm", false);
            Log.d(TAG, "Background Alarm disabled!");
        }
    }

    private OnClickListener notificationIconClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            //Toggle preference between true and false
            mNotificationPreference = !mNotificationPreference;

            updateNotificationPreference();
        }
    };

    private void setupNotification() {
        //Set up persistent notification
        PendingIntent notificationIntent = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), 0);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder.setSmallIcon(R.mipmap.icon_launcher)
                .setContentTitle("Battery Levels")
                .setOngoing(true)       //Ongoing notification
                .setContentIntent(notificationIntent)
                .setPriority(NotificationCompat.PRIORITY_MIN);  //Minimize priority so it doesn't appear in the notification bar, similar to Pebble
        mNotificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        mNotification = builder.build();
    }

    private void updateNotificationPreference() {
        //set checkbox accordingly
        mNotificationCheckBox.setChecked(mNotificationPreference);

        //If user wants the notification icon
        if (mNotificationPreference) {
            mNotificationManager.notify(NotificationID, mNotification);
            mSettingsEditor.putBoolean("notificationIcon", true);
            Log.d(TAG, "Notification Icon enabled!");
        }
        //If the user does not want the notification icon
        else {
            mNotificationManager.cancel(NotificationID);
            mSettingsEditor.putBoolean("notificationIcon", false);
            Log.d(TAG, "Notification Icon disabled!");
        }

    }

    @Override
    protected void onResume() {
        super.onResume();


        sendBatteryToPebble();

        updatePebbleConnection();
    }

    private void registerReceivers() {

        mBatteryIntent = registerReceiver(null, mBatteryFilter);
        //registerReceiver(mBatteryReceiver, timeFilter);
        registerReceiver(mBackgroundReceiver, new IntentFilter(UPDATE_ACTION));
        mBackgroundIntent = PendingIntent.getBroadcast(this, 0, new Intent(UPDATE_ACTION), 0);
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

        mBackgroundReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.i(TAG, "Received Background Broadcast");
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
    protected void onStop() {
        super.onStop();

        mSettingsEditor.commit();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //unregisterReceiver(mBatteryReceiver);
        mNotificationManager.cancel(NotificationID);
        Log.i(TAG, "Unregistered BroadcastReceiver");
    }
}
