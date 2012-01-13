package com.androzic.track;

oneway interface ITrackingCallback
{
    void onNewPoint(boolean continous, double lat, double lon, double elev, double speed, double track, long time);
}
