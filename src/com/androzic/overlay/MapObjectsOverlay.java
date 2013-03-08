/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2013 Andrey Novikov <http://andreynovikov.info/>
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

package com.androzic.overlay;

import java.io.File;
import java.util.Iterator;
import java.util.WeakHashMap;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.preference.PreferenceManager;
import android.view.MotionEvent;

import com.androzic.Androzic;
import com.androzic.MapActivity;
import com.androzic.MapView;
import com.androzic.R;
import com.androzic.data.MapObject;
import com.androzic.map.Map;

public class MapObjectsOverlay extends MapOverlay
{
	private WeakHashMap<MapObject, Bitmap> bitmaps;

	private Paint borderPaint;
	private Paint fillPaint;
	private Paint textPaint;
	private Paint textFillPaint;
	private Paint proximityPaint;

	private int pointWidth;
	private boolean showNames;
	private double mpp;

	public MapObjectsOverlay(final Activity mapActivity)
	{
		super(mapActivity);
		enabled = true;
		
		fillPaint = new Paint();
		fillPaint.setAntiAlias(false);
		fillPaint.setStrokeWidth(1);
		fillPaint.setStyle(Paint.Style.FILL_AND_STROKE);
		fillPaint.setColor(context.getResources().getColor(R.color.waypoint));
		borderPaint = new Paint();
		borderPaint.setAntiAlias(false);
		borderPaint.setStrokeWidth(1);
		borderPaint.setStyle(Paint.Style.STROKE);
		borderPaint.setColor(context.getResources().getColor(R.color.waypointtext));
		textPaint = new Paint();
		textPaint.setAntiAlias(true);
		textPaint.setStrokeWidth(2);
		textPaint.setStyle(Paint.Style.FILL);
		textPaint.setTextAlign(Align.LEFT);
		textPaint.setTextSize(10);
		textPaint.setTypeface(Typeface.SANS_SERIF);
		textPaint.setColor(context.getResources().getColor(R.color.waypointtext));
		textFillPaint = new Paint();
		textFillPaint.setAntiAlias(false);
		textFillPaint.setStrokeWidth(1);
		textFillPaint.setStyle(Paint.Style.FILL_AND_STROKE);
		textFillPaint.setColor(context.getResources().getColor(R.color.waypointbg));
		proximityPaint = new Paint();
		proximityPaint.setAntiAlias(false);
		proximityPaint.setStrokeWidth(1);
		proximityPaint.setStyle(Paint.Style.FILL_AND_STROKE);
		proximityPaint.setColor(context.getResources().getColor(R.color.proximity));

		mpp = 0;

		bitmaps = new WeakHashMap<MapObject, Bitmap>();

		onPreferencesChanged(PreferenceManager.getDefaultSharedPreferences(context));
	}

	public void clearBitmapCache()
	{
		bitmaps.clear();
	}

	@Override
	public void onBeforeDestroy()
	{
		super.onBeforeDestroy();
		clearBitmapCache();
	}

	@Override
	public synchronized void onMapChanged()
	{
		Androzic application = (Androzic) context.getApplication();
		Map map = application.getCurrentMap();
		if (map == null)
			return;

		mpp = map.mpp / map.getZoom();
	}
	
	@Override
	public boolean onSingleTap(MotionEvent e, Rect mapTap, MapView mapView)
	{
		Androzic application = (Androzic) context.getApplication();
		Iterator<MapObject> mapObjects = application.getMapObjects().iterator();
		while (mapObjects.hasNext())
		{
			MapObject mo = mapObjects.next();
			synchronized (mo)
			{
				int[] pointXY = application.getXYbyLatLon(mo.latitude, mo.longitude);
				if (mapTap.contains(pointXY[0], pointXY[1]) && context instanceof MapActivity)
				{
					return ((MapActivity) context).mapObjectTapped(mo._id, (int) e.getX(), (int) e.getY());
				}
			}
		}
		return false;
	}

