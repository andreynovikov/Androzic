/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2012 Andrey Novikov <http://andreynovikov.info/>
 * 
 * This file is part of Androzic application.
 * 
 * Androzic is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Androzic is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Androzic. If not, see <http://www.gnu.org/licenses/>.
 */

package com.androzic.provider;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import com.androzic.Androzic;
import com.androzic.data.MapObject;

public class DataProvider extends ContentProvider
{
	private static final String TAG = "DataProvider";

	private static final int MAPOBJECTS = 1;
	private static final int MAPOBJECTS_ID = 2;

	private static final UriMatcher uriMatcher;

	static
	{
		uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
		uriMatcher.addURI(DataContract.AUTHORITY, DataContract.MAPOBJECTS_PATH, MAPOBJECTS);
		uriMatcher.addURI(DataContract.AUTHORITY, DataContract.MAPOBJECTS_PATH + "/#", MAPOBJECTS_ID);
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
			case MAPOBJECTS:
				return "vnd.android.cursor.dir/vnd.com.androzic.provider.mapobject";
			case MAPOBJECTS_ID:
				return "vnd.android.cursor.item/vnd.com.androzic.provider.mapobject";
			default:
				throw new IllegalArgumentException("Unknown URI " + uri);
		}
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder)
	{
		throw new UnsupportedOperationException("Quering objects is not supported");
	}

	@Override
	public Uri insert(Uri uri, ContentValues values)
	{
		if (uriMatcher.match(uri) != MAPOBJECTS)
		{
			throw new IllegalArgumentException("Unknown URI " + uri);
		}

		if (values == null)
		{
			throw new IllegalArgumentException("Values can not be null");
		}

		MapObject mo = new MapObject();
		mo.latitude = values.getAsDouble(DataContract.MAPOBJECT_COLUMNS[DataContract.MAPOBJECT_LATITUDE_COLUMN]);
		mo.longitude = values.getAsDouble(DataContract.MAPOBJECT_COLUMNS[DataContract.MAPOBJECT_LONGITUDE_COLUMN]);
		byte[] bytes = values.getAsByteArray(DataContract.MAPOBJECT_COLUMNS[DataContract.MAPOBJECT_BITMAP_COLUMN]);
		mo.bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

		long id = Androzic.getApplication().addMapObject(mo);
		Uri objectUri = ContentUris.withAppendedId(DataContract.MAPOBJECTS_URI, id);
		getContext().getContentResolver().notifyChange(objectUri, null);
		return objectUri;
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs)
	{
		if (uriMatcher.match(uri) != MAPOBJECTS_ID)
		{
			if (uriMatcher.match(uri) == MAPOBJECTS)
				throw new UnsupportedOperationException("Currently only updating one object by ID is supported");
			else
				throw new IllegalArgumentException("Unknown URI " + uri);
		}

		if (values == null)
		{
			throw new IllegalArgumentException("Values can not be null");
		}
		long id = ContentUris.parseId(uri);
		MapObject mo = Androzic.getApplication().getMapObject(id);
		if (mo == null)
			return 0;

		synchronized (mo)
		{
			mo.latitude = values.getAsDouble(DataContract.MAPOBJECT_COLUMNS[DataContract.MAPOBJECT_LATITUDE_COLUMN]);
			mo.longitude = values.getAsDouble(DataContract.MAPOBJECT_COLUMNS[DataContract.MAPOBJECT_LONGITUDE_COLUMN]);
			byte[] bytes = values.getAsByteArray(DataContract.MAPOBJECT_COLUMNS[DataContract.MAPOBJECT_BITMAP_COLUMN]);
			mo.bitmap.recycle();
			mo.bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
		}

		getContext().getContentResolver().notifyChange(uri, null);
		return 1;
	}

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs)
	{
		Log.d(TAG, "Delete: " + uri);
		
		long[] ids = null;
		if (uriMatcher.match(uri) == MAPOBJECTS)
		{
			if (!DataContract.MAPOBJECT_ID_SELECTION.equals(selection))
				throw new IllegalArgumentException("Deleting is supported only by ID");
			ids = new long[selectionArgs.length];
			for (int i = 0; i < ids.length; i++)
				ids[i] = Long.parseLong(selectionArgs[i], 10);
		}
		if (uriMatcher.match(uri) == MAPOBJECTS_ID)
		{
			ids = new long[] { ContentUris.parseId(uri) };
		}
		if (ids == null)
			throw new IllegalArgumentException("Unknown URI: " + uri);

		Androzic application = Androzic.getApplication();

		int result = 0;
		for (long id : ids)
		{
			if (application.removeMapObject(id))
				result++;
		}
		return result;
	}
}
