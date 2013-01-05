package com.androzic.data;

import android.graphics.Bitmap;

public class MapObject
{
	public long _id = 0;
	public double latitude = 0;
	public double longitude = 0;
	public int altitude = Integer.MIN_VALUE;
	public int proximity = 0;
	public Bitmap bitmap;

	public MapObject()
	{
	}

	public MapObject(double lat, double lon)
	{
		latitude = lat;
		longitude = lon;
	}
}
