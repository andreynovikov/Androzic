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

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.preference.PreferenceManager;

import com.androzic.R;
import com.androzic.data.Track;
import com.androzic.ui.Viewport;

public class TrackOverlay extends MapOverlay
{
	Paint paint;
	Track track;

	private boolean preserveWidth = false;
	private boolean preserveColor = false;

	public TrackOverlay()
	{
		super();

		track = new Track();

		paint = new Paint();
		paint.setAntiAlias(true);
		paint.setStrokeWidth(3);
		paint.setStyle(Paint.Style.STROKE);

		onPreferencesChanged(PreferenceManager.getDefaultSharedPreferences(application));

		enabled = true;
	}

	public TrackOverlay(final Track aTrack)
	{
		this();

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
		synchronized (track)
		{
			for (Track.TrackSegment segment : track.getSegments())
			{
				synchronized (segment)
				{
					List<Track.TrackPoint> points = segment.getPoints();
					for (Track.TrackPoint tp : points)
					{
						tp.dirty = true;
					}
				}
			}
		}
		application.getMapHolder().refreshMap();
	}

	@Override
	public void onPrepareBuffer(final Viewport viewport, final Canvas c)
	{
		if (!track.show)
			return;

		final int[] cxy = viewport.mapCenterXY;

		int w2 = viewport.canvasWidth / 2;
		int h2 = viewport.canvasHeight / 2;
		int left = cxy[0] - w2;
		int right = cxy[0] + w2;
		int top = cxy[1] - h2;
		int bottom = cxy[1] + h2;

		final Path path = new Path();
		boolean first = true;
		boolean skipped = false;
		int lastX = 0, lastY = 0;
		int[] xy = new int[2];
		
		synchronized (track)
		{
			for (Track.TrackSegment segment : track.getSegments())
			{
				if (! viewport.mapArea.intersects(segment.bounds))
					continue;
				synchronized (segment)
				{
					List<Track.TrackPoint> points = segment.getPoints();
					if (points.size() == 0)
						continue;
					for (Track.TrackPoint tp : points)
					{
						if (tp.dirty)
						{
							application.getXYbyLatLon(tp.latitude, tp.longitude, xy);
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
			}
		}
		c.drawPath(path, paint);
	}

	@Override
	public void onPrepareBufferEx(final Viewport viewport, final Canvas c)
	{
	}

	@Override
	public void onPreferencesChanged(SharedPreferences settings)
	{
		Resources resources = application.getResources();		
		if (!preserveWidth)
			paint.setStrokeWidth(settings.getInt(application.getString(R.string.pref_tracking_linewidth), resources.getInteger(R.integer.def_track_linewidth)));
		if (!preserveColor)
			paint.setColor(settings.getInt(application.getString(R.string.pref_tracking_currentcolor), resources.getColor(R.color.currenttrack)));
	}

}
