package com.nuk.light.traffic;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TrafficLightsActivity extends FragmentActivity implements OnMapReadyCallback {
    /** Property */
    public static final String TAG = "TrafficLights";

    /* 流程控制元件 */
    private ServiceConnection mConnection;
    private MyService mMyService;

    private Handler mHandler;
    private Runnable run_countDown;

    /* Google Map 元件 */
    private GoogleMap mMap;

    /* TrafficLights */
    private List<Light> mLights;
    private Map<String, Bitmap[]> mBitmaps;


    /** Method */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_traffic_lights);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        initialize();
    }

    private void initialize() {
        mConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                MyService.ServiceBinder binder = (MyService.ServiceBinder) service;
                mMyService = binder.getService();
                mMyService.setUiHandler(TAG, null);

                // move center
                LatLng center = (mMyService.getCurrentLocation() == null) ?
                        new LatLng(22.730038, 120.284649) :
                        new LatLng(mMyService.getCurrentLocation().getLatitude(), mMyService.getCurrentLocation().getLongitude());

                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(center, 16));
                mMap.getUiSettings().setAllGesturesEnabled(true);

                // add Lights Markers
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        /* 預先載入每一張可能出現的圖(最大秒數)，R、G: 78 張 Y: 4 張 */
                        mBitmaps.put("Red", new Bitmap[78]);
                        mBitmaps.put("Green", new Bitmap[78]);
                        for (int i = 0; i < 78; i++) {
                            mBitmaps.get("Red")[i] = getLightBitmap(i + 1, "Red");
                            mBitmaps.get("Green")[i] = getLightBitmap(i + 1, "Green");
                        }

                        mBitmaps.put("Yellow", new Bitmap[4]);
                        for (int i = 0; i < 4; i++) {
                            mBitmaps.get("Yellow")[i] = getLightBitmap(i + 1, "Yellow");
                        }

                        /* 取得所有紅綠燈資料 */
                        Cursor lights = mMyService.getAllLights();
                        while (lights.moveToNext()) {
                            Cursor period = mMyService.getPeriod(lights.getInt(lights.getColumnIndex("Id")));
                            if (period.getCount() == 0) {
                                continue;
                            }

                            mLights.add(new Light(
                                    lights.getInt(lights.getColumnIndex("Id")),
                                    period,
                                    lights.getDouble(lights.getColumnIndex("Latitude")),
                                    lights.getDouble(lights.getColumnIndex("Longitude"))
                            ));
                        }

                        /* 開始倒數 */
                        // TODO: 沒有跟主畫面同步跳
                        mHandler.post(run_countDown);
                    }
                }).start();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {}
        };

        mHandler = new Handler();
        run_countDown = new Runnable() {
            @Override
            public void run() {
                for (Light light : mLights) {
                    light.tick();
                }

                mHandler.postDelayed(this, 997);
            }
        };

        mLights = new ArrayList<>();
        mBitmaps = new HashMap<>();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        mMap.getUiSettings().setMyLocationButtonEnabled(true);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        mMap.setMyLocationEnabled(true);

        bindService(new Intent(this, MyService.class), mConnection, BIND_AUTO_CREATE);
    }

    private Bitmap getLightBitmap(int countDown, String status) {
        // Drawable 原圖
        int resourceId;
        switch (status) {
            case "Green":
                resourceId = R.drawable.light_green;
                break;
            case "Red":
                resourceId = R.drawable.light_red;
                break;
            case "Yellow":
                resourceId = R.drawable.light_yellow;
                break;
            default:
                resourceId = R.drawable.light_yellow;
                break;
        }

        BitmapDrawable icon = (BitmapDrawable) getResources().getDrawable(resourceId);
        Bitmap bitmap = icon.getBitmap().copy(Bitmap.Config.ARGB_8888, true);

        // 調整為 80 * 80 大小
        bitmap = Bitmap.createScaledBitmap(bitmap, 80, 80, true);

        // 寫上數字
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize((int) (15 * getResources().getDisplayMetrics().density));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(Color.WHITE);
        paint.setShadowLayer(1f, 0f, 1f, Color.BLACK);

        int x = canvas.getWidth() / 2;
        int y = (int) ((canvas.getHeight() / 2) - ((paint.descent() + paint.ascent()) / 2));
        canvas.drawText(String.valueOf(countDown), x, y, paint);

        return bitmap;
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if (mMyService != null) {
            mMyService.setUiHandler(TAG, null);
        }
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

    /* 封裝原本 TrafficLight 與 Google Map Marker，並實作 tick()，
     * 每秒使用預先載入的圖來切換 Marker icon
     * */
    private class Light {
        /** Property */
        private TrafficLight mTrafficLight;
        private Marker mMarker;


        /** Method */
        Light(int id, Cursor period, final double lat, final double lng) {
            mTrafficLight = new TrafficLight(id, period, mMyService.getDelta());

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mMarker = mMap.addMarker(new MarkerOptions()
                            .position(new LatLng(lat, lng))
                            .flat(true)
                            .anchor(0.5f, 0.5f)
                            .icon(BitmapDescriptorFactory.fromBitmap(getBitmap()))
                    );
                }
            });
        }

        /* 取得先前載入的圖 */
        private Bitmap getBitmap() {
            return mBitmaps.get(mTrafficLight.getStatus())[mTrafficLight.getCountDown() - 1];
        }

        /* 倒數一秒 */
        void tick() {
            mTrafficLight.tick();
            mMarker.setIcon(BitmapDescriptorFactory.fromBitmap(getBitmap()));
        }
    }
}
