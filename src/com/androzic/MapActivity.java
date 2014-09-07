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

import java.io.File;
import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.londatiga.android.ActionItem;
import net.londatiga.android.QuickAction3D;
import net.londatiga.android.QuickAction3D.OnActionItemClickListener;

import org.miscwidgets.interpolator.EasingType.Type;
import org.miscwidgets.interpolator.ExpoInterpolator;
import org.miscwidgets.widget.Panel;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.LightingColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.AnimationSet;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.androzic.data.MapObject;
import com.androzic.data.Route;
import com.androzic.data.Track;
import com.androzic.data.Waypoint;
import com.androzic.data.WaypointSet;
import com.androzic.location.ILocationListener;
import com.androzic.location.ILocationService;
import com.androzic.location.LocationService;
import com.androzic.map.MapInformation;
import com.androzic.navigation.NavigationService;
import com.androzic.overlay.NavigationOverlay;
import com.androzic.overlay.RouteOverlay;
import com.androzic.overlay.TrackOverlay;
import com.androzic.route.RouteDetails;
import com.androzic.route.RouteEdit;
import com.androzic.track.TrackExportDialog;
import com.androzic.util.Astro;
import com.androzic.util.OziExplorerFiles;
import com.androzic.util.StringFormatter;
import com.androzic.waypoint.OnWaypointActionListener;
import com.androzic.waypoint.WaypointFileList;
import com.androzic.waypoint.WaypointInfo;
import com.androzic.waypoint.WaypointProject;
import com.androzic.waypoint.WaypointProperties;

public class MapActivity extends ActionBarActivity implements MapHolder, View.OnClickListener, OnSharedPreferenceChangeListener, OnWaypointActionListener, SeekBar.OnSeekBarChangeListener, Panel.OnPanelListener
{
	private static final String TAG = "MapActivity";

	private static final int RESULT_MANAGE_WAYPOINTS = 0x200;
	private static final int RESULT_LOAD_WAYPOINTS = 0x300;
	private static final int RESULT_SAVE_WAYPOINT = 0x400;
	private static final int RESULT_LOAD_MAP = 0x500;
	private static final int RESULT_MANAGE_TRACKS = 0x600;
	private static final int RESULT_MANAGE_ROUTES = 0x900;
	private static final int RESULT_EDIT_ROUTE = 0x110;
	private static final int RESULT_LOAD_MAP_ATPOSITION = 0x120;
	private static final int RESULT_SAVE_WAYPOINTS = 0x140;

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
	private boolean secondBack;
	private Toast backToast;

	private TextView coordinates;
	private TextView satInfo;

	private TextView waypointName;
	private TextView waypointExtra;
	private TextView routeName;
	private TextView routeExtra;

	private TextView distanceValue;
	private TextView distanceUnit;
	private TextView bearingValue;
	private TextView bearingUnit;
	private TextView turnValue;

	private TextView speedValue;
	private TextView speedUnit;
	private TextView trackValue;
	private TextView trackUnit;
	private TextView elevationValue;
	private TextView elevationUnit;
	private TextView xtkValue;
	private TextView xtkUnit;

	private TextView currentFile;
	private TextView mapZoom;

	protected SeekBar trackBar;
	protected TextView waitBar;
	protected MapView map;
	protected QuickAction3D wptQuickAction;
	protected QuickAction3D rteQuickAction;
	protected QuickAction3D mobQuickAction;
	private ViewGroup dimView;

	protected Androzic application;

	protected ExecutorService executorThread = Executors.newSingleThreadExecutor();
	private FinishHandler finishHandler;

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

	private boolean animationSet;
	private boolean isFullscreen;
	private boolean keepScreenOn;
	private String[] panelActions;
	private List<String> activeActions;
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

		backToast = Toast.makeText(this, R.string.backQuit, Toast.LENGTH_SHORT);
		finishHandler = new FinishHandler(this);
		
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

		panelActions = getResources().getStringArray(R.array.panel_action_values);

		setContentView(R.layout.act_main);
		coordinates = (TextView) findViewById(R.id.coordinates);
		satInfo = (TextView) findViewById(R.id.sats);
		currentFile = (TextView) findViewById(R.id.currentfile);
		mapZoom = (TextView) findViewById(R.id.currentzoom);
		waypointName = (TextView) findViewById(R.id.waypointname);
		waypointExtra = (TextView) findViewById(R.id.waypointextra);
		routeName = (TextView) findViewById(R.id.routename);
		routeExtra = (TextView) findViewById(R.id.routeextra);
		speedValue = (TextView) findViewById(R.id.speed);
		speedUnit = (TextView) findViewById(R.id.speedunit);
		trackValue = (TextView) findViewById(R.id.track);
		trackUnit = (TextView) findViewById(R.id.trackunit);
		elevationValue = (TextView) findViewById(R.id.elevation);
		elevationUnit = (TextView) findViewById(R.id.elevationunit);
		distanceValue = (TextView) findViewById(R.id.distance);
		distanceUnit = (TextView) findViewById(R.id.distanceunit);
		xtkValue = (TextView) findViewById(R.id.xtk);
		xtkUnit = (TextView) findViewById(R.id.xtkunit);
		bearingValue = (TextView) findViewById(R.id.bearing);
		bearingUnit = (TextView) findViewById(R.id.bearingunit);
		turnValue = (TextView) findViewById(R.id.turn);
		trackBar = (SeekBar) findViewById(R.id.trackbar);
		waitBar = (TextView) findViewById(R.id.waitbar);
		map = (MapView) findViewById(R.id.mapview);

