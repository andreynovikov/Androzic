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

package com.androzic;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Stack;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.londatiga.android.ActionItem;
import net.londatiga.android.QuickAction3D;
import net.londatiga.android.QuickAction3D.OnActionItemClickListener;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.LightingColorFilter;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import com.androzic.data.Route;
import com.androzic.data.Track;
import com.androzic.data.Waypoint;
import com.androzic.map.MapInformation;
import com.androzic.navigation.NavigationService;
import com.androzic.overlay.RouteOverlay;
import com.androzic.route.RouteDetails;
import com.androzic.route.RouteEdit;
import com.androzic.util.StringFormatter;
import com.androzic.waypoint.OnWaypointActionListener;
import com.androzic.waypoint.WaypointProperties;

public class MapActivity extends ActionBarActivity implements MapHolder, View.OnClickListener, OnWaypointActionListener, SeekBar.OnSeekBarChangeListener
{
	private static final String TAG = "MapActivity";

	private static final int RESULT_SAVE_WAYPOINT = 0x400;
	private static final int RESULT_LOAD_MAP = 0x500;
	private static final int RESULT_MANAGE_TRACKS = 0x600;
	private static final int RESULT_EDIT_ROUTE = 0x110;

	private static final int qaAddWaypointToRoute = 1;
	private static final int qaNavigateToWaypoint = 2;

	// main preferences
	protected String precisionFormat = "%.0f";
	protected double speedFactor;
	protected String speedAbbr;
	protected double elevationFactor;
	protected String elevationAbbr;
	protected int renderInterval;
	protected int magInterval;
	protected int showDistance;
	protected boolean showAccuracy;
	protected boolean followOnLocation;
	protected int exitConfirmation;

	private TextView speedUnit;
	private TextView elevationUnit;

	protected SeekBar trackBar;
	protected TextView waitBar;
	protected MapView map;
	protected QuickAction3D wptQuickAction;
	protected QuickAction3D rteQuickAction;

	protected Androzic application;

	protected ExecutorService executorThread = Executors.newSingleThreadExecutor();

	private int waypointSelected = -1;
	private int routeSelected = -1;

	public NavigationService navigationService = null;

	protected long lastRenderTime = 0;
	protected long lastMagnetic = 0;

	private boolean isFullscreen;
	LightingColorFilter disable = new LightingColorFilter(0xFFFFFFFF, 0xFF555555);

	protected boolean ready = false;
	private boolean restarting = false;

	/** Called when the activity is first created. */
	@SuppressLint("ShowToast")
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		Log.e(TAG, "onCreate()");

		ready = false;
		isFullscreen = false;

		application = (Androzic) getApplication();

		// check if called after crash
		if (!application.mapsInited)
		{
			restarting = true;
			startActivity(new Intent(this, Splash.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK).putExtras(getIntent()));
			finish();
			return;
		}

		application.setMapHolder(this);

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		Resources resources = getResources();

		if (settings.getBoolean(getString(R.string.pref_hideactionbar), resources.getBoolean(R.bool.def_hideactionbar)))
		{
			requestWindowFeature(Window.FEATURE_NO_TITLE);
		}

		setContentView(R.layout.act_main);
		speedUnit = (TextView) findViewById(R.id.speedunit);
		elevationUnit = (TextView) findViewById(R.id.elevationunit);
		trackBar = (SeekBar) findViewById(R.id.trackbar);
		waitBar = (TextView) findViewById(R.id.waitbar);
		map = (MapView) findViewById(R.id.mapview);

		// set button actions
		findViewById(R.id.finishedit).setOnClickListener(this);
		findViewById(R.id.addpoint).setOnClickListener(this);
		findViewById(R.id.insertpoint).setOnClickListener(this);
		findViewById(R.id.removepoint).setOnClickListener(this);
		findViewById(R.id.orderpoints).setOnClickListener(this);
		findViewById(R.id.finishtrackedit).setOnClickListener(this);
		findViewById(R.id.cutafter).setOnClickListener(this);
		findViewById(R.id.cutbefore).setOnClickListener(this);

