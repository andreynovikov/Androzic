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

package com.androzic.route;

import net.londatiga.android.ActionItem;
import net.londatiga.android.QuickAction;
import net.londatiga.android.QuickAction.OnActionItemClickListener;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockListActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.androzic.Androzic;
import com.androzic.R;
import com.androzic.data.Route;
import com.androzic.data.Waypoint;
import com.androzic.navigation.NavigationService;
import com.androzic.util.StringFormatter;
import com.androzic.waypoint.WaypointProperties;

public class RouteDetails extends SherlockListActivity implements OnItemClickListener
{
	private static final String TAG = "RouteDetails";

	private static final int RESULT_START_ROUTE = 1;
	
	private static final int qaWaypointVisible = 1;
	private static final int qaWaypointNavigate = 2;
	private static final int qaWaypointProperties = 3;

	private NavigationService navigationService;
	private WaypointListAdapter adapter;
    private QuickAction quickAction;
    
	private Route route;
	private boolean navigation;
	private int selectedPosition;

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		int index = getIntent().getExtras().getInt("index");
		navigation = getIntent().getExtras().getBoolean("nav");

		Androzic application = (Androzic) getApplication();
		route = application.getRoute(index);
		
		setTitle(navigation ? "› " + route.name : route.name);

		adapter = new WaypointListAdapter(this, route);
		setListAdapter(adapter);
		
		Resources resources = getResources();
		quickAction = new QuickAction(this);
		quickAction.addActionItem(new ActionItem(qaWaypointVisible, getString(R.string.menu_view), resources.getDrawable(R.drawable.ic_action_show)));
		if (navigation)
		{
			quickAction.addActionItem(new ActionItem(qaWaypointNavigate, getString(R.string.menu_navigate), resources.getDrawable(R.drawable.ic_action_directions)));
		}
		else
		{
			quickAction.addActionItem(new ActionItem(qaWaypointProperties, getString(R.string.menu_edit), resources.getDrawable(R.drawable.ic_action_edit)));
		}
		quickAction.setOnActionItemClickListener(actionItemClickListener);
		
