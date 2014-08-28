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

package com.androzic;

import java.lang.ref.WeakReference;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.internal.view.SupportMenuInflater;
import android.support.v7.internal.view.menu.MenuBuilder;
import android.support.v7.internal.view.menu.MenuPopupHelper;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.animation.AlphaAnimation;
import android.view.animation.AnimationSet;
import android.widget.TextView;

import com.androzic.data.Waypoint;
import com.androzic.location.LocationService;
import com.androzic.overlay.NavigationOverlay;
import com.androzic.util.Clipboard;
import com.androzic.util.CoordinateParser;
import com.androzic.util.StringFormatter;

public class MapFragment extends Fragment implements MapHolder, OnSharedPreferenceChangeListener, OnClickListener, MenuBuilder.Callback
{
	private static final String TAG = "MapFragment";

	// Settings
	/**
	 * UI (map data) update interval in milliseconds
	 */
	private long updatePeriod;
	private boolean followOnLocation;
	private boolean keepScreenOn;
	private int showDistance;

	private String precisionFormat = "%.0f";
	private double speedFactor;
	private String speedAbbr;
	private double elevationFactor;
	private String elevationAbbr;

	// Views
	private MapView map;

	private TextView coordinates;
	private TextView satInfo;
	private TextView currentFile;
	private TextView mapZoom;

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

	private TextView waitBar;

	Androzic application;

	private ExecutorService executorThread = Executors.newSingleThreadExecutor();
	private FinishHandler finishHandler;
	private Handler updateCallback = new Handler();

	protected long lastRenderTime = 0;
	protected long lastDim = 0;
	protected long lastMagnetic = 0;
	private boolean lastGeoid = true;

	private boolean animationSet;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setRetainInstance(true);
		setHasOptionsMenu(true);

		application = Androzic.getApplication();

		finishHandler = new FinishHandler(this);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		View view = inflater.inflate(R.layout.fragment_map, container, false);

		coordinates = (TextView) view.findViewById(R.id.coordinates);
		satInfo = (TextView) view.findViewById(R.id.sats);
		currentFile = (TextView) view.findViewById(R.id.currentfile);
		mapZoom = (TextView) view.findViewById(R.id.currentzoom);
		waypointName = (TextView) view.findViewById(R.id.waypointname);
		waypointExtra = (TextView) view.findViewById(R.id.waypointextra);
		routeName = (TextView) view.findViewById(R.id.routename);
		routeExtra = (TextView) view.findViewById(R.id.routeextra);
		speedValue = (TextView) view.findViewById(R.id.speed);
		speedUnit = (TextView) view.findViewById(R.id.speedunit);
		trackValue = (TextView) view.findViewById(R.id.track);
		trackUnit = (TextView) view.findViewById(R.id.trackunit);
		elevationValue = (TextView) view.findViewById(R.id.elevation);
		elevationUnit = (TextView) view.findViewById(R.id.elevationunit);
		distanceValue = (TextView) view.findViewById(R.id.distance);
		distanceUnit = (TextView) view.findViewById(R.id.distanceunit);
		xtkValue = (TextView) view.findViewById(R.id.xtk);
		xtkUnit = (TextView) view.findViewById(R.id.xtkunit);
		bearingValue = (TextView) view.findViewById(R.id.bearing);
		bearingUnit = (TextView) view.findViewById(R.id.bearingunit);
		turnValue = (TextView) view.findViewById(R.id.turn);
		// trackBar = (SeekBar) findViewById(R.id.trackbar);
		waitBar = (TextView) view.findViewById(R.id.waitbar);
		map = (MapView) view.findViewById(R.id.mapview);
		map.initialize(application, this);

		coordinates.setOnClickListener(this);
		satInfo.setOnClickListener(this);
		currentFile.setOnClickListener(this);
		mapZoom.setOnClickListener(this);

		application.setMapHolder(this);