		wptQuickAction = new QuickAction3D(this, QuickAction3D.VERTICAL);
		wptQuickAction.addActionItem(new ActionItem(qaAddWaypointToRoute, getString(R.string.menu_addtoroute), resources.getDrawable(R.drawable.ic_action_add)));
		wptQuickAction.setOnActionItemClickListener(waypointActionItemClickListener);

		rteQuickAction = new QuickAction3D(this, QuickAction3D.VERTICAL);
		rteQuickAction.addActionItem(new ActionItem(qaNavigateToWaypoint, getString(R.string.menu_thisnavpoint), resources.getDrawable(R.drawable.ic_action_directions)));
		rteQuickAction.setOnActionItemClickListener(routeActionItemClickListener);

		trackBar.setOnSeekBarChangeListener(this);

		map.initialize(application, this);

		if (getIntent().getExtras() != null)
			onNewIntent(getIntent());

		ready = true;
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		Log.e(TAG, "onResume()");

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		Resources resources = getResources();

		// update some preferences
		int speedIdx = Integer.parseInt(settings.getString(getString(R.string.pref_unitspeed), "0"));
		speedFactor = Double.parseDouble(resources.getStringArray(R.array.speed_factors)[speedIdx]);
		speedAbbr = resources.getStringArray(R.array.speed_abbrs)[speedIdx];
		speedUnit.setText(speedAbbr);
		int distanceIdx = Integer.parseInt(settings.getString(getString(R.string.pref_unitdistance), "0"));
		StringFormatter.distanceFactor = Double.parseDouble(resources.getStringArray(R.array.distance_factors)[distanceIdx]);
		StringFormatter.distanceAbbr = resources.getStringArray(R.array.distance_abbrs)[distanceIdx];
		StringFormatter.distanceShortFactor = Double.parseDouble(resources.getStringArray(R.array.distance_factors_short)[distanceIdx]);
		StringFormatter.distanceShortAbbr = resources.getStringArray(R.array.distance_abbrs_short)[distanceIdx];
		int elevationIdx = Integer.parseInt(settings.getString(getString(R.string.pref_unitelevation), "0"));
		elevationFactor = Double.parseDouble(resources.getStringArray(R.array.elevation_factors)[elevationIdx]);
		elevationAbbr = resources.getStringArray(R.array.elevation_abbrs)[elevationIdx];
		elevationUnit.setText(elevationAbbr);
		application.sunriseType = Integer.parseInt(settings.getString(getString(R.string.pref_unitsunrise), "0"));

		renderInterval = settings.getInt(getString(R.string.pref_maprenderinterval), resources.getInteger(R.integer.def_maprenderinterval)) * 100;
		followOnLocation = settings.getBoolean(getString(R.string.pref_mapfollowonloc), resources.getBoolean(R.bool.def_mapfollowonloc));
		magInterval = resources.getInteger(R.integer.def_maginterval) * 1000;
		showDistance = Integer.parseInt(settings.getString(getString(R.string.pref_showdistance_int), getString(R.string.def_showdistance)));

		// prepare views
		findViewById(R.id.editroute).setVisibility(application.editingRoute != null ? View.VISIBLE : View.GONE);
		if (application.editingTrack != null)
		{
			startEditTrack(application.editingTrack);
		}
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		Log.e(TAG, "onDestroy()");
		ready = false;

		if (isFinishing() && !restarting)
		{
			application.clear();
		}

		restarting = false;

