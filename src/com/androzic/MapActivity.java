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

package com.androzic;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.miscwidgets.interpolator.ExpoInterpolator;
import org.miscwidgets.interpolator.EasingType.Type;
import org.miscwidgets.widget.Panel;
import org.miscwidgets.widget.Panel.OnPanelListener;

import android.app.Activity;
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
import android.database.Cursor;
import android.graphics.LightingColorFilter;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceManager;
//TODO replace with ContactsContract
import android.provider.Contacts;
import android.provider.ContactsContract.PhoneLookup;
import android.telephony.SmsMessage;
import android.text.ClipboardManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.view.animation.AlphaAnimation;
import android.view.animation.AnimationSet;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.SeekBar.OnSeekBarChangeListener;

import com.androzic.data.Route;
import com.androzic.data.Track;
import com.androzic.data.Waypoint;
import com.androzic.data.WaypointSet;
import com.androzic.location.ILocationListener;
import com.androzic.location.ILocationService;
import com.androzic.location.LocationService;
import com.androzic.map.MapInformation;
import com.androzic.navigation.NavigationService;
import com.androzic.overlay.AccuracyOverlay;
import com.androzic.overlay.CurrentTrackOverlay;
import com.androzic.overlay.DistanceOverlay;
import com.androzic.overlay.NavigationOverlay;
import com.androzic.overlay.RouteOverlay;
import com.androzic.overlay.SharingOverlay;
import com.androzic.overlay.TrackOverlay;
import com.androzic.overlay.WaypointsOverlay;
import com.androzic.route.RouteEdit;
import com.androzic.route.RouteFileList;
import com.androzic.route.RouteList;
import com.androzic.route.RouteStart;
import com.androzic.track.ITrackingService;
import com.androzic.track.TrackFileList;
import com.androzic.track.TrackList;
import com.androzic.track.TrackingService;
import com.androzic.util.Astro;
import com.androzic.util.CoordinateParser;
import com.androzic.util.OziExplorerFiles;
import com.androzic.util.StringFormatter;
import com.androzic.waypoint.CoordinatesReceived;
import com.androzic.waypoint.WaypointFileList;
import com.androzic.waypoint.WaypointInfo;
import com.androzic.waypoint.WaypointList;
import com.androzic.waypoint.WaypointProject;
import com.androzic.waypoint.WaypointProperties;
import com.androzic.waypoint.WaypointSave;

public class MapActivity extends Activity implements OnClickListener, OnSharedPreferenceChangeListener, OnSeekBarChangeListener, OnPanelListener
{
	private static final String TAG = "MapActivity";
	
	private static final int RESULT_LOAD_TRACK = 0x100;
	private static final int RESULT_MANAGE_WAYPOINTS = 0x200;
	private static final int RESULT_LOAD_WAYPOINTS = 0x300;
	private static final int RESULT_SAVE_WAYPOINT = 0x400;
	private static final int RESULT_LOAD_MAP = 0x500;
	private static final int RESULT_MANAGE_TRACKS = 0x600;
	private static final int RESULT_LOAD_ROUTE = 0x700;
	public static final int RESULT_START_ROUTE = 0x800;
	private static final int RESULT_MANAGE_ROUTES = 0x900;
	private static final int RESULT_EDIT_ROUTE = 0x110;
	private static final int RESULT_LOAD_MAP_ATPOSITION = 0x120;
	private static final int RESULT_SHOW_WAYPOINT = 0x130;
	private static final int RESULT_SAVE_WAYPOINTS = 0x140;

	// main preferences
	protected double speedFactor;
	protected String speedAbbr;
	protected double elevationFactor;
	protected String elevationAbbr;
	protected int renderInterval;
	protected int magInterval;
	protected boolean autoDim;
	protected int dimInterval;
	protected float dimValue;
	protected int showDistance;
	protected boolean showAccuracy;
	protected boolean followOnLocation;

	protected WakeLock wakeLock;

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
	protected ProgressBar waitBar;
	protected MapView map;
	protected Androzic application;

	protected ExecutorService executorThread = Executors.newSingleThreadExecutor();

	protected Route editingRoute = null;
	protected Track editingTrack = null;
	protected Stack<Waypoint> routeEditingWaypoints = null;

	private ILocationService locationService = null;
	private ITrackingService trackingService = null;
	public NavigationService navigationService = null;

	private Location lastKnownLocation;
	protected long lastRenderTime = 0;
	protected long lastDim = 0;
	protected long lastMagnetic = 0;
	private boolean lastGeoid = true;

	protected boolean wasDimmed = false;

	private boolean animationSet;
	private boolean isFullscreen;
	private boolean isSharing;
	private String[] panelActions;
	private List<String> activeActions;
	LightingColorFilter disable = new LightingColorFilter(0xFFFFFFFF, 0xFF555555);

	protected boolean ready = false;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		
		Log.e(TAG,"onCreate()");

