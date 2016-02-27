package com.example.hartmms.bbq;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.os.Handler;
import java.lang.ref.WeakReference;
import android.os.Message;
import android.os.Messenger;
import android.os.Bundle;
import android.os.RemoteException;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.Volley;
import com.android.volley.toolbox.JsonObjectRequest;
import org.json.JSONException;
import org.json.JSONObject;

public class BBQService extends Service {
    private static String LOG_TAG = "BBQ Service";
    private static String LOG_TAG_ONLINE = "BBQ Service: Online runner";
    private static String LOG_TAG_TEMP = "BBQ Service: Temp runner";
    static final int MSG_PROBE_DATA = 1000;
    static final int MSG_SETTINGS = 1002;
    static final int MSG_DEVICE_OFFLINE = 1004;
    static final int MSG_COMM_ERROR = 1005;
    static final int MSG_REGISTER_CLIENT = 1006;
    static final int MSG_UNREGISTER_CLIENT = 1007;
    static final int MSG_SETUP_ERROR = 1008;


    private boolean clientOnline = false;
    private Messenger mClient;
    private static double steinhart_a = 0.00024723753;
    private static double steinhart_b = 0.00023402251;
    private static double steinhart_c = 0.00000013879768;
    private double tempK;
    private double tempC;
    private int tempF;
    private static String deviceID = null;
    private static String particleAPIKEY = null;
    private Handler mHandler = new Handler();
    private int probe1Target, probe2Target;
    private boolean notify;
    private RequestQueue reQueue;

    class BBQServiceHandler extends Handler {
        private final WeakReference<BBQService> mService;
        public BBQServiceHandler(BBQService service) {
            mService = new WeakReference<BBQService>(service);
        }

