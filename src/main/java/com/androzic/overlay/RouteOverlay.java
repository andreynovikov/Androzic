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

package com.androzic.overlay;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.preference.PreferenceManager;
import android.view.MotionEvent;

import com.androzic.MapView;
import com.androzic.R;
import com.androzic.data.Route;
import com.androzic.data.Waypoint;
import com.androzic.ui.Viewport;

public class RouteOverlay extends MapOverlay
{
	private Paint linePaint;
	private Paint borderPaint;
	private Paint fillPaint;
	private Paint textPaint;
	private Paint textFillPaint;
	private Route route;
	private Map<Waypoint, Bitmap> bitmaps;

	private int pointWidth = 10;
	private int routeWidth = 2;
	private boolean showNames;

	private boolean preserveWidth = false;

	public RouteOverlay(final Route route)
	{
		super();

		this.route = route;
		bitmaps = new WeakHashMap<Waypoint, Bitmap>();

		Resources resources = application.getResources();

		linePaint = new Paint();
		linePaint.setAntiAlias(true);
		linePaint.setStrokeWidth(routeWidth);
		linePaint.setStyle(Paint.Style.STROKE);
		linePaint.setColor(resources.getColor(R.color.routeline));
		fillPaint = new Paint();
		fillPaint.setAntiAlias(false);
		fillPaint.setStrokeWidth(1);
		fillPaint.setStyle(Paint.Style.FILL_AND_STROKE);
		fillPaint.setColor(resources.getColor(R.color.routewaypoint));
		borderPaint = new Paint();
		borderPaint.setAntiAlias(false);
		borderPaint.setStrokeWidth(1);
		borderPaint.setStyle(Paint.Style.STROKE);
		borderPaint.setColor(resources.getColor(R.color.routeline));
		textPaint = new Paint();
		textPaint.setAntiAlias(true);
		textPaint.setStrokeWidth(2);
		textPaint.setStyle(Paint.Style.FILL);
		textPaint.setTextAlign(Align.LEFT);
		textPaint.setTextSize(pointWidth * 1.5f);
		textPaint.setTypeface(Typeface.SANS_SERIF);
		textPaint.setColor(resources.getColor(R.color.routewaypointtext));
		textFillPaint = new Paint();
		textFillPaint.setAntiAlias(false);
		textFillPaint.setStrokeWidth(1);
		textFillPaint.setStyle(Paint.Style.FILL_AND_STROKE);
		textFillPaint.setColor(resources.getColor(R.color.routeline));

		onPreferencesChanged(PreferenceManager.getDefaultSharedPreferences(application));

		enabled = true;

		if (route.width <= 0)
			route.width = (int) linePaint.getStrokeWidth();

		if (route.lineColor == -1)
			route.lineColor = linePaint.getColor();
		
		onRoutePropertiesChanged();
	}

	private void initRouteColors()
	{
		linePaint.setColor(route.lineColor);
		linePaint.setAlpha(0xAA);
		borderPaint.setColor(route.lineColor);
		textFillPaint.setColor(route.lineColor);
		textFillPaint.setAlpha(0x88);
		double Y = getLuminance(route.lineColor);
		if (Y <= .5)
			textPaint.setColor(Color.WHITE);
		else
			textPaint.setColor(Color.BLACK);
	}

	private double adjustValue(int cc)
	{
		double val = cc;
		val = val / 255;
		if (val <= 0.03928)
			val = val / 12.92;
		else
			val = Math.pow(((val + 0.055) / 1.055), 2.4);
		return val;
	}

	private double getLuminance(int rgb)
	{
		// http://www.w3.org/TR/WCAG20/relative-luminance.xml
		int R = (rgb & 0x00FF0000) >>> 16;
		int G = (rgb & 0x0000FF00) >>> 8;
		int B = (rgb & 0x000000FF);
		return 0.2126 * adjustValue(R) + 0.7152 * adjustValue(G) + 0.0722 * adjustValue(B);
	}

	public void onRoutePropertiesChanged()
	{
		if (linePaint.getStrokeWidth() != route.width)
		{
			routeWidth = route.width;
			preserveWidth = true;
		}
		if (linePaint.getColor() != route.lineColor)
		{
			initRouteColors();
		}
		if (route.editing)
		{
			linePaint.setPathEffect(new DashPathEffect(new float[] { 5, 2 }, 0));
			linePaint.setStrokeWidth(routeWidth * 3);
		}
		else
		{
			linePaint.setPathEffect(null);
			linePaint.setStrokeWidth(routeWidth);
		}
		bitmaps.clear();
	}

