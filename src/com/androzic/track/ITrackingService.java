package com.androzic.track;

public interface ITrackingService
{
	void registerCallback(ITrackingListener callback);
	void unregisterCallback(ITrackingListener callback);
	boolean isTracking();
}