		return view;
	}

	@Override
	public void onResume()
	{
		super.onResume();
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getActivity());

		onSharedPreferenceChanged(settings, getString(R.string.pref_maprenderinterval));
		onSharedPreferenceChanged(settings, getString(R.string.pref_mapfollowonloc));
		onSharedPreferenceChanged(settings, getString(R.string.pref_wakelock));
		onSharedPreferenceChanged(settings, getString(R.string.pref_showdistance_int));

		onSharedPreferenceChanged(settings, getString(R.string.pref_maphideondrag));
		onSharedPreferenceChanged(settings, getString(R.string.pref_unfollowontap));
		onSharedPreferenceChanged(settings, getString(R.string.pref_lookahead));
		onSharedPreferenceChanged(settings, getString(R.string.pref_mapbest));
		onSharedPreferenceChanged(settings, getString(R.string.pref_mapbestinterval));
		onSharedPreferenceChanged(settings, getString(R.string.pref_cursorvector));
		onSharedPreferenceChanged(settings, getString(R.string.pref_cursorcolor));
		onSharedPreferenceChanged(settings, getString(R.string.pref_navigation_proximity));

		onSharedPreferenceChanged(settings, getString(R.string.pref_unitprecision));
		onSharedPreferenceChanged(settings, getString(R.string.pref_unitspeed));
		onSharedPreferenceChanged(settings, getString(R.string.pref_unitelevation));

		PreferenceManager.getDefaultSharedPreferences(application).registerOnSharedPreferenceChangeListener(this);

		updateGPSStatus();
		updateNavigationStatus();

		map.setKeepScreenOn(keepScreenOn);

		// FIXME Better move it to application?
		if (application.hasEnsureVisible())
		{
			setFollowing(false);
			followOnLocation = false;
			double[] loc = application.getEnsureVisible();
			application.setMapCenter(loc[0], loc[1], true, false);
			application.clearEnsureVisible();
		}
		else
		{
			application.updateLocationMaps(true, map.isBestMapEnabled());
		}

		customizeLayout(settings);

		// Start updating UI
		map.resume();
		map.updateMapInfo();
		map.update();
		map.requestFocus();
		updateCallback.post(updateUI);
	}

	@Override
	public void onPause()
	{
		super.onPause();

		// Stop updating UI
		map.pause();
		updateCallback.removeCallbacks(updateUI);

		PreferenceManager.getDefaultSharedPreferences(application).unregisterOnSharedPreferenceChangeListener(this);
	}

	@Override
	public void onDestroyView()
	{
		super.onDestroyView();

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
		// trackBar = null;
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();

		application = null;
	}

	final private Runnable updateUI = new Runnable() {
		public void run()
		{
			updateCallback.postDelayed(this, updatePeriod);

			if (application.lastKnownLocation == null)
			{
				// TODO Should we do anything else here?
				return;
			}

			map.setLocation(application.lastKnownLocation);

			if (application.gpsEnabled)
			{
				if (!map.isFixed())
				{
					satInfo.setText(R.string.sat_start);
					satInfo.setTextColor(getResources().getColor(R.color.gpsenabled));
					// Mock provider hack
					if (application.gpsContinous)
					{
						map.setMoving(true);
						map.setFixed(true);
					}
				}
				switch (application.gpsStatus)
				{
					case LocationService.GPS_OK:
						satInfo.setTextColor(getResources().getColor(R.color.gpsworking));
						satInfo.setText(String.valueOf(application.gpsFSats) + "/" + String.valueOf(application.gpsTSats));
						if (!map.isFixed())
						{
							map.setMoving(true);
							map.setFixed(true);
						}
						break;
					case LocationService.GPS_SEARCHING:
						satInfo.setTextColor(getResources().getColor(R.color.gpsenabled));
						satInfo.setText(String.valueOf(application.gpsFSats) + "/" + String.valueOf(application.gpsTSats));
						if (map.isFixed())
						{
							map.setFixed(false);
						}
						break;
				}
			}
			else
			{
				satInfo.setText(R.string.sat_stop);
				satInfo.setTextColor(getResources().getColor(R.color.gpsdisabled));
				if (map.isMoving())
				{
					map.setMoving(false);
					// FIXME Should we?
					map.setFixed(false);
				}
			}

			double s = application.lastKnownLocation.getSpeed() * speedFactor;
			double e = application.lastKnownLocation.getAltitude() * elevationFactor;
			double track = application.fixDeclination(application.lastKnownLocation.getBearing());
			speedValue.setText(String.format(precisionFormat, s));
			trackValue.setText(String.valueOf(Math.round(track)));
			elevationValue.setText(String.valueOf(Math.round(e)));
			// TODO set separate color
			if (application.gpsGeoid != lastGeoid)
			{
				int color = application.gpsGeoid ? 0xffffffff : getResources().getColor(R.color.gpsenabled);
				elevationValue.setTextColor(color);
				elevationUnit.setTextColor(color);
				((TextView) getView().findViewById(R.id.elevationname)).setTextColor(color);
				lastGeoid = application.gpsGeoid;
			}

			if (application.shouldEnableFollowing)
			{
				application.shouldEnableFollowing = false;
				if (followOnLocation)
					setFollowing(true);
			}
			else
				map.update();

			updateGPSStatus();

			// auto dim
			/*
			 * if (autoDim && dimInterval > 0 && lastLocationMillis - lastDim >= dimInterval)
			 * {
			 * dimScreen(location);
			 * lastDim = lastLocationMillis;
			 * }
			 */
		}
	};

	private final void customizeLayout(final SharedPreferences settings)
	{
		boolean slVisible = settings.getBoolean(getString(R.string.pref_showsatinfo), true);
		boolean mlVisible = settings.getBoolean(getString(R.string.pref_showmapinfo), true);

		View root = getView();
		root.findViewById(R.id.satinfo).setVisibility(slVisible ? View.VISIBLE : View.GONE);
		root.findViewById(R.id.mapinfo).setVisibility(mlVisible ? View.VISIBLE : View.GONE);

		updateMapViewArea();
	}

	private void updateMapViewArea()
	{
		final ViewTreeObserver vto = map.getViewTreeObserver();
		vto.addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
			@SuppressLint("NewApi")
			@SuppressWarnings("deprecation")
			public void onGlobalLayout()
			{
				View root = getView();
				Rect area = new Rect();
				map.getLocalVisibleRect(area);
				View v = root.findViewById(R.id.topbar);
				if (v != null)
					area.top = v.getBottom();
				v = root.findViewById(R.id.bottombar);
				if (v != null)
					area.bottom = v.getTop();
				v = root.findViewById(R.id.rightbar);
				if (v != null)
					area.right = v.getLeft();
				if (!area.isEmpty())
					map.updateViewArea(area);
				ViewTreeObserver ob;
				if (vto.isAlive())
					ob = vto;
				else
					ob = map.getViewTreeObserver();

				if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN)
				{
					ob.removeGlobalOnLayoutListener(this);
				}
				else
				{
					ob.removeOnGlobalLayoutListener(this);
				}
			}
		});
	}

	private void updateNavigationStatus()
	{
		boolean isNavigating = application.navigationService != null && application.navigationService.isNavigating();
		boolean isNavigatingViaRoute = isNavigating && application.navigationService.isNavigatingViaRoute();

		View rootView = getView();

		// waypoint panel
		rootView.findViewById(R.id.waypointinfo).setVisibility(isNavigating ? View.VISIBLE : View.GONE);
		// route panel
		rootView.findViewById(R.id.routeinfo).setVisibility(isNavigatingViaRoute ? View.VISIBLE : View.GONE);
		// distance
		distanceValue.setVisibility(isNavigating ? View.VISIBLE : View.GONE);
		rootView.findViewById(R.id.distancelt).setVisibility(isNavigating ? View.VISIBLE : View.GONE);
		// bearing
		bearingValue.setVisibility(isNavigating ? View.VISIBLE : View.GONE);
		rootView.findViewById(R.id.bearinglt).setVisibility(isNavigating ? View.VISIBLE : View.GONE);
		// turn
		turnValue.setVisibility(isNavigating ? View.VISIBLE : View.GONE);
		rootView.findViewById(R.id.turnlt).setVisibility(isNavigating ? View.VISIBLE : View.GONE);
		// xtk
		xtkValue.setVisibility(isNavigatingViaRoute ? View.VISIBLE : View.GONE);
		rootView.findViewById(R.id.xtklt).setVisibility(isNavigatingViaRoute ? View.VISIBLE : View.GONE);

		// we hide elevation in portrait mode due to lack of space
		if (getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE)
		{
			if (isNavigatingViaRoute && elevationValue.getVisibility() == View.VISIBLE)
			{
				elevationValue.setVisibility(View.GONE);
				rootView.findViewById(R.id.elevationlt).setVisibility(View.GONE);

				ViewGroup row = (ViewGroup) rootView.findViewById(R.id.movingrow);
				int pos = row.indexOfChild(elevationValue);
				View xtklt = rootView.findViewById(R.id.xtklt);
				row.removeView(xtkValue);
				row.removeView(xtklt);
				row.addView(xtklt, pos);
				row.addView(xtkValue, pos);
				row.getParent().requestLayout();
			}
			else if (!isNavigatingViaRoute && elevationValue.getVisibility() == View.GONE)
			{
				elevationValue.setVisibility(View.VISIBLE);
				rootView.findViewById(R.id.elevationlt).setVisibility(View.VISIBLE);

				ViewGroup row = (ViewGroup) rootView.findViewById(R.id.movingrow);
				int pos = row.indexOfChild(xtkValue);
				View elevationlt = rootView.findViewById(R.id.elevationlt);
				row.removeView(elevationValue);
				row.removeView(elevationlt);
				row.addView(elevationlt, pos);
				row.addView(elevationValue, pos);
				row.getParent().requestLayout();
			}
		}

		if (isNavigatingViaRoute)
		{
			routeName.setText("› " + application.navigationService.navRoute.name);
		}
		if (isNavigating)
		{
			waypointName.setText("» " + application.navigationService.navWaypoint.name);
			// FIXME All overlay operations should go into application
			if (application.navigationOverlay == null)
			{
				application.navigationOverlay = new NavigationOverlay();
				application.navigationOverlay.onMapChanged();
			}
		}
		else if (application.navigationOverlay != null)
		{
			application.navigationOverlay.onBeforeDestroy();
			application.navigationOverlay = null;
		}

		updateMapViewArea();
		map.update();
	}

	private void updateNavigationInfo()
	{
		if (application.navigationService == null || !application.navigationService.isNavigating())
			return;

		double distance = application.navigationService.navDistance;
		double bearing = application.navigationService.navBearing;
		long turn = application.navigationService.navTurn;
		double vmg = application.navigationService.navVMG * speedFactor;
		int ete = application.navigationService.navETE;

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

		if (application.navigationService.isNavigatingViaRoute())
		{
			View rootView = getView();
			boolean hasNext = application.navigationService.hasNextRouteWaypoint();
			if (distance < application.navigationService.navProximity * 3 && !animationSet)
			{
				AnimationSet animation = new AnimationSet(true);
				animation.addAnimation(new AlphaAnimation(1.0f, 0.3f));
				animation.addAnimation(new AlphaAnimation(0.3f, 1.0f));
				animation.setDuration(500);
				animation.setRepeatCount(10);
				rootView.findViewById(R.id.waypointinfo).startAnimation(animation);
				if (!hasNext)
				{
					rootView.findViewById(R.id.routeinfo).startAnimation(animation);
				}
				animationSet = true;
			}
			else if (animationSet)
			{
				rootView.findViewById(R.id.waypointinfo).setAnimation(null);
				if (!hasNext)
				{
					rootView.findViewById(R.id.routeinfo).setAnimation(null);
				}
				animationSet = false;
			}

			if (application.navigationService.navXTK == Double.NEGATIVE_INFINITY)
			{
				xtkValue.setText("--");
				xtkUnit.setText("--");
			}
			else
			{
				String xtksym = application.navigationService.navXTK == 0 ? "" : application.navigationService.navXTK > 0 ? "R" : "L";
				String[] xtks = StringFormatter.distanceC(Math.abs(application.navigationService.navXTK));
				xtkValue.setText(xtks[0] + xtksym);
				xtkUnit.setText(xtks[1]);
			}

			double navDistance = application.navigationService.navRouteDistanceLeft();
			int eta = application.navigationService.navRouteETE(navDistance);
			if (eta < Integer.MAX_VALUE)
				eta += application.navigationService.navETE;
			extra = StringFormatter.distanceH(navDistance + distance, 1000) + " | " + StringFormatter.timeH(eta);
			routeExtra.setText(extra);
		}
	}

	@Override
	public MapView getMapView()
	{
		return map;
	}

	@Override
	public boolean waypointTapped(Waypoint waypoint, int x, int y)
	{
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean routeWaypointTapped(int route, int index, int x, int y)
	{
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean mapObjectTapped(long id, int x, int y)
	{
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void toggleFollowing()
	{
		setFollowing(!map.isFollowing());
	}

	@Override
	public void setFollowing(boolean follow)
	{
		if (application.editingRoute == null && application.editingTrack == null)
		{
			if (showDistance > 0 && application.distanceOverlay != null)
			{
				if (showDistance == 2 && !follow)
				{
					application.distanceOverlay.setAncor(application.getLocation());
					application.distanceOverlay.setEnabled(true);
				}
				else
				{
					application.distanceOverlay.setEnabled(false);
				}
			}
			map.setFollowing(follow);
		}
	}

	@Override
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
					{
						map.updateMapInfo();
						map.update();
					}
				}
				finishHandler.sendEmptyMessage(0);
			}
		});
	}

	@Override
	public void zoomIn()
	{
		// TODO Show toast here
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
					{
						map.updateMapInfo();
						map.update();
					}
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
					{
						map.updateMapInfo();
						map.update();
					}
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
	public void mapChanged()
	{
		map.suspendBestMap();
		map.updateMapInfo();
		map.update();
	}

	private void updateGPSStatus()
	{
		int v = map.isMoving() && application.editingRoute == null && application.editingTrack == null ? View.VISIBLE : View.GONE;
		View view = getView().findViewById(R.id.movinginfo);
		if (view.getVisibility() != v)
		{
			view.setVisibility(v);
			updateMapViewArea();
		}
	}

	@Override
	public void updateCoordinates(double[] latlon)
	{
		// TODO strange situation, needs investigation
		if (application != null)
		{
			final String pos = StringFormatter.coordinates(application.coordinateFormat, " ", latlon[0], latlon[1]);
			getActivity().runOnUiThread(new Runnable() {

				@Override
				public void run()
				{
					coordinates.setText(pos);
				}
			});
		}
	}

	@Override
	public void updateFileInfo()
	{
		final String title = application.getMapTitle();
		getActivity().runOnUiThread(new Runnable() {

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

				double zoom = application.getZoom() * 100;

				if (zoom == 0.0)
				{
					mapZoom.setText("---%");
				}
				else
				{
					int rz = (int) Math.floor(zoom);
					String zoomStr = zoom - rz != 0.0 ? String.format(Locale.getDefault(), "%.1f", zoom) : String.valueOf(rz);
					mapZoom.setText(zoomStr + "%");
				}

				// ImageButton zoomin = (ImageButton) findViewById(R.id.zoomin);
				// ImageButton zoomout = (ImageButton) findViewById(R.id.zoomout);
				// zoomin.setEnabled(application.getNextZoom() != 0.0);
				// zoomout.setEnabled(application.getPrevZoom() != 0.0);

				// LightingColorFilter disable = new LightingColorFilter(0xFFFFFFFF, 0xFF444444);

				// zoomin.setColorFilter(zoomin.isEnabled() ? null : disable);
				// zoomout.setColorFilter(zoomout.isEnabled() ? null : disable);
			}
		});
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
	{
		Resources resources = getResources();
		if (getString(R.string.pref_wakelock).equals(key))
		{
			keepScreenOn = sharedPreferences.getBoolean(key, resources.getBoolean(R.bool.def_wakelock));
		}
		else if (getString(R.string.pref_showdistance_int).equals(key))
		{
			showDistance = Integer.parseInt(sharedPreferences.getString(getString(R.string.pref_showdistance_int), getString(R.string.def_showdistance)));
		}
		else if (getString(R.string.pref_maprenderinterval).equals(key))
		{
			updatePeriod = sharedPreferences.getInt(getString(R.string.pref_maprenderinterval), resources.getInteger(R.integer.def_maprenderinterval)) * 100;
		}
		else if (getString(R.string.pref_mapfollowonloc).equals(key))
		{
			followOnLocation = sharedPreferences.getBoolean(getString(R.string.pref_mapfollowonloc), resources.getBoolean(R.bool.def_mapfollowonloc));
		}
		else if (getString(R.string.pref_maphideondrag).equals(key))
		{
			map.setHideOnDrag(sharedPreferences.getBoolean(getString(R.string.pref_maphideondrag), resources.getBoolean(R.bool.def_maphideondrag)));
		}
		else if (getString(R.string.pref_unfollowontap).equals(key))
		{
			map.setStrictUnfollow(!sharedPreferences.getBoolean(getString(R.string.pref_unfollowontap), resources.getBoolean(R.bool.def_unfollowontap)));
		}
		else if (getString(R.string.pref_lookahead).equals(key))
		{
			map.setLookAhead(sharedPreferences.getInt(getString(R.string.pref_lookahead), resources.getInteger(R.integer.def_lookahead)));
		}
		else if (getString(R.string.pref_mapbest).equals(key))
		{
			map.setBestMapEnabled(sharedPreferences.getBoolean(getString(R.string.pref_mapbest), resources.getBoolean(R.bool.def_mapbest)));
		}
		else if (getString(R.string.pref_mapbestinterval).equals(key))
		{
			map.setBestMapInterval(sharedPreferences.getInt(getString(R.string.pref_mapbestinterval), resources.getInteger(R.integer.def_mapbestinterval)) * 1000);
		}
		else if (getString(R.string.pref_cursorvector).equals(key) || getString(R.string.pref_cursorvectormlpr).equals(key))
		{
			map.setCursorVector(Integer.parseInt(sharedPreferences.getString(getString(R.string.pref_cursorvector), getString(R.string.def_cursorvector))),
					sharedPreferences.getInt(getString(R.string.pref_cursorvectormlpr), resources.getInteger(R.integer.def_cursorvectormlpr)));
		}
		else if (getString(R.string.pref_cursorcolor).equals(key))
		{
			map.setCursorColor(sharedPreferences.getInt(key, resources.getColor(R.color.cursor)));
		}
		else if (getString(R.string.pref_navigation_proximity).equals(key))
		{
			map.setProximity(Integer.parseInt(sharedPreferences.getString(getString(R.string.pref_navigation_proximity), getString(R.string.def_navigation_proximity))));
		}
		else if (getString(R.string.pref_unitprecision).equals(key))
		{
			boolean precision = sharedPreferences.getBoolean(key, resources.getBoolean(R.bool.def_unitprecision));
			precisionFormat = precision ? "%.1f" : "%.0f";
		}
		else if (getString(R.string.pref_unitspeed).equals(key))
		{
			int speedIdx = Integer.parseInt(sharedPreferences.getString(getString(R.string.pref_unitspeed), "0"));
			speedFactor = Double.parseDouble(resources.getStringArray(R.array.speed_factors)[speedIdx]);
			speedAbbr = resources.getStringArray(R.array.speed_abbrs)[speedIdx];
			speedUnit.setText(speedAbbr);
		}
		else if (getString(R.string.pref_unitelevation).equals(key))
		{
			int elevationIdx = Integer.parseInt(sharedPreferences.getString(getString(R.string.pref_unitelevation), "0"));
			elevationFactor = Double.parseDouble(resources.getStringArray(R.array.elevation_factors)[elevationIdx]);
			elevationAbbr = resources.getStringArray(R.array.elevation_abbrs)[elevationIdx];
			elevationUnit.setText(elevationAbbr);
		}
	}

	@Override
	public void onClick(View v)
	{
		MenuBuilder mMenu;
		MenuPopupHelper mPopup;
		switch (v.getId())
		{
			case R.id.coordinates:
			{
				// https://gist.github.com/mediavrog/9345938#file-iconizedmenu-java-L55
				mMenu = new MenuBuilder(getActivity());
				mMenu.setCallback(this);
				mPopup = new MenuPopupHelper(getActivity(), mMenu, v);
				mPopup.setForceShowIcon(true);
				// TODO test it on older device
				// getActivity().getMenuInflater();
				new SupportMenuInflater(getActivity()).inflate(R.menu.location_menu, mMenu);
				mPopup.show();
				break;
			}
			case R.id.sats:
			{
				FragmentManager manager = getFragmentManager();
				GPSInfo dialog = new GPSInfo();
				dialog.show(manager, "dialog");
				break;
			}
			case R.id.currentfile:
			{
				FragmentManager manager = getFragmentManager();
				SuitableMapsList dialog = new SuitableMapsList();
				dialog.show(manager, "dialog");
				break;
			}
			case R.id.currentzoom:
			{
				waitBar.setVisibility(View.VISIBLE);
				waitBar.setText(R.string.msg_wait);
				executorThread.execute(new Runnable() {
					public void run()
					{
						synchronized (map)
						{
							if (application.setZoom(1.))
							{
								map.updateMapInfo();
								map.update();
							}
						}
						finishHandler.sendEmptyMessage(0);
					}
				});
			}
		}
	}

	@Override
	public boolean onMenuItemSelected(MenuBuilder builder, MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.action_information:
			{
				FragmentManager manager = getFragmentManager();
				LocationInfo dialog = new LocationInfo(application.getMapCenter());
				dialog.show(manager, "dialog");
				return true;
			}
			case R.id.action_share:
			{
				Intent i = new Intent(android.content.Intent.ACTION_SEND);
				i.setType("text/plain");
				i.putExtra(Intent.EXTRA_SUBJECT, R.string.currentloc);
				double[] loc = application.getMapCenter();
				String spos = StringFormatter.coordinates(application.coordinateFormat, " ", loc[0], loc[1]);
				i.putExtra(Intent.EXTRA_TEXT, spos);
				startActivity(Intent.createChooser(i, getString(R.string.menu_share)));
				return true;
			}
			case R.id.action_view_elsewhere:
			{
				double[] sloc = application.getMapCenter();
				String geoUri = "geo:" + Double.toString(sloc[0]) + "," + Double.toString(sloc[1]);
				Intent intent = new Intent(android.content.Intent.ACTION_VIEW, Uri.parse(geoUri));
				startActivity(intent);
				return true;
			}
			case R.id.action_copy_location:
			{
				double[] cloc = application.getMapCenter();
				String cpos = StringFormatter.coordinates(application.coordinateFormat, " ", cloc[0], cloc[1]);
				Clipboard.copy(getActivity(), cpos);
				return true;
			}
			case R.id.action_paste_location:
			{
				String text = Clipboard.paste(getActivity());
				try
				{
					double c[] = CoordinateParser.parse(text);
					if (!Double.isNaN(c[0]) && !Double.isNaN(c[1]))
					{
						boolean mapChanged = application.setMapCenter(c[0], c[1], true, false);
						if (mapChanged)
							map.updateMapInfo();
						map.update();
						map.setFollowing(false);
					}
				}
				catch (IllegalArgumentException e)
				{
				}
			}
		}
		return false;
	}

	@Override
	public void onMenuModeChange(MenuBuilder builder)
	{
		// TODO Auto-generated method stub

	}

	@SuppressLint("HandlerLeak")
	private class FinishHandler extends Handler
	{
		private final WeakReference<MapFragment> target;

		FinishHandler(MapFragment fragment)
		{
			this.target = new WeakReference<MapFragment>(fragment);
		}

		@Override
		public void handleMessage(Message msg)
		{
			MapFragment mapFragment = target.get();
			if (mapFragment != null)
			{
				mapFragment.waitBar.setVisibility(View.INVISIBLE);
				mapFragment.waitBar.setText("");
			}
		}
	}

}
