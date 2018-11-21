package com.nuk.light.traffic;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    /** Property */
    /* Log Tag */
    public static final String TAG = "Main";

    /* Permission code */
    private static final int CODE_DRAW_OVER_OTHER_APP_PERMISSION = 2084;
    private static final int MY_PRECISION_FINE_LOCATION = 101;
    private static final int LOCATION_SETTING = 1024;

    /* UI元件 */
    private TextView tv_Street;              // 街道
    private TextView tv_Speed;               // 車速
    private TextView tv_CountDown;           // 剩餘秒數
    private TextView tv_Description;         // 狀況敘述

    private ImageView iv_TrafficLight;       // 紅綠燈狀態
    private ImageView iv_Start;

    private ImageView[] iv_Events;
    private List<Integer> mEventIds;

    private AlertDialog ad_GPS;

    private String mDescription;


    /* 程序控制元件 */
    private Handler mHandler;
    private MyService mMyService;
    private ServiceConnection mConnection;

    private Runnable[] run_twinkle;          // 執行閃爍
    private SharedPreferences mSharedPref;   // 設定


    /* Event 儲存屬性 */
    private ArrayList<ArrayList<String>> ArrayList_Event;

    /** Method */
    /* onCreate */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /* 初始化所有元件 */
        initialize();

        /* 若已經給予定位權限，則開始執行各項運作，否則發出請求 */
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            /* 發出請求，於 onRequestPermissionsResult() 處理結果 */
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MY_PRECISION_FINE_LOCATION);
        } else {
            /* 若 FloatingWindow 有開啟，則關閉該 Service，讓畫面只有主畫面與 FloatingWindow 兩者之一 */
            stopService(new Intent(this, FloatingWindow.class));

            /* Start and bind service */
            startService(new Intent(this, MyService.class));
            bindService(new Intent(this, MyService.class), mConnection, BIND_AUTO_CREATE);
        }
        //TODO:try
        //savedInstanceState.
    }

    /* 初始化所有元件 */
    private void initialize() {
        Log.d(TAG,"Start initialize");
        /* 初始UI元件 */
        // View OnClickListener
        View.OnClickListener onClickListener = new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (v.getId()) {
                    case R.id.report:
                        startActivity(new Intent(MainActivity.this, ReportActivity.class));

                        break;
                    case R.id.setting:
                        startActivity(new Intent(MainActivity.this, SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));

                        break;
                    case R.id.start:
                        /* 取得浮窗權限 */
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            if (!Settings.canDrawOverlays(MainActivity.this)) {
                                mMyService.setVisibility("OVERLAY_SETTING", true);
                                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:" + getPackageName()));
                                startActivityForResult(intent, CODE_DRAW_OVER_OTHER_APP_PERMISSION);
                            } else {
                                /* 開啟 FloatingWindow Service */
                                openFloatingWindow();
                            }
                        }

                        break;
                    case R.id.traffic_lights:
                        startActivity(new Intent(MainActivity.this, TrafficLightsActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));

                        break;
                }
            }
        };

        View.OnClickListener eventOnClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int index = mEventIds.indexOf(v.getId());
                //bool_event_check[index] = true;
                Log.d(TAG,"I click on [index] is" + index);
                toggleTwinkle(index, true);
                tv_Description.setText(ArrayList_Event.get(index).get(3));
            }
        };

        // TextView
        tv_Street = findViewById(R.id.street);
        tv_CountDown = findViewById(R.id.CountDown);
        tv_Speed = findViewById(R.id.speed);
        tv_Description = findViewById(R.id.description);
        tv_Description.setMovementMethod(new ScrollingMovementMethod());
        mDescription = "";

        // ImageView
        iv_Start = findViewById(R.id.start);
        iv_Start.setOnClickListener(onClickListener);
        iv_Start.setAlpha(0.2f);
        iv_Start.setEnabled(false);

        iv_TrafficLight = findViewById(R.id.traffic);

        ImageView iv_Setting = findViewById(R.id.setting);
        iv_Setting.setOnClickListener(onClickListener);

        ImageView iv_Report = findViewById(R.id.report);
        iv_Report.setOnClickListener(onClickListener);

        ImageView iv_Traffic_lights = findViewById(R.id.traffic_lights);
        iv_Traffic_lights.setOnClickListener(onClickListener);

        // Event
        mEventIds = Arrays.asList(R.id.repair, R.id.roadblock, R.id.accident, R.id.police, R.id.drop, R.id.others);

        iv_Events = new ImageView[6];
        run_twinkle = new Runnable[6];
        for (int i = 0; i < 6; i++) {
            final int index = i;
            iv_Events[i] = findViewById(mEventIds.get(i));
            iv_Events[i].setOnClickListener(eventOnClickListener);
            iv_Events[i].setEnabled(false);

            run_twinkle[i] = new Runnable() {
                @Override
                public void run() {
                    iv_Events[index].setAlpha(iv_Events[index].getAlpha() == 1f ? 0.2f : 1f);
                    mHandler.postDelayed(this, 500);
                }
            };
        }

        // Dialog
        ad_GPS = new AlertDialog.Builder(MainActivity.this)
                .setMessage("如要繼續使用，請開啟裝置定位功能")
                .setCancelable(false)
                .setPositiveButton("確認", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mMyService.setVisibility("LOCATION_SETTING", true);
                        //跳转到手机打开GPS页面
                        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        //设置完成后返回原来的界面
                        startActivityForResult(intent, LOCATION_SETTING);
                    }
                })
                .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        mHandler.sendEmptyMessage(Action.SET_NO_GPS);
                    }
                }).create();


        /* 初始程序控制元件 */
        // 初始 Handler 的動作種類
        mHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message message) {
                switch (message.what) {
                    case Action.SET_TRAFFIC_LIGHT:
                        tv_CountDown.setTextColor(Color.BLACK);
                        String countDown = "";

                        switch (mMyService.getStatus()) {
                            case "Green":
                                iv_TrafficLight.setImageResource(R.drawable.light_green);

                                break;
                            case "Red":
                                iv_TrafficLight.setImageResource(R.drawable.light_red);
                                countDown = String.valueOf(mMyService.getCountDown());

                                break;
                            case "Yellow":
                                iv_TrafficLight.setImageResource(R.drawable.light_yellow);

                                break;
                            case "Flash_yellow":
                                iv_TrafficLight.setImageResource(R.drawable.light_yellow);

                                break;
                            default:
                                iv_TrafficLight.setImageResource(R.drawable.light_red);

                                break;
                        }
                        tv_CountDown.setText(countDown);

                        iv_Start.setAlpha(1f);
                        iv_Start.setEnabled(true);
                        if (mDescription.equals("等待GPS定位") || mDescription.equals("附近無紅綠燈") || mDescription.equals("此處沒有道路資料")) {
                            mDescription = "";
                            tv_Description.setText(mDescription);
                        }

                        break;
                    case Action.SET_STREET:
                        tv_Street.setText(mMyService.getStreetName());

                        break;
                    case Action.SET_SPEED:
                        tv_Speed.setText(String.format(Locale.TAIWAN, "%.2f km/hr", mMyService.getCurrentLocation().getSpeed() * 3.6));

                        break;
                    case Action.SET_NO_GPS:
                        // 開啟定位功能對話框
                        if (!ad_GPS.isShowing()) {
                            ad_GPS.show();
                        }

                        iv_Start.setAlpha(1f);
                        iv_Start.setEnabled(false);

                        break;
                    case Action.SET_NO_TRAFFIC_LIGHT:
                        tv_CountDown.setText("");
                        mDescription = "附近無紅綠燈";
                        tv_Description.setText(mDescription);
                        iv_TrafficLight.setImageResource(R.drawable.light_red);

                        iv_Start.setAlpha(1f);
                        iv_Start.setEnabled(true);

                        break;
                    case Action.WAITING_GPS:
                        iv_TrafficLight.setImageResource(R.drawable.light_red);
                        mDescription = "等待GPS定位";
                        tv_Description.setText(mDescription);
                        tv_CountDown.setText("");
                        tv_Street.setText("");
                        tv_Speed.setText("0.00 km/hr");

                        if (ad_GPS.isShowing()) {
                            ad_GPS.dismiss();
                        }

                        break;
                    case Action.SET_NO_NODE:
                        mDescription = "此處沒有道路資料";
                        tv_Description.setText(mDescription);

                        break;
                    case Action.GET_DATA_FAIL_DIALOG:
                        new AlertDialog.Builder(MainActivity.this)
                                .setMessage("資料抓取失敗，請重新啟動")
                                .setPositiveButton("確認", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        finish();
                                    }
                                })
                                .show();

                        break;
                    case Action.UPDATE_NEAREST_EVENT:
                        Log.d(TAG,"Call OMG");
                        ArrayList_Event = mMyService.getNearestEvent();

                        if (ArrayList_Event.size() ==0)
                        {
                            Log.d(TAG,"ArrayList_Event is empty");
                            break;
                        }

                        /* 設定關閉主畫面插播顯示 */
                        if (!mSharedPref.getBoolean("main_cut", false)) {
                            break;
                        }
                        boolean first = true;

                        for (int i = 0; i < 6; i++)
                        {
                            toggleTwinkle(i, true);
                            // TODO: 哪個先顯示 ?
                            if (ArrayList_Event.get(i).get(1).equals("null")) {
                                iv_Events[i].setAlpha(0.2f);
                                iv_Events[i].setEnabled(false);
                            } else {
                                iv_Events[i].setAlpha(1f);
                                iv_Events[i].setEnabled(true);
                                if (first) {
                                    first = false;
                                    tv_Description.setText(ArrayList_Event.get(i).get(3));
                                } else{
                                    toggleTwinkle(i, first);
                                }
                            }
                        }
                        break;
                }

                return true;
            }
        });

        // Connection to bind service
        mConnection = new ServiceConnection() {
            // Called when the connection with the service is established
            public void onServiceConnected(ComponentName className, IBinder service) {
                // Because we have bound to an explicit
                // service that is running in our own process, we can
                // cast its IBinder to a concrete class and directly access it.

                MyService.ServiceBinder mBinder = (MyService.ServiceBinder)service;
                mMyService = mBinder.getService();
                mMyService.setUiHandler(TAG, mHandler);
            }

            // Called when the connection with the service disconnects unexpectedly
            public void onServiceDisconnected(ComponentName className) {

            }
        };

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        mSharedPref = PreferenceManager.getDefaultSharedPreferences(this);

        Log.d(TAG,"Finish initialize");
    }

    /* 開啟 FloatingWindow */
    private void openFloatingWindow() {
        /* 呼叫 google maps，只能在 api 23 以上用 */
        Uri gmmIntentUri = Uri.parse("geo:" + mMyService.getCurrentLocation().getLatitude()
                + "," + mMyService.getCurrentLocation().getLongitude());
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
        mapIntent.setPackage("com.google.android.apps.maps");
        if (mapIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(mapIntent);
        }

        startService(new Intent(MainActivity.this, FloatingWindow.class));
        finish();
    }

    /* 開關事件閃爍，可調整 postDelay */
    private void toggleTwinkle(int eventIndex, boolean isChecked) {
        if (!isChecked) {
            mHandler.postDelayed(run_twinkle[eventIndex], 500);
        } else {
            mHandler.removeCallbacks(run_twinkle[eventIndex]);
            iv_Events[eventIndex].setAlpha(1f);
        }
    }

    /* 請求權限後的動作 */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        // If request is cancelled, the result arrays are empty.
        if (requestCode == MY_PRECISION_FINE_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startService(new Intent(this, MyService.class));
                bindService(new Intent(this, MyService.class), mConnection, BIND_AUTO_CREATE);
            } else {
                finish();
            }
        }
    }

    /* 請求設定後的動作 */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case CODE_DRAW_OVER_OTHER_APP_PERMISSION:
                mMyService.setVisibility("OVERLAY_SETTING", false);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                        Settings.canDrawOverlays(MainActivity.this)) {
                    openFloatingWindow();
                }

                break;
            case LOCATION_SETTING:
                mMyService.setVisibility("LOCATION_SETTING", false);

                break;
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();

        mMyService.setUiHandler(TAG, mHandler);
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (mMyService != null) {
            mMyService.setVisibility(TAG, false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mMyService != null) {
            unbindService(mConnection);
            mMyService.finishIsInvisible();
        }
    }
}