	public void onBeforeDestroy()
	{
		super.onBeforeDestroy();
		bitmaps.clear();
	}

	public Route getRoute()
	{
		return route;
	}

	@Override
	public boolean onSingleTap(MotionEvent e, Rect mapTap, MapView mapView)
	{
		if (! route.show)
			return false;

		List<Waypoint> waypoints = route.getWaypoints();
		synchronized (waypoints)
		{
			for (int i = waypoints.size() - 1; i >= 0; i--)
			{
				Waypoint wpt = waypoints.get(i);
				int[] pointXY = application.getXYbyLatLon(wpt.latitude, wpt.longitude);
				if (mapTap.contains(pointXY[0], pointXY[1]))
				{
					return application.getMapHolder().routeWaypointTapped(route, i, (int) e.getX(), (int) e.getY());
				}
			}
		}
		return false;
	}

	@Override
	public void onPrepareBuffer(final Viewport viewport, final Canvas c)
	{
		if (!route.show)
			return;

		final int[] cxy = viewport.mapCenterXY;

		final Path path = new Path();
		int i = 0;
		int lastX = 0, lastY = 0;
		List<Waypoint> waypoints = route.getWaypoints();
		synchronized (waypoints)
		{
			for (Waypoint wpt : waypoints)
			{
				int[] xy = application.getXYbyLatLon(wpt.latitude, wpt.longitude);

				if (i == 0)
				{
					path.setLastPoint(xy[0] - cxy[0], xy[1] - cxy[1]);
					lastX = xy[0];
					lastY = xy[1];
				}
				else
				{
					if (Math.abs(lastX - xy[0]) > 2 || Math.abs(lastY - xy[1]) > 2)
					{
						path.lineTo(xy[0] - cxy[0], xy[1] - cxy[1]);
						lastX = xy[0];
						lastY = xy[1];
					}
				}
				i++;
			}
		}
		c.drawPath(path, linePaint);
	}

	@Override
	public void onPrepareBufferEx(final Viewport viewport, final Canvas c)
	{
		if (!route.show)
			return;

		final int[] cxy = viewport.mapCenterXY;

		final int half = Math.round(pointWidth / 2);

		List<Waypoint> waypoints = route.getWaypoints();

		synchronized (waypoints)
		{
			for (Waypoint wpt : waypoints)
			{
				Bitmap bitmap = bitmaps.get(wpt);
				if (bitmap == null)
				{
					int width = pointWidth;
					int height = pointWidth + 2;

					if (showNames)
					{
						Rect bounds = new Rect();
						textPaint.getTextBounds(wpt.name, 0, wpt.name.length(), bounds);
						bounds.inset(-2, -4);
						width += 5 + bounds.width();
						if (height < bounds.height())
							height = bounds.height();
					}

					bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
					Canvas bc = new Canvas(bitmap);

					bc.translate(half, half);
					if (showNames)
						bc.translate(0, 2);
					bc.drawCircle(0, 0, half, fillPaint);
					bc.drawCircle(0, 0, half, borderPaint);

					if (showNames)
					{
						Rect rect = new Rect();
						textPaint.getTextBounds(wpt.name, 0, wpt.name.length(), rect);
						rect.inset(-2, -4);
						rect.offset(+half + 5, +half - 3);
						bc.drawRect(rect, textFillPaint);
						bc.drawText(wpt.name, +half + 6, +half, textPaint);
					}
					bitmaps.put(wpt, bitmap);
				}
				int[] xy = application.getXYbyLatLon(wpt.latitude, wpt.longitude);

				Matrix matrix = new Matrix();
				if (viewport.mapHeading != 0f)
					matrix.preRotate(viewport.mapHeading, half, half);
				matrix.postTranslate(xy[0] - half - cxy[0], xy[1] - half - cxy[1]);
				c.drawBitmap(bitmap, matrix, null);
			}
		}
	}

	@Override
	public void onPreferencesChanged(SharedPreferences settings)
	{
		Resources resources = application.getResources();

		if (!preserveWidth)
			routeWidth = settings.getInt(application.getString(R.string.pref_route_linewidth), resources.getInteger(R.integer.def_route_linewidth));
		pointWidth = settings.getInt(application.getString(R.string.pref_route_pointwidth), resources.getInteger(R.integer.def_route_pointwidth));
		showNames = settings.getBoolean(application.getString(R.string.pref_route_showname), true);

		if (!route.editing)
		{
			linePaint.setStrokeWidth(routeWidth);
		}
		textPaint.setTextSize(pointWidth * 1.5f);
		bitmaps.clear();
	}

}
