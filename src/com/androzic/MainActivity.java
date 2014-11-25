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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Stack;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.Pair;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import com.androzic.data.Route;
import com.androzic.data.Track;
import com.androzic.data.Waypoint;
import com.androzic.data.WaypointSet;
import com.androzic.map.Map;
import com.androzic.map.MapInformation;
import com.androzic.map.OnMapActionListener;
import com.androzic.route.OnRouteActionListener;
import com.androzic.route.RouteDetails;
import com.androzic.route.RouteList;
import com.androzic.route.RouteProperties;
import com.androzic.route.RouteSave;
import com.androzic.route.RouteStart;
import com.androzic.track.OnTrackActionListener;
import com.androzic.track.TrackList;
import com.androzic.track.TrackProperties;
import com.androzic.track.TrackSave;
import com.androzic.track.TrackToRoute;
import com.androzic.ui.DrawerAdapter;
import com.androzic.ui.DrawerItem;
import com.androzic.util.StringFormatter;
import com.androzic.waypoint.OnWaypointActionListener;
import com.androzic.waypoint.WaypointDetails;
import com.androzic.waypoint.WaypointInfo;
import com.androzic.waypoint.WaypointList;
import com.androzic.waypoint.WaypointProperties;
import com.shamanland.fab.FloatingActionButton;

public class MainActivity extends ActionBarActivity implements FragmentHolder, OnWaypointActionListener, OnMapActionListener, OnRouteActionListener, OnTrackActionListener, OnSharedPreferenceChangeListener
{
	private static final String TAG = "MainActivity";

	private DrawerLayout mDrawerLayout;
	private ListView mDrawerList;
	private DrawerAdapter mDrawerAdapter;
	private ActionBarDrawerToggle mDrawerToggle;
	private Drawable mHomeDrawable;
	private Toolbar mToolbar;
	private int mToolbarHeight;
	private FloatingActionButton mActionButton;

	private CharSequence mDrawerTitle;
	private CharSequence mTitle;
	private ArrayList<DrawerItem> mDrawerItems;

	private int exitConfirmation;
	private boolean secondBack;
	private Toast backToast;

	protected Androzic application;

	@SuppressLint("ShowToast")
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		Log.e(TAG, "onCreate()");

		setContentView(R.layout.activity_main);

	    mToolbar = (Toolbar) findViewById(R.id.action_toolbar);
	    setSupportActionBar(mToolbar);

	    mActionButton = (FloatingActionButton) findViewById(R.id.toolbar_action_button);
	    
		backToast = Toast.makeText(this, R.string.backQuit, Toast.LENGTH_SHORT);

		application = Androzic.getApplication();

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		onSharedPreferenceChanged(settings, getString(R.string.pref_exit));
		onSharedPreferenceChanged(settings, getString(R.string.pref_hidestatusbar));
		onSharedPreferenceChanged(settings, getString(R.string.pref_orientation));
		settings.registerOnSharedPreferenceChangeListener(this);

