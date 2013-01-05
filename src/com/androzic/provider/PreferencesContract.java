package com.androzic.provider;

import android.net.Uri;

public final class PreferencesContract
{
	public static final String AUTHORITY = "com.androzic.PreferencesProvider";
	protected static final String PATH = "preferences";
	public static final Uri PREFERENCES_URI;
	
	public static final String[] DATA_COLUMNS = new String[] {"VALUE"};
	public static final int DATA_COLUMN = 0;
	public static final String DATA_SELECTION = "IDLIST";
	
	/**
	 * double
	 */
	public static final int SPEED_FACTOR = 1;
	/**
	 * String
	 */
	public static final int SPEED_ABBREVIATION = 2;
	/**
	 * double
	 */
	public static final int DISTANCE_FACTOR = 3;
	/**
	 * String
	 */
	public static final int DISTANCE_ABBREVIATION = 4;
	/**
	 * double
	 */
	public static final int DISTANCE_SHORT_FACTOR = 5;
	/**
	 * String
	 */
	public static final int DISTANCE_SHORT_ABBREVIATION = 6;
	/**
	 * double
	 */
	public static final int ELEVATION_FACTOR = 7;
	/**
	 * String
	 */
	public static final int ELEVATION_ABBREVIATION = 8;
	
	static
	{
		PREFERENCES_URI = Uri.parse("content://" + AUTHORITY + "/" + PATH);
	}
}
