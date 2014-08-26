package com.androzic;

import java.util.ArrayList;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
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

import com.androzic.data.Waypoint;
import com.androzic.map.Map;
import com.androzic.navigation.NavigationService;
import com.androzic.route.RouteList;
import com.androzic.route.RouteListActivity;
import com.androzic.ui.DrawerAdapter;
import com.androzic.ui.DrawerItem;
import com.androzic.waypoint.OnWaypointActionListener;
import com.androzic.waypoint.WaypointList;

public class MainActivity extends ActionBarActivity implements OnWaypointActionListener, OnMapActionListener, OnSharedPreferenceChangeListener, OnClickListener
{
	private static final String TAG = "MapActivity";

	private DrawerLayout mDrawerLayout;
	private ListView mDrawerList;
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
		icon = resources.getDrawable(R.drawable.ic_action_map);
		fragment = Fragment.instantiate(this, MapFragment.class.getName());
		mDrawerItems.add(new DrawerItem(icon, getString(R.string.menu_maps), fragment));
		icon = resources.getDrawable(R.drawable.ic_action_location);
		fragment = Fragment.instantiate(this, WaypointList.class.getName());
		mDrawerItems.add(new DrawerItem(icon, getString(R.string.menu_waypoints), fragment));
		icon = resources.getDrawable(R.drawable.ic_action_directions);
		action = new Intent(this, RouteListActivity.class).putExtra("MODE", RouteList.MODE_MANAGE);
		mDrawerItems.add(new DrawerItem(icon, getString(R.string.menu_routes), action));

		mDrawerItems.add(new DrawerItem(getString(R.string.menu_preferences)));
		icon = resources.getDrawable(R.drawable.ic_action_settings);
		action = new Intent(this, PreferencesHC.class);
		mDrawerItems.add(new DrawerItem(icon, getString(R.string.menu_preferences), action));
		// add plugins to drawer list
		java.util.Map<String, Pair<Drawable, Intent>> plugins = application.getPluginsViews();
		for (String plugin : plugins.keySet())
		{
			mDrawerItems.add(new DrawerItem(plugins.get(plugin).first, plugin, plugins.get(plugin).second));
		}

		mTitle = mDrawerTitle = getTitle();
		mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
		mDrawerList = (ListView) findViewById(R.id.left_drawer);
		mDrawerActions = findViewById(R.id.right_drawer);

		// set a custom shadow that overlays the main content when the drawer opens
		mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);
		// set up the drawer's list view with items and click listener
		mDrawerList.setAdapter(new DrawerAdapter(this, mDrawerItems));
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

		// set button actions
		findViewById(R.id.zoomin).setOnClickListener(this);
		findViewById(R.id.zoomout).setOnClickListener(this);
		findViewById(R.id.nextmap).setOnClickListener(this);
		findViewById(R.id.prevmap).setOnClickListener(this);
		findViewById(R.id.info).setOnClickListener(this);
		findViewById(R.id.follow).setOnClickListener(this);

		if (savedInstanceState == null)
		{
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
	protected void onDestroy()
	{
		super.onDestroy();
		Log.e(TAG, "onDestroy()");

		PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);

		if (isFinishing())
		{
			// clear all overlays from map
			// updateOverlays(null, true);
			// application.waypointsOverlay = null;
			// application.navigationOverlay = null;
			// application.distanceOverlay = null;

			application.clear();
		}

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
		boolean drawerOpen = mDrawerLayout.isDrawerOpen(mDrawerList);
		menu.findItem(R.id.action_search).setVisible(!drawerOpen);
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

	/* The click listner for ListView in the navigation drawer */
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
		DrawerItem item = mDrawerItems.get(position);
		if (item.isAction())
		{
			if (position > 0)
				startActivity(item.action);
		}
		else
		{
			FragmentManager fragmentManager = getSupportFragmentManager();
			fragmentManager.beginTransaction().replace(R.id.content_frame, item.fragment).commit();
			// update selected item and title, then close the drawer
			mDrawerList.setItemChecked(position, true);
			setTitle(item.name);
			mDrawerLayout.closeDrawer(mDrawerList);
			if (item.fragment instanceof MapFragment)
				mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, mDrawerActions);
			else
				mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, mDrawerActions);
		}
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
		// TODO Auto-generated method stub

	}

	@Override
	public void onWaypointNavigate(Waypoint waypoint)
	{
		// TODO Auto-generated method stub

	}

	@Override
	public void onWaypointEdit(Waypoint waypoint)
	{
		// TODO Auto-generated method stub

	}

	@Override
	public void onWaypointShare(Waypoint waypoint)
	{
		// TODO Auto-generated method stub

	}

	@Override
	public void onWaypointRemove(Waypoint waypoint)
	{
		// TODO Auto-generated method stub

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
			case R.id.maps:
				// startActivityForResult(new Intent(this, MapList.class).putExtra("pos", true), RESULT_LOAD_MAP_ATPOSITION);
				break;
			case R.id.waypoints:
				// startActivityForResult(new Intent(this, WaypointListActivity.class), RESULT_MANAGE_WAYPOINTS);
				break;
			case R.id.info:
				startActivity(new Intent(this, Information.class));
				break;
			case R.id.follow:
				// setFollowing(!map.isFollowing());
				break;
		}
	}

}