		mTitle = mDrawerTitle = getTitle();
		mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
		// set a custom shadow that overlays the main content when the drawer opens
		mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);
		// set up the drawer's list view with items and click listener
		mDrawerItems = new ArrayList<DrawerItem>();
		mDrawerList = (ListView) findViewById(R.id.left_drawer);
		mDrawerAdapter = new DrawerAdapter(this, mDrawerItems);
		mDrawerList.setAdapter(mDrawerAdapter);
		mDrawerList.setOnItemClickListener(new DrawerItemClickListener());
		initializeDrawerItems();

		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		getSupportActionBar().setHomeButtonEnabled(true);
		
		mHomeDrawable = getV7DrawerToggleDelegate().getThemeUpIndicator();
		
		mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, mToolbar, R.string.drawer_open, R.string.drawer_close) {
			public void onDrawerClosed(View drawerView)
			{
				if (drawerView == mDrawerList)
				{
					getSupportActionBar().setTitle(mTitle);
					supportInvalidateOptionsMenu();
				}
			}

			public void onDrawerOpened(View drawerView)
			{
				if (drawerView == mDrawerList)
				{
					getSupportActionBar().setTitle(mDrawerTitle);
					supportInvalidateOptionsMenu();
				}
			}
		};
		mDrawerLayout.setDrawerListener(mDrawerToggle);

		getSupportFragmentManager().addOnBackStackChangedListener(mBackStackChangedListener);

		if (savedInstanceState == null)
		{
			mDrawerAdapter.setSelectedItem(-1);
			selectItem(0);
		}
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
		else if (intent.hasExtra("show"))
		{
			Serializable object = intent.getExtras().getSerializable("show");
			if (Class.class.isInstance(object))
			{
				@SuppressWarnings("rawtypes")
				String name = ((Class) object).getName();
				Fragment f = Fragment.instantiate(this, name);
				intent.removeExtra("show");
				f.setArguments(intent.getExtras());
				addFragment(f, name);
			}
		}
		else if (intent.hasExtra("lat") && intent.hasExtra("lon"))
		{
			if (application.ensureVisible(intent.getExtras().getDouble("lat"), intent.getExtras().getDouble("lon")))
				application.getMapHolder().mapChanged();
			else
				application.getMapHolder().conditionsChanged();
			selectItem(0);
		}
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		Log.e(TAG, "onResume()");
	}

	@Override
	protected void onPause()
	{
		super.onPause();
		Log.e(TAG, "onPause()");
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		Log.e(TAG, "onDestroy()");

		getSupportFragmentManager().removeOnBackStackChangedListener(mBackStackChangedListener);
		PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);

		if (isFinishing())
			application.clear();

		application = null;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
