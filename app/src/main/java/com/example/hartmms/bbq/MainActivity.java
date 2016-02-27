package com.example.hartmms.bbq;

import java.util.ArrayList;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.graphics.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.content.Intent;
import android.widget.TextView;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
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
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.components.YAxis.AxisDependency;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;
//import com.github.mikephil.charting.data.filter.Approximator;
//import com.github.mikephil.charting.data.filter.Approximator.ApproximatorType;
//import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
//import com.github.mikephil.charting.listener.ChartTouchListener;
//import com.github.mikephil.charting.listener.OnChartGestureListener;
//import com.github.mikephil.charting.listener.OnChartValueSelectedListener;

public class MainActivity extends AppCompatActivity {

    LineChart chart1;

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

        // Line graph of temps
        chart1 = (LineChart) findViewById(R.id.chart1);
        chart1.setDrawGridBackground(false);
        chart1.getLegend().setEnabled(false);
        chart1.getAxisRight().setEnabled(false);
        chart1.setDescription("");
        chart1.setNoDataText("");
        chart1.setHardwareAccelerationEnabled(true);
        chart1.setHighlightPerTapEnabled(false);
        chart1.setHighlightPerDragEnabled(false);
        chart1.setData(new LineData());
        // yaxis
        YAxis yAxis = chart1.getAxisLeft();
        yAxis.setEnabled(false);
        yAxis.setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART);
        yAxis.setDrawGridLines(true);
        yAxis.setDrawAxisLine(true);
        yAxis.setTextColor(Color.LTGRAY);
        yAxis.setTextSize(getResources().getInteger(R.integer.chartDateTextSize));
        yAxis.setStartAtZero(false);
        // xaxis
        XAxis xAxis = chart1.getXAxis();
        xAxis.setDrawAxisLine(true);
        xAxis.setDrawGridLines(false);
        xAxis.setDrawLabels(true);
        xAxis.setPosition(XAxis.XAxisPosition.TOP);
        xAxis.setTextColor(Color.LTGRAY);
        xAxis.setSpaceBetweenLabels(0);
        xAxis.setTextSize(getResources().getInteger(R.integer.chartDateTextSize));

//        FloatingActionButton exitButton = (FloatingActionButton) findViewById(R.id.exit);
//        exitButton.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
                //Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG).setAction("Action", null).show();
//                finish();
                //dummyData();
//          }
//        });
    }

    private void addEntry(int entry) {
        LineData data = chart1.getData();
        ILineDataSet set = data.getDataSetByIndex(0);
        if (set == null) {
            set = createSet();
            data.addDataSet(set);
        }
        SimpleDateFormat sdf = new SimpleDateFormat("h:mm", Locale.US);
        data.addXValue(sdf.format(new Date()));
        data.addEntry(new Entry(entry, set.getEntryCount()), 0);
        // set data
        chart1.getAxisLeft().setEnabled(true);
        chart1.notifyDataSetChanged();
        chart1.moveViewToX(0);
    }

//    private void dummyData() {
//        LineData data = chart1.getData();
//        ILineDataSet set = data.getDataSetByIndex(0);
//        if (set == null) {
//            set = createSet();
//            data.addDataSet(set);
//        }
//
//        SimpleDateFormat sdf = new SimpleDateFormat("h:mm:", Locale.US);
//        String d = sdf.format(new Date());
//
//        for (int i=30; i<=35; i++) {
//            data.addXValue(d+Integer.toString(i));
//            data.addEntry(new Entry(100+i, set.getEntryCount()), 0);
//        }
//        // set data
//        chart1.getAxisLeft().setEnabled(true);
//        chart1.notifyDataSetChanged();
//        chart1.moveViewToX(0);
//    }

    private LineDataSet createSet() {
        LineDataSet set = new LineDataSet(null, getResources().getString(R.string.probe1_txt));
        set.setAxisDependency(AxisDependency.LEFT);
        set.setColor(ColorTemplate.getHoloBlue());
        set.setCircleColor(Color.WHITE);
        set.setLineWidth(2f);
        set.setCircleSize(2f);
        set.setFillAlpha(65);
        set.setFillColor(ColorTemplate.getHoloBlue());
        set.setHighLightColor(Color.rgb(244, 117, 117));
        set.setValueTextColor(Color.WHITE);
        set.setValueTextSize(9f);
        set.setDrawValues(false);
        return set;
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
                    String probe1Data = msg.getData().getString("probe1temp");
                    addEntry(Integer.parseInt(probe1Data));
                    txtProbe1Temp.setText(probe1Data+" °F");
                    txtProbe2Temp.setText(msg.getData().getString("probe2temp")+" °F");
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
        switch (item.getItemId()) {
            case R.id.clear_chart: {
                chart1.clear();
                break;
            }
//            case R.id.dummy_data: {
//                dummyData();
//                break;
//            }
            case R.id.action_settings: {
                Intent i = new Intent(this, SettingsActivity.class);
                startActivity(i);
                break;
            }
            case R.id.exit: {
                finish();
            }
        }
        return true;
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
