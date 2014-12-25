/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2012  Andrey Novikov <http://andreynovikov.info/>
 *
 * This file is part of Androzic application.
 *
 * Androzic is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * Androzic is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with Androzic.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.androzic.provider;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.UriMatcher;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;

import com.androzic.R;

public class PreferencesProvider extends ContentProvider
{
	private static final String TAG = "PreferenceProvider";

	private static final int OBJECTS = 1;
	private static final int OBJECTS_ID = 2;
	private static final UriMatcher uriMatcher;

	static
	{
		uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
		uriMatcher.addURI(PreferencesContract.AUTHORITY, PreferencesContract.PATH, OBJECTS);
		uriMatcher.addURI(PreferencesContract.AUTHORITY, PreferencesContract.PATH + "/#", OBJECTS_ID);
	}

	@Override
	public boolean onCreate()
	{
		return true;
	}

	@Override
	public String getType(Uri uri)
	{
		switch (uriMatcher.match(uri))
		{
			case OBJECTS:
				return "vnd.android.cursor.dir/vnd.com.androzic.provider.preference";
			case OBJECTS_ID:
				return "vnd.android.cursor.item/vnd.com.androzic.provider.preference";
			default:
				throw new IllegalArgumentException("Unknown URI " + uri);
		}
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder)
	{
		Log.d(TAG, "Query: " + uri);
		int[] ids = null;
		if (uriMatcher.match(uri) == OBJECTS)
		{
			if (! PreferencesContract.DATA_SELECTION.equals(selection))
				throw new IllegalArgumentException("Quering multiple items is not supported");
			ids = new int[selectionArgs.length];
			for (int i = 0; i < ids.length; i++)
				ids[i] = Integer.parseInt(selectionArgs[i], 10);
		}
		if (uriMatcher.match(uri) == OBJECTS_ID)
		{
			ids = new int[]{(int) ContentUris.parseId(uri)};
		}
		if (ids == null)
			throw new IllegalArgumentException("Unknown URI: " + uri);

		Context context = getContext();
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
		Resources resources = context.getResources();
		MatrixCursor cursor = new MatrixCursor(projection);
		
		for (int id : ids)
		{
			MatrixCursor.RowBuilder row = cursor.newRow();
	
			switch (id)
			{
				case PreferencesContract.SPEED_FACTOR:
				{
					int speedIdx = Integer.parseInt(settings.getString(context.getString(R.string.pref_unitspeed), "0"));
					double speedFactor = Double.parseDouble(resources.getStringArray(R.array.speed_factors)[speedIdx]);
					row.add(speedFactor);				
					break;
				}
				case PreferencesContract.SPEED_ABBREVIATION:
				{
					int speedIdx = Integer.parseInt(settings.getString(context.getString(R.string.pref_unitspeed), "0"));
					String speedAbbr = resources.getStringArray(R.array.speed_abbrs)[speedIdx];
					row.add(speedAbbr);
					break;
				}
				case PreferencesContract.DISTANCE_FACTOR:
				{
					int distanceIdx = Integer.parseInt(settings.getString(context.getString(R.string.pref_unitdistance), "0"));
					double distanceFactor = Double.parseDouble(resources.getStringArray(R.array.distance_factors)[distanceIdx]);
					row.add(distanceFactor);
					break;
				}
				case PreferencesContract.DISTANCE_ABBREVIATION:
				{
					int distanceIdx = Integer.parseInt(settings.getString(context.getString(R.string.pref_unitdistance), "0"));
					String distanceAbbr = resources.getStringArray(R.array.distance_abbrs)[distanceIdx];
					row.add(distanceAbbr);
					break;
				}
				case PreferencesContract.DISTANCE_SHORT_FACTOR:
				{
					int distanceIdx = Integer.parseInt(settings.getString(context.getString(R.string.pref_unitdistance), "0"));
					double distanceShortFactor = Double.parseDouble(resources.getStringArray(R.array.distance_factors_short)[distanceIdx]);
					row.add(distanceShortFactor);
					break;
				}
				case PreferencesContract.DISTANCE_SHORT_ABBREVIATION:
				{
					int distanceIdx = Integer.parseInt(settings.getString(context.getString(R.string.pref_unitdistance), "0"));
					String distanceShortAbbr = resources.getStringArray(R.array.distance_abbrs_short)[distanceIdx];
					row.add(distanceShortAbbr);
					break;
				}
				case PreferencesContract.ELEVATION_FACTOR:
				{
					int elevationIdx = Integer.parseInt(settings.getString(context.getString(R.string.pref_unitelevation), "0"));
					double elevationFactor = Double.parseDouble(resources.getStringArray(R.array.elevation_factors)[elevationIdx]);
					row.add(elevationFactor);
					break;
				}
				case PreferencesContract.ELEVATION_ABBREVIATION:
				{
					int elevationIdx = Integer.parseInt(settings.getString(context.getString(R.string.pref_unitelevation), "0"));
					String elevationAbbr = resources.getStringArray(R.array.elevation_abbrs)[elevationIdx];
					row.add(elevationAbbr);
					break;
				}
				case PreferencesContract.COORDINATES_FORMAT:
				{
					int coordinatesFormat = Integer.parseInt(settings.getString(context.getString(R.string.pref_unitcoordinate), "0"));
					row.add(coordinatesFormat);
					break;
				}
				default:
					throw new IllegalArgumentException("Unsupported item");
			}
		}
		
		return cursor;
	}

	@Override
	public Uri insert(Uri uri, ContentValues values)
	{
		throw new UnsupportedOperationException("Preferences can not be inserted");
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs)
	{
		throw new UnsupportedOperationException("Preferences can not be updated");
	}

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) 
	{
		throw new UnsupportedOperationException("Preferences can not be deleted");
	}
}