		// set button actions
		findViewById(R.id.zoomin).setOnClickListener(this);
		findViewById(R.id.zoomout).setOnClickListener(this);
		findViewById(R.id.nextmap).setOnClickListener(this);
		findViewById(R.id.prevmap).setOnClickListener(this);
		findViewById(R.id.maps).setOnClickListener(this);
		findViewById(R.id.waypoints).setOnClickListener(this);
		findViewById(R.id.info).setOnClickListener(this);
		findViewById(R.id.follow).setOnClickListener(this);
		findViewById(R.id.locate).setOnClickListener(this);
		findViewById(R.id.tracking).setOnClickListener(this);
		findViewById(R.id.expand).setOnClickListener(this);
		findViewById(R.id.finishedit).setOnClickListener(this);
		findViewById(R.id.addpoint).setOnClickListener(this);
		findViewById(R.id.insertpoint).setOnClickListener(this);
		findViewById(R.id.removepoint).setOnClickListener(this);
		findViewById(R.id.orderpoints).setOnClickListener(this);
		findViewById(R.id.finishtrackedit).setOnClickListener(this);
		findViewById(R.id.cutafter).setOnClickListener(this);
		findViewById(R.id.cutbefore).setOnClickListener(this);

		Panel panel = (Panel) findViewById(R.id.panel);
		panel.setOnPanelListener(this);
		panel.setInterpolator(new ExpoInterpolator(Type.OUT));

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

		String navWpt = settings.getString(getString(R.string.nav_wpt), "");
		if (!"".equals(navWpt) && savedInstanceState == null)
		{
			Intent intent = new Intent(getApplicationContext(), NavigationService.class).setAction(NavigationService.NAVIGATE_MAPOBJECT);
			intent.putExtra(NavigationService.EXTRA_NAME, navWpt);
			intent.putExtra(NavigationService.EXTRA_LATITUDE, (double) settings.getFloat(getString(R.string.nav_wpt_lat), 0));
			intent.putExtra(NavigationService.EXTRA_LONGITUDE, (double) settings.getFloat(getString(R.string.nav_wpt_lon), 0));
			intent.putExtra(NavigationService.EXTRA_PROXIMITY, settings.getInt(getString(R.string.nav_wpt_prx), 0));
			startService(intent);
		}

