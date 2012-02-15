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

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;

import com.androzic.Androzic;
import com.androzic.MapView;
import com.androzic.R;
import com.androzic.util.Geo;

public class AccuracyOverlay extends MapOverlay
{
	Paint paint;
	int radius = 0;
	float accuracy = 0;

	public AccuracyOverlay(final Activity activity)
	{
		super(activity);

		paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStrokeWidth(5);
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        paint.setColor(context.getResources().getColor(R.color.accuracy));
	}

	public void setAccuracy(float accuracy)
	{
		if (accuracy > 0 && this.accuracy != accuracy)
		{
			this.accuracy = accuracy;
			Androzic application = (Androzic) context.getApplication();
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
	protected void onDraw(Canvas c, MapView mapView, int centerX, int centerY)
	{
		final int[] cxy = mapView.mapCenterXY;

		if (radius > 0 && mapView.currentLocation != null)
		{
			c.drawCircle(mapView.currentLocationXY[0] - cxy[0], mapView.currentLocationXY[1] - cxy[1], radius, paint);
		}
	}

	@Override
	protected void onDrawFinished(Canvas c, MapView mapView, int centerX, int centerY)
	{
		// TODO Auto-generated method stub

	}

	@Override
	public void onPreferencesChanged(SharedPreferences settings)
	{
		// TODO Auto-generated method stub

	}
}