        @Override
        public void handleMessage(Message msg) {
            Log.d(LOG_TAG, "client sent a message: ");
            switch (msg.what) {
                case MSG_REGISTER_CLIENT:
                    mClient = msg.replyTo;
                    clientOnline = true;
                    Log.d(LOG_TAG, "client online");
                    break;
                case MSG_UNREGISTER_CLIENT:
                    clientOnline = false;
                    Log.d(LOG_TAG, "client offline");
                    break;
                case MSG_SETTINGS:
                    Log.d(LOG_TAG, "got settings");
                    deviceID = msg.getData().getString("deviceID");
                    particleAPIKEY = msg.getData().getString("particleAPIKEY");
                    probe1Target = msg.getData().getInt("probe1Target");
                    probe2Target = msg.getData().getInt("probe2Target");
                    notify = msg.getData().getBoolean("notify");
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }
    final Messenger mMessenger = new Messenger(new BBQServiceHandler(this));


    @Override
    public IBinder onBind(Intent intent) {
        Log.v(LOG_TAG, "in onRebind");
        super.onRebind(intent);
        return mMessenger.getBinder();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.v(LOG_TAG, "in onCreate");
        // check to see if device is online
        reQueue = Volley.newRequestQueue(this);
        mHandler.post(mBBQOnline);
    }

    @Override
    public void onRebind(Intent intent) {
        Log.v(LOG_TAG, "in onRebind");
        clientOnline = true;
        super.onRebind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.v(LOG_TAG, "in onUnbind");
        clientOnline = false;
        return true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.v(LOG_TAG, "in onDestroy");
        // kill all handlers.
        reQueue.cancelAll("BBQ_TAG");
        //HTTPClient.getInstance(getBaseContext()).cancellAll("BBQ_JSON");
        mHandler.removeCallbacksAndMessages(null);
    }

    private Runnable mBBQOnline = new Runnable() {
        @Override
        public void run() {
            Log.d(LOG_TAG_ONLINE, "thread started");
            SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
            deviceID = SP.getString("deviceID", null);
            particleAPIKEY = SP.getString("particleAPIKEY", null);
            if (deviceID != null && particleAPIKEY != null) {
                Log.d(LOG_TAG_ONLINE, "dev and API are real, running request");
                String url = String.format("https://api.particle.io/v1/devices/%s?access_token=%s", deviceID, particleAPIKEY);
                JsonObjectRequest jsonObjReq = new JsonObjectRequest(Request.Method.GET,
                        url, null, new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        Log.d(LOG_TAG_ONLINE, response.toString());
                        try {
                            if (response.getBoolean("connected")) {
                                mHandler.post(mBBQTemps);
                            } else {
                                Log.d(LOG_TAG_ONLINE, "device not connected" );
                                try {
                                    if (clientOnline)
                                        mClient.send(Message.obtain(null, MSG_DEVICE_OFFLINE, 0, 0));
                                } catch (RemoteException e) {
                                    Log.d(LOG_TAG, "can't message UI");
                                }
                                mHandler.postDelayed(mBBQOnline, 30000);
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
//                    @Override
//                    public Priority getPriority() {
//                        return Request.Priority.IMMEDIATE;
//                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.d(LOG_TAG_ONLINE, "conn error");
                        try {
                            if (clientOnline)
                                mClient.send(Message.obtain(null, MSG_COMM_ERROR, 0, 0));
                        } catch (RemoteException e) {
                            Log.d(LOG_TAG, "can't message UI");
                        }
                        mHandler.postDelayed(mBBQOnline, 30000);
                    }
                });
                Log.d(LOG_TAG_ONLINE, "JSON request submitted");
                jsonObjReq.setTag("BBQ_JSON");
                reQueue.add(jsonObjReq);
                //HTTPClient.getInstance(getBaseContext()).addToRequestQueue(jsonObjReq);
            } else {
                Log.d(LOG_TAG_ONLINE, "dev or API are null");
                try {
                    if (clientOnline)
                        mClient.send(Message.obtain(null, MSG_SETUP_ERROR, 0, 0));
                } catch (RemoteException e) {
                    Log.d(LOG_TAG_ONLINE, "can't message UI");
                }
            }
        }
    };

    private Runnable mBBQTemps = new Runnable() {
        @Override
        public void run() {
            Log.d(LOG_TAG_TEMP, "thread started");
            String url = String.format("https://api.particle.io/v1/devices/%s/%s?access_token=%s", deviceID, "resistance", particleAPIKEY);
            JsonObjectRequest jsonObjReq = new JsonObjectRequest(Request.Method.GET,
                    url, null, new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    Log.d(LOG_TAG_TEMP, response.toString());
                    try {
                        // Parsing json object response
                        // response will be a json object
                        int resistance = response.getInt("result");
                        double ln_resistance = Math.log(resistance);
                        tempK = 1 / (steinhart_a + steinhart_b * ln_resistance + steinhart_c * (ln_resistance * ln_resistance * ln_resistance));
                        tempC = tempK - 273.15;
                        tempF = (int) (tempC * 1.8 + 32);
                        try {
                            if (clientOnline) {
                                Bundle b = new Bundle();
                                b.putString("probe1temp", Integer.toString(tempF));
                                // TODO: put in probe2 calc formulas here.
                                b.putString("probe2temp", "-");
                                Message msg = Message.obtain(null, MSG_PROBE_DATA);
                                msg.setData(b);
                                mClient.send(msg);
                            }
                        } catch (RemoteException e) {
                            Log.d(LOG_TAG_TEMP, "can't message UI");
                        }
                        mHandler.postDelayed(mBBQOnline, 60000);
                        if (tempF >= probe1Target && notify) {
                            showNotification("Probe 1 reached the target temperature");
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
//                @Override
//                public Priority getPriority() {
//                    return Request.Priority.IMMEDIATE;
//                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    try {
                        if (clientOnline)
                            mClient.send(Message.obtain(null, MSG_COMM_ERROR, 0, 0));
                    } catch (RemoteException e) {
                        // The client is dead. Remove it from the list; we are going through the list from back to front so this is safe to do inside the loop.
                        Log.d(LOG_TAG_TEMP, "can't message UI");
                    }
                    mHandler.postDelayed(mBBQOnline, 30000);
                }
            });
            Log.d(LOG_TAG_TEMP, "JSON request submitted");
            jsonObjReq.setTag("BBQ_JSON");
            //HTTPClient.getInstance(getBaseContext()).addToRequestQueue(jsonObjReq);
            reQueue.add(jsonObjReq);
        }
    };

    public void showNotification(String guts) {
        Intent intent = new Intent(this, MainActivity.class);
        // key to restoring activity and not re-launching
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, 0);
        Notification notification = new NotificationCompat.Builder(this)
                .setTicker("test1")
//                .setSmallIcon(android.R.drawable.ic_menu_report_image)
                .setSmallIcon(R.drawable.ic_notificaion)
                .setContentTitle("BBQ Temp")
                .setContentText(guts)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_ALL)
                .build();

        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(0, notification);
    }
}
