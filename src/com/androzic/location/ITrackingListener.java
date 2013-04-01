package com.androzic.location;

public interface ITrackingListener
{
    void onNewPoint(boolean continous, double lat, double lon, double elev, double speed, double track, double accuracy, long time);
}
