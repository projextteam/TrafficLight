package com.nuk.light.traffic;

import android.Manifest;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MyService extends Service implements LocationListener {
    /**
     * 外部使用
     */
    /* 設定 UI Handler */
    public void setUiHandler(String tag, Handler handler) {
        mVisibility.put(tag, true);

        if (handler == null) {
            handler = new Handler();
        }

        if (mUiHandler == null) {
            mUiHandler = handler;

            /* 若資料庫第一次建立，會從伺服器下載 */
            if (mMyDB.isOnCreate()) {
                mServiceHandler.post(run_downloadData);
            }

            /* 向 server 抓取事件 */
            mEventHandler.post(run_getEvent);

            /* 開始取得 Location */
            mServiceHandler.post(run_getLocation);
        } else {
            mServiceHandler.removeCallbacks(run_resetLocation);
            mUiHandler = handler;

            /* 判斷 Tag，更新 UI */
            switch (tag) {
                case MainActivity.TAG:
                    if (!mLocationProvidable) {
                        mUiHandler.sendEmptyMessage(Action.SET_NO_GPS);
                    } else if (mCurrentLocation == null) {
                        mUiHandler.sendEmptyMessage(Action.WAITING_GPS);
                    } else {
                        if (mCurrentTrafficLight == null) {
                            mUiHandler.sendEmptyMessage(Action.SET_NO_TRAFFIC_LIGHT);
                        } else {
                            mUiHandler.sendEmptyMessage(Action.SET_SPEED);
                            mUiHandler.sendEmptyMessage(Action.SET_STREET);
                            mUiHandler.sendEmptyMessage(Action.SET_TRAFFIC_LIGHT);
                            mUiHandler.sendEmptyMessage(Action.UPDATE_NEAREST_EVENT);
                        }
                    }

                    break;
                case FloatingWindow.TAG:
                    if (mCurrentTrafficLight == null) {
                        mUiHandler.sendEmptyMessage(Action.SET_NO_TRAFFIC_LIGHT);
                    } else {
                        mUiHandler.sendEmptyMessage(Action.SET_MAX_PROGRESS);
                        mUiHandler.sendEmptyMessage(Action.SET_TRAFFIC_LIGHT);
                    }

                    break;
            }
        }
    }

    /* 設定緊急事件位置 UI Handler */
    public void setReportUiHandler(Handler handler) {
        mServiceHandler.removeCallbacks(run_resetLocation);
        mReportUiHandler = handler;
        mVisibility.put(ReportActivity.TAG, true);
    }

    /* 取得現在位置 */
    public Location getCurrentLocation() {
        return mCurrentLocation;
    }

    /* 取得現在路名 */
    public String getStreetName() {
        return mStreetName;
    }

    /* 取得現在紅綠燈秒數 */
    public int getCountDown() {
        return mCountDown;
    }

    /* 取得現在紅綠燈燈號 */
    public String getStatus() {
        return mStatus;
    }

    /* 取得現在燈號最大秒數 */
    public int getMaxLightSecond() {
        return mCurrentTrafficLight == null ?
                0 :
                mCurrentTrafficLight.getMaxLightSecond();
    }

    /* 設定是否可視，若設定不可視則再判斷是否完全離開畫面 */
    public void setVisibility(String tag, boolean visibility) {
        mVisibility.put(tag, visibility);
        if (!visibility && !mVisibility.containsValue(true)) {
            mServiceHandler.postDelayed(run_resetLocation, 10000);
        }
    }

    /* 關閉 */
    public void finishIsInvisible() {
        if (!mVisibility.containsValue(true)) {
            mLocationManager.removeUpdates(this);
            stopSelf();
        }
    }

    /* 從手機端資料庫抓取 所有事件 */
    public Cursor getAllEvent()
    {
        return mMyDB.getAllEvent();
    }

    /* 回傳最近事件集合 */
    public ArrayList<ArrayList<String>> getNearestEvent()
    {
        return NearestEvent;
    }

    /* 取得所有紅綠燈 */
    public Cursor getAllLights() {
        return mMyDB.getAllLights();
    }

    /* 取得與伺服器的時間差 */
    public double getDelta() {
        return mDelta;
    }

    /* 取得紅綠燈之 Period */
    public Cursor getPeriod(int id) {
        return mMyDB.getPeriodCursor(id);
    }

    /* 讓 Setting 頁面可以存取 MyService */
    public SharedPreferences.OnSharedPreferenceChangeListener getSharedPrefChangeListener() {
        return mSharedPrefChangeListener;
    }

    /**
     * Property
     */
    /* log TAG */
    private static final String TAG = "MyService";

    /* Location */
    private LocationManager mLocationManager;
    private Geocoder mGeocoder;                             // 經緯度與地址轉換，用來取得路名
    private Location mCurrentLocation;

    private static final int ONE_MINUTE = 1000 * 60;
    private static final int TWO_MINUTES = 1000 * 60 * 2;
    private static final int LOCATION_UPDATE_MIN_DISTANCE = 5;     // 每一公尺
    private static final int LOCATION_UPDATE_MIN_TIME = 1000;      // 每10毫秒

    /* 當前狀態 */
    private String mStreetName;                             // 街道名稱
    private String mStatus;                                 // 紅綠燈狀態
    private int mCountDown;                                 // 紅綠燈秒數

    /* 選取紅綠燈 */
    private TrafficLight mCurrentTrafficLight;              // 當前紅綠燈

    private Cursor mClosestNode;                            // 實際上是所在 Vector 上的所有點，但 Cursor 會維持在最近的那一個位置
    private Cursor mNeighborNodes;                          // 相鄰的所有點，用來判斷接下來的方向
    private Cursor mTrafficLights;                          // 所在路上的紅綠燈

    /* 會用到的方向種類 */
    private enum BearingType {
        CURRENT_TO_CLOSEST,
        CURRENT_TO_NEIGHBOR,
        NEIGHBOR_TO_CLOSEST
    }

    /* 文字轉語音 */
    private TextToSpeech mTextToSpeech;

    /* 程序控制元件 */
    private IBinder mBinder;                                // Binder given to client
    class ServiceBinder extends Binder {
        /* 回傳 instance of MyService */
        MyService getService() {
            return MyService.this;
        }
    }

    /* setting */
    private SharedPreferences mSharedPref;
    private SharedPreferences.OnSharedPreferenceChangeListener mSharedPrefChangeListener;

    /* for UI */
    private Handler mUiHandler;                             // 更新 UI 使用
    private Handler mReportUiHandler;                       // 更新緊急事件位置使用

    /* for Service 內部 */
    private HandlerThread mServiceThread;                   // Service 主要流程，使用者位置更新所在 Thread
    private HandlerThread mEventThread;                     // 一般事件、緊急事件更新處理所在 Thread
    private Handler mServiceHandler;
    private Handler mEventHandler;

    private ExecutorService mExecutor;                      // 線程池

    private Runnable run_getLocation;                       // 開始抓取位置，
    private Runnable run_downloadData;                      // 向伺服器取得資料庫所有資料
    private Runnable run_getStreet;                         // 使用 Google API 取得道路資訊
    private Runnable run_countDown;                         // 用來倒數計時
    private Runnable run_resetLocation;                     // 重設位置相關操作
    private Runnable run_getEvent;                          // 向伺服器請求抓取事件資料
    private Runnable run_updateEmergency;

    private boolean mLocationProvidable;                    // 判斷 Location 是否可用
    private HashMap<String, Boolean> mVisibility;
    private boolean mEmergency;

    /* 資料庫 */
    private MyDB mMyDB;
    private boolean mIsDownloadDatabase;
    private double mDelta;

    /* event 相關 */
    private String key_Event; //事件key值
    private ArrayList<ArrayList<String>> NearestEvent; // 所有最近事件的集合
    private double past_lat;
    private double past_lng;
    private double move_distance; // 移動距離相關屬性


    /**
     * Service lift cycle
     */
    /* onCreate */
    @Override
    public void onCreate() {
        super.onCreate();

        /* 初始化所有元件 */
        initialize();
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                String response = NetUtils.get("traffic_light/getTime.php?time=" + System.currentTimeMillis());
                if(response != null)
                {
                    mDelta = Double.parseDouble(response);
                }else
                {
                    mDelta =0;
                }

            }
        });
    }

    /* 初始所有元件 */
    private void initialize() {
        Log.d(TAG,"Start initialize");

        /* Location */
        mLocationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);    // 取得定位服務
        mGeocoder = new Geocoder(MyService.this, Locale.TAIWAN);                          // Geocoder


        /* 當前狀態 */
        mStreetName = "";


        /* 文字轉語音 */
        mTextToSpeech = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int arg0) {
                // TTS 初始化成功
                if (arg0 == TextToSpeech.SUCCESS) {
                    mTextToSpeech.setPitch(0.9f); // 音調
                    mTextToSpeech.setSpeechRate(0.85f); // 速度

                    // 目前指定的【語系+國家】TTS, 已下載離線語音檔, 可以離線發音
                    if (mTextToSpeech.isLanguageAvailable(Locale.TAIWAN) == TextToSpeech.LANG_COUNTRY_AVAILABLE) {
                        mTextToSpeech.setLanguage(Locale.TAIWAN);
                    }
                }
            }
        });


        /* 初始程序控制元件 */
        // 回傳給 Client 的 Binder
        mBinder = new ServiceBinder();

        mSharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        mSharedPrefChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                switch (key) {
                    case "pip_cut":
                        if (mEmergency) {
                            if (sharedPreferences.getBoolean(key, false)) {
                                startActivity(new Intent(MyService.this, ReportActivity.class)
                                        .putExtra("PipMode", true));
                            } else {
                                mReportUiHandler.sendEmptyMessage(Action.FINISH_EMERGENCY);
                            }
                        }
                        break;
//                     case "main cut":
//                          break;
                }
            }
        };

        mVisibility = new HashMap<>();
        mEmergency = false;

        // 新開一條 HandlerThread 處理 Service 中的耗時操作
        // TODO: thread 管理
        mServiceThread = new HandlerThread("MyService");
        mServiceThread.start();
        mEventThread = new HandlerThread("Emergency");
        mEventThread.start();

        // 管理 ServiceThread
        mServiceHandler = new Handler(mServiceThread.getLooper());
        mEventHandler = new Handler(mEventThread.getLooper());

        // 線程池
        mExecutor = Executors.newFixedThreadPool(10);

        // 向伺服器取得資料庫所有資料
        run_downloadData = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG,"Download Database process");
                String response = NetUtils.get("traffic_light/getData.php");
                if (response == null) {
                    deleteDatabase("SQLiteDB.db");
                    mServiceHandler.removeCallbacksAndMessages(null);
                    Log.d(TAG,"Download Database Failure");
                    mUiHandler.sendEmptyMessage(Action.GET_DATA_FAIL_DIALOG);

                    return;
                }else
                    {
                        Log.d(TAG,"Download Database Success");
                    }

                mMyDB.insertAllData(response);
            }
        };

        // 開始取得位置
        run_getLocation = new Runnable() {
            @Override
            public void run() {
                /* 確認是否擁有 Location 權限 */
                if (ActivityCompat.checkSelfPermission(MyService.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                        && ActivityCompat.checkSelfPermission(MyService.this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }

                mUiHandler.sendEmptyMessage(Action.WAITING_GPS);

                /* 判斷位置是否開啟 */
                if (mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    mLocationProvidable = true;

                    Location lastGpsLocation = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    Location lastNetworkLocation = mLocationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                    long now = Calendar.getInstance().getTimeInMillis();

                    /* 若 getLastKnownLocation 取得的值可以用，就先做第一次定位 */
                    if (lastGpsLocation != null){// && now - lastGpsLocation.getTime() < TWO_MINUTES) {
                        firstSetCurrentLocation(lastGpsLocation);
                    }

                    else if (lastNetworkLocation != null){// && now - lastNetworkLocation.getTime() < ONE_MINUTE) {
                        //firstSetCurrentLocation(lastNetworkLocation);
                    }
                }

                /* 開始監聽 Location，如果有耗電考量，可以只用 GPS 即可，(模擬器用 Network 很可怕) */
                mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, LOCATION_UPDATE_MIN_TIME, LOCATION_UPDATE_MIN_DISTANCE, MyService.this);
               // mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, LOCATION_UPDATE_MIN_TIME, LOCATION_UPDATE_MIN_DISTANCE, MyService.this);
            }
        };

        // 取得路名 Runnable
        run_getStreet = new Runnable() {
            @Override
            public void run() {
                try {
                    List<Address> addresses;
                    String name;
                    /* 在某些地方會有 Address 但沒有 Thoroughfare 資訊，以 FeatureName 代替 */
                    if ((addresses = mGeocoder.getFromLocation(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude(), 1)).size() > 0 &&
                            ((name = addresses.get(0).getThoroughfare()) != null ||
                            (name = addresses.get(0).getFeatureName()) != null)) {
                        mStreetName = name;

                        /* 更新UI */
                        mUiHandler.sendEmptyMessage(Action.SET_STREET);

                        /* 第一次取得路名，開始計算自己的位置 */
                        if (mClosestNode == null) {
                            /* 相同路名上最近的 Node */
                            mClosestNode = moveToClosestPosition(mMyDB.getNodesOnRoad(mStreetName));

                            if (mClosestNode.getCount() == 0) {
                                mClosestNode = null;
                                mCurrentLocation = null;
                                mUiHandler.sendEmptyMessage(Action.SET_NO_NODE);
                                return;
                            }

                            /* Closest Node 相鄰的 Nodes */
                            mNeighborNodes = mMyDB.getNeighborNodes(mClosestNode);

                            /* 確定所在 Vector:
                             * (1) 若 Closest Node 不是路口點，則可直接確定所在 Vector
                             * (2) 而若是路口點，則再從每一個相鄰 Node 所在的 Vector 中，取相對方向最接近的那一個也可得出
                             * */

                            /* 路口情況 */
                            if (mClosestNode.getShort(mClosestNode.getColumnIndex("IsCross")) == 1) {
                                /* 把 NeighborNode 移到方向(NeighborNode -> ClosestNode)與方向(現在位置 -> ClosestNode) 最接近的那個 */
                                moveNeighborToTarget(getBearing(BearingType.CURRENT_TO_CLOSEST), BearingType.NEIGHBOR_TO_CLOSEST);
                            } else {
                                mNeighborNodes.moveToFirst();
                            }

                            /* 取得 Vector 後，把該 Vector 上的 Nodes 存起來 */
                            mClosestNode.close();
                            mClosestNode = moveToClosestPosition(mMyDB.getNodesOnNodeVector(mNeighborNodes));

                            /* 取得該 Vector 的紅綠燈，第一次先預設顯示最近的那個，而這也是當作確認目前行進的方向 */
                            mTrafficLights = moveToClosestPosition(mMyDB.getTrafficLightsOnNodeVector(mNeighborNodes));

                            /* 開啟計時器 Thread 顯示紅綠燈 */
                            updateTrafficLight();

                            /* 更新車速 */
                            mUiHandler.sendEmptyMessage(Action.SET_SPEED);
                        }
                    } else if (mClosestNode == null) {
                        mCurrentLocation = null;
                        mUiHandler.sendEmptyMessage(Action.WAITING_GPS);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };

        // 倒數計時器
        run_countDown = new Runnable() {
            @Override
            public void run() {
                if (mCurrentTrafficLight.isNeededReminding(mSharedPref.getString("remind_second", null))) {
                    mTextToSpeech.speak("前方路口即將紅燈，請減速慢行", TextToSpeech.QUEUE_FLUSH, null);
                }

                mStatus = mCurrentTrafficLight.getStatus();
                mCountDown = mCurrentTrafficLight.getCountDown();

                if (!mCurrentTrafficLight.tick()) {
                    mUiHandler.sendEmptyMessage(Action.SET_MAX_PROGRESS);
                }

                mUiHandler.sendEmptyMessage(Action.SET_TRAFFIC_LIGHT);

                mServiceHandler.postDelayed(this, 997);
            }
        };

        // 重設位置
        run_resetLocation = new Runnable() {
            @Override
            public void run() {
                resetLocation(true);
            }
        };

        //抓取Event資料庫
        run_getEvent = new Runnable() {
            @Override
            public void run() {

                //Log.d(TAG,"Start download DB from server");
                String response = NetUtils.post("traffic_light/catch_event.php","key="+key_Event);
                //String response = NetUtils.get("http://140.127.208.227/traffic_light/test_catch_event.php?key="+key_Event);
                if(response == null)
                {
                    if(mUiHandler != null)
                    {
                        mUiHandler.sendEmptyMessage(Action.GET_EVENT_FAIL_DIALOG);
                    }
                    return;
                }

                try {
                    JSONObject jsonObject = new JSONObject(response);
                    String response_Event = jsonObject.getString("event");
                    String response_Location = jsonObject.getString("location");
                    JSONArray jsonArray = new JSONArray(response_Location);

                    if ("Please send a key".equals(response_Event)) {
                        Log.d(TAG,"Please send a key");
                        if(mUiHandler != null)
                        {
                            mUiHandler.sendEmptyMessage(Action.GET_EVENT_FAIL_DIALOG);
                        }
                        return;
                    }

                    if ("No event update".equals(response_Event))
                    {
                        Log.d(TAG,"No event update");
                        mIsDownloadDatabase = true;

                    }else
                    {
                        Log.d(TAG,"事件更新");
                        mIsDownloadDatabase = true;
                        key_Event = mMyDB.insertEvent(response_Event);

                        if(mUiHandler != null)
                        {
                            mUiHandler.sendEmptyMessage(Action.UPDATE_REPORT_MARKER);
                        }
                    }

                    if (!mEmergency && jsonArray.length() != 0)
                    {
                        mEmergency = true;

                        //有緊急車輛資訊 開始快速抓取位置
                        mEventHandler.post(run_updateEmergency);

                        if (mSharedPref.getBoolean("pip_cut", false) &&
                                (mVisibility.get(ReportActivity.TAG) == null || !mVisibility.get(ReportActivity.TAG))) {
                            startActivity(new Intent(MyService.this, ReportActivity.class)
                                    .putExtra("PipMode", true));
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                //Log.d(TAG,"Finish download DB from server");
                //每10秒自動抓取
                //demo版先改成三秒試試
                mEventHandler.postDelayed(this, 3000);
            }
        };

        // 更新緊急事件位置
        run_updateEmergency = new Runnable() {
            @Override
            public void run() {
                String response = NetUtils.get("traffic_light/getLocation.php");
                try {
                    JSONArray jsonArray = new JSONArray(response);
                    if (jsonArray.length() != 0) {
                        for (int i = 0; i < jsonArray.length(); i++) {
                            JSONObject jsonObject = jsonArray.getJSONObject(i);
                            // TODO: 多緊急事件同時發生
                            Message message = Message.obtain();
                            message.what = Action.HAVE_EMERGENCY;
                            message.obj = new LatLng(jsonObject.getDouble("Latitude"),
                                    jsonObject.getDouble("Longitude"));

                            if (mReportUiHandler != null) {
                                mReportUiHandler.sendMessage(message);
                            }

                            mEventHandler.postDelayed(this, 1000);
                        }
                    } else {
                        if (mReportUiHandler != null) {
                            Log.d(TAG, "EMERGENCY_finish");
                            mReportUiHandler.sendEmptyMessage(Action.FINISH_EMERGENCY);
                        }

                        mEmergency = false;
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        };


        /* 資料庫 */
        mMyDB = new MyDB(this);
        mIsDownloadDatabase = false;


        /* event 相關 */
        key_Event = "0";

        NearestEvent = new ArrayList<>(); // 所有最近事件的集合
        past_lat = past_lng = move_distance = 0.0; // 移動距離相關屬性

        Log.d(TAG,"Finish initialize");
    }

    /* Bind Service */
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    /* onDestroy */
    @Override
    public void onDestroy() {
        super.onDestroy();

        mLocationManager.removeUpdates(this);
        mUiHandler.removeCallbacksAndMessages(null);
        mUiHandler = null;

        mServiceHandler.removeCallbacksAndMessages(null);
        mServiceThread.quit();

        mEventHandler.removeCallbacksAndMessages(null);
        mEventThread.quit();

        if (mReportUiHandler != null) {
            mReportUiHandler.removeCallbacksAndMessages(null);
        }

        mExecutor.shutdown();
        mExecutor.shutdownNow();

        mTextToSpeech.stop();
        mTextToSpeech.shutdown();

        mMyDB.close();
    }


    /**
     * 抓取正確紅綠燈
     */
    /* 第一次取得位置 */
    private void firstSetCurrentLocation(final Location location) {
        mCurrentLocation = location;

        /* Demo 使用 */
        mUiHandler.sendEmptyMessage(Action.UPDATE_CURRENT_MARKER);
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                NetUtils.post("traffic_light/pushData.php", "id=demo&" +
                        "lat=" + location.getLatitude() + "&" +
                        "lng=" + location.getLongitude()
                );
            }
        });

        /* 更新車速、路名 */
        run_getStreet.run();                    // 第一次更新路名會執行第一次定位
    }

    /* 重設位置 */
    private void resetLocation(boolean removeUpdates) {
        mLocationProvidable = false;

        if (removeUpdates) {
            mUiHandler.removeCallbacksAndMessages(null);
            mUiHandler = null;
            mLocationManager.removeUpdates(this);
        }

        mServiceHandler.removeCallbacks(run_countDown);

        mStreetName = "";
        mCurrentLocation = null;
        mCurrentTrafficLight = null;
        mClosestNode = null;     // 最近點為 Null 才會執行 firstLocate()
    }

    /* 把 Cursor 移動到最接近自己位置的那個 */
    private Cursor moveToClosestPosition (Cursor cursor) {
        // TODO: 更快的算法
        // 目前使用的方式為 O(N)，N 為 cursor 資料數
        int min_i = -1;
        float min_distance = Float.MAX_VALUE;

        for (int i = 0; i < cursor.getCount(); i++) {
            cursor.moveToPosition(i);

            float[] results = new float[1];
            Location.distanceBetween(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude(),
                    cursor.getDouble(cursor.getColumnIndex("Latitude")),
                    cursor.getDouble(cursor.getColumnIndex("Longitude")),
                    results);

            if (results[0] < min_distance) {
                min_distance = results[0];
                min_i = i;
            }
        }

        cursor.moveToPosition(min_i);

        return cursor;
    }

    /* 依照傳入的方向類型，回傳該方向數值 */
    private float getBearing(BearingType type) {
        float[] results = new float[2];

        switch (type) {
            case CURRENT_TO_CLOSEST:
                Location.distanceBetween(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude(),
                        mClosestNode.getDouble(mClosestNode.getColumnIndex("Latitude")),
                        mClosestNode.getDouble(mClosestNode.getColumnIndex("Longitude")),
                        results);

                break;
            case CURRENT_TO_NEIGHBOR:
                Location.distanceBetween(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude(),
                        mNeighborNodes.getDouble(mNeighborNodes.getColumnIndex("Latitude")),
                        mNeighborNodes.getDouble(mNeighborNodes.getColumnIndex("Longitude")),
                        results);

                break;
            case NEIGHBOR_TO_CLOSEST:
                Location.distanceBetween(mNeighborNodes.getDouble(mNeighborNodes.getColumnIndex("Latitude")),
                        mNeighborNodes.getDouble(mNeighborNodes.getColumnIndex("Longitude")),
                        mClosestNode.getDouble(mClosestNode.getColumnIndex("Latitude")),
                        mClosestNode.getDouble(mClosestNode.getColumnIndex("Longitude")),
                        results);

                break;
        }

        return results[1];
    }

    /* 把 NeighborNodes Cursor 移動到與目標方向最接近的那個 */
    private void moveNeighborToTarget(float targetBearing, BearingType type) {
        int min_i = -1;
        float min_angle = 180;
        for (int i = 0; i < mNeighborNodes.getCount(); i++) {
            mNeighborNodes.moveToPosition(i);

            float angle = Math.abs(targetBearing - getBearing(type));
            if (angle > 180) {
                angle = 360 - angle;
            }

            if (angle < min_angle) {
                min_angle = angle;
                min_i = i;
            }
        }

        mNeighborNodes.moveToPosition(min_i);
    }

    /* 傳入紅綠燈 Id，更新紅綠燈 */
    private void updateTrafficLight() {
        int id = mTrafficLights.getInt(mTrafficLights.getColumnIndex("Id"));

        if (id == 0) {
            /* 關閉前一個倒數 Thread */
            mServiceHandler.removeCallbacks(run_countDown);

            mUiHandler.sendEmptyMessage(Action.SET_NO_TRAFFIC_LIGHT);

            mCurrentTrafficLight = null;
        }
        /* 使用 TrafficLight 顯示紅綠燈 */
        else if (mCurrentTrafficLight == null || mCurrentTrafficLight.Id != id) {
            /* 關閉前一個倒數 Thread */
            mServiceHandler.removeCallbacks(run_countDown);

            /* 建立新的紅綠燈 */
            mCurrentTrafficLight = new TrafficLight(id, getPeriod(id), mDelta);

            /* 繼續倒數 */
            mUiHandler.sendEmptyMessage(Action.SET_MAX_PROGRESS);
            mServiceHandler.post(run_countDown);
        }
    }

    /* android developer 來的 */
    // TODO: 修改參數
    // 我們需要的是很即時的位置，兩分鐘可能太長，需要經過多次實驗來微調。
    private boolean isBetterLocation(Location location) {
        // Check if the old and new location have same position
        boolean isSamePosition = location.getLatitude() == mCurrentLocation.getLatitude() &&
                location.getLongitude() == location.getLongitude();

        if (isSamePosition) {
            return false;
        }

        // Check whether the new location fix is newer or older
        long timeDelta = location.getTime() - mCurrentLocation.getTime();
        boolean isSignificantlyNewer = timeDelta > TWO_MINUTES;
        boolean isSignificantlyOlder = timeDelta < -TWO_MINUTES;
        boolean isNewer = timeDelta > 0;

        // If it's been more than two minutes since the current location, use the new location
        // because the user has likely moved
        if (isSignificantlyNewer) {
            return true;
            // If the new location is more than two minutes older, it must be worse
        } else if (isSignificantlyOlder) {
            return false;
        }

        // Check whether the new location fix is more or less accurate
        int accuracyDelta = (int) (location.getAccuracy() - mCurrentLocation.getAccuracy());
        boolean isLessAccurate = accuracyDelta > 0;
        boolean isMoreAccurate = accuracyDelta < 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 200;

        // Check if the old and new location are from the same provider
        boolean isFromSameProvider = isSameProvider(location.getProvider(),
                mCurrentLocation.getProvider());

        // Determine location quality using a combination of timeliness and accuracy
        if (isMoreAccurate) {
            return true;
        } else if (isNewer && !isLessAccurate) {
            return true;
        } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
            return true;
        }
        return false;
    }

    /* Checks whether two providers are the same */
    private boolean isSameProvider(String provider1, String provider2) {
        if (provider1 == null) {
            return provider2 == null;
        }
        return provider1.equals(provider2);
    }

    /* 去資料庫抓取最近事件並儲存 */
    private void storeNearestEvent()
    {
        Log.d(TAG,"Start StoreNearestEvent");

        for (int category_number=1;category_number<=6;category_number++)
        {
            int Index =0; // 儲存最近事件的索引
            double distance = Double.parseDouble(mSharedPref.getString("remind_accuracy", null));
            Cursor  myDBCategoryEvent = mMyDB.getCategoryEvent(category_number);
            ArrayList<String> event = new ArrayList<>(); //儲存特定最近事件
            event.add(Integer.toString(category_number)); // category_number

            if (myDBCategoryEvent.getCount() != 0)
            {
                Log.d(TAG,"StoreNearestEvent " + Integer.toString(category_number) + " getCount is Success!");
                myDBCategoryEvent.moveToFirst();
                while(!myDBCategoryEvent.isAfterLast())
                {
                    // 進行比對最短距離
                    float s[] = new float[1];
                    Location.distanceBetween(mCurrentLocation.getLatitude(),mCurrentLocation.getLongitude(),myDBCategoryEvent.getDouble(1),myDBCategoryEvent.getDouble(2),s);
                    if(s[0]<distance)
                    {
                        distance =s[0];
                        Index = myDBCategoryEvent.getPosition();
                    }
                    myDBCategoryEvent.moveToNext();
                }
                Log.d(TAG,"Index is : "+ Integer.toString(Index));

                //移動到指定事件
                myDBCategoryEvent.moveToPosition(Index);


                //將最近事件存進 ArrayList
                event.add(myDBCategoryEvent.getString(4)); // starttime
                event.add(myDBCategoryEvent.getString(5)); // endtime
                event.add(myDBCategoryEvent.getString(7)); // content
            }else
            {
                Log.d(TAG,"StoreNearestEvent " + Integer.toString(category_number) + " Count is empty!");
                // 資料庫返回 空集合
                event.add("null");
                event.add("null");
                event.add("null");
            }
            NearestEvent.add(event);

        }
        Log.d(TAG,"Finish StoreNearestEvent");
    }

    /** Override LocationListener */
    @Override
    public void onLocationChanged(Location location) {
        /* 第一次取得位置 */
        if (mCurrentLocation == null) {
            firstSetCurrentLocation(location);
            return;
        }

        /* 位置移動中 */
        if (isBetterLocation(location)) {
            String closestNode_id = mClosestNode.getString(mClosestNode.getColumnIndex("Id"));

            /* 最近點為路口: 先判斷是遠離離是靠近這個路口，紅綠燈方向就可由路口 Number 而定 */
            if (mClosestNode.getInt(mClosestNode.getColumnIndex("IsCross")) == 1) {
                /* 計算距離 */
                float[] currentResults = new float[1];
                Location.distanceBetween(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude(),
                        mClosestNode.getDouble(mClosestNode.getColumnIndex("Latitude")),
                        mClosestNode.getDouble(mClosestNode.getColumnIndex("Longitude")),
                        currentResults);
                float[] nextResults = new float[1];
                Location.distanceBetween(location.getLatitude(), location.getLongitude(),
                        mClosestNode.getDouble(mClosestNode.getColumnIndex("Latitude")),
                        mClosestNode.getDouble(mClosestNode.getColumnIndex("Longitude")),
                        nextResults);

                /* 遠離路口的方向，檢查是換路口還是同一條路往回走 */
                int direction;
                if (currentResults[0] < nextResults[0]) {
                    int position = mNeighborNodes.getPosition();
                    /* 檢查方向 */
                    moveNeighborToTarget(mCurrentLocation.bearingTo(location), BearingType.CURRENT_TO_NEIGHBOR);

                    /* 可知紅綠燈方向 */
                    direction = mMyDB.getCrossNumberOnNodeVector(mClosestNode, mNeighborNodes) == 0 ? 1 : 0;

                    /* 如果不在原本的路上，需重新取得 Node, Traffic Light */
                    if (position != mNeighborNodes.getPosition()) {
                        mClosestNode.close();
                        mClosestNode = mMyDB.getNodesOnNodeVector(mNeighborNodes);

                        mTrafficLights.close();
                        mTrafficLights = mMyDB.getTrafficLightsOnNodeVector(mNeighborNodes);
                    }

                    /* 更新 ClosestNode */
                    mCurrentLocation = location;
                    moveToClosestPosition(mClosestNode);

                    if (!closestNode_id.equals(mClosestNode.getString(mClosestNode.getColumnIndex("Id")))) {
                        mNeighborNodes = mMyDB.getNeighborNodes(mClosestNode);
                    }

                    /* 遠離路口才須更新路名，可減少一些 request */
                    mExecutor.execute(run_getStreet);
                }
                /* 靠近路口，只需更新紅綠燈，且不須再更新最近點 */
                else {
                    /* 可知紅綠燈方向 */
                    direction = mClosestNode.getInt(mClosestNode.getColumnIndex("Number")) == 0 ? 0 : 1;
                    mCurrentLocation = location;     // 更新現在位置
                }

                mTrafficLights.moveToPosition(direction);
            }
            /* 最近點不是路口:
             * (1) 檢查方向看是否需要更新紅綠燈
             * (2) 檢查最近點是否有變
             * */
            else {
                /* 檢查方向 */
                moveNeighborToTarget(mCurrentLocation.bearingTo(location), BearingType.CURRENT_TO_NEIGHBOR);

                /* 紅綠燈顯示方向是否要改變 */
                if ((mTrafficLights.getInt(mTrafficLights.getColumnIndex("Direction")) > 0 && mNeighborNodes.getPosition() == 0) ||
                        mTrafficLights.getInt(mTrafficLights.getColumnIndex("Direction")) < 0 && mNeighborNodes.getPosition() == 1) {
                    mTrafficLights.moveToPosition((mTrafficLights.getPosition() == 0) ? 1 : 0);
                }

                /* 更新 ClosestNode */
                mCurrentLocation = location;
                moveToClosestPosition(mClosestNode);

                if (!closestNode_id.equals(mClosestNode.getString(mClosestNode.getColumnIndex("Id")))) {
                    mNeighborNodes = mMyDB.getNeighborNodes(mClosestNode);
                }
            }

            /* 更新紅綠燈 */
            updateTrafficLight();

            /* 更新車速 */
            mUiHandler.sendEmptyMessage(Action.SET_SPEED);

            /* 紀錄移動距離 */
            double now_lat = mCurrentLocation.getLatitude();
            double now_lng = mCurrentLocation.getLongitude();
            float disBetween[] = new float[1];

            Location.distanceBetween(past_lat, past_lng, now_lat, now_lng, disBetween);
            move_distance += disBetween[0];
            past_lat =now_lat;
            past_lng= now_lng;

            /* 當移動距離達到500m 開始抓取最近事件*/
            //Log.d(TAG,"UPDATE_NEAREST_EVENT");
            //Log.d(TAG, Double.toString(move_distance) + " + " + mIsDownloadDatabase);
            if (move_distance >= Double.parseDouble(mSharedPref.getString("remind_frequency", null)) && mIsDownloadDatabase)
            {
                //Log.d(TAG, Double.toString(move_distance) + "+" + mSharedPref.getString("remind_frequency", null) );
                Log.d(TAG,"Need Database");
                storeNearestEvent();
                move_distance= 0.0;
                mUiHandler.sendEmptyMessage(Action.UPDATE_NEAREST_EVENT);
            }

            /* Demo 使用 */
            if (mVisibility.containsKey(ReportActivity.TAG) && mVisibility.get(ReportActivity.TAG)) {
                (mReportUiHandler != null && mUiHandler != mReportUiHandler ? mReportUiHandler : mUiHandler)
                        .sendEmptyMessage(Action.UPDATE_CURRENT_MARKER);
            }
            mExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    NetUtils.post("traffic_light/pushData.php", "id=demo&" +
                            "lat=" + mCurrentLocation.getLatitude() + "&" +
                            "lng=" + mCurrentLocation.getLongitude()
                    );
                }
            });
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // TODO: 可用衛星信號等資訊
    }

    @Override
    public void onProviderEnabled(String provider) {
        if (provider.equals("network")) {
            return;
        }

        mLocationProvidable = true;
        mUiHandler.sendEmptyMessage(Action.WAITING_GPS);
    }

    @Override
    public void onProviderDisabled(String provider) {
        if (provider.equals("network")) {
            return;
        }

        resetLocation(false);

        mUiHandler.sendEmptyMessage(Action.SET_NO_GPS);
    }
}