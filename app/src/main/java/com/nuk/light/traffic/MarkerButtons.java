package com.nuk.light.traffic;

import android.app.DialogFragment;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Toast;

import com.google.android.gms.maps.model.LatLng;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MarkerButtons extends DialogFragment {

    public static final String TAG = "MarkerButtons";
    private ImageButton Delete;
    private ImageButton Extend;

    private String MarkerInfo;
    private String[] datas;
    private Double latitude,longitude;

    private ReportActivity mReportActivity;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this
        View v = inflater.inflate(R.layout.marker_buttons, container, false);

        Delete = (ImageButton) v.findViewById(R.id.delete);
        Delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast toast = Toast.makeText(getActivity(), "Delete", Toast.LENGTH_LONG);
                toast.show();

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        String category = "";
                        switch (datas[0]) {
                            case ("道路施工"):
                                category = "1";
                                break;
                            case ("道路封鎖"):
                                category = "2";
                                break;
                            case ("車禍現場"):
                                category = "3";
                                break;
                            case ("警察臨檢"):
                                category = "4";
                                break;
                            case ("大型物掉落"):
                                category = "5";
                                break;
                            case ("其他事件"):
                                category = "6";
                                break;
                        }
                        NetUtils.post("traffic_light/delete_event.php",
                                "category=" + "'" + category + "'" +
                                        "&latitude=" + "'" + Double.toString(latitude) + "'" +
                                        "&longitude=" + "'" + Double.toString(longitude) + "'");
                    }
                }).start();

                mReportActivity.deleteEvent(new LatLng(latitude, longitude));

                dismiss();
            }
        });

        Extend = (ImageButton) v.findViewById(R.id.extend);
        Extend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast toast = Toast.makeText(getActivity(), "Extend", Toast.LENGTH_LONG);
                toast.show();

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        String category = "";
                        switch(datas[0])
                        {
                            case("道路施工"):
                                category ="1";
                                break;
                            case("道路封鎖"):
                                category ="2";
                                break;
                            case("車禍現場"):
                                category ="3";
                                break;
                            case("警察臨檢"):
                                category ="4";
                                break;
                            case("大型物掉落"):
                                category ="5";
                                break;
                            case("其他事件"):
                                category ="6";
                                break;
                        }
                        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.TAIWAN);
                        String delay = "";
                        try {
                            Date date =simpleDateFormat.parse(datas[2]);
                            date.setTime(date.getTime() + 7200000);

                            delay = simpleDateFormat.format(date);

                        } catch (ParseException e) {
                            e.printStackTrace();
                        }

                        NetUtils.post("traffic_light/delay_event.php",
                                "category=" + "'" + category + "'" +
                                        "&latitude=" + "'" + Double.toString(latitude) + "'" +
                                        "&longitude=" + "'" + Double.toString(longitude) + "'" +
                                        "&endtime=" + "'" + delay + "'");
                    }
                }).start();

                mReportActivity.extendEvent(new LatLng(latitude, longitude));

                dismiss();
            }
        });

        return v;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        mReportActivity = (ReportActivity) context;

        Bundle mArgs = getArguments();
        if (getArguments() != null){
            MarkerInfo = mArgs.getString("MarkerInfo");
            datas = MarkerInfo.split(",");
            latitude = mArgs.getDouble("lat");
            longitude = mArgs.getDouble("lng");

        }
    }
}