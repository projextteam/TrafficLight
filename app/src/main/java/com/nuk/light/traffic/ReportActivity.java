package com.nuk.light.traffic;

import android.app.FragmentManager;
import android.app.PictureInPictureParams;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Rational;
import android.view.View;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.Projection;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReportActivity extends FragmentActivity implements OnMapReadyCallback, GoogleMap.OnInfoWindowClickListener {
    /**
     * 外部使用
     */
    /* EventDialog2 會被 add_warning 擋到 */
    public void setUiVisibility(int id, int visibility) {
        findViewById(id).setVisibility(visibility);
    }

    /* 切換 onClickListener */
    public void setMapClickable(boolean clickable) {
        mMap.setOnMapClickListener(clickable ? mMapClickListener : null);
    }

    /* 重設 ChosenMarker */
    public void resetChosenMarker() {
        if (mChosenMarker != null) {
            mChosenMarker.setVisible(false);
            mChosenMarker = null;
        }
    }

    /* 選擇自己位置 */
    public void chooseCurrentLocation() {
        mChosenLatLng = new LatLng(mMyService.getCurrentLocation().getLatitude(), mMyService.getCurrentLocation().getLongitude());
        resetChosenMarker();
        zoomCurrentLocation();
    }

    /* 發送事件回 Server */
    public void sendEvent(final int category, final String content) {
        /* 還未選擇任何位置 */
        if (mChosenLatLng == null) {
            Toast.makeText(this, "未選擇位置", Toast.LENGTH_LONG).show();
            return;
        }

        final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.TAIWAN);
        final Date date = new Date();
        final DecimalFormat format = new DecimalFormat("###.########");
        final String starttime = simpleDateFormat.format(date);
        date.setTime(date.getTime() + 7200000);
        final String endtime = simpleDateFormat.format(date);

        new Thread(new Runnable() {
            @Override
            public void run() {
                NetUtils.post("traffic_light/insert_event.php" ,
                        "category=" +"'"+ Integer.toString(category)+"'" +
                                "&latitude="+"'"+ format.format(mChosenLatLng.latitude)+"'" +
                                "&longitude="+"'" + format.format(mChosenLatLng.longitude)+"'" +
                                "&ip="+"'" + Utils.getIPAddress(true) +"'" +
                                "&starttime="+"'"+ starttime+"'" +
                                "&endtime="+"'" + endtime+"'" +
                                "&status="+"'" + "1"+"'" +
                                "&content="+"'" + content+"'");
            }
        }).start();

        //在地圖上即時加入剛新增的事件Marker
        //手機資料庫尚未更新
        String name ="";
        int show_icon =R.drawable.event_others1;
        switch (category) {
            case 1:
                name = "道路施工";
                show_icon = R.drawable.event_repari1;
                break;
            case 2:
                name = "道路封鎖";
                show_icon = R.drawable.event_roadblock1;
                break;
            case 3:
                name = "車禍現場";
                show_icon = R.drawable.event_acident1;
                break;
            case 4:
                name = "警察臨檢";
                show_icon = R.drawable.event_police1;
                break;
            case 5:
                name = "大型物掉落";
                show_icon = R.drawable.event_drop1;
                break;
            case 6:
                name = "其他事件";
                show_icon = R.drawable.event_others1;
                break;
        }
        mEventMarkers.add(mMap.addMarker(new MarkerOptions()
                .position(new LatLng(mChosenLatLng.latitude,mChosenLatLng.longitude))
                .snippet(name+","
                        +starttime+","
                        +endtime+","
                        +content)
                .icon(BitmapDescriptorFactory.fromResource(show_icon))
        ));

        resetChosenMarker();
    }

    /* 刪除事件 */
    public void deleteEvent(LatLng eventLatLng) {
        for (Marker marker : mEventMarkers) {
            if (marker.getPosition().equals(eventLatLng)) {
                marker.remove();
            }
        }
    }

    /* 延時事件 */
    public void extendEvent(LatLng eventLatLng) {
        for (Marker marker : mEventMarkers) {
            if (marker.getPosition().equals(eventLatLng)) {
                marker.hideInfoWindow();
            }
        }
    }


    /**
     * Property
     */
    public static final String TAG = "Report";

    /* Ui 元件 */
    private TextView tv_StreetName;
    private ImageButton add_warning;

    /* Google 元件 */
    private GoogleMap mMap;

    private Geocoder mGeocoder;
    private Marker mCurrentMarker;
    private Marker mEmergency;
    private Marker mChosenMarker;
    private List<Marker> mEventMarkers;
    private GoogleMap.InfoWindowAdapter mInfoWindowAdapter;
    private GoogleMap.OnMapClickListener mMapClickListener;
    private LatLng mChosenLatLng;

    /* 程序控制元件 */
    private Handler mHandler;               // 用此 Handler 處理 MyService 送過來的動作
    private MyService mMyService;            // 用此 MyService instance 來操作 MyService
    private ServiceConnection mConnection;

    private ExecutorService mExecutor;

    private SharedPreferences mSharedPref;

    /* PictureInPicture */
    private boolean mInPipMode;
    private PictureInPictureParams mPictureInPictureParams;

    /**
      Method
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.reportmap);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        mInPipMode = getIntent().getBooleanExtra("PipMode", false);

        /* 初始化所有元件 */
        initialize();
    }

    /* 初始化所有元件 */
    private void initialize() {
        tv_StreetName = findViewById(R.id.rep_street_name);
        tv_StreetName.setVisibility(View.GONE);
        tv_StreetName.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mSharedPref.getBoolean("pip_cut", false)) {
                    mInPipMode = true;
                    minimize();
                }
            }
        });

        // Button
        add_warning = findViewById(R.id.addwarning);
        add_warning.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EventDialog eDialog = new EventDialog();
                eDialog.show(getFragmentManager(), EventDialog.TAG);
            }
        });

        // Google 元件
        mGeocoder = new Geocoder(this, Locale.TAIWAN);

        // 事件 Marker list
        mEventMarkers = new Vector<>();

        // Marker 自訂資訊頁面
        mInfoWindowAdapter = new GoogleMap.InfoWindowAdapter() {
            @Override
            public View getInfoWindow(Marker marker) {
                // 自訂視窗樣式, 如果要採用預設則回傳 null, 會去執行 getInfoContents
                return null;
            }

            @Override
            public View getInfoContents(final Marker marker) {
                Log.d(TAG, "getInfoContents");
                // 自訂顯示內容
                if (marker.getSnippet() != null) {
                    Log.d(TAG, "not null");
                    View view = getLayoutInflater().inflate(R.layout.marker_info, null);

                    final String[] datas = marker.getSnippet().split(",");

                    if (datas.length >= 3) {
                        TextView M_eventtype = view.findViewById(R.id.eventype);
                        M_eventtype.setText("事件類型：" + datas[0]);
                        TextView M_starttime = view.findViewById(R.id.starttime);
                        M_starttime.setText("發生時間：" + datas[1]);
                        TextView M_endtime = view.findViewById(R.id.endtime);
                        M_endtime.setText("結束時間：" + datas[2] + " (預計)");
                        if (datas.length > 3) {
                            TextView M_description = (TextView) view.findViewById(R.id.description);
                            M_description.setText("補充資訊：" + datas[3]);
                        }
                    }
                    return view;
                }

                return null;
            }
        };

        // MapClickListener
        mMapClickListener = new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                mChosenLatLng = latLng;
                if (mChosenMarker == null) {
                    mChosenMarker = mMap.addMarker(new MarkerOptions().position(latLng));
                } else {
                    mChosenMarker.setPosition(latLng);
                }

                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16));

                FragmentManager fm = getFragmentManager();
                fm.beginTransaction()
                        .show(fm.findFragmentByTag("EventDialog2"))
                        .commit();

                //Toast正常運行