		String navRoute = settings.getString(getString(R.string.nav_route), "");
		if (!"".equals(navRoute) && settings.getBoolean(getString(R.string.pref_navigation_loadlast), getResources().getBoolean(R.bool.def_navigation_loadlast)) && savedInstanceState == null)
		{
			int ndir = settings.getInt(getString(R.string.nav_route_dir), 0);
			int nwpt = settings.getInt(getString(R.string.nav_route_wpt), -1);
			try
			{
				int rt = -1;
				Route route = application.getRouteByFile(navRoute);
				if (route != null)
				{
					route.show = true;
					rt = application.getRouteIndex(route);
				}
				else
				{
					File rtf = new File(navRoute);
					// FIXME It's bad - it can be not a first route in a file
					route = OziExplorerFiles.loadRoutesFromFile(rtf, application.charset).get(0);
					rt = application.addRoute(route);
				}
				RouteOverlay newRoute = new RouteOverlay(route);
				application.overlayManager.routeOverlays.add(newRoute);
				startService(new Intent(this, NavigationService.class).setAction(NavigationService.NAVIGATE_ROUTE).putExtra(NavigationService.EXTRA_ROUTE_INDEX, rt).putExtra(NavigationService.EXTRA_ROUTE_DIRECTION, ndir).putExtra(NavigationService.EXTRA_ROUTE_START, nwpt));
			}
			catch (Exception e)
			{
				Log.e(TAG, "Failed to start navigation", e);
			}
		}

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
	protected void onNewIntent(Intent intent)
	{
		Log.e(TAG, "onNewIntent()");
		if (intent.hasExtra("launch"))
		{
			Serializable object = intent.getExtras().getSerializable("launch");
			if (Class.class.isInstance(object))
			{
				Intent launch = new Intent(this, (Class<?>) object);
				launch.putExtras(intent);
				launch.removeExtra("launch");
				startActivity(launch);
			}
		}
		else if (intent.hasExtra("lat") && intent.hasExtra("lon"))
		{
			Androzic application = (Androzic) getApplication();
			application.ensureVisible(intent.getExtras().getDouble("lat"), intent.getExtras().getDouble("lon"));
		}
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
		application.angleType = Integer.parseInt(settings.getString(getString(R.string.pref_unitangle), "0"));
		trackUnit.setText((application.angleType == 0 ? "deg" : getString(R.string.degmag)));
		bearingUnit.setText((application.angleType == 0 ? "deg" : getString(R.string.degmag)));
		application.coordinateFormat = Integer.parseInt(settings.getString(getString(R.string.pref_unitcoordinate), "0"));
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
		customizeLayout(settings);
		findViewById(R.id.editroute).setVisibility(application.editingRoute != null ? View.VISIBLE : View.GONE);
		if (application.editingTrack != null)
		{
			startEditTrack(application.editingTrack);
		}
		updateGPSStatus();
		updateNavigationStatus();

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
				updateMovingInfo(lastKnownLocation, true);
				updateNavigationInfo();
				dimScreen(lastKnownLocation);
			}
			else if (lastKnownLocation.getProvider().equals(LocationManager.NETWORK_PROVIDER))
			{
				dimScreen(lastKnownLocation);
			}
		}

		bindService(new Intent(this, LocationService.class), locationConnection, BIND_AUTO_CREATE);
		bindService(new Intent(this, NavigationService.class), navigationConnection, BIND_AUTO_CREATE);

		registerReceiver(broadcastReceiver, new IntentFilter(NavigationService.BROADCAST_NAVIGATION_STATUS));
		registerReceiver(broadcastReceiver, new IntentFilter(NavigationService.BROADCAST_NAVIGATION_STATE));
		registerReceiver(broadcastReceiver, new IntentFilter(LocationService.BROADCAST_LOCATING_STATUS));
		registerReceiver(broadcastReceiver, new IntentFilter(LocationService.BROADCAST_TRACKING_STATUS));
		registerReceiver(broadcastReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));
		registerReceiver(broadcastReceiver, new IntentFilter(Intent.ACTION_SCREEN_ON));

		application.updateLocationMaps(true, map.isBestMapEnabled());

		updateMapViewArea();
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

		// save active route
		Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
		editor.putString(getString(R.string.nav_route), "");
		editor.putString(getString(R.string.nav_wpt), "");
		if (navigationService != null)
		{
			if (navigationService.isNavigatingViaRoute())
			{
				Route route = navigationService.navRoute;
				if (route.filepath != null)
				{
					editor.putString(getString(R.string.nav_route), route.filepath);
					editor.putInt(getString(R.string.nav_route_idx), application.getRouteIndex(navigationService.navRoute));
					editor.putInt(getString(R.string.nav_route_dir), navigationService.navDirection);
					editor.putInt(getString(R.string.nav_route_wpt), navigationService.navCurrentRoutePoint);
				}
			}
			else if (navigationService.isNavigating())
			{
				MapObject wpt = navigationService.navWaypoint;
				editor.putString(getString(R.string.nav_wpt), wpt.name);
				editor.putInt(getString(R.string.nav_wpt_prx), wpt.proximity);
				editor.putFloat(getString(R.string.nav_wpt_lat), (float) wpt.latitude);
				editor.putFloat(getString(R.string.nav_wpt_lon), (float) wpt.longitude);
			}
		}
		editor.commit();

		if (navigationService != null)
		{
			unbindService(navigationConnection);
			navigationService = null;
		}
		if (locationService != null)
		{
			locationService.unregisterLocationCallback(locationListener);
			locationService = null;
		}
		unbindService(locationConnection);
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

		coordinates = null;
		satInfo = null;
		currentFile = null;
		mapZoom = null;
		waypointName = null;
		waypointExtra = null;
		routeName = null;
		routeExtra = null;
		speedValue = null;
		speedUnit = null;
		trackValue = null;
		elevationValue = null;
		elevationUnit = null;
		distanceValue = null;
		distanceUnit = null;
		xtkValue = null;
		xtkUnit = null;
		bearingValue = null;
		turnValue = null;
		trackBar = null;
	}

	private ServiceConnection navigationConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service)
		{
			navigationService = ((NavigationService.LocalBinder) service).getService();
			runOnUiThread(new Runnable() {
				public void run()
				{
					if (!ready)
						return;
					updateNavigationStatus();
					updateNavigationInfo();
				}
			});
			Log.d(TAG, "Navigation service connected");
		}

		public void onServiceDisconnected(ComponentName className)
		{
			navigationService = null;
			Log.d(TAG, "Navigation service disconnected");
		}
	};

	private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent)
		{
			String action = intent.getAction();
			Log.e(TAG, "Broadcast: " + action);
			if (action.equals(NavigationService.BROADCAST_NAVIGATION_STATE))
			{
				final int state = intent.getExtras().getInt("state");
				runOnUiThread(new Runnable() {
					public void run()
					{
						if (!ready)
							return;
						if (state == NavigationService.STATE_REACHED)
						{
							Toast.makeText(getApplicationContext(), R.string.arrived, Toast.LENGTH_LONG).show();
						}
						updateNavigationStatus();
					}
				});
			}
			else if (action.equals(NavigationService.BROADCAST_NAVIGATION_STATUS))
			{
				runOnUiThread(new Runnable() {
					public void run()
					{
						if (!ready)
							return;
						updateNavigationInfo();
					}
				});
			}
			else if (action.equals(LocationService.BROADCAST_TRACKING_STATUS))
			{
				updateMapButtons();
			}
			else if (action.equals(LocationService.BROADCAST_LOCATING_STATUS))
			{
				updateMapButtons();
				if (locationService != null && !locationService.isLocating())
					map.clearLocation();
			}
			// In fact this is not needed on modern devices through activity is always
			// paused when the screen is turned off. But we will keep it, may be there
			// exist some devices (ROMs) that do not pause activities.
			else if (action.equals(Intent.ACTION_SCREEN_OFF))
			{
				map.pause();
			}
			else if (action.equals(Intent.ACTION_SCREEN_ON))
			{
				map.resume();
			}
		}
	};

	private ServiceConnection locationConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder binder)
		{
			locationService = (ILocationService) binder;
			locationService.registerLocationCallback(locationListener);
			Log.d(TAG, "Location service connected");
		}

		public void onServiceDisconnected(ComponentName className)
		{
			locationService = null;
			Log.d(TAG, "Location service disconnected");
		}
	};

	private ILocationListener locationListener = new ILocationListener() {
		@Override
		public void onGpsStatusChanged(String provider, final int status, final int fsats, final int tsats)
		{
			if (LocationManager.GPS_PROVIDER.equals(provider))
			{
				runOnUiThread(new Runnable() {
					public void run()
					{
						if (!ready)
							return;
						switch (status)
						{
							case LocationService.GPS_OK:
								if (!map.isFixed())
								{
									satInfo.setTextColor(getResources().getColor(R.color.gpsworking));
									map.setMoving(true);
									map.setFixed(true);
									updateGPSStatus();
								}
								satInfo.setText(String.valueOf(fsats) + "/" + String.valueOf(tsats));
								break;
							case LocationService.GPS_OFF:
								satInfo.setText(R.string.sat_stop);
								satInfo.setTextColor(getResources().getColor(R.color.gpsdisabled));
								map.setMoving(false);
								map.setFixed(false);
								updateGPSStatus();
								break;
							case LocationService.GPS_SEARCHING:
								if (map.isFixed())
								{
									satInfo.setTextColor(getResources().getColor(R.color.gpsenabled));
									map.setFixed(false);
								}
								satInfo.setText(String.valueOf(fsats) + "/" + String.valueOf(tsats));
								break;
						}
					}
				});
			}
		}

		@Override
		public void onLocationChanged(final Location location, final boolean continous, final boolean geoid, final float smoothspeed, final float avgspeed)
		{
			if (!ready)
				return;

			Log.d(TAG, "Location arrived");

			final long lastLocationMillis = location.getTime();

			boolean magnetic = false;
			if (application.angleType == 1 && lastLocationMillis - lastMagnetic >= magInterval)
			{
				magnetic = true;
				lastMagnetic = lastLocationMillis;
			}

			// update map
			if (lastLocationMillis - lastRenderTime >= renderInterval)
			{
				lastRenderTime = lastLocationMillis;

				//application.setLocation(location, magnetic);
				map.setLocation(location);
				final boolean enableFollowing = followOnLocation && lastKnownLocation == null;

				lastKnownLocation = location;

				if (application.overlayManager.accuracyOverlay != null && location.hasAccuracy())
				{
					application.overlayManager.accuracyOverlay.setAccuracy(location.getAccuracy());
				}

				runOnUiThread(new Runnable() {
					public void run()
					{
						if (!LocationManager.GPS_PROVIDER.equals(location.getProvider()) && map.isMoving())
						{
							map.setMoving(false);
							updateGPSStatus();
						}
						// Mock provider hack
						if (!map.isFixed() && continous && LocationManager.GPS_PROVIDER.equals(location.getProvider()))
						{
							satInfo.setText(R.string.sat_start);
							satInfo.setTextColor(getResources().getColor(R.color.gpsworking));
							map.setMoving(true);
							map.setFixed(true);
							updateGPSStatus();
						}

						updateMovingInfo(location, geoid);

						if (enableFollowing)
							setFollowing(true);
						else
							map.update();

						// auto dim
						if (autoDim && dimInterval > 0 && lastLocationMillis - lastDim >= dimInterval)
						{
							dimScreen(location);
							lastDim = lastLocationMillis;
						}
					}
				});
			}
		}

		@Override
		public void onProviderChanged(String provider)
		{
		}

		@Override
		public void onProviderDisabled(String provider)
		{
			if (LocationManager.GPS_PROVIDER.equals(provider))
			{
				runOnUiThread(new Runnable() {
					public void run()
					{
						if (!ready)
							return;
						satInfo.setText(R.string.sat_stop);
						satInfo.setTextColor(getResources().getColor(R.color.gpsdisabled));
						map.setMoving(false);
						map.setFixed(false);
						updateGPSStatus();
					}
				});
			}
		}

		@Override
		public void onProviderEnabled(String provider)
		{
			if (LocationManager.GPS_PROVIDER.equals(provider))
			{
				runOnUiThread(new Runnable() {
					public void run()
					{
						if (!ready)
							return;
						if (!map.isFixed())
						{
							satInfo.setText(R.string.sat_start);
							satInfo.setTextColor(getResources().getColor(R.color.gpsenabled));
						}
					}
				});
			}
		}
	};

	private void updateMapViewArea()
	{
		final ViewTreeObserver vto = map.getViewTreeObserver();
		vto.addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
			public void onGlobalLayout()
			{
				Rect area = new Rect();
				map.getLocalVisibleRect(area);
				View v = findViewById(R.id.topbar);
				if (v != null)
					area.top = v.getBottom();
				v = findViewById(R.id.bottombar);
				if (v != null)
					area.bottom = v.getTop();
				v = findViewById(R.id.rightbar);
				if (v != null)
					area.right = v.getLeft();
				if (!area.isEmpty())
					map.updateViewArea(area);
				if (vto.isAlive())
				{
					vto.removeGlobalOnLayoutListener(this);
				}
				else
				{
					final ViewTreeObserver vto1 = map.getViewTreeObserver();
					vto1.removeGlobalOnLayoutListener(this);
				}
			}
		});
	}

	public void updateMap()
	{
		if (map != null)
			map.postInvalidate();
	}

	private final void updateMapButtons()
	{
		ViewGroup container = (ViewGroup) findViewById(R.id.button_container);

		for (String action : panelActions)
		{
			int id = getResources().getIdentifier(action, "id", getPackageName());
			ImageButton aib = (ImageButton) container.findViewById(id);
			if (aib != null)
			{
				if (activeActions.contains(action))
				{
					aib.setVisibility(View.VISIBLE);
					switch (id)
					{
						case R.id.follow:
							aib.setImageDrawable(getResources().getDrawable(map.isFollowing() ? R.drawable.cursor_drag_arrow : R.drawable.target));
							break;
						case R.id.locate:
							boolean isLocating = locationService != null && locationService.isLocating();
							aib.setImageDrawable(getResources().getDrawable(isLocating ? R.drawable.pin_map_no : R.drawable.pin_map));
							break;
						case R.id.tracking:
							boolean isTracking = locationService != null && locationService.isTracking();
							aib.setImageDrawable(getResources().getDrawable(isTracking ? R.drawable.doc_delete : R.drawable.doc_edit));
							break;
					}
				}
				else
				{
					aib.setVisibility(View.GONE);
				}
			}
		}
	}

	public final void updateCoordinates(final double[] latlon)
	{
		// TODO strange situation, needs investigation
		if (application != null)
		{
			final String pos = StringFormatter.coordinates(application.coordinateFormat, " ", latlon[0], latlon[1]);
			this.runOnUiThread(new Runnable() {

				@Override
				public void run()
				{
					coordinates.setText(pos);
				}
			});
		}
	}

	public final void updateFileInfo()
	{
		final String title = application.getMapTitle();
		this.runOnUiThread(new Runnable() {

			@Override
			public void run()
			{
				if (title != null)
				{
					currentFile.setText(title);
				}
				else
				{
					currentFile.setText("-no map-");
				}

				updateZoomInfo();
			}
		});
	}

	protected final void updateZoomInfo()
	{
		double zoom = application.getZoom() * 100;

		if (zoom == 0.0)
		{
			mapZoom.setText("---%");
		}
		else
		{
			int rz = (int) Math.floor(zoom);
			String zoomStr = zoom - rz != 0.0 ? String.format("%.1f", zoom) : String.valueOf(rz);
			mapZoom.setText(zoomStr + "%");
		}

		ImageButton zoomin = (ImageButton) findViewById(R.id.zoomin);
		ImageButton zoomout = (ImageButton) findViewById(R.id.zoomout);
		zoomin.setEnabled(application.getNextZoom() != 0.0);
		zoomout.setEnabled(application.getPrevZoom() != 0.0);

		LightingColorFilter disable = new LightingColorFilter(0xFFFFFFFF, 0xFF444444);

		zoomin.setColorFilter(zoomin.isEnabled() ? null : disable);
		zoomout.setColorFilter(zoomout.isEnabled() ? null : disable);
	}

	protected void updateGPSStatus()
	{
		int v = map.isMoving() && application.editingRoute == null && application.editingTrack == null ? View.VISIBLE : View.GONE;
		View view = findViewById(R.id.movinginfo);
		if (view.getVisibility() != v)
		{
			view.setVisibility(v);
			updateMapViewArea();
		}
	}

	protected void updateNavigationStatus()
	{
		boolean isNavigating = navigationService != null && navigationService.isNavigating();
		boolean isNavigatingViaRoute = isNavigating && navigationService.isNavigatingViaRoute();

		// waypoint panel
		findViewById(R.id.waypointinfo).setVisibility(isNavigating ? View.VISIBLE : View.GONE);
		// route panel
		findViewById(R.id.routeinfo).setVisibility(isNavigatingViaRoute ? View.VISIBLE : View.GONE);
		// distance
		distanceValue.setVisibility(isNavigating ? View.VISIBLE : View.GONE);
		findViewById(R.id.distancelt).setVisibility(isNavigating ? View.VISIBLE : View.GONE);
		// bearing
		bearingValue.setVisibility(isNavigating ? View.VISIBLE : View.GONE);
		findViewById(R.id.bearinglt).setVisibility(isNavigating ? View.VISIBLE : View.GONE);
		// turn
		turnValue.setVisibility(isNavigating ? View.VISIBLE : View.GONE);
		findViewById(R.id.turnlt).setVisibility(isNavigating ? View.VISIBLE : View.GONE);
		// xtk
		xtkValue.setVisibility(isNavigatingViaRoute ? View.VISIBLE : View.GONE);
		findViewById(R.id.xtklt).setVisibility(isNavigatingViaRoute ? View.VISIBLE : View.GONE);

		// we hide elevation in portrait mode due to lack of space
		if (getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE)
		{
			if (isNavigatingViaRoute && elevationValue.getVisibility() == View.VISIBLE)
			{
				elevationValue.setVisibility(View.GONE);
				findViewById(R.id.elevationlt).setVisibility(View.GONE);

				ViewGroup row = (ViewGroup) findViewById(R.id.movingrow);
				int pos = row.indexOfChild(elevationValue);
				View xtklt = findViewById(R.id.xtklt);
				row.removeView(xtkValue);
				row.removeView(xtklt);
				row.addView(xtklt, pos);
				row.addView(xtkValue, pos);
				row.getParent().requestLayout();
			}
			else if (!isNavigatingViaRoute && elevationValue.getVisibility() == View.GONE)
			{
				elevationValue.setVisibility(View.VISIBLE);
				findViewById(R.id.elevationlt).setVisibility(View.VISIBLE);

				ViewGroup row = (ViewGroup) findViewById(R.id.movingrow);
				int pos = row.indexOfChild(xtkValue);
				View elevationlt = findViewById(R.id.elevationlt);
				row.removeView(elevationValue);
				row.removeView(elevationlt);
				row.addView(elevationlt, pos);
				row.addView(elevationValue, pos);
				row.getParent().requestLayout();
			}
		}

		if (isNavigatingViaRoute)
		{
			routeName.setText("› " + navigationService.navRoute.name);
		}
		if (isNavigating)
		{
			waypointName.setText("» " + navigationService.navWaypoint.name);
			if (application.overlayManager.navigationOverlay == null)
			{
				application.overlayManager.navigationOverlay = new NavigationOverlay();
				application.overlayManager.navigationOverlay.onMapChanged();
			}
		}
		else if (application.overlayManager.navigationOverlay != null)
		{
			application.overlayManager.navigationOverlay.onBeforeDestroy();
			application.overlayManager.navigationOverlay = null;
		}

		updateMapViewArea();
		map.update();
	}

	protected void updateNavigationInfo()
	{
		if (navigationService == null || !navigationService.isNavigating())
			return;

		double distance = navigationService.navDistance;
		double bearing = navigationService.navBearing;
		long turn = navigationService.navTurn;
		double vmg = navigationService.navVMG * speedFactor;
		int ete = navigationService.navETE;

		String[] dist = StringFormatter.distanceC(distance, precisionFormat);
		String extra = String.format(precisionFormat, vmg) + " " + speedAbbr + " | " + StringFormatter.timeH(ete);

		String trnsym = "";
		if (turn > 0)
		{
			trnsym = "R";
		}
		else if (turn < 0)
		{
			trnsym = "L";
			turn = -turn;
		}

		bearing = application.fixDeclination(bearing);
		distanceValue.setText(dist[0]);
		distanceUnit.setText(dist[1]);
		bearingValue.setText(String.valueOf(Math.round(bearing)));
		turnValue.setText(String.valueOf(Math.round(turn)) + trnsym);
		waypointExtra.setText(extra);

		if (navigationService.isNavigatingViaRoute())
		{
			boolean hasNext = navigationService.hasNextRouteWaypoint();
			if (distance < navigationService.navProximity * 3 && !animationSet)
			{
				AnimationSet animation = new AnimationSet(true);
				animation.addAnimation(new AlphaAnimation(1.0f, 0.3f));
				animation.addAnimation(new AlphaAnimation(0.3f, 1.0f));
				animation.setDuration(500);
				animation.setRepeatCount(10);
				findViewById(R.id.waypointinfo).startAnimation(animation);
				if (!hasNext)
				{
					findViewById(R.id.routeinfo).startAnimation(animation);
				}
				animationSet = true;
			}
			else if (animationSet)
			{
				findViewById(R.id.waypointinfo).setAnimation(null);
				if (!hasNext)
				{
					findViewById(R.id.routeinfo).setAnimation(null);
				}
				animationSet = false;
			}

			if (navigationService.navXTK == Double.NEGATIVE_INFINITY)
			{
				xtkValue.setText("--");
				xtkUnit.setText("--");
			}
			else
			{
				String xtksym = navigationService.navXTK == 0 ? "" : navigationService.navXTK > 0 ? "R" : "L";
				String[] xtks = StringFormatter.distanceC(Math.abs(navigationService.navXTK));
				xtkValue.setText(xtks[0] + xtksym);
				xtkUnit.setText(xtks[1]);
			}

			double navDistance = navigationService.navRouteDistanceLeft();
			int eta = navigationService.navRouteETE(navDistance);
			if (eta < Integer.MAX_VALUE)
				eta += navigationService.navETE;
			extra = StringFormatter.distanceH(navDistance + distance, 1000) + " | " + StringFormatter.timeH(eta);
			routeExtra.setText(extra);
		}
	}

	protected void updateMovingInfo(final Location location, final boolean geoid)
	{
		double s = location.getSpeed() * speedFactor;
		double e = location.getAltitude() * elevationFactor;
		double track = application.fixDeclination(location.getBearing());
		speedValue.setText(String.format(precisionFormat, s));
		trackValue.setText(String.valueOf(Math.round(track)));
		elevationValue.setText(String.valueOf(Math.round(e)));
		// TODO set separate color
		if (geoid != lastGeoid)
		{
			int color = geoid ? 0xffffffff : getResources().getColor(R.color.gpsenabled);
			elevationValue.setTextColor(color);
			elevationUnit.setTextColor(color);
			((TextView) findViewById(R.id.elevationname)).setTextColor(color);
			lastGeoid = geoid;
		}
	}

	private final void customizeLayout(final SharedPreferences settings)
	{
		boolean slVisible = settings.getBoolean(getString(R.string.pref_showsatinfo), true);
		boolean mlVisible = settings.getBoolean(getString(R.string.pref_showmapinfo), true);

		findViewById(R.id.satinfo).setVisibility(slVisible ? View.VISIBLE : View.GONE);
		findViewById(R.id.mapinfo).setVisibility(mlVisible ? View.VISIBLE : View.GONE);

		updateMapViewArea();
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
		updateGPSStatus();
		if (showDistance > 0)
			application.overlayManager.distanceOverlay.setEnabled(false);
		map.setFocusable(false);
		map.setFocusableInTouchMode(false);
		trackBar.requestFocus();
		updateMapViewArea();
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
		updateGPSStatus();
		application.routeEditingWaypoints = new Stack<Waypoint>();
		if (showDistance > 0)
			application.overlayManager.distanceOverlay.setEnabled(false);
		updateMapViewArea();
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

	public void zoomMap(final float factor)
	{
		waitBar.setVisibility(View.VISIBLE);
		waitBar.setText(R.string.msg_wait);
		executorThread.execute(new Runnable() {
			public void run()
			{
				synchronized (map)
				{
					if (application.zoomBy(factor))
						conditionsChanged();
				}
				finishHandler.sendEmptyMessage(0);
			}
		});
	}

	@Override
	public void zoomIn()
	{
		//TODO Show toast here
		if (application.getNextZoom() == 0.0)
			return;
		waitBar.setVisibility(View.VISIBLE);
		waitBar.setText(R.string.msg_wait);
		executorThread.execute(new Runnable() {
			public void run()
			{
				synchronized (map)
				{
					if (application.zoomIn())
						conditionsChanged();
				}
				finishHandler.sendEmptyMessage(0);
			}
		});
	}

	@Override
	public void zoomOut()
	{
		if (application.getPrevZoom() == 0.0)
			return;
		waitBar.setVisibility(View.VISIBLE);
		waitBar.setText(R.string.msg_wait);
		executorThread.execute(new Runnable() {
			public void run()
			{
				synchronized (map)
				{
					if (application.zoomOut())
						conditionsChanged();
				}
				finishHandler.sendEmptyMessage(0);
			}
		});
	}

	@Override
	public void previousMap()
	{
		waitBar.setVisibility(View.VISIBLE);
		waitBar.setText(R.string.msg_wait);
		executorThread.execute(new Runnable() {
			public void run()
			{
				synchronized (map)
				{
					if (application.prevMap())
						mapChanged();
				}
				finishHandler.sendEmptyMessage(0);
			}
		});
	}

	@Override
	public void nextMap()
	{
		waitBar.setVisibility(View.VISIBLE);
		waitBar.setText(R.string.msg_wait);
		executorThread.execute(new Runnable() {
			public void run()
			{
				synchronized (map)
				{
					if (application.nextMap())
						mapChanged();
				}
				finishHandler.sendEmptyMessage(0);
			}
		});
	}


	@Override
	public void conditionsChanged()
	{
		map.updateMapInfo();
		map.update();
	}

	@Override
	public void mapChanged()
	{
		map.suspendBestMap();
		map.updateMapInfo();
		map.update();
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
	public boolean onCreateOptionsMenu(final Menu menu)
	{
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.options_menu, menu);

		// add plugins
		SubMenu views = menu.findItem(R.id.menuView).getSubMenu();
		Map<String, Pair<Drawable, Intent>> plugins = application.getPluginsViews();
		for (String plugin : plugins.keySet())
		{
			MenuItem item = views.add(plugin);
			item.setIntent(plugins.get(plugin).second);
			if (plugins.get(plugin).first != null)
				item.setIcon(plugins.get(plugin).first);
		}

		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(final Menu menu)
	{
		if (application.editingRoute != null || application.editingTrack != null)
			return false;

		boolean wpt = application.hasWaypoints();
		boolean rts = application.hasRoutes();
		boolean nvw = navigationService != null && navigationService.isNavigating();
		boolean nvr = navigationService != null && navigationService.isNavigatingViaRoute();

		menu.findItem(R.id.menuManageWaypoints).setEnabled(wpt);
		menu.findItem(R.id.menuExportCurrentTrack).setEnabled(application.overlayManager.currentTrackOverlay != null);
		menu.findItem(R.id.menuClearCurrentTrack).setEnabled(application.overlayManager.currentTrackOverlay != null);
		menu.findItem(R.id.menuManageRoutes).setVisible(!nvr);
		menu.findItem(R.id.menuStartNavigation).setVisible(!nvr);
		menu.findItem(R.id.menuStartNavigation).setEnabled(rts);
		menu.findItem(R.id.menuNavigationDetails).setVisible(nvr);
		menu.findItem(R.id.menuNextNavPoint).setVisible(nvr);
		menu.findItem(R.id.menuPrevNavPoint).setVisible(nvr);
		menu.findItem(R.id.menuNextNavPoint).setEnabled(navigationService != null && navigationService.hasNextRouteWaypoint());
		menu.findItem(R.id.menuPrevNavPoint).setEnabled(navigationService != null && navigationService.hasPrevRouteWaypoint());
		menu.findItem(R.id.menuStopNavigation).setEnabled(nvw);
		menu.findItem(R.id.menuSetAnchor).setVisible(showDistance > 0 && !map.isFollowing());

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.menuSearch:
				onSearchRequested();
				return true;
			case R.id.menuNewWaypoint:
				startActivityForResult(new Intent(this, WaypointProperties.class).putExtra("INDEX", -1), RESULT_SAVE_WAYPOINT);
				return true;
			case R.id.menuProjectWaypoint:
				startActivityForResult(new Intent(this, WaypointProject.class), RESULT_SAVE_WAYPOINT);
				return true;
			case R.id.menuLoadWaypoints:
				startActivityForResult(new Intent(this, WaypointFileList.class), RESULT_LOAD_WAYPOINTS);
				return true;
			case R.id.menuExportCurrentTrack:
		        FragmentManager fm = getSupportFragmentManager();
		        TrackExportDialog trackExportDialog = new TrackExportDialog(locationService);
		        trackExportDialog.show(fm, "track_export");
				return true;
			case R.id.menuExpandCurrentTrack:
				new AlertDialog.Builder(this).setIcon(android.R.drawable.ic_dialog_alert).setTitle(R.string.warning).setMessage(R.string.msg_expandcurrenttrack).setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which)
					{
						if (application.overlayManager.currentTrackOverlay != null)
						{
							Track track = locationService.getTrack();
							track.show = true;
							application.overlayManager.currentTrackOverlay.setTrack(track);
						}
					}
				}).setNegativeButton(R.string.no, null).show();
				return true;
			case R.id.menuClearCurrentTrack:
				new AlertDialog.Builder(this).setIcon(android.R.drawable.ic_dialog_alert).setTitle(R.string.warning).setMessage(R.string.msg_clearcurrenttrack).setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which)
					{
						if (application.overlayManager.currentTrackOverlay != null)
							application.overlayManager.currentTrackOverlay.clear();
						locationService.clearTrack();
					}
				}).setNegativeButton(R.string.no, null).show();
				return true;
			case R.id.menuNavigationDetails:
				startActivity(new Intent(this, RouteDetails.class).putExtra("index", application.getRouteIndex(navigationService.navRoute)).putExtra("nav", true));
				return true;
			case R.id.menuNextNavPoint:
				navigationService.nextRouteWaypoint();
				return true;
			case R.id.menuPrevNavPoint:
				navigationService.prevRouteWaypoint();
				return true;
			case R.id.menuStopNavigation:
			{
				navigationService.stopNavigation();
				return true;
			}
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
			case R.id.menuPreferences:
				if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB)
				{
					startActivity(new Intent(this, Preferences.class));
				}
				else
				{
					startActivity(new Intent(this, PreferencesHC.class));
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
			case RESULT_MANAGE_WAYPOINTS:
			{
				application.saveWaypoints();
				break;
			}
			case RESULT_LOAD_WAYPOINTS:
			{
				if (resultCode == RESULT_OK)
				{
					Bundle extras = data.getExtras();
					int count = extras.getInt("count");
					if (count > 0)
					{
						application.overlayManager.waypointsOverlay.clearBitmapCache();
					}
				}
				break;
			}
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
			case RESULT_SAVE_WAYPOINTS:
				if (resultCode == RESULT_OK)
				{
					application.saveDefaultWaypoints();
				}
				break;
			case RESULT_MANAGE_TRACKS:
				for (Iterator<TrackOverlay> iter = application.overlayManager.fileTrackOverlays.iterator(); iter.hasNext();)
				{
					TrackOverlay to = iter.next();
					to.onTrackPropertiesChanged();
					if (to.getTrack().removed)
					{
						to.onBeforeDestroy();
						iter.remove();
					}
				}
				if (resultCode == RESULT_OK)
				{
					Bundle extras = data.getExtras();
					int index = extras.getInt("index");
					startEditTrack(application.getTrack(index));
				}
				break;
			case RESULT_MANAGE_ROUTES:
			{
				for (Iterator<RouteOverlay> iter = application.overlayManager.routeOverlays.iterator(); iter.hasNext();)
				{
					RouteOverlay ro = iter.next();
					ro.onRoutePropertiesChanged();
					if (ro.getRoute().removed)
					{
						ro.onBeforeDestroy();
						iter.remove();
					}
				}
				if (resultCode == RESULT_OK)
				{
					Bundle extras = data.getExtras();
					int index = extras.getInt("index");
					int dir = extras.getInt("dir");
					if (dir != 0)
						startService(new Intent(this, NavigationService.class).setAction(NavigationService.NAVIGATE_ROUTE).putExtra(NavigationService.EXTRA_ROUTE_INDEX, index).putExtra(NavigationService.EXTRA_ROUTE_DIRECTION, dir));
					else
						startEditRoute(application.getRoute(index));
				}
				break;
			}
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
			case RESULT_LOAD_MAP_ATPOSITION:
				if (resultCode == RESULT_OK)
				{
					Bundle extras = data.getExtras();
					final int id = extras.getInt("id");
					if (application.selectMap(id))
					{
						map.suspendBestMap();
						map.updateMapInfo();
						map.update();
					}
					else
					{
						map.update();
					}
				}
				break;
		}
	}

	final Handler backHandler = new Handler();

	@Override
	public void onBackPressed()
	{
		switch (exitConfirmation)
		{
			case 0:
				// wait for second back
				if (secondBack)
				{
					backToast.cancel();
					MapActivity.this.finish();
				}
				else
				{
					secondBack = true;
					backToast.show();
					backHandler.postDelayed(new Runnable() {
						@Override
						public void run()
						{
							secondBack = false;
						}
					}, 2000);
				}
				return;
			case 1:
				// Ask the user if they want to quit
				new AlertDialog.Builder(this).setIcon(android.R.drawable.ic_dialog_alert).setTitle(R.string.quitQuestion).setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which)
					{
						// TODO change context everywhere?
						stopService(new Intent(MapActivity.this, NavigationService.class));
						MapActivity.this.finish();
					}
				}).setNegativeButton(R.string.no, null).show();
				return;
			default:
				super.onBackPressed();
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
				updateGPSStatus();
				if (showDistance == 2)
				{
					application.overlayManager.distanceOverlay.setEnabled(true);
				}
				updateMapViewArea();
				map.requestFocus();
				break;
			case R.id.finishtrackedit:
				application.editingTrack.editing = false;
				application.editingTrack.editingPos = -1;
				application.editingTrack = null;
				findViewById(R.id.edittrack).setVisibility(View.GONE);
				findViewById(R.id.trackdetails).setVisibility(View.GONE);
				updateGPSStatus();
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
		Intent intent = new Intent(getApplicationContext(), NavigationService.class).setAction(NavigationService.NAVIGATE_MAPOBJECT);
		intent.putExtra(NavigationService.EXTRA_NAME, waypoint.name);
		intent.putExtra(NavigationService.EXTRA_LATITUDE, waypoint.latitude);
		intent.putExtra(NavigationService.EXTRA_LONGITUDE, waypoint.longitude);
		intent.putExtra(NavigationService.EXTRA_PROXIMITY, waypoint.proximity);
		startService(intent);
	}

	@Override
	public void onWaypointEdit(final Waypoint waypoint)
	{
		int index = application.getWaypointIndex(waypoint);
		startActivityForResult(new Intent(this, WaypointProperties.class).putExtra("INDEX", index), RESULT_SAVE_WAYPOINT);
	}

	@Override
	public void onWaypointShare(final Waypoint waypoint)
	{
		Intent i = new Intent(android.content.Intent.ACTION_SEND);
		i.setType("text/plain");
		i.putExtra(Intent.EXTRA_SUBJECT, R.string.currentloc);
		String coords = StringFormatter.coordinates(application.coordinateFormat, " ", waypoint.latitude, waypoint.longitude);
		i.putExtra(Intent.EXTRA_TEXT, waypoint.name + " @ " + coords);
		startActivity(Intent.createChooser(i, getString(R.string.menu_share)));
	}

	@Override
	public void onWaypointRemove(final Waypoint waypoint)
	{
		new AlertDialog.Builder(this).setIcon(android.R.drawable.ic_dialog_alert).setTitle(R.string.removeWaypointQuestion)
		.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which)
			{
				WaypointSet wptset = waypoint.set;
				application.removeWaypoint(waypoint);
				application.saveWaypoints(wptset);
				map.invalidate();
			}

		}).setNegativeButton(R.string.no, null).show();
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
			secondBack = false;
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
		else if (getString(R.string.pref_panelactions).equals(key))
		{
			String pa = sharedPreferences.getString(key, resources.getString(R.string.def_panelactions));
			activeActions = Arrays.asList(pa.split(","));
		}
	}

	@Override
	public void onPanelClosed(Panel panel)
	{
		// save panel state
		Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
		editor.putBoolean(getString(R.string.ui_drawer_open), false);
		editor.commit();
	}

	@Override
	public void onPanelOpened(Panel panel)
	{
		updateMapButtons();
		// save panel state
		Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
		editor.putBoolean(getString(R.string.ui_drawer_open), true);
		editor.commit();
	}
	
	@SuppressLint("HandlerLeak")
	private class FinishHandler extends Handler
	{
		private final WeakReference<MapActivity> target;

		FinishHandler(MapActivity activity)
		{
			this.target = new WeakReference<MapActivity>(activity);
		}

		@Override
		public void handleMessage(Message msg)
		{
			MapActivity mapActivity = target.get();
			if (mapActivity != null)
			{
				mapActivity.waitBar.setVisibility(View.INVISIBLE);
				mapActivity.waitBar.setText("");
			}
		}
	}
}
