package com.nuk.light.traffic;

import android.app.DialogFragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.Arrays;
import java.util.List;

public class EventDialog extends DialogFragment {

    public static final String TAG = "EventDialog1";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this
        View v = inflater.inflate(R.layout.event_dialog, container, false);

        final List<Integer> buttonIds = Arrays.asList(R.id.ib_repair, R.id.ib_accident, R.id.ib_road_block, R.id.ib_drop, R.id.ib_police);

        View.OnClickListener onClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Bundle args = new Bundle();
                int category = 0;
                String type = "";
                switch (v.getId()) {
                    case R.id.ib_repair:
                        category = 1;
                        type = "道路施工";

                        break;
                    case R.id.ib_road_block:
                        category = 2;
                        type = "道路封鎖";
                        break;
                    case R.id.ib_accident:
                        category = 3;
                        type = "車禍現場";
                        break;
                    case R.id.ib_police:
                        category = 4;
                        type = "警察臨檢";
                        break;
                    case R.id.ib_drop:
                        category = 5;
                        type = "大型物掉落";
                        break;
                }

                args.putString("type", type);
                args.putInt("category", category);

                EventDialog2 eventDialog2 = new EventDialog2();
                eventDialog2.setArguments(args);

                getFragmentManager().beginTransaction()
                        .remove(EventDialog.this)
                        .add(R.id.container, eventDialog2, EventDialog2.TAG)
                        .addToBackStack(TAG)
                        .commit();
            }
        };

        for (int id : buttonIds) {
            v.findViewById(id).setOnClickListener(onClickListener);
        }

        return v;
    }
}