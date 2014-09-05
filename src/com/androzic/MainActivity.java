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

import java.util.ArrayList;
import java.util.UUID;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
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
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import com.androzic.data.Route;
import com.androzic.data.Track;
import com.androzic.data.Waypoint;
import com.androzic.map.Map;
import com.androzic.navigation.NavigationService;
import com.androzic.route.OnRouteActionListener;
import com.androzic.route.RouteList;
import com.androzic.route.RouteProperties;
import com.androzic.route.RouteSave;
import com.androzic.track.OnTrackActionListener;
import com.androzic.track.TrackList;
import com.androzic.track.TrackProperties;
import com.androzic.track.TrackSave;
import com.androzic.track.TrackToRoute;
import com.androzic.ui.DrawerAdapter;
import com.androzic.ui.DrawerItem;
import com.androzic.util.StringFormatter;
import com.androzic.waypoint.OnWaypointActionListener;
import com.androzic.waypoint.WaypointInfo;
import com.androzic.waypoint.WaypointList;
import com.androzic.waypoint.WaypointProperties;

public class MainActivity extends ActionBarActivity implements OnWaypointActionListener, OnMapActionListener, OnRouteActionListener, OnTrackActionListener, OnSharedPreferenceChangeListener, OnClickListener
{
	private static final String TAG = "MainActivity";

	private DrawerLayout mDrawerLayout;
	private ListView mDrawerList;
	private DrawerAdapter mDrawerAdapter;
	private View mDrawerActions;
	private ActionBarDrawerToggle mDrawerToggle;

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

		backToast = Toast.makeText(this, R.string.backQuit, Toast.LENGTH_SHORT);

		application = Androzic.getApplication();

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		onSharedPreferenceChanged(settings, getString(R.string.pref_exit));
		onSharedPreferenceChanged(settings, getString(R.string.pref_hidestatusbar));
		onSharedPreferenceChanged(settings, getString(R.string.pref_orientation));
		settings.registerOnSharedPreferenceChangeListener(this);

		mDrawerItems = new ArrayList<DrawerItem>();

		Resources resources = getResources();

		Drawable icon;
		Intent action;
		Fragment fragment;

		// add main items to drawer list
		icon = resources.getDrawable(R.drawable.ic_action_mapmode);
		fragment = Fragment.instantiate(this, MapFragment.class.getName());
		mDrawerItems.add(new DrawerItem(icon, getString(R.string.menu_map), fragment));
		icon = resources.getDrawable(R.drawable.ic_action_location);
		fragment = Fragment.instantiate(this, WaypointList.class.getName());
		mDrawerItems.add(new DrawerItem(icon, getString(R.string.menu_waypoints), fragment));
		icon = resources.getDrawable(R.drawable.ic_action_directions);
		fragment = Fragment.instantiate(this, RouteList.class.getName());
		mDrawerItems.add(new DrawerItem(icon, getString(R.string.menu_routes), fragment));
		icon = resources.getDrawable(R.drawable.ic_action_track);
		fragment = Fragment.instantiate(this, TrackList.class.getName());
		mDrawerItems.add(new DrawerItem(icon, getString(R.string.menu_tracks), fragment));

		// add plugins to drawer list
		mDrawerItems.add(new DrawerItem());
		icon = resources.getDrawable(R.drawable.ic_action_location_found);
		action = new Intent(this, HSIActivity.class);
		mDrawerItems.add(new DrawerItem(icon, getString(R.string.menu_hsi), action));
		java.util.Map<String, Pair<Drawable, Intent>> plugins = application.getPluginsViews();
		for (String plugin : plugins.keySet())
		{
			mDrawerItems.add(new DrawerItem(plugins.get(plugin).first, plugin, plugins.get(plugin).second));
		}

		// add supplementary items to drawer list
		mDrawerItems.add(new DrawerItem());
		icon = resources.getDrawable(R.drawable.ic_action_settings);
		action = new Intent(this, PreferencesHC.class);
		mDrawerItems.add(new DrawerItem(icon, getString(R.string.menu_preferences), action).makeSupplementary());
		icon = resources.getDrawable(R.drawable.ic_action_info);
		fragment = Fragment.instantiate(this, About.class.getName());
		mDrawerItems.add(new DrawerItem(icon, getString(R.string.menu_about), fragment).makeSupplementary());

		mTitle = mDrawerTitle = getTitle();
		mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
		mDrawerList = (ListView) findViewById(R.id.left_drawer);
		mDrawerActions = findViewById(R.id.right_drawer);

