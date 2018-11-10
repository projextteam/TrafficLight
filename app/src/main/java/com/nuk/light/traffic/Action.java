package com.nuk.light.traffic;


class Action {                 // handler 執行的動作種類
    static final int SET_TRAFFIC_LIGHT = 0;
    static final int SET_STREET = 1;
    static final int SET_SPEED = 2;
    static final int SET_NO_NODE = 5;
    static final int SET_NO_GPS = 4;
    static final int SET_NO_TRAFFIC_LIGHT = 6;
    static final int WAITING_GPS = 7;
    static final int SET_MAX_PROGRESS = 8;

    static final int GET_DATA_FAIL_DIALOG = 15;
    static final int GET_EVENT_FAIL_DIALOG = 16;

    static final int UPDATE_NEAREST_EVENT = 17;
    static final int UPDATE_REPORT_MARKER = 18;

    static final int HAVE_EMERGENCY = 20;
    static final int FINISH_EMERGENCY = 21;
}