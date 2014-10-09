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
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.Stack;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.londatiga.android.ActionItem;
import net.londatiga.android.QuickAction3D;
import net.londatiga.android.QuickAction3D.OnActionItemClickListener;

import org.miscwidgets.widget.Panel;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.LightingColorFilter;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.androzic.data.Route;
import com.androzic.data.Track;
import com.androzic.data.Waypoint;
import com.androzic.location.ILocationService;
import com.androzic.location.LocationService;
import com.androzic.map.MapInformation;
import com.androzic.navigation.NavigationService;
import com.androzic.overlay.RouteOverlay;
import com.androzic.route.RouteDetails;
import com.androzic.route.RouteEdit;
import com.androzic.util.Astro;
import com.androzic.util.StringFormatter;
import com.androzic.waypoint.OnWaypointActionListener;
import com.androzic.waypoint.WaypointInfo;
import com.androzic.waypoint.WaypointProperties;

public class MapActivity extends ActionBarActivity implements MapHolder, View.OnClickListener, OnSharedPreferenceChangeListener, OnWaypointActionListener, SeekBar.OnSeekBarChangeListener
{
	private static final String TAG = "MapActivity";

	private static final int RESULT_SAVE_WAYPOINT = 0x400;
	private static final int RESULT_LOAD_MAP = 0x500;
	private static final int RESULT_MANAGE_TRACKS = 0x600;
	private static final int RESULT_EDIT_ROUTE = 0x110;

	private static final int qaAddWaypointToRoute = 1;
	private static final int qaNavigateToWaypoint = 2;
	private static final int qaNavigateToMapObject = 2;

	// main preferences
	protected String precisionFormat = "%.0f";
	protected double speedFactor;
	protected String speedAbbr;
	protected double elevationFactor;
	protected String elevationAbbr;
	protected int renderInterval;
	protected int magInterval;
	protected boolean autoDim;
	protected int dimInterval;
	protected int dimValue;
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
	protected QuickAction3D mobQuickAction;
	private ViewGroup dimView;

	protected Androzic application;

	protected ExecutorService executorThread = Executors.newSingleThreadExecutor();

	private int waypointSelected = -1;
	private int routeSelected = -1;
	private long mapObjectSelected = -1;

	private ILocationService locationService = null;
	public NavigationService navigationService = null;

	private Location lastKnownLocation;
	protected long lastRenderTime = 0;
	protected long lastDim = 0;
	protected long lastMagnetic = 0;
	private boolean lastGeoid = true;

	private boolean isFullscreen;
	private boolean keepScreenOn;
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
		setRequestedOrientation(Integer.parseInt(settings.getString(getString(R.string.pref_orientation), "-1")));
		settings.registerOnSharedPreferenceChangeListener(this);
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

		mobQuickAction = new QuickAction3D(this, QuickAction3D.VERTICAL);
		mobQuickAction.addActionItem(new ActionItem(qaNavigateToMapObject, getString(R.string.menu_navigate), resources.getDrawable(R.drawable.ic_action_directions)));
		mobQuickAction.setOnActionItemClickListener(mapObjectActionItemClickListener);

		trackBar.setOnSeekBarChangeListener(this);

		map.initialize(application, this);

		dimView = new RelativeLayout(this);

		// set activity preferences
		onSharedPreferenceChanged(settings, getString(R.string.pref_exit));
		onSharedPreferenceChanged(settings, getString(R.string.pref_unitprecision));
		// set map preferences
		onSharedPreferenceChanged(settings, getString(R.string.pref_mapadjacent));
		onSharedPreferenceChanged(settings, getString(R.string.pref_mapcropborder));
		onSharedPreferenceChanged(settings, getString(R.string.pref_mapdrawborder));
		onSharedPreferenceChanged(settings, getString(R.string.pref_cursorcolor));
		onSharedPreferenceChanged(settings, getString(R.string.pref_grid_mapshow));
		onSharedPreferenceChanged(settings, getString(R.string.pref_grid_usershow));
		onSharedPreferenceChanged(settings, getString(R.string.pref_grid_preference));
		onSharedPreferenceChanged(settings, getString(R.string.pref_panelactions));

		if (getIntent().getExtras() != null)
			onNewIntent(getIntent());

