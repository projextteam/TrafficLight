package com.nuk.light.traffic;

import android.database.Cursor;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Locale;

class TrafficLight {
    /** Property */
    private Cursor mPeriod;

    private int[] mLightSeconds;      // 每種燈號的秒數
    private String[] mStatus;         // 燈號的種類
    private int mCurrent;             // 現在燈號
    private int mCountDown;           // 現在燈號剩餘秒數
    private int mSecondInFeature;     // 此種週期剩餘時間

    int Id;                    // 紅綠燈編號


    /** Method */
    /* 直接使用資料庫取得的 mPeriod Cursor，來建構 TrafficLight，
     * 儲存燈號類型、秒數，並抓取現在時間計算現在的燈號情況。
     * */
    TrafficLight(int id, Cursor period, double delta) {
        Id = id;
        mPeriod = period;

        /* 取得現在時間 */
        String rightNow = new SimpleDateFormat("HH:mm:ss", Locale.TAIWAN).format(System.currentTimeMillis() + delta);
        int secondNow = parseSecond(rightNow);

        /* 計算現在應運行的週期 */
        while (mPeriod.moveToNext()) {
            int base_time = parseSecond(mPeriod.getString(mPeriod.getColumnIndex("Base_time")));
            if (base_time > secondNow) {
                mPeriod.move(-1);
                mSecondInFeature = base_time - secondNow;

                break;
            }

            if (mPeriod.isLast()) {
                mSecondInFeature = 86400 - secondNow;

                break;
            }
        }

        /* 設初始值 */
        initialize();

        if (mLightSeconds == null) {
            Log.d("TrafficLight", String.valueOf(id));
        }

        /* 計算此週期總時間 */
        int totalSecond = 0;
        for (int second : mLightSeconds) {
            totalSecond += second;
        }

        /* 再依目前時間來計算此週期過了多少時間 */
        int secondFromStart = (secondNow - parseSecond(mPeriod.getString(mPeriod.getColumnIndex("Base_time")))) % totalSecond;
        for (int i = 0, second = 0; i < mLightSeconds.length; second += mLightSeconds[i], i++) {
            if (secondFromStart - second < mLightSeconds[i]) {
                mCountDown = mLightSeconds[i] - secondFromStart + second;
                mCurrent = i;

                break;
            }
        }

        if (!mPeriod.moveToNext()) {
            mPeriod.moveToFirst();
        }
    }

    private void initialize() {
        /* 判斷此週期有什麼類型的燈 */
        switch (mPeriod.getString(mPeriod.getColumnIndex("First_light"))) {
            case "Red":
                mLightSeconds = new int[]{
                        parseSecond(mPeriod.getString(mPeriod.getColumnIndex("Red"))),
                        parseSecond(mPeriod.getString(mPeriod.getColumnIndex("Green"))),
                        parseSecond(mPeriod.getString(mPeriod.getColumnIndex("Yellow")))
                };

                mStatus = new String[]{"Red", "Green", "Yellow"};

                break;
            case "Green":
                mLightSeconds = new int[]{
                        parseSecond(mPeriod.getString(mPeriod.getColumnIndex("Green"))),
                        parseSecond(mPeriod.getString(mPeriod.getColumnIndex("Yellow"))),
                        parseSecond(mPeriod.getString(mPeriod.getColumnIndex("Red")))
                };
                mStatus = new String[]{"Green", "Yellow", "Red"};

                break;
            case "Flash_yellow":
                mLightSeconds = new int[]{parseSecond(mPeriod.getString(mPeriod.getColumnIndex("Flash_yellow")))};
                mStatus = new String[]{"Flash_yellow"};

                break;
        }
    }

    /* 將時間格式解析成秒數 */
    private int parseSecond(String time) {
        String[] split = time.split(":");

        return Integer.parseInt(split[0]) * 3600 +
                Integer.parseInt(split[1]) * 60 +
                Integer.parseInt(split[2]);
    }

    /* 進入下一秒的狀態 */
    boolean tick() {
        /* 若換週期的時間到了，重新初始 TrafficLight */
        if (--mSecondInFeature == 0) {
            initialize();

            mCurrent = 0;
            mCountDown = mLightSeconds[mCurrent];

            int base_time = parseSecond(mPeriod.getString(mPeriod.getColumnIndex("Base_time")));
            if (mPeriod.moveToNext()) {
                mSecondInFeature = parseSecond(mPeriod.getString(mPeriod.getColumnIndex("Base_time"))) - base_time;
            } else {
                mPeriod.moveToFirst();
                mSecondInFeature = 86400 - base_time;
            }

            return true;
        }
        /* 若此燈號歸零，跳至下一個燈號 */
        else if (--mCountDown == 0) {
            if (++mCurrent == mStatus.length) {
                mCurrent = 0;
            }

            mCountDown = mLightSeconds[mCurrent];

            return true;
        }

        return false;
    }

    /* 回傳當前燈號 */
    String getStatus() {
        return mStatus[mCurrent];
    }

    /* 回傳當前秒數 */
    int getCountDown() {
        return mCountDown;
    }

    /* 回傳此燈號最大秒數 */
    int getMaxLightSecond() {
        return mLightSeconds[mCurrent];
    }

    /* 回傳是否紅燈剩五秒需要提醒 */
    boolean isNeededReminding(String second) {
        return getStatus().equals("Green") && mCountDown == Integer.parseInt(second);
    }
}