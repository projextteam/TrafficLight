package com.nuk.light.traffic;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;

public class SettingsFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    private SharedPreferences.OnSharedPreferenceChangeListener mSharedPrefChangeListener;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mSharedPrefChangeListener = ((SettingsActivity) context).getListener();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preferences);
        SharedPreferences sharedPref = getPreferenceScreen().getSharedPreferences();
        sharedPref.registerOnSharedPreferenceChangeListener(mSharedPrefChangeListener);
        sharedPref.registerOnSharedPreferenceChangeListener(this);

        String[] keys = new String[]{"remind_second", "remind_accuracy", "remind_frequency"};
        for (String key : keys) {
            ListPreference listPreference = (ListPreference) findPreference(key);
            listPreference.setSummary(listPreference.getEntry());
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Preference pref = findPreference(key);

        /* 讓當前 listPreference 設定可以顯示出來 */
        if (pref instanceof ListPreference) {
            pref.setSummary(((ListPreference) pref).getEntry());
        }
    }
}
