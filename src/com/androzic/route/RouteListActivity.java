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

package com.androzic.route;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBarActivity;

import com.androzic.Androzic;
import com.androzic.R;
import com.androzic.data.Route;
import com.androzic.overlay.RouteOverlay;

public class RouteListActivity extends ActionBarActivity implements OnRouteActionListener
{
	static final int RESULT_START_ROUTE = 1;
	static final int RESULT_LOAD_ROUTE = 2;
	static final int RESULT_ROUTE_DETAILS = 3;
	
	private Androzic application;

	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		application = Androzic.getApplication();

		setContentView(R.layout.act_fragment);

		if (savedInstanceState == null)
		{
			Fragment fragment = Fragment.instantiate(this, RouteList.class.getName());
			FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
			fragmentTransaction.add(android.R.id.content, fragment, "RouteList");
			fragmentTransaction.commit();
		}
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);

		switch (requestCode)
		{
			case RESULT_START_ROUTE:
				if (resultCode == RESULT_OK)
					finish();
				break;
			case RESULT_LOAD_ROUTE:
				if (resultCode == RESULT_OK)
				{
					final Androzic application = Androzic.getApplication();
					int[] indexes = data.getExtras().getIntArray("index");
					for (int index : indexes)
					{
						RouteOverlay newRoute = new RouteOverlay(this, application.getRoute(index));
						application.routeOverlays.add(newRoute);
					}
				}
				break;
			case RESULT_ROUTE_DETAILS:
				if (resultCode == RESULT_OK)
				{
					finish();
				}
		}
	}

	@Override
	public void onRouteDetails(Route route)
	{
		startActivityForResult(new Intent(this, RouteDetails.class).putExtra("index", application.getRouteIndex(route)), RESULT_ROUTE_DETAILS);
	}

	@Override
	public void onRouteNavigate(Route route)
	{
		startActivityForResult(new Intent(this, RouteStart.class).putExtra("index", application.getRouteIndex(route)), RESULT_START_ROUTE);
	}

	@Override
	public void onRouteEdit(Route route)
	{
		startActivity(new Intent(this, RouteProperties.class).putExtra("index", application.getRouteIndex(route)));
	}

	@Override
	public void onRouteEditPath(Route route)
	{
		route.show = true;
		setResult(RESULT_OK, new Intent().putExtra("index", application.getRouteIndex(route)));
		finish();
	}

	@Override
	public void onRouteSave(Route route)
	{
		startActivity(new Intent(this, RouteSave.class).putExtra("index", application.getRouteIndex(route)));
	}
}
