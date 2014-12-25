package com.androzic.location;

import com.androzic.data.Track;

public interface ILocationService
{
	void registerLocationCallback(ILocationListener callback);
	void unregisterLocationCallback(ILocationListener callback);
	void registerTrackingCallback(ITrackingListener callback);
	void unregisterTrackingCallback(ITrackingListener callback);
	boolean isLocating();
	boolean isTracking();
	float getHDOP();
	float getVDOP();
	Track getTrack();
	Track getTrack(long start, long end);
	void clearTrack();
	long getTrackStartTime();
	long getTrackEndTime();
}