		// set a custom shadow that overlays the main content when the drawer opens
		mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);
		// set up the drawer's list view with items and click listener
		mDrawerAdapter = new DrawerAdapter(this, mDrawerItems);
		mDrawerList.setAdapter(mDrawerAdapter);
		mDrawerList.setOnItemClickListener(new DrawerItemClickListener());

		// enable ActionBar app icon to behave as action to toggle nav drawer
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		getSupportActionBar().setHomeButtonEnabled(true);

		// ActionBarDrawerToggle ties together the the proper interactions
		// between the sliding drawer and the action bar app icon
		mDrawerToggle = new ActionBarDrawerToggle(this, /* host Activity */
		mDrawerLayout, /* DrawerLayout object */
		R.drawable.ic_drawer, /* nav drawer image to replace 'Up' caret */
		R.string.drawer_open, /* "open drawer" description for accessibility */
		R.string.drawer_close /* "close drawer" description for accessibility */
		) {
			public void onDrawerClosed(View drawerView)
			{
				if (drawerView == mDrawerList)
				{
					getSupportActionBar().setTitle(mTitle);
					supportInvalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
				}
			}

			public void onDrawerOpened(View drawerView)
			{
				if (drawerView == mDrawerList)
				{
					getSupportActionBar().setTitle(mDrawerTitle);
					supportInvalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
				}
			}
		};
		mDrawerLayout.setDrawerListener(mDrawerToggle);
		getSupportFragmentManager().addOnBackStackChangedListener(mBackStackChangedListener);

		// set button actions
		findViewById(R.id.zoomin).setOnClickListener(this);
		findViewById(R.id.zoomout).setOnClickListener(this);
		findViewById(R.id.nextmap).setOnClickListener(this);
		findViewById(R.id.prevmap).setOnClickListener(this);

		if (savedInstanceState == null)
		{
			mDrawerAdapter.setSelectedItem(-1);
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
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.general_menu, menu);
		return super.onCreateOptionsMenu(menu);
	}

	/* Called whenever we call invalidateOptionsMenu() */
	@Override
	public boolean onPrepareOptionsMenu(Menu menu)
	{
		// If the nav drawer is open, hide action items related to the content view
		boolean hide = mDrawerLayout.isDrawerOpen(mDrawerList) || mDrawerAdapter.getSelectedItem() != 0;
		menu.findItem(R.id.action_search).setVisible(!hide);
		menu.findItem(R.id.action_locating).setVisible(!hide);
		menu.findItem(R.id.action_tracking).setVisible(!hide);
		menu.findItem(R.id.action_locating).setChecked(application.isLocating());
		menu.findItem(R.id.action_tracking).setChecked(application.isTracking());
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		// The action bar home/up action should open or close the drawer.
		// ActionBarDrawerToggle will take care of this.
		if (mDrawerToggle.onOptionsItemSelected(item))
		{
			return true;
		}
		// Handle action buttons
		switch (item.getItemId())
		{
		/*
		 * case R.id.action_websearch:
		 * // create intent to perform web search for this planet
		 * Intent intent = new Intent(Intent.ACTION_WEB_SEARCH);
		 * intent.putExtra(SearchManager.QUERY, getActionBar().getTitle());
		 * // catch event that there's no activity to handle intent
		 * if (intent.resolveActivity(getPackageManager()) != null)
		 * {
		 * startActivity(intent);
		 * }
		 * else
		 * {
		 * Toast.makeText(this, R.string.app_not_available, Toast.LENGTH_LONG).show();
		 * }
		 * return true;
		 */
			case R.id.action_locating:
				application.enableLocating(!application.isLocating());
				return true;
			case R.id.action_tracking:
				application.enableTracking(!application.isTracking());
				return true;
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
		if (item.type == DrawerItem.ItemType.ACTION)
		{
			if (position > 0)
				startActivity(item.action);
		}
		// Fragments
		else
		{
			FragmentManager fm = getSupportFragmentManager();
			if (position == 0)
			{
				FragmentTransaction ft = fm.beginTransaction();
				if (fm.getBackStackEntryCount() == 0)
				{
					ft.add(R.id.content_frame, item.fragment, "map");
				}
				else
				{
					fm.popBackStackImmediate(0, FragmentManager.POP_BACK_STACK_INCLUSIVE);
					Fragment fragment = fm.findFragmentByTag("map");
					ft.attach(fragment);
				}
				ft.commit();
			}
			else
			{
				if (fm.getBackStackEntryCount() > 0)
					fm.popBackStackImmediate(0, FragmentManager.POP_BACK_STACK_INCLUSIVE);
				addFragment(item.fragment);
			}
			// update selected item and title, then close the drawer
			updateDrawerUI(item, position);
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
	public void onWaypointNavigate(Waypoint waypoint)
	{
		Intent intent = new Intent(application, NavigationService.class).setAction(NavigationService.NAVIGATE_MAPOBJECT);
		intent.putExtra(NavigationService.EXTRA_NAME, waypoint.name);
		intent.putExtra(NavigationService.EXTRA_LATITUDE, waypoint.latitude);
		intent.putExtra(NavigationService.EXTRA_LONGITUDE, waypoint.longitude);
		intent.putExtra(NavigationService.EXTRA_PROXIMITY, waypoint.proximity);
		application.startService(intent);
		selectItem(0);
	}

	@Override
	public void onWaypointEdit(Waypoint waypoint)
	{
		// TODO Refactor
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

/*
	if (resultCode == RESULT_OK)
	{
		final Androzic application = Androzic.getApplication();
		int[] indexes = data.getExtras().getIntArray("index");
		for (int index : indexes)
		{
			RouteOverlay newRoute = new RouteOverlay(application.getRoute(index));
			application.routeOverlays.add(newRoute);
		}
	}
*/

	@Override
	public void onRouteDetails(Route route)
	{
//		startActivityForResult(new Intent(this, RouteDetails.class).putExtra("index", application.getRouteIndex(route)), RESULT_ROUTE_DETAILS);
	}

	@Override
	public void onRouteNavigate(Route route)
	{
//		startActivityForResult(new Intent(this, RouteStart.class).putExtra("index", application.getRouteIndex(route)), RESULT_START_ROUTE);
	}

	@Override
	public void onRouteEdit(Route route)
	{
		startActivity(new Intent(this, RouteProperties.class).putExtra("index", application.getRouteIndex(route)));
	}

	@Override
	public void onRouteEditPath(Route route)
	{
//		route.show = true;
//		setResult(RESULT_OK, new Intent().putExtra("index", application.getRouteIndex(route)));
	}

	@Override
	public void onRouteSave(Route route)
	{
		startActivity(new Intent(this, RouteSave.class).putExtra("index", application.getRouteIndex(route)));
	}

/*
 				if (resultCode == Activity.RESULT_OK)
				{
					final Androzic application = Androzic.getApplication();
					int[] indexes = data.getExtras().getIntArray("index");
					for (int index : indexes)
					{
						TrackOverlay newTrack = new TrackOverlay(application.getTrack(index));
						application.fileTrackOverlays.add(newTrack);
					}
				}
 */

	@Override
	public void onTrackEdit(Track track)
	{
		startActivity(new Intent(this, TrackProperties.class).putExtra("INDEX", application.getTrackIndex(track)));
	}

	@Override
	public void onTrackEditPath(Track track)
	{
//		setResult(RESULT_OK, new Intent().putExtra("index", application.getTrackIndex(track)));
	}

	@Override
	public void onTrackToRoute(Track track)
	{
		startActivity(new Intent(this, TrackToRoute.class).putExtra("INDEX", application.getTrackIndex(track)));
	}

	@Override
	public void onTrackSave(Track track)
	{
		startActivity(new Intent(this, TrackSave.class).putExtra("INDEX", application.getTrackIndex(track)));
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
	{
		Resources resources = getResources();

		if (getString(R.string.pref_exit).equals(key))
		{
			exitConfirmation = Integer.parseInt(sharedPreferences.getString(key, "0"));
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

	private void updateDrawerUI(DrawerItem item, int position)
	{
		mDrawerList.setItemChecked(position, true);
		mDrawerAdapter.setSelectedItem(position);
		setTitle(item.name);
		if (item.fragment instanceof MapFragment)
			mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, mDrawerActions);
		else
			mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, mDrawerActions);
	}

	private void restoreDrawerUI()
	{
		FragmentManager fm = getSupportFragmentManager();
		String tag = "map";
		if (fm.getBackStackEntryCount() > 0)
		{
			FragmentManager.BackStackEntry bse = fm.getBackStackEntryAt(fm.getBackStackEntryCount() - 1);
			tag = bse.getName();
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
						// TODO change context everywhere?
						stopService(new Intent(MainActivity.this, NavigationService.class));
						MainActivity.this.finish();
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
			case R.id.zoomin:
				application.getMapHolder().zoomIn();
				break;
			case R.id.zoomout:
				application.getMapHolder().zoomOut();
				break;
			case R.id.nextmap:
				application.getMapHolder().nextMap();
				break;
			case R.id.prevmap:
				application.getMapHolder().previousMap();
				break;
		}
	}

	private void addFragment(Fragment fragment)
	{
		FragmentManager fm = getSupportFragmentManager();
		// Get topmost fragment		
		String tag = "map";
		if (fm.getBackStackEntryCount() > 0)
		{
			FragmentManager.BackStackEntry bse = fm.getBackStackEntryAt(fm.getBackStackEntryCount() - 1);
			tag = bse.getName();
		}
		FragmentTransaction ft = fm.beginTransaction();
		Fragment top = getSupportFragmentManager().findFragmentByTag(tag);
		// Detach it
		ft.detach(top);
		// Add new fragment
		addFragment(fragment, ft);
		ft.commit();
	}

	private void addFragment(Fragment fragment, FragmentTransaction ft)
	{
		// Add fragment to back stack with unique tag
		String tag = UUID.randomUUID().toString();
		ft.add(R.id.content_frame, fragment, tag);
		ft.addToBackStack(tag);
	}

}
