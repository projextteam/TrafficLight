package com.nuk.light.traffic;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.constraint.ConstraintLayout;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class FloatingWindow extends Service {
    /** Property */
    public static final String TAG = "FloatingWindow";

    /* UI元件 */
    private TextView tv_Distance;              // 距離，目前隨燈號顏色變化
    private TextView tv_CountDown;             // 剩餘秒數

    private ImageButton iv_Traffic;
    private ProgressBar mProgressBar;
    private ImageButton iv_play ;
    private ImageButton iv_close;
    private ImageButton iv_home ;

    /* 視窗控制元件 */
    private View mFloatingView;
    private WindowManager mWindowManager;
    private WindowManager.LayoutParams params;

    /* 程序控制元件 */
    private Handler mHandler;
    private MyService mMyService;
    private ServiceConnection mConnection;


    /** Method */
    /* onBind */
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /* onCreate */
    @Override
    public void onCreate() {
        super.onCreate();


        /* 初始化所有元件 */
        initialize();
        bindService(new Intent(this, MyService.class), mConnection, BIND_AUTO_CREATE);
    }

    /* 初始化所有元件 */
    private void initialize() {
        /* 初始視窗控制元件 */
        mFloatingView = LayoutInflater.from(this).inflate(R.layout.floatingwidget,null);

        // TextView
        tv_CountDown = mFloatingView.findViewById(R.id.CountDown);

        // ImageView
        iv_play = mFloatingView.findViewById(R.id.report);
        iv_play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                /* 進入 Report 頁面 */
                startActivity(new Intent(FloatingWindow.this, ReportActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                stopSelf();
            }
        });
        iv_close = mFloatingView.findViewById(R.id.close);
        iv_close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                /* 離開並關閉程式 */
                        mHandler.removeCallbacksAndMessages(null);
                        mMyService.setVisibility(TAG, false);
                        mMyService.finishIsInvisible();
                        stopSelf();
            }
        });
        iv_home = mFloatingView.findViewById(R.id.home);
        iv_home.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                /* 返回主頁面 */
                startActivity(new Intent(FloatingWindow.this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                stopSelf();
            }
        });
        iv_Traffic = mFloatingView.findViewById(R.id.Countdow);

        // ProgressBar
        mProgressBar = mFloatingView.findViewById(R.id.progressBar2);

        // 設定起始位置參數，起始位置(0, 0)
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 0;

        // 使用 WindowManager，來設定 FloatingView
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // 偵測使用者觸控及移動的動作，讓 FloatingView 跟著移動位置
        mFloatingView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // remember the initial position.
                        initialX = params.x;
                        initialY = params.y;

                        // get the touch position
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();

                        return true;
                    case MotionEvent.ACTION_MOVE:
                        // Calculate the X and Y coordinates of the view.
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);

                        // Update the layout with new X & Y coordinate
                        mWindowManager.updateViewLayout(mFloatingView, params);

                        return true;
                    case MotionEvent.ACTION_UP:
                        int xDiff = (int) (event.getRawX() - initialTouchX);
                        int yDiff = (int) (event.getRawY() - initialTouchY);

                        if (xDiff < 5 && yDiff < 5) {
                            v.performClick();
                        }

                        return true;
                }

                return false;
            }
        });

        mFloatingView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (iv_close.getVisibility() != View.VISIBLE) {
                    iv_close.setVisibility(View.VISIBLE);
                    iv_home.setVisibility(View.VISIBLE);
                    iv_play.setVisibility(View.VISIBLE);
                }
                else {
                    iv_close.setVisibility(View.GONE);
                    iv_home.setVisibility(View.GONE);
                    iv_play.setVisibility(View.GONE);
                }
            }
        });




        /* 初始程序控制元件 */
        mHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message message) {
                switch (message.what) {
                    case Action.SET_TRAFFIC_LIGHT:
                        mProgressBar.setProgress(mMyService.getCountDown() - 1);
                        String countDown = "";

                        switch (mMyService.getStatus()) {
                            case "Green":
                                iv_Traffic.setImageResource(R.drawable.light_green1);
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                    mProgressBar.setProgressTintList(ColorStateList.valueOf(Color.GREEN));
                                }

                                break;
                            case "Red":
                                iv_Traffic.setImageResource(R.drawable.light_red1);
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                    mProgressBar.setProgressTintList(ColorStateList.valueOf(Color.RED));
                                }
                                countDown = String.valueOf(mMyService.getCountDown());

                                break;
                            case "Yellow":
                                iv_Traffic.setImageResource(R.drawable.light_yellow1);
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                    mProgressBar.setProgressTintList(ColorStateList.valueOf(Color.YELLOW));
                                }

                                break;
                            case "Flash_yellow":
                                iv_Traffic.setImageResource(R.drawable.light_yellow1);

                                break;
                            default:
                                iv_Traffic.setImageResource(R.drawable.light_red1);

                                break;
                        }
                        tv_CountDown.setText(countDown);

                        break;
                    case Action.SET_MAX_PROGRESS:
                        mProgressBar.setMax(mMyService.getMaxLightSecond() - 1);

                        break;
                    case Action.SET_NO_GPS:
                        tv_CountDown.setText("");
                        iv_Traffic.setImageResource(R.drawable.light_red1);
                        mProgressBar.setProgress(0);

                        break;
                    case Action.SET_NO_TRAFFIC_LIGHT:
                        tv_CountDown.setText("");
                        iv_Traffic.setImageResource(R.drawable.light_red1);
                        mProgressBar.setProgress(0);

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

                if (mWindowManager != null) {
                    mWindowManager.addView(mFloatingView, params);
                }
            }

            // Called when the connection with the service disconnects unexpectedly
            public void onServiceDisconnected(ComponentName className) {

            }
        };
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mMyService.setVisibility(TAG, false);

        if (mFloatingView != null) {
            mWindowManager.removeView(mFloatingView);
        }

        unbindService(mConnection);
    }
}