	protected void drawMapObject(Canvas c, MapObject mo, Androzic application, int[] cxy)
	{
		int[] xy = application.getXYbyLatLon(mo.latitude, mo.longitude);
		
		Bitmap bitmap = null;
		int dx = 0;
		int dy = 0;
		
		if (mo.bitmap != null)
		{
			bitmap = mo.bitmap;
			dx = mo.bitmap.getWidth() / 2;
			dy = mo.bitmap.getHeight() / 2;
		}
		
		if (bitmap == null)
			bitmap = bitmaps.get(mo);
		
		if (bitmap == null)
		{
			int width = pointWidth;
			int height = pointWidth;

			Bitmap icon = null;
			if (!"".equals(mo.image) && application.iconsEnabled)
			{
				icon = BitmapFactory.decodeFile(application.iconPath + File.separator + mo.image);
				if (icon == null)
				{
					mo.drawImage = false;
				}
				else
				{
					width = icon.getWidth();
					height = icon.getHeight();
					mo.drawImage = true;
				}
			}

			Rect rect = null;
			rect = new Rect(0, 0, width, height);

			Rect bounds = new Rect();

			if (showNames)
			{
				textPaint.getTextBounds(mo.name, 0, mo.name.length(), bounds);
				bounds.right = bounds.right + 4;
				bounds.bottom = bounds.bottom + 4;
				width += 6 + bounds.width();
				if (height < bounds.height())
					height = bounds.height();
			}

			bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
			Canvas bc = new Canvas(bitmap);

			if (mo.drawImage)
			{
				bc.drawBitmap(icon, 0, icon.getHeight() > bounds.height() ? 0 : (bounds.height() - icon.getHeight()) / 2, null);
			}
			else
			{
				int tc = 0, bgc = 0;
				if (mo.textcolor != Integer.MIN_VALUE)
				{
					tc = borderPaint.getColor();
					borderPaint.setColor(mo.textcolor);
				}
				if (mo.backcolor != Integer.MIN_VALUE)
				{
					bgc = fillPaint.getColor();
					fillPaint.setColor(mo.backcolor);
				}
				bc.save();
				bc.translate(0, pointWidth > bounds.height() ? 0 : (bounds.height() - pointWidth) / 2);
				bc.drawRect(rect, borderPaint);
				rect.inset(1, 1);
				bc.drawRect(rect, fillPaint);
				bc.restore();
				if (mo.textcolor != Integer.MIN_VALUE)
				{
					borderPaint.setColor(tc);
				}
				if (mo.backcolor != Integer.MIN_VALUE)
				{
					fillPaint.setColor(bgc);
				}
			}

			if (showNames)
			{
				int tc = 0;
				if (mo.textcolor != Integer.MIN_VALUE)
				{
					tc = textPaint.getColor();
					textPaint.setColor(mo.textcolor);
				}
				bc.translate(width - bounds.right, -bounds.top + (height - bounds.height()) / 2);
				bc.drawRect(bounds, textFillPaint);
				bc.drawText(mo.name, 2, 2, textPaint);
				if (mo.textcolor != Integer.MIN_VALUE)
				{
					textPaint.setColor(tc);
				}
			}
			bitmaps.put(mo, bitmap);
		}

		if (mo.bitmap == null)
		{
			dx = mo.drawImage ? application.iconX : pointWidth / 2;
			dy = mo.drawImage ? application.iconY : bitmap.getHeight() / 2;
		}

		if (mo.proximity > 0 && mpp > 0)
			c.drawCircle(xy[0] - cxy[0], xy[1] - cxy[1], (float) (mo.proximity / mpp), proximityPaint);

		c.drawBitmap(bitmap, xy[0] - dx - cxy[0], xy[1] - dy - cxy[1], null);		
	}

	@Override
	protected void onDraw(final Canvas c, final MapView mapVie, int centerX, int centerYw)
	{
	}

	@Override
	protected void onDrawFinished(final Canvas c, final MapView mapView, int centerX, int centerY)
	{
		Androzic application = (Androzic) context.getApplication();

		final int[] cxy = mapView.mapCenterXY;

		Iterator<MapObject> mapObjects = application.getMapObjects().iterator();
		while (mapObjects.hasNext())
		{
			MapObject mo = mapObjects.next();
			synchronized (mo)
			{
				drawMapObject(c, mo, application, cxy);
			}
		}
	}

	@Override
	public void onPreferencesChanged(SharedPreferences settings)
	{
		pointWidth = settings.getInt(context.getString(R.string.pref_waypoint_width), context.getResources().getInteger(R.integer.def_waypoint_width));
		showNames = settings.getBoolean(context.getString(R.string.pref_waypoint_showname), true);
		fillPaint.setColor(settings.getInt(context.getString(R.string.pref_waypoint_color), context.getResources().getColor(R.color.waypoint)));
		int alpha = textFillPaint.getAlpha();
		textFillPaint.setColor(settings.getInt(context.getString(R.string.pref_waypoint_bgcolor), context.getResources().getColor(R.color.waypointbg)));
		textFillPaint.setAlpha(alpha);
		borderPaint.setColor(settings.getInt(context.getString(R.string.pref_waypoint_namecolor), context.getResources().getColor(R.color.waypointtext)));
		textPaint.setColor(settings.getInt(context.getString(R.string.pref_waypoint_namecolor), context.getResources().getColor(R.color.waypointtext)));
		textPaint.setTextSize(pointWidth * 1.5f);
		clearBitmapCache();
	}
}