		ready = true;
	}

	@Override
	protected void onStart()
	{
		super.onStart();
		Log.e(TAG, "onStart()");
		((ViewGroup) getWindow().getDecorView()).addView(dimView);
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
		autoDim = settings.getBoolean(getString(R.string.pref_mapdim), resources.getBoolean(R.bool.def_mapdim));
		dimInterval = settings.getInt(getString(R.string.pref_mapdiminterval), resources.getInteger(R.integer.def_mapdiminterval)) * 1000;
		dimValue = settings.getInt(getString(R.string.pref_mapdimvalue), resources.getInteger(R.integer.def_mapdimvalue));

		map.setHideOnDrag(settings.getBoolean(getString(R.string.pref_maphideondrag), resources.getBoolean(R.bool.def_maphideondrag)));
		map.setStrictUnfollow(!settings.getBoolean(getString(R.string.pref_unfollowontap), resources.getBoolean(R.bool.def_unfollowontap)));
		map.setLookAhead(settings.getInt(getString(R.string.pref_lookahead), resources.getInteger(R.integer.def_lookahead)));
		map.setBestMapEnabled(settings.getBoolean(getString(R.string.pref_mapbest), resources.getBoolean(R.bool.def_mapbest)));
		map.setBestMapInterval(settings.getInt(getString(R.string.pref_mapbestinterval), resources.getInteger(R.integer.def_mapbestinterval)) * 1000);
		map.setCursorVector(Integer.parseInt(settings.getString(getString(R.string.pref_cursorvector), getString(R.string.def_cursorvector))),
				settings.getInt(getString(R.string.pref_cursorvectormlpr), resources.getInteger(R.integer.def_cursorvectormlpr)));
		map.setProximity(Integer.parseInt(settings.getString(getString(R.string.pref_navigation_proximity), getString(R.string.def_navigation_proximity))));

		// prepare views
		findViewById(R.id.editroute).setVisibility(application.editingRoute != null ? View.VISIBLE : View.GONE);
		if (application.editingTrack != null)
		{
			startEditTrack(application.editingTrack);
		}

		if (settings.getBoolean(getString(R.string.ui_drawer_open), false))
		{
			Panel panel = (Panel) findViewById(R.id.panel);
			panel.setOpen(true, false);
		}

		onSharedPreferenceChanged(settings, getString(R.string.pref_wakelock));
		map.setKeepScreenOn(keepScreenOn);

		// TODO move into application
		if (lastKnownLocation != null)
		{
			if (lastKnownLocation.getProvider().equals(LocationManager.GPS_PROVIDER))
			{
				dimScreen(lastKnownLocation);
			}
			else if (lastKnownLocation.getProvider().equals(LocationManager.NETWORK_PROVIDER))
			{
				dimScreen(lastKnownLocation);
			}
		}

		registerReceiver(broadcastReceiver, new IntentFilter(LocationService.BROADCAST_LOCATING_STATUS));
		registerReceiver(broadcastReceiver, new IntentFilter(LocationService.BROADCAST_TRACKING_STATUS));

		application.updateLocationMaps(true, map.isBestMapEnabled());

		map.resume();
		map.updateMapInfo();
		map.update();
		map.requestFocus();
	}

	@Override
	protected void onPause()
	{
		super.onPause();
		Log.e(TAG, "onPause()");

		unregisterReceiver(broadcastReceiver);
		map.pause();
	}

	@Override
	protected void onStop()
	{
		super.onStop();
		Log.e(TAG, "onStop()");
		((ViewGroup) getWindow().getDecorView()).removeView(dimView);
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		Log.e(TAG, "onDestroy()");
		ready = false;

		PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);

		if (isFinishing() && !restarting)
		{
			application.clear();
		}

		restarting = false;

		application = null;
		map = null;
	}

	private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent)
		{
			String action = intent.getAction();
			Log.e(TAG, "Broadcast: " + action);
			if (action.equals(LocationService.BROADCAST_TRACKING_STATUS))
			{
				//updateMapButtons();
			}
			else if (action.equals(LocationService.BROADCAST_LOCATING_STATUS))
			{
				//updateMapButtons();
				if (locationService != null && !locationService.isLocating())
					map.clearLocation();
			}
		}
	};

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

	protected void dimScreen(Location location)
	{
		int color = Color.TRANSPARENT;
		Calendar now = GregorianCalendar.getInstance(TimeZone.getDefault());
		if (autoDim && !Astro.isDaytime(application.getZenith(), location, now))
			color = dimValue << 57; // value * 2 and shifted to transparency octet
		dimView.setBackgroundColor(color);
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
				return true;
			}
			else
			{
				Location loc = application.getLocationAsLocation();
		        FragmentManager fm = getSupportFragmentManager();
		        WaypointInfo waypointInfo = (WaypointInfo) fm.findFragmentByTag("waypoint_info");
		        if (waypointInfo == null)
		        	waypointInfo = new WaypointInfo();
		        waypointInfo.setWaypoint(waypoint);
				Bundle args = new Bundle();
				args.putDouble("lat", loc.getLatitude());
				args.putDouble("lon", loc.getLongitude());
				waypointInfo.setArguments(args);
				waypointInfo.show(fm, "waypoint_info");
				return true;
			}
		}
		catch (Exception e)
		{
			return false;
		}
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

	public boolean mapObjectTapped(long id, int x, int y)
	{
		mapObjectSelected = id;
		mobQuickAction.show(map, x, y);
		return true;
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

	private OnActionItemClickListener mapObjectActionItemClickListener = new OnActionItemClickListener() {
		@Override
		public void onItemClick(QuickAction3D source, int pos, int actionId)
		{
			switch (actionId)
			{
				case qaNavigateToMapObject:
					navigationService.navigateTo(application.getMapObject(mapObjectSelected));
					break;
			}
			mapObjectSelected = -1;
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
	protected void onRestoreInstanceState(Bundle savedInstanceState)
	{
		super.onRestoreInstanceState(savedInstanceState);
		Log.e(TAG, "onRestoreInstanceState()");
		lastKnownLocation = savedInstanceState.getParcelable("lastKnownLocation");
		lastRenderTime = savedInstanceState.getLong("lastRenderTime");
		lastMagnetic = savedInstanceState.getLong("lastMagnetic");
		lastDim = savedInstanceState.getLong("lastDim");
		lastGeoid = savedInstanceState.getBoolean("lastGeoid");

		waypointSelected = savedInstanceState.getInt("waypointSelected");
		routeSelected = savedInstanceState.getInt("routeSelected");
		mapObjectSelected = savedInstanceState.getLong("mapObjectSelected");

		/*
		 * double[] distAncor = savedInstanceState.getDoubleArray("distAncor");
		 * if (distAncor != null)
		 * {
		 * application.distanceOverlay = new DistanceOverlay(this);
		 * application.distanceOverlay.setAncor(distAncor);
		 * }
		 */
	}

	@Override
	protected void onSaveInstanceState(Bundle outState)
	{
		super.onSaveInstanceState(outState);
		Log.e(TAG, "onSaveInstanceState()");
		outState.putParcelable("lastKnownLocation", lastKnownLocation);
		outState.putLong("lastRenderTime", lastRenderTime);
		outState.putLong("lastMagnetic", lastMagnetic);
		outState.putLong("lastDim", lastDim);
		outState.putBoolean("lastGeoid", lastGeoid);

		outState.putInt("waypointSelected", waypointSelected);
		outState.putInt("routeSelected", routeSelected);
		outState.putLong("mapObjectSelected", mapObjectSelected);

		if (application.overlayManager.distanceOverlay != null)
		{
			outState.putDoubleArray("distAncor", application.overlayManager.distanceOverlay.getAncor());
		}
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
	{
		Resources resources = getResources();
		// application preferences
		if (getString(R.string.pref_orientation).equals(key))
		{
			setRequestedOrientation(Integer.parseInt(sharedPreferences.getString(key, "-1")));
		}
		// activity preferences
		else if (getString(R.string.pref_wakelock).equals(key))
		{
			keepScreenOn = sharedPreferences.getBoolean(key, resources.getBoolean(R.bool.def_wakelock));
			android.view.Window wnd = getWindow();
			if (wnd != null)
			{
				if (keepScreenOn)
					wnd.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
				else
					wnd.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
			}
		}
		else if (getString(R.string.pref_exit).equals(key))
		{
			exitConfirmation = Integer.parseInt(sharedPreferences.getString(key, "0"));
			//secondBack = false;
		}
		else if (getString(R.string.pref_unitprecision).equals(key))
		{
			boolean precision = sharedPreferences.getBoolean(key, resources.getBoolean(R.bool.def_unitprecision));
			precisionFormat = precision ? "%.1f" : "%.0f";
		}
		// map preferences
		else if (getString(R.string.pref_cursorcolor).equals(key))
		{
			map.setCursorColor(sharedPreferences.getInt(key, resources.getColor(R.color.cursor)));
		}
	}

	@Override
	public void updateCoordinates(double[] latlon)
	{
	}

	@Override
	public void updateFileInfo()
	{
	}
}
