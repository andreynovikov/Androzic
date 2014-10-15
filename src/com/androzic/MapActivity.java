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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.londatiga.android.QuickAction3D;
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

import com.androzic.data.Track;
import com.androzic.data.Waypoint;
import com.androzic.map.MapInformation;
import com.androzic.navigation.NavigationService;
import com.androzic.overlay.RouteOverlay;
import com.androzic.util.StringFormatter;

public class MapActivity extends ActionBarActivity implements View.OnClickListener, SeekBar.OnSeekBarChangeListener
{
	private static final String TAG = "MapActivity";

	private static final int RESULT_SAVE_WAYPOINT = 0x400;
	private static final int RESULT_LOAD_MAP = 0x500;
	private static final int RESULT_MANAGE_TRACKS = 0x600;
	private static final int RESULT_EDIT_ROUTE = 0x110;

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

	protected Androzic application;

	protected ExecutorService executorThread = Executors.newSingleThreadExecutor();

	private int waypointSelected = -1;

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
		map = (MapView) findViewById(R.id.mapview);

		findViewById(R.id.finishtrackedit).setOnClickListener(this);
		findViewById(R.id.cutafter).setOnClickListener(this);
		findViewById(R.id.cutbefore).setOnClickListener(this);

		trackBar.setOnSeekBarChangeListener(this);

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

	public boolean waypointTapped(Waypoint waypoint, int x, int y)
	{
		try
		{
			if (application.editingRoute != null)
			{
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
}