		ready = false;
		isFullscreen = false;
		isSharing = false;

		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB)
		{
			requestWindowFeature(Window.FEATURE_NO_TITLE);
		}

		application = (Androzic) getApplication();
		application.setMapActivity(this);

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		setRequestedOrientation(Integer.parseInt(settings.getString(getString(R.string.pref_orientation), "-1")));
		settings.registerOnSharedPreferenceChangeListener(this);

		final MapState mapState = (MapState) getLastNonConfigurationInstance();
		if (mapState != null)
		{
			Log.e(TAG,"has MapState");
//			application.initialize(mapState);

			editingTrack = mapState.editingTrack;
			editingRoute = mapState.editingRoute;
			routeEditingWaypoints = mapState.routeEditingWaypoints;

/*			
			for (Track track : application.getTracks())
			{
				TrackOverlay newTrack = new TrackOverlay(this, track);
				application.fileTrackOverlays.add(newTrack);
			}

			for (Route route : application.getRoutes())
			{
				RouteOverlay newRoute = new RouteOverlay(this, route);
				application.routeOverlays.add(newRoute);
			}

			if (mapState.currentTrack != null)
			{
				application.currentTrackOverlay = new CurrentTrackOverlay(this);
				application.currentTrackOverlay.setTrack(mapState.currentTrack);
			}
			if (isSharing)
			{
				String session = settings.getString(getString(R.string.pref_sharing_session), "");
				String user = settings.getString(getString(R.string.pref_sharing_user), "");

				application.sharingOverlay = new SharingOverlay(this);
				application.sharingOverlay.setIdentity(session, user);
				application.sharingOverlay.setMapContext(this);
			}
			*/
			application.setMapActivity(this);
		}
		else
		{
			Log.e(TAG,"no MapState");
			// check if called after crash
			if (! application.mapsInited)
			{
				startActivity(new Intent(this, Splash.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
				finish();
				return;
			}
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
		waitBar = (ProgressBar) findViewById(R.id.waitbar);
		map = (MapView) findViewById(R.id.mapview);

		// set button actions
		findViewById(R.id.zoomin).setOnClickListener(this);
		findViewById(R.id.zoomout).setOnClickListener(this);
		findViewById(R.id.nextmap).setOnClickListener(this);
		findViewById(R.id.prevmap).setOnClickListener(this);
		findViewById(R.id.info).setOnClickListener(this);
		findViewById(R.id.follow).setOnClickListener(this);
		findViewById(R.id.locate).setOnClickListener(this);
		findViewById(R.id.tracking).setOnClickListener(this);
		findViewById(R.id.share).setOnClickListener(this);
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

		trackBar.setOnSeekBarChangeListener(this);

		registerForContextMenu(map);

		map.initialize(application);

		String navWpt = settings.getString(getString(R.string.nav_wpt), "");
		if (!"".equals(navWpt))
		{
			try
			{
				int index = -1;
				if (mapState == null)
				{
//					float lat = settings.getFloat(getString(R.string.nav_wpt_lat), 0);
//					float lon = settings.getFloat(getString(R.string.nav_wpt_lon), 0);
//					index = application.addWaypoint(new Waypoint(navWpt, "", lat, lon));
				}
				else
				{
					index = settings.getInt(getString(R.string.nav_wpt_idx), -1);
					startService(new Intent(this, NavigationService.class).setAction(NavigationService.NAVIGATE_WAYPOINT).putExtra("index", index));
				}
//				startService(new Intent(this, NavigationService.class).setAction(NavigationService.NAVIGATE_WAYPOINT).putExtra("index", index));
			}
			catch (Exception e)
			{
			}
		}

		String navRoute = settings.getString(getString(R.string.nav_route), "");
		if (!"".equals(navRoute)
				&& (mapState != null || settings.getBoolean(getString(R.string.pref_navigation_loadlast), getResources().getBoolean(R.bool.def_navigation_loadlast))))
		{
			int ndir = settings.getInt(getString(R.string.nav_route_dir), 0);
			int nwpt = settings.getInt(getString(R.string.nav_route_wpt), -1);
			try
			{
				int rt = -1;
				if (mapState == null)
				{
					File rtf = new File(navRoute);
					Route route = OziExplorerFiles.loadRoutesFromFile(rtf).get(0);
					rt = application.addRoute(route);
					RouteOverlay newRoute = new RouteOverlay(this, route);
					application.routeOverlays.add(newRoute);
				}
				else
				{
					rt = settings.getInt(getString(R.string.nav_route_idx), -1);
				}
				startService(new Intent(this, NavigationService.class).setAction(NavigationService.NAVIGATE_ROUTE).putExtra("index", rt).putExtra("direction", ndir).putExtra("start", nwpt));
			}
			catch (Exception e)
			{
			}
		}

		// set map preferences
		onSharedPreferenceChanged(settings, getString(R.string.pref_cursorcolor));
		onSharedPreferenceChanged(settings, getString(R.string.pref_grid_mapshow));
		onSharedPreferenceChanged(settings, getString(R.string.pref_grid_usershow));
		onSharedPreferenceChanged(settings, getString(R.string.pref_grid_preference));
		onSharedPreferenceChanged(settings, getString(R.string.pref_panelactions));

		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "DoNotDimScreen");

		ready = true;
	}

	@Override
	protected void onStart()
	{
		super.onStart();
		Log.e(TAG,"onStart()");
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		Log.e(TAG,"onResume()");

		map.becomeNotReady();
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
		showAccuracy = settings.getBoolean(getString(R.string.pref_showaccuracy), true);
		autoDim = settings.getBoolean(getString(R.string.pref_mapdim), resources.getBoolean(R.bool.def_mapdim));
		dimInterval = settings.getInt(getString(R.string.pref_mapdiminterval), resources.getInteger(R.integer.def_mapdiminterval)) * 1000;
		dimValue = (float) ((100 - settings.getInt(getString(R.string.pref_mapdimvalue), resources.getInteger(R.integer.def_mapdimvalue))) * 0.01);

		map.setHideOnDrag(settings.getBoolean(getString(R.string.pref_maphideondrag), resources.getBoolean(R.bool.def_maphideondrag)));
		map.setStrictUnfollow(!settings.getBoolean(getString(R.string.pref_unfollowontap), resources.getBoolean(R.bool.def_unfollowontap)));
		map.setLookAhead(settings.getInt(getString(R.string.pref_lookahead), resources.getInteger(R.integer.def_lookahead)));
		map.setBestMapEnabled(settings.getBoolean(getString(R.string.pref_mapbest), resources.getBoolean(R.bool.def_mapbest)));
		map.setBestMapInterval(settings.getInt(getString(R.string.pref_mapbestinterval), resources.getInteger(R.integer.def_mapbestinterval)) * 1000);
		map.setCursorVector(Integer.parseInt(settings.getString(getString(R.string.pref_cursorvector), getString(R.string.def_cursorvector))), settings.getInt(getString(R.string.pref_cursorvectormlpr), resources.getInteger(R.integer.def_cursorvectormlpr)));
		map.setProximity(Integer.parseInt(settings.getString(getString(R.string.pref_navigation_proximity), getString(R.string.def_navigation_proximity))));
		
		// prepare views
		customizeLayout(settings);
		findViewById(R.id.editroute).setVisibility(editingRoute != null ? View.VISIBLE : View.GONE);
		if (editingTrack != null)
		{
			startEditTrack(editingTrack);
		}
		updateGPSStatus();
		updateNavigationStatus();
		// prepare overlays
		updateOverlays(settings, false);

		if (settings.getBoolean(getString(R.string.ui_drawer_open), false))
		{
			Panel panel = (Panel) findViewById(R.id.panel);
			panel.setOpen(true, false);
		}

		//TODO move into application
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

		onSharedPreferenceChanged(settings, getString(R.string.pref_wakelock));

		bindService(new Intent(this, LocationService.class), locationConnection, BIND_AUTO_CREATE);
		bindService(new Intent(this, TrackingService.class), trackingConnection, BIND_AUTO_CREATE);
		bindService(new Intent(this, NavigationService.class), navigationConnection, BIND_AUTO_CREATE);

		registerReceiver(broadcastReceiver, new IntentFilter(NavigationService.BROADCAST_NAVIGATION_STATUS));
		registerReceiver(broadcastReceiver, new IntentFilter(NavigationService.BROADCAST_NAVIGATION_STATE));
		registerReceiver(broadcastReceiver, new IntentFilter(LocationService.BROADCAST_LOCATING_STATUS));
		registerReceiver(broadcastReceiver, new IntentFilter(TrackingService.BROADCAST_TRACKING_STATUS));
		registerReceiver(smsReceiver, new IntentFilter("android.provider.Telephony.SMS_RECEIVED"));

		if (application.hasEnsureVisible())
		{
			setFollowing(false);
			double[] loc = application.getEnsureVisible();
			application.setMapCenter(loc[0], loc[1], false);
			application.clearEnsureVisible();
		}
		else
		{
			application.updateLocationInfo(map.isBestMapEnabled());
		}
		map.updateMapInfo();
		map.update();
		application.notifyOverlays();
		map.requestFocus();
	}

	@Override
	protected void onPause()
	{
		super.onPause();
		Log.e(TAG,"onPause()");

		unregisterReceiver(smsReceiver);
		unregisterReceiver(broadcastReceiver);

		if (wakeLock.isHeld())
		{
			wakeLock.release();
		}
		
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
				Waypoint wpt = navigationService.navWaypoint;
				editor.putString(getString(R.string.nav_wpt), wpt.name);
				editor.putInt(getString(R.string.nav_wpt_idx), application.getWaypointIndex(wpt));
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
			locationService.unregisterCallback(locationListener);
			locationService = null;
		}
		unbindService(locationConnection);
		unbindService(trackingConnection);
	}

	@Override
	protected void onStop()
	{
		super.onStop();
		Log.e(TAG,"onStop()");		
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		Log.e(TAG,"onDestroy()");
		ready = false;

		PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);

		wakeLock = null;

		if (isFinishing())
		{
			// clear all overlays from map
			updateOverlays(null, true);
			application.waypointsOverlay = null;
			application.navigationOverlay = null;
			application.distanceOverlay = null;

			application.clear();
		}
		
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
		waitBar = null;
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
			else if (action.equals(TrackingService.BROADCAST_TRACKING_STATUS))
			{
				updateMapButtons();
			}
			else if (action.equals(LocationService.BROADCAST_LOCATING_STATUS))
			{
				updateMapButtons();
				if (locationService != null && ! locationService.isLocating())
					map.clearLocation();
			}
		}
	};

	private ServiceConnection trackingConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder binder)
		{
			trackingService = (ITrackingService) binder;
			Log.d(TAG, "Tracking service connected");
		}

		public void onServiceDisconnected(ComponentName className)
		{
			trackingService = null;
			Log.d(TAG, "Tracking service disconnected");
		}
	};

	private ServiceConnection locationConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder binder)
		{
			locationService = (ILocationService) binder;
			locationService.registerCallback(locationListener);
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
			if (map.isReady() && lastLocationMillis - lastRenderTime >= renderInterval)
			{
				lastRenderTime = lastLocationMillis;

				application.setLocation(location, magnetic);
				map.setLocation(location);
				final boolean enableFollowing = followOnLocation && lastKnownLocation == null;
				
				lastKnownLocation = location;

				if (application.accuracyOverlay != null && location.hasAccuracy())
				{
					application.accuracyOverlay.setAccuracy(location.getAccuracy());
				}
				
				runOnUiThread(new Runnable() {
					public void run()
					{
						if (!LocationManager.GPS_PROVIDER.equals(location.getProvider()) && map.isMoving())
						{
							map.setMoving(false);
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

		@Override
		public void onSensorChanged(final float azimuth, final float pitch, final float roll)
		{
		}
	};

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
						case R.id.nextmap:
							aib.setEnabled(application.getPrevMap() != 0);
							aib.setColorFilter(aib.isEnabled() ? null : disable);
							break;
						case R.id.prevmap:
							aib.setEnabled(application.getNextMap() != 0);
							aib.setColorFilter(aib.isEnabled() ? null : disable);
							break;
						case R.id.follow:
							aib.setImageDrawable(getResources().getDrawable(map.isFollowing()? R.drawable.cursor_drag_arrow : R.drawable.target));
							break;
						case R.id.locate:
							boolean isLocating = locationService != null && locationService.isLocating();
							aib.setImageDrawable(getResources().getDrawable(isLocating ? R.drawable.pin_map_no : R.drawable.pin_map));
							break;
						case R.id.tracking:
							boolean isTracking =  trackingService != null && trackingService.isTracking();
							aib.setImageDrawable(getResources().getDrawable(isTracking ? R.drawable.doc_delete : R.drawable.doc_edit));
							break;
						case R.id.share:
							aib.setEnabled(application.isPaid);
							aib.setColorFilter(aib.isEnabled() ? null : disable);
							aib.setImageDrawable(getResources().getDrawable(isSharing ? R.drawable.user : R.drawable.users));
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

	protected final void updateCoordinates(final double latlon[])
	{
		//TODO strange situation, needs investigation
		if (application != null)
		{
			String pos = StringFormatter.coordinates(application.coordinateFormat, " ", latlon[0], latlon[1]);
			coordinates.setText(pos);
			updateMapButtons();
		}
	}

	protected final void updateFileInfo()
	{
		String title = application.getMapTitle();
		if (title != null)
		{
			currentFile.setText(title);
		}
		else
		{
			currentFile.setText("-no map-");
		}

		updateMapButtons();
		updateZoomInfo();
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
		findViewById(R.id.movinginfo).setVisibility(map.isMoving() && editingRoute == null && editingTrack == null ? View.VISIBLE : View.GONE);
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
			if (application.navigationOverlay == null)
			{
				application.navigationOverlay = new NavigationOverlay(this);
			}
		}
		else if (application.navigationOverlay != null)
		{
			application.navigationOverlay.onBeforeDestroy();
			application.navigationOverlay = null;
		}
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

		String[] dist = StringFormatter.distanceC(distance);
		String extra = String.valueOf(Math.round(vmg)) + " " + speedAbbr + " | " + StringFormatter.timeH(ete);

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
			if (distance < navigationService.routeProximity * 3 && !animationSet)
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

			String rdist = StringFormatter.distanceH(navigationService.navRouteDistanceLeft() + distance, 1000);
			extra = rdist;
			routeExtra.setText(extra);
		}
	}

	protected void updateMovingInfo(final Location location, final boolean geoid)
	{
		double s = location.getSpeed() * speedFactor;
		double e = location.getAltitude() * elevationFactor;
		double track = application.fixDeclination(location.getBearing());
		speedValue.setText(String.valueOf(Math.round(s)));
		trackValue.setText(String.valueOf(Math.round(track)));
		elevationValue.setText(String.valueOf(Math.round(e)));
		//TODO set separate color
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

		findViewById(R.id.satinfo).setVisibility(slVisible ? View.VISIBLE : View.INVISIBLE);
		findViewById(R.id.mapinfo).setVisibility(mlVisible ? View.VISIBLE : View.GONE);
	}

	private final void updateOverlays(final SharedPreferences settings, final boolean justRemove)
	{
		boolean ctEnabled = false;
		boolean wptEnabled = false;
		boolean navEnabled = false;
		boolean distEnabled = false;
		boolean accEnabled = false;
		boolean shareEnabled = false;

		if (!justRemove)
		{
			ctEnabled = settings.getBoolean(getString(R.string.pref_showcurrenttrack), true);
			wptEnabled = settings.getBoolean(getString(R.string.pref_showwaypoints), true);
			distEnabled = showDistance > 0;
			accEnabled = showAccuracy;
			navEnabled = navigationService != null && navigationService.isNavigating();
			shareEnabled = isSharing;
		}
		if (ctEnabled && application.currentTrackOverlay == null)
		{
			application.currentTrackOverlay = new CurrentTrackOverlay(this);
		}
		else if (!ctEnabled && application.currentTrackOverlay != null)
		{
			application.currentTrackOverlay.onBeforeDestroy();
			application.currentTrackOverlay = null;
		}
		if (application.waypointsOverlay == null)
		{
			application.waypointsOverlay = new WaypointsOverlay(this);
			application.waypointsOverlay.setWaypoints(application.getWaypoints());
		}
		application.waypointsOverlay.setVisible(wptEnabled);
		if (navEnabled && application.navigationOverlay == null)
		{
			application.navigationOverlay = new NavigationOverlay(this);
		}
		else if (!navEnabled && application.navigationOverlay != null)
		{
			application.navigationOverlay.onBeforeDestroy();
			application.navigationOverlay = null;
		}
		if (distEnabled && application.distanceOverlay == null)
		{
			application.distanceOverlay = new DistanceOverlay(this);
		}
		else if (!distEnabled && application.distanceOverlay != null)
		{
			application.distanceOverlay.onBeforeDestroy();
			application.distanceOverlay = null;
		}
		if (!shareEnabled && application.sharingOverlay != null)
		{
			application.sharingOverlay.onBeforeDestroy();
			application.sharingOverlay = null;
		}
		if (accEnabled && application.accuracyOverlay == null)
		{
			application.accuracyOverlay = new AccuracyOverlay(this);
			application.accuracyOverlay.setAccuracy(application.getLocationAsLocation().getAccuracy());
		}
		else if (!accEnabled && application.accuracyOverlay != null)
		{
			application.accuracyOverlay.onBeforeDestroy();
			application.accuracyOverlay = null;
		}

		if (justRemove)
		{
			for (TrackOverlay to : application.fileTrackOverlays)
			{
				to.onBeforeDestroy();
			}
			application.fileTrackOverlays.clear();
			for (RouteOverlay ro : application.routeOverlays)
			{
				ro.onBeforeDestroy();
			}
			application.routeOverlays.clear();
			if (application.waypointsOverlay != null)
			{
				application.waypointsOverlay.onBeforeDestroy();
			}
		}
		else
		{
			for (TrackOverlay to : application.fileTrackOverlays)
			{
				to.onPreferencesChanged(settings);
			}
			for (RouteOverlay ro : application.routeOverlays)
			{
				ro.onPreferencesChanged(settings);
			}
			if (application.waypointsOverlay != null)
			{
				application.waypointsOverlay.onPreferencesChanged(settings);
			}
			if (application.navigationOverlay != null)
			{
				application.navigationOverlay.onPreferencesChanged(settings);
			}
			if (application.sharingOverlay != null)
			{
				application.sharingOverlay.onPreferencesChanged(settings);
			}
			if (application.distanceOverlay != null)
			{
				application.distanceOverlay.onPreferencesChanged(settings);
			}
			if (application.accuracyOverlay != null)
			{
				application.accuracyOverlay.onPreferencesChanged(settings);
			}
			if (application.currentTrackOverlay != null)
			{
				application.currentTrackOverlay.onPreferencesChanged(settings);
			}
		}
	}

	private void startEditTrack(Track track)
	{
		setFollowing(false);
		editingTrack = track;
		editingTrack.editing = true;
		int n = editingTrack.getPoints().size() - 1;
		int p = editingTrack.editingPos >= 0 ? editingTrack.editingPos : n;
		editingTrack.editingPos = p;
		trackBar.setMax(n);
		trackBar.setProgress(0);
		trackBar.setProgress(p);
		trackBar.setKeyProgressIncrement(1);
		onProgressChanged(trackBar, p, false);
		findViewById(R.id.edittrack).setVisibility(View.VISIBLE);
		findViewById(R.id.trackdetails).setVisibility(View.VISIBLE);
		updateGPSStatus();
		if (showDistance > 0)
			application.distanceOverlay.disable();
		map.setFocusable(false);
		map.setFocusableInTouchMode(false);
		trackBar.requestFocus();
		map.invalidate();
	}

	private void startEditRoute(Route route)
	{
		setFollowing(false);
		editingRoute = route;
		editingRoute.editing = true;

		boolean newroute = true;
		for (Iterator<RouteOverlay> iter = application.routeOverlays.iterator(); iter.hasNext();)
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
			application.addRoute(editingRoute);
			RouteOverlay newRoute = new RouteOverlay(this, editingRoute);
			application.routeOverlays.add(newRoute);
		}
		findViewById(R.id.editroute).setVisibility(View.VISIBLE);
		updateGPSStatus();
		routeEditingWaypoints = new Stack<Waypoint>();
		if (showDistance > 0)
			application.distanceOverlay.disable();
		map.invalidate();
	}

	public void setFollowing(boolean follow)
	{
		if (editingRoute == null && editingTrack == null)
		{
			if (showDistance > 0)
			{
				if (showDistance == 2 && ! follow)
				{
					application.distanceOverlay.setAncor(application.getLocation());
					application.distanceOverlay.enable();
				}
				else
				{
					application.distanceOverlay.disable();
				}
			}
			map.setFollowing(follow);
		}
	}

	public void zoomMap(final float factor)
	{
		waitBar.setVisibility(View.VISIBLE);
		executorThread.execute(new Runnable() {
			public void run()
			{
				application.zoomBy(factor);
				finishHandler.sendEmptyMessage(0);
			}
		});
	}

	protected void dimScreen(Location location)
	{
		if (!autoDim)
			return;

		Calendar now = GregorianCalendar.getInstance(TimeZone.getDefault());
		WindowManager.LayoutParams lp = getWindow().getAttributes();
		if (Astro.isDaytime(application.getZenith(), location, now))
		{
			// dim with user preferences is broken, so undim only if dimmed
			// before
			if (wasDimmed)
			{
				lp.screenBrightness = -1.0f;
				getWindow().setAttributes(lp);
			}
		}
		else
		{
			wasDimmed = true;
			lp.screenBrightness = dimValue;
			getWindow().setAttributes(lp);
		}
	}

	public void showWaypointInfo()
	{
		Location loc = application.getLocationAsLocation();
		startActivityForResult(new Intent(this, WaypointInfo.class).putExtra("INDEX", map.waypointSelected).putExtra("lat", loc.getLatitude()).putExtra("lon", loc.getLongitude()), RESULT_SHOW_WAYPOINT);
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu)
	{
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.options_menu, menu);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(final Menu menu)
	{
		if (editingRoute != null || editingTrack != null)
			return false;

		boolean wpt = application.hasWaypoints();
		boolean rts = application.hasRoutes();
		boolean nvw = navigationService != null && navigationService.isNavigating();
		boolean nvr = navigationService != null && navigationService.isNavigatingViaRoute();

		menu.findItem(R.id.menuManageWaypoints).setEnabled(wpt);
		menu.findItem(R.id.menuSaveWaypoints).setEnabled(wpt);
		menu.findItem(R.id.menuRemoveWaypoints).setEnabled(wpt);
		menu.findItem(R.id.menuManageTracks).setEnabled(application.hasTracks());
		menu.findItem(R.id.menuNewRoute).setVisible(!nvw);
		menu.findItem(R.id.menuLoadRoute).setVisible(!nvr);
		menu.findItem(R.id.menuManageRoutes).setVisible(!nvr);
		menu.findItem(R.id.menuManageRoutes).setEnabled(rts);
		menu.findItem(R.id.menuStartNavigation).setEnabled(rts);
		menu.findItem(R.id.menuNextNavPoint).setVisible(nvr);
		menu.findItem(R.id.menuPrevNavPoint).setVisible(nvr);
		menu.findItem(R.id.menuNextNavPoint).setEnabled(navigationService != null && navigationService.hasNextRouteWaypoint());
		menu.findItem(R.id.menuPrevNavPoint).setEnabled(navigationService != null && navigationService.hasPrevRouteWaypoint());
		menu.findItem(R.id.menuStopNavigation).setEnabled(nvw);
		menu.findItem(R.id.menuSetAnchor).setVisible(showDistance > 0 && ! map.isFollowing());

		return true;
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo)
	{
		if (map.waypointSelected < 0)
			return;
		menu.setHeaderTitle(application.getWaypoint(map.waypointSelected).name);
		MenuInflater inflater = getMenuInflater();
		if (editingRoute == null && editingTrack == null)
			inflater.inflate(R.menu.waypoint_context_menu, menu);
		else if (editingTrack == null)
			inflater.inflate(R.menu.waypoint_editing_context_menu, menu);
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.menuSearch:
                onSearchRequested();
                return true;
			case R.id.menuGPS:
				startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
				return true;
			case R.id.menuAddWaypoint:
			{
				double[] loc = application.getMapCenter();
				Waypoint waypoint = new Waypoint("", "", loc[0], loc[1]);
        		waypoint.date = Calendar.getInstance().getTime();
				int wpt = application.addWaypoint(waypoint);
				waypoint.name = "WPT" + wpt;
				application.saveDefaultWaypoints();
				map.update();
				return true;
			}
			case R.id.menuNewWaypoint:
				startActivityForResult(new Intent(this, WaypointProperties.class).putExtra("INDEX", -1), RESULT_SAVE_WAYPOINT);
				return true;
			case R.id.menuProjectWaypoint:
				startActivityForResult(new Intent(this, WaypointProject.class), RESULT_SAVE_WAYPOINT);
				return true;
			case R.id.menuManageWaypoints:
				startActivityForResult(new Intent(this, WaypointList.class), RESULT_MANAGE_WAYPOINTS);
				return true;
			case R.id.menuLoadWaypoints:
				startActivityForResult(new Intent(this, WaypointFileList.class), RESULT_LOAD_WAYPOINTS);
				return true;
			case R.id.menuSaveWaypoints:
				startActivityForResult(new Intent(this, WaypointSave.class), RESULT_SAVE_WAYPOINTS);
				return true;
			case R.id.menuRemoveWaypoints:
			{
				application.clearDefaultWaypoints();
				application.waypointsOverlay.clear();
				application.saveDefaultWaypoints();
				map.update();
				return true;
			}
			case R.id.menuManageTracks:
				startActivityForResult(new Intent(this, TrackList.class), RESULT_MANAGE_TRACKS);
				return true;
			case R.id.menuLoadTrack:
				startActivityForResult(new Intent(this, TrackFileList.class), RESULT_LOAD_TRACK);
				return true;
			case R.id.menuClearTrackTail:
				application.currentTrackOverlay.clear();
				return true;
			case R.id.menuNewRoute:
				startEditRoute(new Route("New route", "", true));
				return true;
			case R.id.menuManageRoutes:
				startActivityForResult(new Intent(this, RouteList.class).putExtra("MODE", RouteList.MODE_MANAGE), RESULT_MANAGE_ROUTES);
				return true;
			case R.id.menuLoadRoute:
				startActivityForResult(new Intent(this, RouteFileList.class), RESULT_LOAD_ROUTE);
				return true;
			case R.id.menuStartNavigation:
				if (application.getRoutes().size() > 1)
				{
					startActivityForResult(new Intent(this, RouteList.class).putExtra("MODE", RouteList.MODE_START), RESULT_START_ROUTE);
				}
				else
				{
					startActivityForResult(new Intent(this, RouteStart.class).putExtra("INDEX", 0), RESULT_START_ROUTE);
				}
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
			case R.id.menuHSI:
				startActivity(new Intent(this, HSIActivity.class));
				return true;
			case R.id.menuCompass:
				startActivity(new Intent(this, CompassActivity.class));
				return true;
			case R.id.menuInformation:
				startActivity(new Intent(this, Information.class));
				return true;
			case R.id.menuMapInfo:
				startActivity(new Intent(this, MapInformation.class));
				return true;
			case R.id.menuCursorMaps:
				startActivityForResult(new Intent(this, MapList.class).putExtra("pos", true), RESULT_LOAD_MAP_ATPOSITION);
				return true;
			case R.id.menuAllMaps:
				startActivityForResult(new Intent(this, MapList.class), RESULT_LOAD_MAP);
				return true;
			case R.id.menuShare:
				Intent i = new Intent(android.content.Intent.ACTION_SEND);
				i.setType("text/plain");
				i.putExtra(Intent.EXTRA_SUBJECT, R.string.currentloc);
				double[] sloc = application.getMapCenter();
				String spos = StringFormatter.coordinates(application.coordinateFormat, " ", sloc[0], sloc[1]);
				i.putExtra(Intent.EXTRA_TEXT, spos);
				startActivity(Intent.createChooser(i, getString(R.string.menu_share)));
				return true;
			case R.id.menuCopyLocation:
			{
				ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
				double[] cloc = application.getMapCenter();
				String cpos = StringFormatter.coordinates(application.coordinateFormat, " ", cloc[0], cloc[1]);
				clipboard.setText(cpos);
				return true;
			}
			case R.id.menuPasteLocation:
			{
				ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
				String q = (String) clipboard.getText();
				try
				{
					double c[] = CoordinateParser.parse(q);
					if (! Double.isNaN(c[0]) && ! Double.isNaN(c[1]))
					{
						boolean mapChanged = application.setMapCenter(c[0], c[1], false);
						if (mapChanged)
							map.updateMapInfo();
						map.update();
						map.setFollowing(false);
					}
				}
				catch (IllegalArgumentException e)
				{
				}
				return true;
			}
			case R.id.menuSetAnchor:
				if (showDistance > 0)
				{
					application.distanceOverlay.setAncor(application.getMapCenter());
					application.distanceOverlay.enable();
					map.invalidate();
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

	@Override
	public boolean onContextItemSelected(final MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.menuWaypointVisible:
				boolean mapChanged = application.setMapCenter(application.getWaypoint(map.waypointSelected).latitude, application.getWaypoint(map.waypointSelected).longitude, false);
				if (mapChanged)
					map.updateMapInfo();
				map.setFollowing(false);
				return true;
			case R.id.menuWaypointNavigate:
				startService(new Intent(this, NavigationService.class).setAction(NavigationService.NAVIGATE_WAYPOINT).putExtra("index", map.waypointSelected));
				return true;
			case R.id.menuWaypointProperties:
				startActivityForResult(new Intent(this, WaypointProperties.class).putExtra("INDEX", map.waypointSelected), RESULT_SAVE_WAYPOINT);
				return true;
			case R.id.menuWaypointRemove:
				WaypointSet set = application.getWaypoint(map.waypointSelected).set;
				application.removeWaypoint(map.waypointSelected);
				application.saveWaypoints(set);
				map.invalidate();
				return true;
			case R.id.menuAddWaypointToRoute:
				Waypoint wpt = application.getWaypoint(map.waypointSelected);
				routeEditingWaypoints.push(editingRoute.addWaypoint(wpt.name, wpt.latitude, wpt.longitude));
				map.invalidate();
				return true;
			default:
				return super.onContextItemSelected(item);
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);

		switch (requestCode)
		{
			case RESULT_LOAD_TRACK:
			{
				if (resultCode == RESULT_OK)
				{
					Bundle extras = data.getExtras();
					int[] index = extras.getIntArray("index");
					for (int i : index)
					{
						Track track = application.getTrack(i);
						TrackOverlay newTrack = new TrackOverlay(this, track);
						application.fileTrackOverlays.add(newTrack);
					}
				}
				break;
			}
			case RESULT_SHOW_WAYPOINT:
				if (resultCode == RESULT_OK)
				{
					Bundle extras = data.getExtras();
					int index = extras.getInt("index");
					int action = extras.getInt("action");
					switch (action)
					{
						case R.id.navigate_button:
							startService(new Intent(this, NavigationService.class).setAction(NavigationService.NAVIGATE_WAYPOINT).putExtra("index", index));
							break;
						case R.id.properties_button:
							startActivityForResult(new Intent(this, WaypointProperties.class).putExtra("INDEX", index), RESULT_SAVE_WAYPOINT);
							break;
						case R.id.remove_button:
							WaypointSet wptset = application.getWaypoint(index).set;
							application.removeWaypoint(index);
							application.saveWaypoints(wptset);
							map.invalidate();
							break;
					}
				}
				break;
			case RESULT_MANAGE_WAYPOINTS:
			{
				application.waypointsOverlay.clear();
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
						application.waypointsOverlay.clear();
					}
				}
				break;
			}
			case RESULT_SAVE_WAYPOINT:
			{
				if (resultCode == RESULT_OK)
				{
					application.waypointsOverlay.clear();
					application.saveWaypoints();
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
				for (Iterator<TrackOverlay> iter = application.fileTrackOverlays.iterator(); iter.hasNext();)
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
				for (Iterator<RouteOverlay> iter = application.routeOverlays.iterator(); iter.hasNext();)
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
					startEditRoute(application.getRoute(index));
				}
				break;
			}
			case RESULT_START_ROUTE:
				if (resultCode == RESULT_OK)
				{
					Bundle extras = data.getExtras();
					int index = extras.getInt("index");
					int dir = extras.getInt("dir");
					startService(new Intent(this, NavigationService.class).setAction(NavigationService.NAVIGATE_ROUTE).putExtra("index", index).putExtra("direction", dir));
				}
				break;
			case RESULT_EDIT_ROUTE:
				for (Iterator<RouteOverlay> iter = application.routeOverlays.iterator(); iter.hasNext();)
				{
					RouteOverlay ro = iter.next();
					if (ro.getRoute().editing)
						ro.onRoutePropertiesChanged();
				}
				map.invalidate();
				break;
			case RESULT_LOAD_ROUTE:
				if (resultCode == RESULT_OK)
				{
					Bundle extras = data.getExtras();
					int[] index = extras.getIntArray("index");
					for (int i : index)
					{
						Route route = application.getRoute(i);
						RouteOverlay newRoute = new RouteOverlay(this, route);
						application.routeOverlays.add(newRoute);
					}
				}
				break;
			case RESULT_LOAD_MAP:
				if (resultCode == RESULT_OK)
				{
					Bundle extras = data.getExtras();
					final int id = extras.getInt("id");
					application.loadMap(id);
					map.becomeNotReady();
					map.suspendBestMap();
					setFollowing(false);
					map.updateMapInfo();
					map.update();
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

	@Override
	public boolean onKeyDown(final int keyCode, final KeyEvent event)
	{
		// Handle the back button
		if (keyCode == KeyEvent.KEYCODE_BACK)
		{
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

			return true;
		}
		else
		{
			return super.onKeyDown(keyCode, event);
		}

	}

	final Handler finishHandler = new Handler() {
		public void handleMessage(Message msg)
		{
			waitBar.setVisibility(View.INVISIBLE);
			map.updateMapInfo();
			map.update();
		}
	};

	@Override
	public void onClick(View v)
	{
		switch (v.getId())
		{
			case R.id.zoomin:
				if (application.getNextZoom() == 0.0)
					break;
				waitBar.setVisibility(View.VISIBLE);
				executorThread.execute(new Runnable() {
					public void run()
					{
						application.zoomIn();
						finishHandler.sendEmptyMessage(0);
					}
				});
				break;
			case R.id.zoomout:
				if (application.getPrevZoom() == 0.0)
					break;
				waitBar.setVisibility(View.VISIBLE);
				executorThread.execute(new Runnable() {
					public void run()
					{
						application.zoomOut();
						finishHandler.sendEmptyMessage(0);
					}
				});
				break;
			case R.id.nextmap:
				waitBar.setVisibility(View.VISIBLE);
				executorThread.execute(new Runnable() {
					public void run()
					{
						if (application.prevMap())
						{
							map.suspendBestMap();
						}
						finishHandler.sendEmptyMessage(0);
					}
				});
				break;
			case R.id.prevmap:
				waitBar.setVisibility(View.VISIBLE);
				executorThread.execute(new Runnable() {
					public void run()
					{
						if (application.nextMap())
						{
							map.suspendBestMap();
						}
						finishHandler.sendEmptyMessage(0);
					}
				});
				break;
			case R.id.info:
				startActivity(new Intent(this, Information.class));
				break;
			case R.id.follow:
				setFollowing(!map.isFollowing());
				break;
			case R.id.locate:
			{
				boolean isLocating = locationService != null && locationService.isLocating();
				application.enableLocating(! isLocating);
				Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
				editor.putBoolean(getString(R.string.lc_locate), ! isLocating);
				editor.commit();
				break;
			}
			case R.id.tracking:
			{
				boolean isTracking = trackingService != null && trackingService.isTracking();
				application.enableTracking(! isTracking);
				Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
				editor.putBoolean(getString(R.string.lc_track), ! isTracking);
				editor.commit();
				break;
			}
			case R.id.share:
				if (isSharing)
				{
					if (application.sharingOverlay != null)
					{
						application.sharingOverlay.onBeforeDestroy();
					}
					application.sharingOverlay = null;
				}
				else
				{
					SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);

					String session = settings.getString(getString(R.string.pref_sharing_session), "");
					String user = settings.getString(getString(R.string.pref_sharing_user), "");

					if (session.length() == 0 || user.length() == 0)
					{
						new AlertDialog.Builder(this).setIcon(android.R.drawable.ic_dialog_alert).setTitle(R.string.err_notconfigured).setMessage(R.string.sessionQuestion).setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {

							@Override
							public void onClick(DialogInterface dialog, int which)
							{
								if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB)
								{
									startActivity(new Intent(MapActivity.this, Preferences.class).putExtra("pref", "pref_sharing"));
								}
								else
								{
									startActivity(new Intent(MapActivity.this, PreferencesHC.class).putExtra("pref", R.id.pref_sharing));
								}								
							}

						}).setNegativeButton(R.string.no, null).show();

						break;
					}
					else
					{
						application.sharingOverlay = new SharingOverlay(this);
						application.sharingOverlay.setIdentity(session, user);
						application.sharingOverlay.setMapContext(this);
					}
				}
				isSharing = !isSharing;
				updateMapButtons();
				map.invalidate();
				break;
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
				editingTrack.cutBefore(trackBar.getProgress());
				int nb = editingTrack.getPoints().size() - 1;
				trackBar.setMax(nb);
				trackBar.setProgress(0);
				map.invalidate();
				break;
			case R.id.cutafter:
				editingTrack.cutAfter(trackBar.getProgress());
				int na = editingTrack.getPoints().size() - 1;
				trackBar.setMax(na);
				trackBar.setProgress(0);
				trackBar.setProgress(na);
				map.invalidate();
				break;
			case R.id.addpoint:
				double[] aloc = application.getMapCenter();
				routeEditingWaypoints.push(editingRoute.addWaypoint("RWPT" + editingRoute.length(), aloc[0], aloc[1]));
				map.invalidate();
				break;
			case R.id.insertpoint:
				double[] iloc = application.getMapCenter();
				routeEditingWaypoints.push(editingRoute.insertWaypoint("RWPT" + editingRoute.length(), iloc[0], iloc[1]));
				map.invalidate();
				break;
			case R.id.removepoint:
				if (!routeEditingWaypoints.empty())
				{
					editingRoute.removeWaypoint(routeEditingWaypoints.pop());
					map.invalidate();
				}
				break;
			case R.id.orderpoints:
				startActivityForResult(new Intent(this, RouteEdit.class).putExtra("INDEX", application.getRoutes().size() - 1), RESULT_EDIT_ROUTE);
				break;
			case R.id.finishedit:
				if ("New route".equals(editingRoute.name))
				{
					SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd_HH-mm");
					editingRoute.name = formatter.format(new Date());
				}
				editingRoute.editing = false;
				for (Iterator<RouteOverlay> iter = application.routeOverlays.iterator(); iter.hasNext();)
				{
					RouteOverlay ro = iter.next();
					ro.onRoutePropertiesChanged();
				}
				editingRoute = null;
				routeEditingWaypoints = null;
				findViewById(R.id.editroute).setVisibility(View.GONE);
				updateGPSStatus();
				if (showDistance == 2)
				{
					application.distanceOverlay.enable();
				}
				map.invalidate();
				map.requestFocus();
				break;
			case R.id.finishtrackedit:
				editingTrack.editing = false;
				editingTrack.editingPos = -1;
				editingTrack = null;
				findViewById(R.id.edittrack).setVisibility(View.GONE);
				findViewById(R.id.trackdetails).setVisibility(View.GONE);
				updateGPSStatus();
				if (showDistance == 2)
				{
					application.distanceOverlay.enable();
				}
				map.invalidate();
				map.setFocusable(true);
				map.setFocusableInTouchMode(true);
				map.requestFocus();
				break;
		}
	}

	@Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
	{
		switch (seekBar.getId())
		{
			case R.id.trackbar:
				if (fromUser)
				{
					editingTrack.editingPos = progress;
				}
				Track.TrackPoint tp = editingTrack.getPoint(progress);
				double ele = tp.elevation * elevationFactor;
				((TextView) findViewById(R.id.tp_number)).setText("#" + (progress + 1));
				// FIXME Need UTM support here
				((TextView) findViewById(R.id.tp_latitude)).setText(StringFormatter.coordinate(application.coordinateFormat, tp.latitude));
				((TextView) findViewById(R.id.tp_longitude)).setText(StringFormatter.coordinate(application.coordinateFormat, tp.longitude));
				((TextView) findViewById(R.id.tp_elevation)).setText(String.valueOf(Math.round(ele)) + " " + elevationAbbr);
				((TextView) findViewById(R.id.tp_time)).setText(SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.SHORT, SimpleDateFormat.SHORT).format(new Date(tp.time)));
				boolean mapChanged = application.setMapCenter(tp.latitude, tp.longitude, false);
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

	private BroadcastReceiver smsReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent)
		{
			Bundle bundle = intent.getExtras();
			String sender = "";
			String title = "";
			double coords[] = null;
			
			if (bundle != null)
			{
				Object[] pdus = (Object[]) bundle.get("pdus");
				for (int i = 0; i < pdus.length; i++)
				{
					SmsMessage msg = SmsMessage.createFromPdu((byte[]) pdus[i]);
					String text = msg.getMessageBody().toString();
					if (text.contains("@"))
					{
						int idx = text.indexOf("@");
						title = text.substring(0, idx).trim();
						text = text.substring(idx+1, text.length()).trim();
					}
					coords = CoordinateParser.parse(text);
					if (! Double.isNaN(coords[0]) && ! Double.isNaN(coords[1]))
					{
						sender = msg.getOriginatingAddress();
						Uri uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(sender));
						Cursor c = getContentResolver().query(uri, new String[]{PhoneLookup.DISPLAY_NAME}, null, null, null);
						if (c.moveToFirst())
						{
							sender = c.getString(c.getColumnIndex(Contacts.Phones.DISPLAY_NAME));
						}
					}
				}
				if (coords != null && ! Double.isNaN(coords[0]) && ! Double.isNaN(coords[1]))
				{
					Location loc = application.getLocationAsLocation();
					startActivity(new Intent(MapActivity.this, CoordinatesReceived.class).putExtra("title", title).putExtra("sender", sender).putExtra("lat", coords[0]).putExtra("lon", coords[1]).putExtra("clat", loc.getLatitude()).putExtra("clon", loc.getLongitude()));
				}
			}
		}
	};

	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState)
	{
		super.onRestoreInstanceState(savedInstanceState);
		Log.e(TAG,"onRestoreInstanceState()");
		lastKnownLocation = savedInstanceState.getParcelable("lastKnownLocation");
		lastRenderTime = savedInstanceState.getLong("lastRenderTime");
		lastMagnetic = savedInstanceState.getLong("lastMagnetic");
		lastDim = savedInstanceState.getLong("lastDim");
		wasDimmed = savedInstanceState.getBoolean("wasDimmed");
		isSharing = savedInstanceState.getBoolean("isSharing");
		lastGeoid = savedInstanceState.getBoolean("lastGeoid");
		/*
		double[] distAncor = savedInstanceState.getDoubleArray("distAncor");
		if (distAncor != null)
		{
			application.distanceOverlay = new DistanceOverlay(this);
			application.distanceOverlay.setAncor(distAncor);
		}
		*/
	}

	@Override
	protected void onSaveInstanceState(Bundle outState)
	{
		super.onSaveInstanceState(outState);
		Log.e(TAG,"onSaveInstanceState()");
		outState.putParcelable("lastKnownLocation", lastKnownLocation);
		outState.putLong("lastRenderTime", lastRenderTime);
		outState.putLong("lastMagnetic", lastMagnetic);
		outState.putLong("lastDim", lastDim);
		outState.putBoolean("wasDimmed", wasDimmed);
		outState.putBoolean("isSharing", isSharing);
		outState.putBoolean("lastGeoid", lastGeoid);
		if (application.distanceOverlay != null)
		{
			outState.putDoubleArray("distAncor", application.distanceOverlay.getAncor());
		}
	}

	@Override
	public Object onRetainNonConfigurationInstance()
	{
		Log.e(TAG, "onRetainNonConfigurationInstance()");
		MapState mapState = new MapState();

//		application.onRetainNonConfigurationInstance(mapState);

		mapState.editingTrack = editingTrack;
		mapState.editingRoute = editingRoute;
		mapState.routeEditingWaypoints = routeEditingWaypoints;

/*		
		if (application.currentTrackOverlay != null)
		{
			mapState.currentTrack = application.currentTrackOverlay.getTrack();
		}
*/
		return mapState;
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
	{
		// TODO Auto-generated method stub
		Resources resources = getResources();
		// application preferences
		if (getString(R.string.pref_folder_map).equals(key))
		{
			if (application.setMapPath(sharedPreferences.getString(getString(R.string.pref_folder_map), resources.getString(R.string.def_folder_map))))
			{
				map.updateMapInfo();
				map.update();
			}
		}
		else if (getString(R.string.pref_folder_waypoint).equals(key))
		{
			application.setDataPath(Androzic.PATH_WAYPOINTS, sharedPreferences.getString(key, resources.getString(R.string.def_folder_waypoint)));
		}
		else if (getString(R.string.pref_folder_track).equals(key))
		{
			application.setDataPath(Androzic.PATH_TRACKS, sharedPreferences.getString(key, resources.getString(R.string.def_folder_track)));
		}
		else if (getString(R.string.pref_folder_route).equals(key))
		{
			application.setDataPath(Androzic.PATH_ROUTES, sharedPreferences.getString(key, resources.getString(R.string.def_folder_route)));
		}
		else if (getString(R.string.pref_folder_icon).equals(key))
		{
			application.setDataPath(Androzic.PATH_ICONS, sharedPreferences.getString(key, resources.getString(R.string.def_folder_icon)));
		}
		else if (getString(R.string.pref_orientation).equals(key))
		{
			setRequestedOrientation(Integer.parseInt(sharedPreferences.getString(key, "-1")));
		}
		else if (getString(R.string.pref_grid_mapshow).equals(key))
		{
			application.mapGrid = sharedPreferences.getBoolean(key, false);
			application.initGrids();
		}
		else if (getString(R.string.pref_grid_usershow).equals(key))
		{
			application.userGrid = sharedPreferences.getBoolean(key, false);
			application.initGrids();
		}
		else if (getString(R.string.pref_grid_preference).equals(key))
		{
			application.gridPrefer = Integer.parseInt(sharedPreferences.getString(key, "0"));
			application.initGrids();
		}
		else if (getString(R.string.pref_grid_userscale).equals(key) || getString(R.string.pref_grid_userunit).equals(key)
				|| getString(R.string.pref_grid_usermpp).equals(key))
		{
			application.initGrids();
		}
		else if (getString(R.string.pref_useonlinemap).equals(key) && sharedPreferences.getBoolean(key, false))
		{
			application.setOnlineMap(sharedPreferences.getString(getString(R.string.pref_onlinemap), resources.getString(R.string.def_onlinemap)));
		}
		else if (getString(R.string.pref_onlinemap).equals(key) || getString(R.string.pref_onlinemapscale).equals(key))
		{
			application.setOnlineMap(sharedPreferences.getString(getString(R.string.pref_onlinemap), resources.getString(R.string.def_onlinemap)));
		}
		// activity preferences
		else if (getString(R.string.pref_wakelock).equals(key))
		{
			boolean lock = sharedPreferences.getBoolean(key, resources.getBoolean(R.bool.def_wakelock));
			if (lock && !wakeLock.isHeld())
			{
				wakeLock.acquire();
			}
			else if (!lock && wakeLock.isHeld())
			{
				wakeLock.release();
			}
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
}