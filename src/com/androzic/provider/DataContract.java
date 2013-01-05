package com.androzic.provider;

import android.net.Uri;

public final class DataContract
{
	public static final String AUTHORITY = "com.androzic.DataProvider";
	protected static final String MAPOBJECTS_PATH = "mapobjects";
	public static final Uri MAPOBJECTS_URI;
	
	public static final String[] MAPOBJECT_COLUMNS = new String[] {"latitude", "longitude", "bitmap"};
	public static final int MAPOBJECT_LATITUDE_COLUMN = 0;
	public static final int MAPOBJECT_LONGITUDE_COLUMN = 1;
	public static final int MAPOBJECT_BITMAP_COLUMN = 2;
	public static final String MAPOBJECT_ID_SELECTION = "IDLIST";
	
	static
	{
		MAPOBJECTS_URI = Uri.parse("content://" + AUTHORITY + "/" + MAPOBJECTS_PATH);
	}
}
