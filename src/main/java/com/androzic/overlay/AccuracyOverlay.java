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

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;

import com.androzic.R;
import com.androzic.ui.Viewport;
import com.androzic.util.Geo;

public class AccuracyOverlay extends MapOverlay
{
	Paint paint;
	int radius = 0;
	float accuracy = 0;

	public AccuracyOverlay()
	{
		super();

		paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStrokeWidth(5);
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        paint.setColor(application.getResources().getColor(R.color.accuracy));
	}

	public void setAccuracy(float accuracy)
	{
		if (accuracy > 0 && this.accuracy != accuracy)
		{
			this.accuracy = accuracy;
			double[] loc = application.getLocation();
			double[] prx = Geo.projection(loc[0], loc[1], accuracy/2, 90);
			int[] cxy = application.getXYbyLatLon(loc[0], loc[1]);
			int[] pxy = application.getXYbyLatLon(prx[0], prx[1]);
			radius = (int) Math.hypot((pxy[0]-cxy[0]), (pxy[1]-cxy[1]));
		}
		enabled = accuracy > 0;
    }

	@Override
	public void onMapChanged()
	{
		float a =  accuracy;
		accuracy = 0;
		setAccuracy(a);
	}

	@Override
	public void onPrepareBuffer(final Viewport viewport, final Canvas c)
	{
		final int[] cxy = viewport.mapCenterXY;

		if (radius > 0 && !Double.isNaN(viewport.location[0]))
		{
			c.drawCircle(viewport.locationXY[0] - cxy[0], viewport.locationXY[1] - cxy[1], radius, paint);
		}
	}

	@Override
	public void onPrepareBufferEx(final Viewport viewport, final Canvas c)
	{
	}

	@Override
	public void onPreferencesChanged(SharedPreferences settings)
	{
	}
}