//		MenuInflater inflater = getMenuInflater();
//		inflater.inflate(R.menu.general_menu, menu);
		return super.onCreateOptionsMenu(menu);
	}

	/* Called whenever we call invalidateOptionsMenu() */
	@Override
	public boolean onPrepareOptionsMenu(Menu menu)
	{
		// If the nav drawer is open, hide action items related to the content view
		//boolean hide = mDrawerLayout.isDrawerOpen(mDrawerList) || mDrawerAdapter.getSelectedItem() != 0;
		//menu.findItem(R.id.action_search).setVisible(!hide);
		//menu.findItem(R.id.action_locating).setVisible(!hide);
		//menu.findItem(R.id.action_tracking).setVisible(!hide);
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		// Handle action buttons
		switch (item.getItemId())
		{
			case android.R.id.home:
				FragmentManager fm = getSupportFragmentManager();
				if (fm.getBackStackEntryCount() > 0)
				{
					View v = getCurrentFocus();
					if (v != null)
					{
						// Hide keyboard
						final InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
						imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
					}
					fm.popBackStack();
					return true;
				}
				else
				{
					if (mDrawerToggle.onOptionsItemSelected(item))
					{
						return true;
					}
				}
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	/* The click listener for ListView in the navigation drawer */
	private class DrawerItemClickListener implements ListView.OnItemClickListener
	{
		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position, long id)
		{
			selectItem(position);
		}
	}

	private void selectItem(int position)
	{
		if (mDrawerAdapter.getSelectedItem() == position)
			return;
		
		DrawerItem item = mDrawerItems.get(position);
		// Actions
		if (item.type == DrawerItem.ItemType.INTENT)
		{
			if (position > 0)
				startActivity(item.intent);
		}
		// Fragments
		else if (item.type == DrawerItem.ItemType.FRAGMENT)
		{
			FragmentManager fm = getSupportFragmentManager();
			if (fm.getBackStackEntryCount() > 0)
			{
				fm.popBackStackImmediate(0, FragmentManager.POP_BACK_STACK_INCLUSIVE);
			}
			FragmentTransaction ft = fm.beginTransaction();
			Fragment parent = fm.findFragmentById(R.id.content_frame);
			if (parent != null)
			{
				ft.detach(parent);
			}
			Fragment fragment = fm.findFragmentByTag(item.name);
			if (fragment != null)
			{
				ft.attach(fragment);
			}
			else
			{
				ft.add(R.id.content_frame, item.fragment, item.name);
			}
			ft.commit();
			// update selected item and title, then close the drawer
			updateDrawerUI(item, position);
		}
		else if (item.type == DrawerItem.ItemType.ACTION)
		{
			Log.e(TAG, "ACTION");
			runOnUiThread(item.action);
		}
		mDrawerLayout.closeDrawer(mDrawerList);
	}

	@Override
	public void setTitle(CharSequence title)
	{
		mTitle = title;
		getSupportActionBar().setTitle(mTitle);
	}

	/**
	 * When using the ActionBarDrawerToggle, you must call it during
	 * onPostCreate() and onConfigurationChanged()...
	 */

	@Override
	protected void onPostCreate(Bundle savedInstanceState)
	{
		super.onPostCreate(savedInstanceState);
		// Sync the toggle state after onRestoreInstanceState has occurred.
		mDrawerToggle.syncState();
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig)
	{
		super.onConfigurationChanged(newConfig);
		// Pass any configuration change to the drawer toggles
		mDrawerToggle.onConfigurationChanged(newConfig);
	}

	@Override
	public void onWaypointView(Waypoint waypoint)
	{
		if (application.ensureVisible(waypoint))
			application.getMapHolder().mapChanged();
		else
			application.getMapHolder().conditionsChanged();
		selectItem(0);
	}

	@Override
	public void onWaypointShow(Waypoint waypoint)
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
	}

	@Override
	public void onWaypointDetails(Waypoint waypoint)
	{
		Location loc = application.getLocationAsLocation();
        FragmentManager fm = getSupportFragmentManager();
        WaypointDetails waypointDetails = (WaypointDetails) fm.findFragmentByTag("waypoint_details");
        if (waypointDetails == null)
        	waypointDetails = new WaypointDetails();
        waypointDetails.setWaypoint(waypoint);
		Bundle args = new Bundle();
		args.putDouble("lat", loc.getLatitude());
		args.putDouble("lon", loc.getLongitude());
		waypointDetails.setArguments(args);
		addFragment(waypointDetails, "waypoint_details");
	}

	@Override
	public void onWaypointNavigate(Waypoint waypoint)
	{
		application.startNavigation(waypoint);
		selectItem(0);
	}

	@Override
	public void onWaypointEdit(Waypoint waypoint)
	{
		FragmentManager fm = getSupportFragmentManager();
		WaypointProperties waypointProperties = (WaypointProperties) fm.findFragmentByTag("waypoint_properties");
		if (waypointProperties == null)
			waypointProperties = (WaypointProperties) Fragment.instantiate(this, WaypointProperties.class.getName());
		waypointProperties.setWaypoint(waypoint);
		addFragment(waypointProperties, "waypoint_properties");
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
				sendBroadcast(new Intent(Androzic.BROADCAST_WAYPOINT_REMOVED));
				application.getMapHolder().refreshMap();
			}
		}).setNegativeButton(R.string.no, null).show();
	}

	@Override
	public void onOpenMap()
	{
        MapList mapList = (MapList) Fragment.instantiate(this, MapList.class.getName());
		addFragment(mapList, "map_list");
	}

	@Override
	public void onMapDetails(Map map)
	{
		//TODO Should show any map information, not only current
        MapInformation mapInformation = (MapInformation) Fragment.instantiate(this, MapInformation.class.getName());
		addFragment(mapInformation, "map_information");
	}

	@Override
	public void onMapSelectedAtPosition(Map map)
	{
		if (application.setMap(map))
			application.getMapHolder().mapChanged();
	}

	@Override
	public void onMapSelected(Map map)
	{
		if (application.loadMap(map))
			application.getMapHolder().mapChanged();
	}

	@Override
	public void onRouteDetails(Route route)
	{
		FragmentManager fm = getSupportFragmentManager();
		RouteDetails routeDetails = (RouteDetails) fm.findFragmentByTag("route_details");
		if (routeDetails == null)
			routeDetails = (RouteDetails) Fragment.instantiate(this, RouteDetails.class.getName());
		routeDetails.setRoute(route);
		addFragment(routeDetails, "route_properties");
	}

	@Override
	public void onRouteNavigate(Route route)
	{
        FragmentManager fm = getSupportFragmentManager();
        RouteStart routeStartDialog = new RouteStart(route);
        routeStartDialog.show(fm, "route_start");
	}
	
	@Override
	public void onRouteNavigate(Route route, int direction, int waypointIndex)
	{
		application.startNavigation(route, direction, waypointIndex);
		selectItem(0);
	}

	@Override
	public void onRouteEdit(Route route)
	{
		FragmentManager fm = getSupportFragmentManager();
		RouteProperties routeProperties = (RouteProperties) fm.findFragmentByTag("route_properties");
		if (routeProperties == null)
			routeProperties = (RouteProperties) Fragment.instantiate(this, RouteProperties.class.getName());
		routeProperties.setRoute(route);
		addFragment(routeProperties, "route_properties");
	}

	@Override
	public void onRouteEditPath(Route route)
	{
		application.editingRoute = route;
		application.editingRoute.show = true;
		application.editingRoute.editing = true;
		application.routeEditingWaypoints = new Stack<Waypoint>();
		application.dispatchRoutePropertiesChanged(route);
		selectItem(0);
	}

	@Override
	public void onRouteSave(Route route)
	{
        FragmentManager fm = getSupportFragmentManager();
        RouteSave routeSaveDialog = new RouteSave(route);
        routeSaveDialog.show(fm, "route_save");
	}

	@Override
	public void onRouteWaypointEdit(Waypoint waypoint)
	{
		FragmentManager fm = getSupportFragmentManager();
		WaypointProperties waypointProperties = (WaypointProperties) fm.findFragmentByTag("waypoint_properties");
		if (waypointProperties == null)
			waypointProperties = (WaypointProperties) Fragment.instantiate(this, WaypointProperties.class.getName());
		waypointProperties.setRouteWaypoint(waypoint);
		addFragment(waypointProperties, "waypoint_properties");
	}

	@Override
	public void onTrackEdit(Track track)
	{
		FragmentManager fm = getSupportFragmentManager();
		TrackProperties trackProperties = (TrackProperties) fm.findFragmentByTag("track_properties");
		if (trackProperties == null)
			trackProperties = (TrackProperties) Fragment.instantiate(this, TrackProperties.class.getName());
		trackProperties.setTrack(track);
		addFragment(trackProperties, "track_properties");
	}

	@Override
	public void onTrackEditPath(Track track)
	{
//		setResult(RESULT_OK, new Intent().putExtra("index", application.getTrackIndex(track)));
	}

	@Override
	public void onTrackToRoute(Track track)
	{
		FragmentManager fm = getSupportFragmentManager();
		TrackToRoute trackToRouteDialog = new TrackToRoute(track);
		trackToRouteDialog.show(fm, "track_to_route");
	}

	@Override
	public void onTrackSave(Track track)
	{
        FragmentManager fm = getSupportFragmentManager();
        TrackSave trackSaveDialog = new TrackSave(track);
        trackSaveDialog.show(fm, "track_save");
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
	{
		Resources resources = getResources();

		if (getString(R.string.pref_exit).equals(key))
		{
			exitConfirmation = Integer.parseInt(sharedPreferences.getString(key, "0"));
			initializeDrawerItems();
			secondBack = false;
		}
		else if (getString(R.string.pref_hidestatusbar).equals(key))
		{
			if (sharedPreferences.getBoolean(key, resources.getBoolean(R.bool.def_hidestatusbar)))
				getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
			else
				getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
		}
		else if (getString(R.string.pref_orientation).equals(key))
		{
			setRequestedOrientation(Integer.parseInt(sharedPreferences.getString(key, "-1")));
		}
	}

	private void initializeDrawerItems()
	{
		if (mDrawerItems == null)
			return;

		mDrawerItems.clear();

		Resources resources = getResources();

		Drawable icon;
		Intent action;
		Fragment fragment;
		FragmentManager fm = getSupportFragmentManager();		

		// add main items to drawer list
		icon = resources.getDrawable(R.drawable.ic_map_white_24dp);
		fragment = fm.findFragmentByTag(getString(R.string.menu_map));
		if (fragment == null)
			fragment = Fragment.instantiate(this, MapFragment.class.getName());
		mDrawerItems.add(new DrawerItem(icon, getString(R.string.menu_map), fragment));

		icon = resources.getDrawable(R.drawable.ic_place_white_24dp);
		fragment = fm.findFragmentByTag(getString(R.string.menu_waypoints));
		if (fragment == null)
			fragment = Fragment.instantiate(this, WaypointList.class.getName());
		mDrawerItems.add(new DrawerItem(icon, getString(R.string.menu_waypoints), fragment));

		icon = resources.getDrawable(R.drawable.ic_directions_white_24dp);
		fragment = fm.findFragmentByTag(getString(R.string.menu_routes));
		if (fragment == null)
			fragment = Fragment.instantiate(this, RouteList.class.getName());
		mDrawerItems.add(new DrawerItem(icon, getString(R.string.menu_routes), fragment));

		icon = resources.getDrawable(R.drawable.ic_gesture_white_24dp);
		fragment = fm.findFragmentByTag(getString(R.string.menu_tracks));
		if (fragment == null)
			fragment = Fragment.instantiate(this, TrackList.class.getName());
		mDrawerItems.add(new DrawerItem(icon, getString(R.string.menu_tracks), fragment));

		// add plugins to drawer list
		mDrawerItems.add(new DrawerItem());
		icon = resources.getDrawable(R.drawable.ic_my_location_white_24dp);
		action = new Intent(this, HSIActivity.class);
		mDrawerItems.add(new DrawerItem(icon, getString(R.string.menu_hsi), action).makeMinor());

		java.util.Map<String, Pair<Drawable, Intent>> plugins = application.getPluginsViews();
		for (String plugin : plugins.keySet())
		{
			mDrawerItems.add(new DrawerItem(plugins.get(plugin).first, plugin, plugins.get(plugin).second).makeMinor());
		}

		// add supplementary items to drawer list
		mDrawerItems.add(new DrawerItem());
		icon = resources.getDrawable(R.drawable.ic_settings_white_24dp);
		fragment = fm.findFragmentByTag(getString(R.string.menu_preferences));
		if (fragment == null)
			fragment = Fragment.instantiate(this, PreferencesHC.class.getName());
		mDrawerItems.add(new DrawerItem(icon, getString(R.string.menu_preferences), fragment).makeMinor().makeSupplementary());

		icon = resources.getDrawable(R.drawable.ic_info_white_24dp);
		fragment = fm.findFragmentByTag(getString(R.string.menu_about));
		if (fragment == null)
			fragment = Fragment.instantiate(this, About.class.getName());
		mDrawerItems.add(new DrawerItem(icon, getString(R.string.menu_about), fragment).makeMinor().makeSupplementary());

		if (exitConfirmation == 3)
		{
			icon = resources.getDrawable(R.drawable.ic_open_in_new_white_24dp);
			Runnable exit = new Runnable() {
				@Override
				public void run()
				{
					Log.e(TAG, "Exit");
					MainActivity.this.finish();
				}
			};
			mDrawerItems.add(new DrawerItem(icon, getString(R.string.menu_exit), exit).makeMinor().makeSupplementary());
		}

		mDrawerAdapter.notifyDataSetChanged();
	}

	private void updateDrawerUI(DrawerItem item, int position)
	{
		mDrawerList.setItemChecked(position, true);
		mDrawerAdapter.setSelectedItem(position);
		setTitle(item.name);
	}

	private void restoreDrawerUI()
	{
		FragmentManager fm = getSupportFragmentManager();
		String tag = "map";
		if (fm.getBackStackEntryCount() > 0)
		{
			mDrawerToggle.setDrawerIndicatorEnabled(false);
			
			// FIXME This is the uggliest hack I have ever seen!
			getV7DrawerToggleDelegate().setActionBarUpIndicator(mHomeDrawable, R.string.cancel);
		    setSupportActionBar(mToolbar);
		    // End of hack
			
			mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, mDrawerList);
			FragmentManager.BackStackEntry bse = fm.getBackStackEntryAt(fm.getBackStackEntryCount() - 1);
			tag = bse.getName();
		}
		else
		{
			mDrawerToggle.setDrawerIndicatorEnabled(true);
			mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, mDrawerList);
		}
		Log.e(TAG, "restoreDrawerUI: " + tag);
		Fragment fragment = fm.findFragmentByTag(tag);
		for (int pos = 0; pos < mDrawerItems.size(); pos++)
		{
			DrawerItem item = mDrawerItems.get(pos);
			if (item.type == DrawerItem.ItemType.FRAGMENT && item.fragment == fragment)
			{
				updateDrawerUI(item, pos);
				break;
			}
		}
	}
	
	private FragmentManager.OnBackStackChangedListener mBackStackChangedListener = new FragmentManager.OnBackStackChangedListener()
	{
		@Override
        public void onBackStackChanged()
        {
			restoreDrawerUI();
        }
    };

	final Handler backHandler = new Handler();

	@Override
	public void onBackPressed()
	{
		if (getSupportFragmentManager().getBackStackEntryCount() > 0)
		{
			super.onBackPressed();
			return;
		}
		else if (mDrawerAdapter.getSelectedItem() != 0)
		{
			selectItem(0);
			return;
		}
		
		switch (exitConfirmation)
		{
			case 0:
				// wait for second back
				if (secondBack)
				{
					backToast.cancel();
					MainActivity.this.finish();
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
						MainActivity.this.finish();
					}
				}).setNegativeButton(R.string.no, null).show();
				return;
			case 3:
				// Quit is performed from menu
				return;
			default:
				super.onBackPressed();
		}
	}

	@Override
	public void addFragment(Fragment fragment, String tag)
	{
		FragmentManager fm = getSupportFragmentManager();
		// Get topmost fragment
		Fragment parent;
		if (fm.getBackStackEntryCount() > 0)
		{
			FragmentManager.BackStackEntry bse = fm.getBackStackEntryAt(fm.getBackStackEntryCount() - 1);
			parent = fm.findFragmentByTag(bse.getName());
		}
		else
		{
			parent = fm.findFragmentById(R.id.content_frame);
		}
		FragmentTransaction ft = fm.beginTransaction();
		// Detach parent
		ft.detach(parent);
		// Add new fragment to back stack
		ft.add(R.id.content_frame, fragment, tag);
		ft.addToBackStack(tag);
		ft.commit();
	}

	@Override
	public FloatingActionButton enableActionButton()
	{
		// FIXME Do not hard code dimensions
		int height = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 104, getResources().getDisplayMetrics());
		ViewGroup.LayoutParams params = mToolbar.getLayoutParams();
		mToolbarHeight = params.height;
		params.height = height;
		mActionButton.setVisibility(View.VISIBLE);
		return mActionButton;
	}

	@Override
	public void disableActionButton()
	{
		// FIXME Do not hardcode dimensions
		ViewGroup.LayoutParams params = mToolbar.getLayoutParams();
		params.height = mToolbarHeight;
		mActionButton.setOnClickListener(null);
		mActionButton.setVisibility(View.GONE);
	}

}