		application = null;
		map = null;
	}

	public void updateMap()
	{
	}

	private void startEditTrack(Track track)
	{
		setFollowing(false);
		application.editingTrack = track;
		application.editingTrack.editing = true;
		int n = application.editingTrack.getPoints().size() - 1;
		int p = application.editingTrack.editingPos >= 0 ? application.editingTrack.editingPos : n;
		application.editingTrack.editingPos = p;
		trackBar.setMax(n);
		trackBar.setProgress(0);
		trackBar.setProgress(p);
		trackBar.setKeyProgressIncrement(1);
		onProgressChanged(trackBar, p, false);
		findViewById(R.id.edittrack).setVisibility(View.VISIBLE);
		findViewById(R.id.trackdetails).setVisibility(View.VISIBLE);
		//updateGPSStatus();
		if (showDistance > 0)
			application.overlayManager.distanceOverlay.setEnabled(false);
		map.setFocusable(false);
		map.setFocusableInTouchMode(false);
		trackBar.requestFocus();
		//updateMapViewArea();
	}

	private void startEditRoute(Route route)
	{
		setFollowing(false);
		application.editingRoute = route;
		application.editingRoute.editing = true;

		boolean newroute = true;
		for (Iterator<RouteOverlay> iter = application.overlayManager.routeOverlays.iterator(); iter.hasNext();)
		{
			RouteOverlay ro = iter.next();
			if (ro.getRoute().editing)
			{
				ro.onRoutePropertiesChanged();
				newroute = false;
			}
		}
		if (newroute)
		{
			RouteOverlay newRoute = new RouteOverlay(application.editingRoute);
			application.overlayManager.routeOverlays.add(newRoute);
		}
		findViewById(R.id.editroute).setVisibility(View.VISIBLE);
		//updateGPSStatus();
		application.routeEditingWaypoints = new Stack<Waypoint>();
		if (showDistance > 0)
			application.overlayManager.distanceOverlay.setEnabled(false);
		//updateMapViewArea();
	}


	@Override
	public MapView getMapView()
	{
		return map;
	}

	@Override
	public void setFollowing(boolean follow)
	{
		if (application.editingRoute == null && application.editingTrack == null)
		{
			if (showDistance > 0 && application.overlayManager.distanceOverlay != null)
			{
				if (showDistance == 2 && !follow)
				{
					application.overlayManager.distanceOverlay.setAncor(application.getLocation());
					application.overlayManager.distanceOverlay.setEnabled(true);
				}
				else
				{
					application.overlayManager.distanceOverlay.setEnabled(false);
				}
			}
			map.setFollowing(follow);
		}
	}

	@Override
	public void zoomMap(final float factor)
	{
	}

	@Override
	public void conditionsChanged()
	{
	}

	@Override
	public void mapChanged()
	{
	}

	public boolean waypointTapped(Waypoint waypoint, int x, int y)
	{
		try
		{
			if (application.editingRoute != null)
			{
				routeSelected = -1;
				waypointSelected = application.getWaypointIndex(waypoint);
				wptQuickAction.show(map, x, y);
			}
		}
		catch (Exception e)
		{
			return false;
		}
		return true;
	}

	/**
	 * Performs action on a tapped route waypoint.
	 * 
	 * @param route
	 *            route index
	 * @param index
	 *            waypoint index inside route
	 * @param x
	 *            view X coordinate
	 * @param y
	 *            view Y coordinate
	 * @return true if any action was performed
	 */
	public boolean routeWaypointTapped(int route, int index, int x, int y)
	{
		if (application.editingRoute != null && application.editingRoute == application.getRoute(route))
		{
			startActivityForResult(new Intent(this, WaypointProperties.class).putExtra("INDEX", index).putExtra("ROUTE", route + 1), RESULT_EDIT_ROUTE);
			return true;
		}
		else if (navigationService != null && navigationService.navRoute == application.getRoute(route))
		{
			// routeSelected = route;
			waypointSelected = index;
			rteQuickAction.show(map, x, y);
			return true;
		}
		else
		{
			startActivity(new Intent(this, RouteDetails.class).putExtra("INDEX", route));
			return true;
		}
	}

	@Override
	public boolean onPrepareOptionsMenu(final Menu menu)
	{
		if (application.editingRoute != null || application.editingTrack != null)
			return false;

		menu.findItem(R.id.menuSetAnchor).setVisible(showDistance > 0 && !map.isFollowing());

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.menuMapInfo:
				startActivity(new Intent(this, MapInformation.class));
				return true;
			case R.id.menuAllMaps:
				startActivityForResult(new Intent(this, MapList.class), RESULT_LOAD_MAP);
				return true;
			case R.id.menuSetAnchor:
				if (showDistance > 0)
				{
					application.overlayManager.distanceOverlay.setAncor(application.getMapCenter());
					application.overlayManager.distanceOverlay.setEnabled(true);
				}
				return true;
		}
		return false;
	}

	private OnActionItemClickListener waypointActionItemClickListener = new OnActionItemClickListener() {
		@Override
		public void onItemClick(QuickAction3D source, int pos, int actionId)
		{
			Waypoint wpt = application.getWaypoint(waypointSelected);

			switch (actionId)
			{
				case qaAddWaypointToRoute:
					application.routeEditingWaypoints.push(application.editingRoute.addWaypoint(wpt.name, wpt.latitude, wpt.longitude));
					map.invalidate();
					break;
			}
			waypointSelected = -1;
		}
	};

	private OnActionItemClickListener routeActionItemClickListener = new OnActionItemClickListener() {
		@Override
		public void onItemClick(QuickAction3D source, int pos, int actionId)
		{
			switch (actionId)
			{
				case qaNavigateToWaypoint:
					navigationService.setRouteWaypoint(waypointSelected);
					break;
			}
			waypointSelected = -1;
		}
	};

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);

		switch (requestCode)
		{
			case RESULT_SAVE_WAYPOINT:
			{
				if (resultCode == RESULT_OK)
				{
					application.overlayManager.waypointsOverlay.clearBitmapCache();
					application.saveWaypoints();
					if (data != null && data.hasExtra("index")
							&& PreferenceManager.getDefaultSharedPreferences(this).getBoolean(getString(R.string.pref_waypoint_visible), getResources().getBoolean(R.bool.def_waypoint_visible)))
						application.ensureVisible(application.getWaypoint(data.getIntExtra("index", -1)));
				}
				break;
			}
			case RESULT_MANAGE_TRACKS:
				if (resultCode == RESULT_OK)
				{
					Bundle extras = data.getExtras();
					int index = extras.getInt("index");
					startEditTrack(application.getTrack(index));
				}
				break;
			case RESULT_EDIT_ROUTE:
				for (Iterator<RouteOverlay> iter = application.overlayManager.routeOverlays.iterator(); iter.hasNext();)
				{
					RouteOverlay ro = iter.next();
					if (ro.getRoute().editing)
						ro.onRoutePropertiesChanged();
				}
				break;
			case RESULT_LOAD_MAP:
				if (resultCode == RESULT_OK)
				{
					Bundle extras = data.getExtras();
					final int id = extras.getInt("id");
					synchronized (map)
					{
						application.loadMap(id);
						map.suspendBestMap();
						setFollowing(false);
						map.updateMapInfo();
						map.update();
					}
				}
				break;
		}
	}

	@Override
	public void onClick(View v)
	{
		switch (v.getId())
		{
			case R.id.expand:
				ImageButton expand = (ImageButton) findViewById(R.id.expand);
				if (isFullscreen)
				{
					getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
					expand.setImageDrawable(getResources().getDrawable(R.drawable.expand));
				}
				else
				{
					getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
					expand.setImageDrawable(getResources().getDrawable(R.drawable.collapse));
				}
				isFullscreen = !isFullscreen;
				break;
			case R.id.cutbefore:
				application.editingTrack.cutBefore(trackBar.getProgress());
				int nb = application.editingTrack.getPoints().size() - 1;
				trackBar.setMax(nb);
				trackBar.setProgress(0);
				break;
			case R.id.cutafter:
				application.editingTrack.cutAfter(trackBar.getProgress());
				int na = application.editingTrack.getPoints().size() - 1;
				trackBar.setMax(na);
				trackBar.setProgress(0);
				trackBar.setProgress(na);
				break;
			case R.id.addpoint:
				double[] aloc = application.getMapCenter();
				application.routeEditingWaypoints.push(application.editingRoute.addWaypoint("RWPT" + application.editingRoute.length(), aloc[0], aloc[1]));
				break;
			case R.id.insertpoint:
				double[] iloc = application.getMapCenter();
				application.routeEditingWaypoints.push(application.editingRoute.insertWaypoint("RWPT" + application.editingRoute.length(), iloc[0], iloc[1]));
				break;
			case R.id.removepoint:
				if (!application.routeEditingWaypoints.empty())
				{
					application.editingRoute.removeWaypoint(application.routeEditingWaypoints.pop());
				}
				break;
			case R.id.orderpoints:
				startActivityForResult(new Intent(this, RouteEdit.class).putExtra("INDEX", application.getRouteIndex(application.editingRoute)), RESULT_EDIT_ROUTE);
				break;
			case R.id.finishedit:
				if ("New route".equals(application.editingRoute.name))
				{
					SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd_HH-mm");
					application.editingRoute.name = formatter.format(new Date());
				}
				application.editingRoute.editing = false;
				for (Iterator<RouteOverlay> iter = application.overlayManager.routeOverlays.iterator(); iter.hasNext();)
				{
					RouteOverlay ro = iter.next();
					ro.onRoutePropertiesChanged();
				}
				application.editingRoute = null;
				application.routeEditingWaypoints = null;
				findViewById(R.id.editroute).setVisibility(View.GONE);
				//updateGPSStatus();
				if (showDistance == 2)
				{
					application.overlayManager.distanceOverlay.setEnabled(true);
				}
				//updateMapViewArea();
				map.requestFocus();
				break;
			case R.id.finishtrackedit:
				application.editingTrack.editing = false;
				application.editingTrack.editingPos = -1;
				application.editingTrack = null;
				findViewById(R.id.edittrack).setVisibility(View.GONE);
				findViewById(R.id.trackdetails).setVisibility(View.GONE);
				//updateGPSStatus();
				if (showDistance == 2)
				{
					application.overlayManager.distanceOverlay.setEnabled(true);
				}
				map.setFocusable(true);
				map.setFocusableInTouchMode(true);
				map.requestFocus();
				break;
		}
	}

	@Override
	public void onWaypointView(Waypoint waypoint)
	{
	}

	@Override
	public void onWaypointShow(Waypoint waypoint)
	{
	}

	@Override
	public void onWaypointNavigate(final Waypoint waypoint)
	{
	}

	@Override
	public void onWaypointEdit(final Waypoint waypoint)
	{
	}

	@Override
	public void onWaypointShare(final Waypoint waypoint)
	{
	}

	@Override
	public void onWaypointRemove(final Waypoint waypoint)
	{
	}

	@Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
	{
		switch (seekBar.getId())
		{
			case R.id.trackbar:
				if (fromUser)
				{
					application.editingTrack.editingPos = progress;
				}
				Track.TrackPoint tp = application.editingTrack.getPoint(progress);
				double ele = tp.elevation * elevationFactor;
				((TextView) findViewById(R.id.tp_number)).setText("#" + (progress + 1));
				// FIXME Need UTM support here
				((TextView) findViewById(R.id.tp_latitude)).setText(StringFormatter.coordinate(application.coordinateFormat, tp.latitude));
				((TextView) findViewById(R.id.tp_longitude)).setText(StringFormatter.coordinate(application.coordinateFormat, tp.longitude));
				((TextView) findViewById(R.id.tp_elevation)).setText(String.valueOf(Math.round(ele)) + " " + elevationAbbr);
				((TextView) findViewById(R.id.tp_time)).setText(SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.SHORT, SimpleDateFormat.SHORT).format(new Date(tp.time)));
				boolean mapChanged = application.setMapCenter(tp.latitude, tp.longitude, false, false);
				if (mapChanged)
					map.updateMapInfo();
				map.update();
				break;
		}
	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar)
	{
	}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar)
	{
	}

	@Override
	public void updateCoordinates(double[] latlon)
	{
	}

	@Override
	public void updateFileInfo()
	{
	}

	@Override
	public boolean mapObjectTapped(long id, int x, int y)
	{
		return false;
	}
}
