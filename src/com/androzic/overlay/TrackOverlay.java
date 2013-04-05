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

import java.util.List;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.preference.PreferenceManager;

import com.androzic.Androzic;
import com.androzic.MapView;
import com.androzic.R;
import com.androzic.data.Track;
import com.androzic.data.Track.TrackPoint;

public class TrackOverlay extends MapOverlay
{
	Paint paint;
	Track track;

	private boolean preserveWidth = false;
	private boolean preserveColor = false;

	public TrackOverlay(final Activity mapActivity)
	{
		super(mapActivity);

		track = new Track();

		paint = new Paint();
		paint.setAntiAlias(true);
		paint.setStrokeWidth(3);
		paint.setStyle(Paint.Style.STROKE);

		onPreferencesChanged(PreferenceManager.getDefaultSharedPreferences(context));

		enabled = true;
	}

	public TrackOverlay(final Activity mapActivity, final Track aTrack)
	{
		this(mapActivity);

		track = aTrack;
		if (track.width > 0)
		{
			paint.setStrokeWidth(track.width);
			preserveWidth = true;
		}
		else
		{
			track.width = (int) paint.getStrokeWidth();
		}
		if (track.color != -1)
		{
			paint.setColor(track.color);
			preserveColor = true;
		}
		else
		{
			track.color = paint.getColor();
		}
	}

	public void onTrackPropertiesChanged()
	{
		if (paint.getStrokeWidth() != track.width)
		{
			paint.setStrokeWidth(track.width);
			preserveWidth = true;
		}
		if (paint.getColor() != track.color)
		{
			paint.setColor(track.color);
			preserveColor = true;
		}
	}

	public void setTrack(Track track)
	{
		this.track = track;
		onTrackPropertiesChanged();
	}

	public Track getTrack()
	{
		return track;
	}

	@Override
	public void onMapChanged()
	{
		List<TrackPoint> trackpoints = track.getPoints();
		synchronized (trackpoints)
		{
			for (TrackPoint tp : trackpoints)
			{
				tp.dirty = true;
			}
		}
	}

	@Override
	protected void onDraw(final Canvas c, final MapView mapView, int centerX, int centerY)
	{
		if (!track.show)
			return;

		Androzic application = (Androzic) context.getApplication();

		final int[] cxy = mapView.mapCenterXY;

		int w2 = mapView.getWidth() / 2;
		int h2 = mapView.getHeight() / 2;
		int left = cxy[0] - w2;
		int right = cxy[0] + w2;
		int top = cxy[1] - h2;
		int bottom = cxy[1] + h2;

		final Path path = new Path();
		boolean first = true;
		boolean skipped = false;
		int lastX = 0, lastY = 0;
		List<TrackPoint> trackpoints = track.getPoints();
		synchronized (trackpoints)
		{
			for (TrackPoint tp : trackpoints)
			{
				int[] xy = new int[2];
				if (tp.dirty)
				{
					xy = application.getXYbyLatLon(tp.latitude, tp.longitude);
					tp.x = xy[0];
					tp.y = xy[1];
					tp.dirty = false;
				}
				else
				{
					xy[0] = tp.x;
					xy[1] = tp.y;
				}

				if (first)
				{
					path.setLastPoint(xy[0] - cxy[0], xy[1] - cxy[1]);
					lastX = xy[0];
					lastY = xy[1];
					first = false;
					continue;
				}
				if ((lastX == xy[0] && lastY == xy[1]) ||
					lastX < left && cxy[0] < left ||
					lastX > right && cxy[0] > right ||
					lastY < top && cxy[1] < top ||
					lastY > bottom && cxy[1] > bottom)
				{
					lastX = xy[0];
					lastY = xy[1];
					skipped = true;
					continue;
				}
				if (skipped)
				{
					path.moveTo(lastX - cxy[0], lastY - cxy[1]);
					skipped = false;
				}
				if (tp.continous)
					path.lineTo(xy[0] - cxy[0], xy[1] - cxy[1]);
				else
					path.moveTo(xy[0] - cxy[0], xy[1] - cxy[1]);
				lastX = xy[0];
				lastY = xy[1];
			}
		}
		c.drawPath(path, paint);
	}

	@Override
	protected void onDrawFinished(final Canvas c, final MapView mapView, int centerX, int centerY)
	{
	}

	@Override
	public void onPreferencesChanged(SharedPreferences settings)
	{
		if (!preserveWidth)
			paint.setStrokeWidth(settings.getInt(context.getString(R.string.pref_tracking_linewidth), context.getResources().getInteger(R.integer.def_track_linewidth)));
		if (!preserveColor)
			paint.setColor(settings.getInt(context.getString(R.string.pref_tracking_currentcolor), context.getResources().getColor(R.color.currenttrack)));
	}

}
