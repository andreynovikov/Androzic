/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2014 Andrey Novikov <http://andreynovikov.info/>
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

import java.util.ArrayList;
import java.util.List;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;

import com.androzic.Androzic;
import com.androzic.R;
import com.androzic.map.ozf.Grid;
import com.androzic.map.ozf.OzfMap;

public class OverlayManager
{
	public static final int ORDER_SHOW_PREFERENCE = 0;
	public static final int ORDER_DRAW_PREFERENCE = 1;

	private static final int UPDATE_MESSAGE = 1;

	private Androzic application;
	private Handler executionHandler;

	public LatLonGridOverlay llGridOverlay;
	public OtherGridOverlay grGridOverlay;
	public CurrentTrackOverlay currentTrackOverlay;
	public NavigationOverlay navigationOverlay;
	public MapObjectsOverlay mapObjectsOverlay;
	public WaypointsOverlay waypointsOverlay;
	public DistanceOverlay distanceOverlay;
	public AccuracyOverlay accuracyOverlay;
	public List<TrackOverlay> fileTrackOverlays = new ArrayList<TrackOverlay>();
	public List<RouteOverlay> routeOverlays = new ArrayList<RouteOverlay>();

	public boolean mapGrid = false;
	public boolean userGrid = false;
	public int gridPrefer = 0;

	public OverlayManager(Looper looper)
	{
		application = Androzic.getApplication();
		executionHandler = new Handler(looper);
		createOverlays();
	}

	public void init()
	{
		if (waypointsOverlay == null)
			createOverlays();
		waypointsOverlay.setWaypoints(application.getWaypoints());
	}

	private void createOverlays()
	{
		mapObjectsOverlay = new MapObjectsOverlay();
		waypointsOverlay = new WaypointsOverlay();
	}

	public void onMapObjectsChanged()
	{
		mapObjectsOverlay.clearBitmapCache();
	}

	public void onWaypointsChanged()
	{
		waypointsOverlay.clearBitmapCache();
	}

	public void setWaypointsOverlayEnabled(boolean enabled)
	{
		waypointsOverlay.setEnabled(enabled);
	}

	public void setNavigationOverlayEnabled(boolean enabled)
	{
		if (enabled && navigationOverlay == null)
		{
			navigationOverlay = new NavigationOverlay();
		}
		else if (!enabled && navigationOverlay != null)
		{
			navigationOverlay.onBeforeDestroy();
			navigationOverlay = null;
		}
	}

	public void setCurrentTrackOverlayEnabled(boolean enabled)
	{
		if (enabled && currentTrackOverlay == null)
		{
			currentTrackOverlay = new CurrentTrackOverlay();
		}
		else if (!enabled && currentTrackOverlay != null)
		{
			currentTrackOverlay.onBeforeDestroy();
			currentTrackOverlay = null;
		}
	}

	public void setAccuracyOverlayEnabled(boolean enabled)
	{
		if (enabled && accuracyOverlay == null)
		{
			accuracyOverlay = new AccuracyOverlay();
			accuracyOverlay.setAccuracy(application.getLocationAsLocation().getAccuracy());
		}
		else if (!enabled && accuracyOverlay != null)
		{
			accuracyOverlay.onBeforeDestroy();
			accuracyOverlay = null;
		}
	}

	public void setDistanceOverlayEnabled(boolean enabled)
	{
		if (enabled && distanceOverlay == null)
		{
			distanceOverlay = new DistanceOverlay();
		}
		else if (!enabled && distanceOverlay != null)
		{
			distanceOverlay.onBeforeDestroy();
			distanceOverlay = null;
		}
	}

	public List<MapOverlay> getOverlays(int order)
	{
		List<MapOverlay> overlays = new ArrayList<MapOverlay>();
		if (order == ORDER_DRAW_PREFERENCE)
		{
			if (llGridOverlay != null)
				overlays.add(llGridOverlay);
			if (grGridOverlay != null)
				overlays.add(grGridOverlay);
			if (accuracyOverlay != null)
				overlays.add(accuracyOverlay);
			overlays.addAll(fileTrackOverlays);
			if (currentTrackOverlay != null)
				overlays.add(currentTrackOverlay);
			overlays.addAll(routeOverlays);
			if (navigationOverlay != null)
				overlays.add(navigationOverlay);
			if (waypointsOverlay != null)
				overlays.add(waypointsOverlay);
			if (mapObjectsOverlay != null)
				overlays.add(mapObjectsOverlay);
			if (distanceOverlay != null)
				overlays.add(distanceOverlay);
		}
		else
		{
			if (accuracyOverlay != null)
				overlays.add(accuracyOverlay);
			if (distanceOverlay != null)
				overlays.add(distanceOverlay);
			if (navigationOverlay != null)
				overlays.add(navigationOverlay);
			if (currentTrackOverlay != null)
				overlays.add(currentTrackOverlay);
			overlays.addAll(routeOverlays);
			if (waypointsOverlay != null)
				overlays.add(waypointsOverlay);
			overlays.addAll(fileTrackOverlays);
			if (mapObjectsOverlay != null)
				overlays.add(mapObjectsOverlay);
			if (grGridOverlay != null)
				overlays.add(grGridOverlay);
			if (llGridOverlay != null)
				overlays.add(llGridOverlay);
		}
		return overlays;
	}