		getListView().setOnItemClickListener(this);
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		if (navigation)
		{
			bindService(new Intent(this, NavigationService.class), navigationConnection, BIND_AUTO_CREATE);
			boolean lock = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(getString(R.string.pref_wakelock), getResources().getBoolean(R.bool.def_wakelock));
			if (lock)
				getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		}
	}

	@Override
	protected void onPause()
	{
		super.onPause();
		if (navigation)
		{
			unregisterReceiver(navigationReceiver);
			unbindService(navigationConnection);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu)
	{
		if (! navigation)
		{
			MenuInflater inflater = getSupportMenuInflater();
			inflater.inflate(R.menu.routedetails_menu, menu);
		}
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.menuStartNavigation:
				Androzic application = (Androzic) getApplication();
				int index = application.getRouteIndex(route);
				startActivityForResult(new Intent(this, RouteStart.class).putExtra("index", index), RESULT_START_ROUTE);
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
			case RESULT_START_ROUTE:
				if (resultCode == RESULT_OK)
				{
					setResult(RESULT_OK);
					finish();
				}
				break;
		}
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) 
	{
		selectedPosition = position;
		quickAction.show(view);
	}

	private OnActionItemClickListener actionItemClickListener = new OnActionItemClickListener(){
		@Override
		public void onItemClick(QuickAction source, int pos, int actionId)
		{
			Androzic application = Androzic.getApplication();
	    	switch (actionId)
	    	{
	    		case qaWaypointVisible:
	    			route.show = true;
	    			application.ensureVisible(route.getWaypoint(selectedPosition));
	    			setResult(RESULT_OK);
					finish();
	    			break;
				case qaWaypointNavigate:
					if (navigationService != null)
					{
						if (navigationService.navDirection == NavigationService.DIRECTION_REVERSE)
							selectedPosition = route.length() - selectedPosition - 1;
						navigationService.setRouteWaypoint(selectedPosition);
		    			adapter.notifyDataSetChanged();
					}
					break;
	    		case qaWaypointProperties:
	    			int index = application.getRouteIndex(route);
	    			startActivity(new Intent(RouteDetails.this, WaypointProperties.class).putExtra("INDEX", selectedPosition).putExtra("ROUTE", index + 1));
	    	        break;
	    	}
		}
	};

	private ServiceConnection navigationConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service)
		{
			navigationService = ((NavigationService.LocalBinder) service).getService();
			registerReceiver(navigationReceiver, new IntentFilter(NavigationService.BROADCAST_NAVIGATION_STATUS));
			registerReceiver(navigationReceiver, new IntentFilter(NavigationService.BROADCAST_NAVIGATION_STATE));
			Log.d(TAG, "Navigation broadcast receiver registered");
			runOnUiThread(new Runnable() {
				public void run()
				{
					adapter.notifyDataSetChanged();
				}
			});
		}

		public void onServiceDisconnected(ComponentName className)
		{
			unregisterReceiver(navigationReceiver);
			navigationService = null;
		}
	};

	private BroadcastReceiver navigationReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent)
		{
			Log.e(TAG, "Broadcast: " + intent.getAction());
			if (intent.getAction().equals(NavigationService.BROADCAST_NAVIGATION_STATE))
			{
				final int state = intent.getExtras().getInt("state");
				runOnUiThread(new Runnable() {
					public void run()
					{
						if (state == NavigationService.STATE_REACHED)
						{
							Toast.makeText(getApplicationContext(), R.string.arrived, Toast.LENGTH_LONG).show();
							navigation = false;
						}
						adapter.notifyDataSetChanged();
					}
				});
			}
			if (intent.getAction().equals(NavigationService.BROADCAST_NAVIGATION_STATUS))
			{
				runOnUiThread(new Runnable() {
					public void run()
					{
						adapter.notifyDataSetChanged();
					}
				});
			}
		}
	};

	public class WaypointListAdapter extends BaseAdapter
	{
		private LayoutInflater mInflater;
		private int mItemLayout;
		private Route mRoute;

		public WaypointListAdapter(Context context, Route route)
		{
			mItemLayout = R.layout.route_waypoint_list_item;
			mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			mRoute = route;
		}

		public Waypoint getItem(int position)
		{
			if (navigation && navigationService != null && navigationService.navDirection  == NavigationService.DIRECTION_REVERSE)
				position = mRoute.length() - position - 1;
			return mRoute.getWaypoint(position);
		}

		@Override
		public long getItemId(int position)
		{
			if (navigation && navigationService != null && navigationService.navDirection  == NavigationService.DIRECTION_REVERSE)
				position = mRoute.length() - position - 1;
			return position;
		}

		@Override
		public int getCount()
		{
			return mRoute.length();
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent)
		{
			View v;
			if (convertView == null)
			{
				v = mInflater.inflate(mItemLayout, parent, false);
			}
			else
			{
				v = convertView;
				v = mInflater.inflate(mItemLayout, parent, false);
			}
			Waypoint wpt = (Waypoint) getItem(position);
			TextView text = (TextView) v.findViewById(R.id.name);
			if (text != null)
			{
				text.setText(wpt.name);
			}
			if (navigation && navigationService != null && navigationService.isNavigatingViaRoute())
			{
				int progress = position - navigationService.navRouteCurrentIndex();
				if (position > 0)
				{
					double dist = progress == 0 ? navigationService.navDistance : mRoute.distanceBetween(position - 1, position);
					String distance = StringFormatter.distanceH(dist);
					text = (TextView) v.findViewById(R.id.distance);
					if (text != null)
					{
						text.setText(distance);
//						if (progress == 0)
//							text.setTextAppearance(RouteDetails.this, resid);
					}
					double crs;
					if (progress == 0)
						crs = navigationService.navBearing;
					else if (navigationService.navDirection == NavigationService.DIRECTION_FORWARD)
						crs = mRoute.course(position - 1, position);
					else
						crs = mRoute.course(position, position - 1);
					String course = StringFormatter.bearingH(crs);
					text = (TextView) v.findViewById(R.id.course);
					if (text != null)
					{
						text.setText(course);
					}
				}
				if (progress >= 0)
				{
					double dist = navigationService.navDistance;
					if (progress > 0)
						dist += navigationService.navRouteDistanceLeftTo(position);
					String distance = StringFormatter.distanceH(dist);
					text = (TextView) v.findViewById(R.id.total_distance);
					if (text != null)
					{
						text.setText(distance);
					}
					int ete = progress == 0 ? navigationService.navETE : navigationService.navRouteWaypointETE(position);
					String s = StringFormatter.timeR(ete);
					text = (TextView) v.findViewById(R.id.ete);
					if (text != null)
					{
						text.setText(s);
					}
					int eta = navigationService.navETE;
					if (progress > 0 && eta < Integer.MAX_VALUE)
					{
						int t = navigationService.navRouteETETo(position);
						if (t < Integer.MAX_VALUE)
							eta += t;
					}
					s = StringFormatter.timeR(eta);
					text = (TextView) v.findViewById(R.id.eta);
					if (text != null)
					{
						text.setText(s);
					}
					
					if (progress == 0)
					{
						text = (TextView) v.findViewById(R.id.name);
						text.setText("» " + text.getText());
					}
				}
				else
				{
					text = (TextView) v.findViewById(R.id.name);
					text.setTextColor(text.getTextColors().withAlpha(128));
					text = (TextView) v.findViewById(R.id.distance);
					text.setTextColor(text.getTextColors().withAlpha(128));
					text = (TextView) v.findViewById(R.id.course);
					text.setTextColor(text.getTextColors().withAlpha(128));
				}
			}
			else
			{
				if (position > 0)
				{
					double dist = mRoute.distanceBetween(position - 1, position);
					String distance = StringFormatter.distanceH(dist);
					text = (TextView) v.findViewById(R.id.distance);
					if (text != null)
					{
						text.setText(distance);
					}
					double crs = mRoute.course(position - 1, position);
					String course = StringFormatter.bearingH(crs);
					text = (TextView) v.findViewById(R.id.course);
					if (text != null)
					{
						text.setText(course);
					}
				}
				double dist = position > 0 ? mRoute.distanceBetween(0, position) : 0.;
				String distance = StringFormatter.distanceH(dist);
				text = (TextView) v.findViewById(R.id.total_distance);
				if (text != null)
				{
					text.setText(distance);
				}
			}

			return v;
		}

		@Override
		public boolean hasStableIds()
		{
			return true;
		}
	}
}
