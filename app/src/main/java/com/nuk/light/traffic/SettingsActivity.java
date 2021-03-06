package com.nuk.light.traffic;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;

public class SettingsActivity extends Activity {
    /** 外部使用 */
    public SharedPreferences.OnSharedPreferenceChangeListener getListener() {
        return mMyService.getSharedPrefChangeListener();
    }

    /** Property */
    public static final String TAG = "Settings";

    private ServiceConnection mConnection;
    private MyService mMyService;


    /** Method */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                MyService.ServiceBinder binder = (MyService.ServiceBinder) service;
                mMyService = binder.getService();
                mMyService.setUiHandler(TAG, null);

                /* 顯示 Setting 選項 */
                getFragmentManager().beginTransaction()
                        .replace(android.R.id.content, new SettingsFragment())
                        .commit();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {

            }
        };

        bindService(new Intent(this, MyService.class), mConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onRestart() {
        super.onRestart();

        mMyService.setUiHandler(TAG, null);
    }

    @Override
    protected void onStop() {
        super.onStop();

        mMyService.setVisibility(TAG, false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        unbindService(mConnection);
        mMyService.finishIsInvisible();
    }
}