//                Toast toast = Toast.makeText(ReportActivity.this, latLng.toString(), Toast.LENGTH_LONG);
//                toast.show();
            }
        };

        /* 程序控制元件 */
        // 線程池
        mExecutor = Executors.newFixedThreadPool(5);

        // Handler
        mHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                switch (msg.what) {
                    case Action.HAVE_EMERGENCY:
                        if (tv_StreetName.getVisibility() == View.GONE) {
                            tv_StreetName.setVisibility(View.VISIBLE);
                        }

                        setStreetName((LatLng) msg.obj);

                        if (mEmergency == null) {
                            mEmergency = mMap.addMarker(new MarkerOptions()
                                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.siren_16))
                                    .position((LatLng) msg.obj));
                            if (mInPipMode) {
                                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(mEmergency.getPosition(), 16));
                            }
                        } else {
                            animateMarker(mInPipMode, mEmergency, (LatLng) msg.obj);
                        }

                        break;
                    case Action.FINISH_EMERGENCY:
                        if (mInPipMode) {
                            finish();
                        } else {
                            tv_StreetName.setVisibility(View.GONE);
                            /* 某種情況 mEmergency == null */
                            if (mEmergency != null) {
                                mEmergency.setVisible(false);
                                mEmergency = null;
                            }
                        }

                        break;

                    case Action.UPDATE_REPORT_MARKER:
                        //清除 事件Marker
                        for (Marker eventMarker : mEventMarkers) {
                            eventMarker.remove();
                        }

                        //新增 事件Marker
                        setting_marker();

                        break;
                    case Action.UPDATE_CURRENT_MARKER:
                        setCurrentMarker();

                        break;
                }

                return true;
            }
        });

        // Bind Connection
        mConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                MyService.ServiceBinder binder = (MyService.ServiceBinder) service;
                mMyService = binder.getService();
                if (!mInPipMode) {
                    mMyService.setUiHandler(TAG, mHandler);
                }
                mMyService.setReportUiHandler(mHandler);

                //設定所有marker
                setting_marker();

                // 設定自己的位置
                zoomCurrentLocation();
                setCurrentMarker();

                /* 判斷是否進入 Picture-in-picture 模式 */
                if (mInPipMode) {
                    minimize();
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {

            }
        };

        mSharedPref = PreferenceManager.getDefaultSharedPreferences(this);

        /* PictureInPicture 參數 */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mPictureInPictureParams = new PictureInPictureParams.Builder()
                    .setAspectRatio(new Rational(2, 3))     // Pip 畫面寬高比
                    .build();
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setOnInfoWindowClickListener(this);
        bindService(new Intent(this, MyService.class), mConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mMyService != null && add_warning.getVisibility() == View.INVISIBLE) {
            mInPipMode = false;
            mMyService.setUiHandler(TAG, mHandler);
            add_warning.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();

        zoomCurrentLocation();
        mMyService.setUiHandler(TAG, mHandler);
    }

    /* Pip 往下移除只會進入 onStop，不會進入 onDestroy */
    @Override
    protected void onStop() {
        super.onStop();

        mMyService.setVisibility(TAG, false);
    }

    @Override
    public void onBackPressed() {
        if (!mInPipMode && getFragmentManager().findFragmentByTag(EventDialog2.TAG) == null) {
            startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
        }

        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        unbindService(mConnection);
        mHandler.removeCallbacksAndMessages(null);
        mMyService.finishIsInvisible();
    }

    /* 視角回到自己的位置 */
    private void zoomCurrentLocation() {
        LatLng center;
        if (mMyService.getCurrentLocation() != null) {
            center = new LatLng(
                    mMyService.getCurrentLocation().getLatitude(),
                    mMyService.getCurrentLocation().getLongitude()
            );
        } else if (!mEventMarkers.isEmpty()) {
            center = mEventMarkers.get(0).getPosition();
        } else {
            center = new LatLng(22.730038, 120.284649);
        }

        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(center, 16));
    }

    private void setCurrentMarker() {
        if (mMyService.getCurrentLocation() == null) {
            return;
        }

        if (mCurrentMarker == null) {
            mCurrentMarker = mMap.addMarker(new MarkerOptions()
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.direction))
                    .position(new LatLng(
                            mMyService.getCurrentLocation().getLatitude(),
                            mMyService.getCurrentLocation().getLongitude()
                    ))
                    .flat(true));
        } else {
            animateMarker(false, mCurrentMarker, new LatLng(
                    mMyService.getCurrentLocation().getLatitude(),
                    mMyService.getCurrentLocation().getLongitude()
            ));
        }
    }

    /**
     * Users cannot interact with UI elements when in PIP mode and
     * the details of small UI elements may be difficult to see.
     * */
    /* 進入 PictureInPicture 模式 */
    private void minimize() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            add_warning.setVisibility(View.INVISIBLE);
            enterPictureInPictureMode(mPictureInPictureParams);
        }
    }

    /* 動畫移動 Marker 位置 */
    private void animateMarker(final boolean isCenter, final Marker marker, final LatLng toPosition) {
        final long start = SystemClock.uptimeMillis();
        Projection projection = mMap.getProjection();
        android.graphics.Point startPoint = projection.toScreenLocation(marker.getPosition());
        final LatLng startLatLng = projection.fromScreenLocation(startPoint);
        final Interpolator interpolator = new LinearInterpolator();
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                long elapsed = SystemClock.uptimeMillis() - start;
                float t = interpolator.getInterpolation((float) elapsed / 1000);
                double lng = t * toPosition.longitude + (1 - t)
                        * startLatLng.longitude;
                double lat = t * toPosition.latitude + (1 - t)
                        * startLatLng.latitude;
                marker.setPosition(new LatLng(lat, lng));
                if (isCenter) {
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(marker.getPosition(), 16));
                }
                if (t < 1.0) {
                    // Post again 16ms later.
                    mHandler.postDelayed(this, 16);
                }
            }
        });
    }

    /* 更新 Emergency 所在街道名稱 */
    private void setStreetName(final LatLng latLng) {
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                List<Address> addresses;
                final String name;
                try {
                    if ((addresses = mGeocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)).size() > 0 &&
                            (name = addresses.get(0).getThoroughfare()) != null) {
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                tv_StreetName.setText(name);
                            }
                        });
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void setting_marker() {
        Cursor event = mMyService.getAllEvent();

        // event 的欄位按照DB順序排列
        while (event.moveToNext()) {
            String category = "";
            int icon = R.drawable.event_others1;
            switch (event.getInt(event.getColumnIndex("category"))) {
                case 1:
                    category = "道路施工";
                    icon = R.drawable.event_repari1;
                    break;
                case 2:
                    category = "道路封鎖";
                    icon = R.drawable.event_roadblock1;
                    break;
                case 3:
                    category = "車禍現場";
                    icon = R.drawable.event_acident1;
                    break;
                case 4:
                    category = "警察臨檢";
                    icon = R.drawable.event_police1;
                    break;
                case 5:
                    category = "大型物掉落";
                    icon = R.drawable.event_drop1;
                    break;
                case 6:
                    category = "其他事件";
                    icon = R.drawable.event_others1;
                    break;
            }

            BitmapDrawable bitmapped = (BitmapDrawable)getResources().getDrawable(icon);
            Bitmap b = bitmapped.getBitmap();
            Bitmap bitmap = Bitmap.createScaledBitmap(b, 80, 80, false);

            mEventMarkers.add(mMap.addMarker(new MarkerOptions()
                    .position(new LatLng(event.getDouble(1), event.getDouble(2)))
                    .snippet(category + ","
                            + event.getString(4) + ","
                            + event.getString(5) + ","
                            + event.getString(7))
                    .icon(BitmapDescriptorFactory.fromBitmap(bitmap))
            ));
        }
        mMap.setInfoWindowAdapter(mInfoWindowAdapter);
    }

    @Override
    public void onInfoWindowClick(Marker marker) {
        Bundle args = new Bundle();
        args.putString("MarkerInfo", marker.getSnippet());
        args.putDouble("lat",marker.getPosition().latitude);
        args.putDouble("lng",marker.getPosition().longitude);
        MarkerButtons bFragment = new MarkerButtons();
        bFragment.setArguments(args);
        bFragment.show(getFragmentManager(),MarkerButtons.TAG);
    }
}