	public void notifyOverlays()
	{
		if (!executionHandler.hasMessages(UPDATE_MESSAGE))
		{
			Message msg = Message.obtain(executionHandler, new Runnable() {
				@Override
				public void run()
				{
					executionHandler.removeMessages(UPDATE_MESSAGE);
					
					List<MapOverlay> overlays = getOverlays(ORDER_SHOW_PREFERENCE);
	
					for (MapOverlay mo : overlays)
					{
						boolean enabled = mo.setEnabled(false);
						mo.onMapChanged();
						mo.setEnabled(enabled);
						application.getMapHolder().refreshMap();
					}
				}
			});
			msg.what = UPDATE_MESSAGE;
			executionHandler.sendMessage(msg);
		}
	}

	public void onPreferencesChanged(final SharedPreferences settings)
	{
		for (TrackOverlay to : fileTrackOverlays)
		{
			to.onPreferencesChanged(settings);
		}
		for (RouteOverlay ro : routeOverlays)
		{
			ro.onPreferencesChanged(settings);
		}
		if (waypointsOverlay != null)
		{
			waypointsOverlay.onPreferencesChanged(settings);
		}
		if (navigationOverlay != null)
		{
			navigationOverlay.onPreferencesChanged(settings);
		}
		if (mapObjectsOverlay != null)
		{
			mapObjectsOverlay.onPreferencesChanged(settings);
		}
		if (distanceOverlay != null)
		{
			distanceOverlay.onPreferencesChanged(settings);
		}
		if (accuracyOverlay != null)
		{
			accuracyOverlay.onPreferencesChanged(settings);
		}
		if (currentTrackOverlay != null)
		{
			currentTrackOverlay.onPreferencesChanged(settings);
		}
	}

	public void initGrids(OzfMap currentMap)
	{
		llGridOverlay = null;
		grGridOverlay = null;
		if (mapGrid && currentMap != null && currentMap.llGrid != null && currentMap.llGrid.enabled)
		{
			LatLonGridOverlay llgo = new LatLonGridOverlay();
			llgo.setGrid(currentMap.llGrid);
			llGridOverlay = llgo;
		}
		if (mapGrid && currentMap != null && currentMap.grGrid != null && currentMap.grGrid.enabled && (!userGrid || gridPrefer == 0))
		{
			OtherGridOverlay ogo = new OtherGridOverlay();
			ogo.setGrid(currentMap.grGrid);
			grGridOverlay = ogo;
		}
		else if (userGrid && currentMap != null)
		{
			SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(application);
			OtherGridOverlay ogo = new OtherGridOverlay();
			Grid grid = new Grid();
			grid.color1 = 0xFF0000FF;
			grid.color2 = 0xFF0000FF;
			grid.color3 = 0xFF0000FF;
			grid.enabled = true;
			// TODO Optimize this
			grid.spacing = Integer.parseInt(settings.getString(application.getString(R.string.pref_grid_userscale), application.getResources().getString(R.string.def_grid_userscale)));
			int distanceIdx = Integer.parseInt(settings.getString(application.getString(R.string.pref_grid_userunit), "0"));
			grid.spacing *= Double.parseDouble(application.getResources().getStringArray(R.array.distance_factors_short)[distanceIdx]);
			grid.maxMPP = Integer.parseInt(settings.getString(application.getString(R.string.pref_grid_usermpp), application.getResources().getString(R.string.def_grid_usermpp)));
			ogo.setGrid(grid);
			grGridOverlay = ogo;
		}
	}

	public void clear()
	{
		setNavigationOverlayEnabled(false);
		setCurrentTrackOverlayEnabled(false);
		setAccuracyOverlayEnabled(false);
		setDistanceOverlayEnabled(false);

		if (llGridOverlay != null)
			llGridOverlay.onBeforeDestroy();
		if (grGridOverlay != null)
			grGridOverlay.onBeforeDestroy();

		llGridOverlay = null;
		grGridOverlay = null;

		for (TrackOverlay to : fileTrackOverlays)
		{
			to.onBeforeDestroy();
		}
		fileTrackOverlays.clear();
		for (RouteOverlay ro : routeOverlays)
		{
			ro.onBeforeDestroy();
		}
		routeOverlays.clear();

		mapObjectsOverlay.onBeforeDestroy();
		waypointsOverlay.onBeforeDestroy();

		mapObjectsOverlay = null;
		waypointsOverlay = null;

		executionHandler.removeCallbacksAndMessages(null);
		executionHandler = null;
	}
}
