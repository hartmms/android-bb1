package com.example.hartmms.bbq;

import android.os.Bundle;
//import android.support.design.widget.FloatingActionButton;
//import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.content.Intent;
import android.widget.TextView;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.res.Resources;
import android.support.v4.app.NotificationCompat;
import android.view.WindowManager;
import android.util.Log;
import android.os.Message;
import android.os.Messenger;
import android.content.ServiceConnection;
import android.content.Context;
import android.content.ComponentName;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.Handler;

public class MainActivity extends AppCompatActivity {

//    LineChart chart1;

    TextView txtProbe1Temp;
    TextView txtProbe2Temp;
    TextView txtDebug;
//    static String deviceID;
//    static String particleAPIKEY;
    Messenger mService = null;
    boolean notify;
    boolean serviceConnected;
    final Messenger mMessenger = new Messenger(new IncomingHandler());
    Intent service;
//    int probe1Target, probe2Target;
    SharedPreferences SP;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("BBQ", "onCreate");

        // Launch probe temp monitor service.
        service = new Intent(this, BBQService.class);
        startService(service);
        bindService(service, mConnection, Context.BIND_AUTO_CREATE);

        // Setup settings
        SP = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        setKeepScreenOn(SP.getBoolean("keep_screen_on", false));
//        deviceID = SP.getString("deviceID", null);
//        particleAPIKEY = SP.getString("particleAPIKEY", null);
//        probe1Target = Integer.parseInt(SP.getString("probe1Target", "999"));
//        probe2Target = Integer.parseInt(SP.getString("probe2Target", "999"));
//        notify = SP.getBoolean("notify", false);
//        SharedPreferences.OnSharedPreferenceChangeListener listener = new SharedPreferences.OnSharedPreferenceChangeListener() {
//            @Override
//            public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
//                Log.d("BBQ", "Found a changed preference:" + key);
//                if (key.equals("keep_screen_on")) {
//                    setKeepScreenOn(SP.getBoolean("keep_screen_on", false));
//                } else if (key.equals("deviceID")) {
//                    deviceID = SP.getString("deviceID", null);
//                } else if (key.equals("particleAPIKEY")) {
//                    particleAPIKEY = SP.getString("particleAPIKEY", null);
//                } else if (key.equals("probe1Target")) {
//                    probe1Target = Integer.parseInt(SP.getString("probe1Target", "999"));
//                } else if (key.equals("probe2Target")) {
//                    probe2Target = Integer.parseInt(SP.getString("probe2Target", "999"));
//                } else if (key.equals("notify")) {
//                    notify = SP.getBoolean("notify", false);
//                }
//                sendSettings();
//            }
//        };
//        SP.registerOnSharedPreferenceChangeListener(listener);

        // setup default app view
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        txtDebug = (TextView) findViewById(R.id.txtDebug);
        txtProbe1Temp = (TextView) findViewById(R.id.txtProbe1Temp);
        txtProbe2Temp = (TextView) findViewById(R.id.txtProbe2Temp);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d("BBQ", "onResume");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d("BBQ", "onPause");
    }

    @Override
    protected void onStart() {
        Log.d("BBQ", "onStart");
        if (serviceConnected)
            sendSettings();
        super.onStart();
    }

    @Override
    protected void onStop() {
        Log.d("BBQ", "onStop");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.d("BBQ", "onDestroy");
        super.onDestroy();
        unbindService(mConnection);
        stopService(service);
    }

    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case BBQService.MSG_COMM_ERROR:
                    txtDebug.setText(getResources().getString(R.string.connection_error));
                    txtDebug.setVisibility(View.VISIBLE);
                    break;
                case BBQService.MSG_DEVICE_OFFLINE:
                    txtDebug.setText(getResources().getString(R.string.offline));
                    txtDebug.setVisibility(View.VISIBLE);
                    break;
                case BBQService.MSG_PROBE_DATA:
                    txtDebug.setVisibility(View.INVISIBLE);
                    txtProbe1Temp.setText(msg.getData().getString("probe1temp"));
                    txtProbe2Temp.setText(msg.getData().getString("probe2temp"));
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            serviceConnected = true;
            mService = new Messenger(service);
            try {
                Message msg = Message.obtain(null, BBQService.MSG_REGISTER_CLIENT);
                msg.replyTo = mMessenger;
                mService.send(msg);
                sendSettings();
            } catch (RemoteException e) {
                // In this case the service has crashed before we could even do anything with it
                Log.d("BBQ", "message to service is dead");
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been unexpectedly disconnected - process crashed.
            mService = null;
            serviceConnected = false;
            //textStatus.setText("Disconnected.");
        }
    };

    private void sendSettings() {
        setKeepScreenOn(SP.getBoolean("keep_screen_on", false));
        String deviceID = SP.getString("deviceID", null);
        String particleAPIKEY = SP.getString("particleAPIKEY", null);
        int probe1Target = Integer.parseInt(SP.getString("probe1Target", "999"));
        int probe2Target = Integer.parseInt(SP.getString("probe2Target", "999"));
        boolean notify = SP.getBoolean("notify", false);
        try {
            Bundle bundle = new Bundle();
            bundle.putString("deviceID", deviceID);
            bundle.putString("particleAPIKEY", particleAPIKEY);
            bundle.putInt("probe1Target", probe1Target);
            bundle.putInt("probe2Target", probe2Target);
            bundle.putBoolean("notify", notify);
            Message msg = Message.obtain(null, BBQService.MSG_SETTINGS);
            msg.setData(bundle);
            mService.send(msg);
        } catch (RemoteException e) {
            // In this case the service has crashed before we could even do anything with it
            Log.d("BBQ", "message to service is dead");
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Intent i = new Intent(this, SettingsActivity.class);
            startActivity(i);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void updateTextFields() {
        txtDebug.setVisibility(View.INVISIBLE);
        //txtProbe1Temp.setText(String.format("%.0f F", tempF));
    }

    public void setKeepScreenOn(boolean keep_screen_on) {
        if (keep_screen_on) {
            //Log.d("BBQ", "keep screen on");
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            //Log.d("BBQ", "Allow screen to turn off");
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }
}
