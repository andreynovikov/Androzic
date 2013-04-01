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

package com.androzic.waypoint;

import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Window;
import com.androzic.Androzic;
import com.androzic.R;
import com.androzic.util.Geo;
import com.androzic.util.StringFormatter;

public class CoordinatesReceived extends SherlockActivity implements OnClickListener
{
    private double lat, lon;
	
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        
		requestWindowFeature(Window.FEATURE_LEFT_ICON);
		setContentView(R.layout.act_coordinates_received);

		Bundle extras = getIntent().getExtras();
		
        String title = extras.getString("title");
        String sender = extras.getString("sender");
        lat = extras.getDouble("lat");
        lon = extras.getDouble("lon");
        
        if (title != null && ! "".equals(title))
        {
        	setTitle(title);
        }
		this.setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, android.R.drawable.ic_dialog_map);

		Androzic application = (Androzic) getApplication();
		double[] ll = application.getLocation();

        ((TextView) findViewById(R.id.message)).setText(getString(R.string.new_coordinates, sender));
	
		String coords = StringFormatter.coordinates(application.coordinateFormat, " ", lat, lon);
		((TextView) findViewById(R.id.coordinates)).setText(coords);
		
		double dist = Geo.distance(ll[0], ll[1], lat, lon);
		double bearing = Geo.bearing(ll[0], ll[1], lat, lon);
		bearing = application.fixDeclination(bearing);
		String distance = StringFormatter.distanceH(dist)+" "+StringFormatter.bearingH(bearing);
		((TextView) findViewById(R.id.distance)).setText(distance);
		
	    ((Button) findViewById(R.id.show_button)).setOnClickListener(this);
	    ((Button) findViewById(R.id.dismiss_button)).setOnClickListener(this);
    }

	@Override
    public void onClick(View v)
    {
		if (v.getId() == R.id.show_button)
		{
			Androzic application = (Androzic) getApplication();
			application.ensureVisible(lat, lon);
		}
   		finish();
    }

	@Override
	protected void onDestroy()
	{
		super.onDestroy();
	}

}