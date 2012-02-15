package com.androzic.location;

public interface ILocationService
{
	void registerCallback(ILocationListener callback);
	void unregisterCallback(ILocationListener callback);
	boolean isLocating();
	float getHDOP();
	float getVDOP();
}
