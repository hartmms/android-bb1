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
import android.os.Handler;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.Request.Method;
import com.android.volley.toolbox.JsonObjectRequest;
import org.json.JSONException;
import org.json.JSONObject;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.res.Resources;
import android.support.v4.app.NotificationCompat;



public class MainActivity extends AppCompatActivity {

    boolean Online = false;
    static double steinhart_a = 0.00024723753;
    static double steinhart_b = 0.00023402251;
    static double steinhart_c = 0.00000013879768;
    double tempK;
    double tempC;
    double tempF;
    float dpWidth = 0;
    static String deviceID;
    static String particleAPIKEY;

    TextView txtProbe1Temp;
    TextView txtProbe2Temp;
    TextView txtDebug;
//    LineChart chart1;

    boolean blnDebugOnShow = false;
    Handler mHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        deviceID = SP.getString("deviceID", null);
        particleAPIKEY = SP.getString("particleAPIKEY", null);

        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        txtDebug = (TextView) findViewById(R.id.txtDebug);
        txtProbe1Temp = (TextView) findViewById(R.id.txtProbe1Temp);
        txtProbe2Temp = (TextView) findViewById(R.id.txtProbe2Temp);
//        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
//        fab.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                showNotification();
//                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
//                        .setAction("Action", null).show();
//            }
//        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        mHandler.post(mBBQOnline);
    }

    @Override
    protected void onPause() {
        super.onPause();
        HTTPClient.getInstance(getBaseContext()).cancellAll("BBQ_JSON");
        mHandler.removeCallbacksAndMessages(null);
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
        txtProbe1Temp.setText(String.format("%.0f F", tempF));
    }

    public void showNotification(String guts) {
        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), 0);
        Resources r = getResources();
        Notification notification = new NotificationCompat.Builder(this)
                .setTicker("test1")
                .setSmallIcon(android.R.drawable.ic_menu_report_image)
                .setContentTitle("BBQ Temp")
                .setContentText(guts)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();

        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(0, notification);
    }


    private Runnable mBBQOnline = new Runnable() {
        @Override
        public void run() {
            String url = String.format("https://api.particle.io/v1/devices/%s?access_token=%s", deviceID, particleAPIKEY);
//            Log.i("BBQ:URL", "mBBQOnline:" + url);
            JsonObjectRequest jsonObjReq = new JsonObjectRequest(Method.GET,
                    url, null, new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    //Log.d("BBQOnline JSON response", response.toString());
                    try {
                        // Parsing json object response
                        // response will be a json object
                        Online = response.getBoolean("connected");
                        if (Online) {
                            mHandler.post(mBBQTemps);
                        } else {
                            txtDebug.setText(getResources().getString(R.string.offline));
                            txtDebug.setVisibility(View.VISIBLE);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    txtDebug.setText(getResources().getString(R.string.connection_error));
                    txtDebug.setVisibility(View.VISIBLE);
                    mHandler.postDelayed(mBBQOnline, 30000);
                }
            });
            jsonObjReq.setTag("BBQ_JSON");
            HTTPClient.getInstance(getBaseContext()).addToRequestQueue(jsonObjReq);
        }
    };

    private Runnable mBBQTemps = new Runnable() {
        @Override
        public void run() {
            String url = String.format("https://api.particle.io/v1/devices/%s/%s?access_token=%s", deviceID, "resistance", particleAPIKEY);
//            Log.i("BBQ:URL", "mBBQTemps:" + url);
            final SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
            final int targetTemp = Integer.parseInt(SP.getString("probe1Target", "999"));
            JsonObjectRequest jsonObjReq = new JsonObjectRequest(Method.GET,
                    url, null, new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    //Log.d("BBQTemp JSON response", response.toString());
                    try {
                        // Parsing json object response
                        // response will be a json object
                        int resistance = response.getInt("result");
                        double ln_resistance = Math.log(resistance);
                        tempK = 1 / (steinhart_a + steinhart_b * ln_resistance + steinhart_c * (ln_resistance * ln_resistance * ln_resistance));
                        tempC = tempK - 273.15;
                        tempF = tempC * 1.8 + 32;
                        // timestamp = row.getJSONObject("coreInfo").getJSONObject("last_heard");
                        updateTextFields();
                        mHandler.postDelayed(mBBQTemps, 60000);
//                        if (tempF >= targetTemp) {
//                            showNotification("Probe 1 reached the target temperature");
//                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }, new Response.ErrorListener() {
                 @Override
                 public void onErrorResponse(VolleyError error) {
                     txtDebug.setText(getResources().getString(R.string.connection_error));
                     txtDebug.setVisibility(View.VISIBLE);
                     mHandler.postDelayed(mBBQOnline, 30000);
                 }
            });
            jsonObjReq.setTag("BBQ_JSON");
            HTTPClient.getInstance(getBaseContext()).addToRequestQueue(jsonObjReq);
        }
    };
}
