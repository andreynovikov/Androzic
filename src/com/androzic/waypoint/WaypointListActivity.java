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

package com.androzic.waypoint;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBarActivity;

import com.androzic.Androzic;
import com.androzic.R;
import com.androzic.data.Waypoint;
import com.androzic.navigation.NavigationService;
import com.androzic.util.StringFormatter;

public class WaypointListActivity extends ActionBarActivity implements OnWaypointActionListener
{
	private Androzic application;

	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		application = Androzic.getApplication();

		setContentView(R.layout.act_fragment);

		if (savedInstanceState == null)
		{
			Fragment fragment = Fragment.instantiate(this, WaypointList.class.getName());
			FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
			fragmentTransaction.add(android.R.id.content, fragment, "WaypointList");
			fragmentTransaction.commit();
		}
	}

	@Override
	public void onWaypointView(Waypoint waypoint)
	{
		application.ensureVisible(waypoint);
		finish();
	}

	@Override
	public void onWaypointNavigate(Waypoint waypoint)
	{
		Intent intent = new Intent(application, NavigationService.class).setAction(NavigationService.NAVIGATE_MAPOBJECT);
		intent.putExtra(NavigationService.EXTRA_NAME, waypoint.name);
		intent.putExtra(NavigationService.EXTRA_LATITUDE, waypoint.latitude);
		intent.putExtra(NavigationService.EXTRA_LONGITUDE, waypoint.longitude);
		intent.putExtra(NavigationService.EXTRA_PROXIMITY, waypoint.proximity);
		application.startService(intent);
		finish();
	}

	@Override
	public void onWaypointEdit(Waypoint waypoint)
	{
		int index = application.getWaypointIndex(waypoint);
		startActivity(new Intent(application, WaypointProperties.class).putExtra("INDEX", index));
	}

	@Override
	public void onWaypointShare(Waypoint waypoint)
	{
		Intent i = new Intent(android.content.Intent.ACTION_SEND);
		i.setType("text/plain");
		i.putExtra(Intent.EXTRA_SUBJECT, R.string.currentloc);
		String coords = StringFormatter.coordinates(application.coordinateFormat, " ", waypoint.latitude, waypoint.longitude);
		i.putExtra(Intent.EXTRA_TEXT, waypoint.name + " @ " + coords);
		startActivity(Intent.createChooser(i, getString(R.string.menu_share)));
	}

	@Override
	public void onWaypointRemove(Waypoint waypoint)
	{
		application.removeWaypoint(waypoint);
	}

}
