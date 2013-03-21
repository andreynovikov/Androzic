package com.androzic.location;

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
}
