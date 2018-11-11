package com.nuk.light.traffic;

import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class EventDialog2 extends Fragment {

    public static final String TAG = "EventDialog2";

    private ImageView vType;
    private String mType;
    private int mCategory;

    private EditText mEditText;

    private ReportActivity mReportActivity;

    private FragmentManager mFragmentManager;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        mReportActivity = (ReportActivity) context;
        mReportActivity.setUiVisibility(R.id.addwarning, View.GONE);

        Bundle mArgs = getArguments();
        if (getArguments() != null){
            mType = mArgs.getString("type");
            mCategory = mArgs.getInt("category");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this
        View v = inflater.inflate(R.layout.event_dialog2, container, false);

        TextView eventType = v.findViewById(R.id.event);
        eventType.setText(mType);

        vType = v.findViewById(R.id.eventicon);
        switch (mCategory){
            case 1:
            vType.setImageResource(R.drawable.event_repari1);
            break;
            case 2:
                vType.setImageResource(R.drawable.event_roadblock1);
                break;
            case 3:
                vType.setImageResource(R.drawable.event_acident1);
                break;
            case 4:
                vType.setImageResource(R.drawable.event_police1);
                break;
            case 5:
                vType.setImageResource(R.drawable.event_drop1);
                break;
            case 6:
                vType.setImageResource(R.drawable.event_others1);
                break;
            default:
                break;
        }

        mEditText = v.findViewById(R.id.editText3);

        View.OnClickListener onClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (v.getId()) {
                    case R.id.currentlocate:
                        mReportActivity.chooseCurrentLocation();

                        break;
                    case R.id.chooselocate:
                        mFragmentManager.beginTransaction()
                                .hide(EventDialog2.this)
                                .commit();

                        break;
                    case R.id.sendmeassage:
                        mReportActivity.setUiVisibility(R.id.addwarning, View.VISIBLE);
                        mFragmentManager.popBackStackImmediate(EventDialog.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE);
                        mFragmentManager.beginTransaction()
                                .remove(mFragmentManager.findFragmentByTag(EventDialog.TAG))
                                .remove(EventDialog2.this)
                                .commit();

                        mReportActivity.sendEvent(mCategory, mEditText.getText().toString());

                        break;
                }
            }
        };

        int[] buttonIds = new int[]{R.id.currentlocate, R.id.chooselocate, R.id.sendmeassage};
        for (int id : buttonIds) {
            v.findViewById(id).setOnClickListener(onClickListener);
        }

        mFragmentManager = getFragmentManager();

        return v;
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        mReportActivity.setMapClickable(hidden);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mReportActivity.setUiVisibility(R.id.addwarning, View.VISIBLE);
        mReportActivity.resetChosenMarker();
    }
}