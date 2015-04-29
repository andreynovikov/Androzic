/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2014  Andrey Novikov <http://andreynovikov.info/>
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

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.ListFragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.internal.view.SupportMenuInflater;
import android.support.v7.internal.view.menu.MenuBuilder;
import android.support.v7.internal.view.menu.MenuPopupHelper;
import android.support.v7.internal.view.menu.MenuPresenter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.androzic.Androzic;
import com.androzic.FragmentHolder;
import com.androzic.R;
import com.androzic.data.Route;
import com.androzic.data.Waypoint;
import com.androzic.navigation.NavigationService;
import com.androzic.util.StringFormatter;
import com.androzic.waypoint.OnWaypointActionListener;

public class RouteDetails extends ListFragment implements OnSharedPreferenceChangeListener, MenuBuilder.Callback, MenuPresenter.Callback
{
	private static final String TAG = "RouteDetails";

	Androzic application;
	private FragmentHolder fragmentHolderCallback;
	private OnRouteActionListener routeActionsCallback;
	private OnWaypointActionListener waypointActionsCallback;
	
	private WaypointListAdapter adapter;
	private int selectedKey;
	private Drawable selectedBackground;
	private int accentColor;
    
	private Route route;
	private boolean navigation;

	public RouteDetails()
	{
		application = Androzic.getApplication();
	}

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
		setRetainInstance(true);
		accentColor = getResources().getColor(R.color.theme_accent_color);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		return inflater.inflate(R.layout.list_with_empty_view, container, false);
	}

	@Override
	public void onAttach(Activity activity)
	{
		super.onAttach(activity);

		// This makes sure that the container activity has implemented
		// the callback interface. If not, it throws an exception
		try
		{
			fragmentHolderCallback = (FragmentHolder) activity;
		}
		catch (ClassCastException e)
		{
			throw new ClassCastException(activity.toString() + " must implement FragmentHolder");
		}
		try
		{
			routeActionsCallback = (OnRouteActionListener) activity;
		}
		catch (ClassCastException e)
		{
			throw new ClassCastException(activity.toString() + " must implement OnRouteActionListener");
		}
		try
		{
			waypointActionsCallback = (OnWaypointActionListener) activity;
		}
		catch (ClassCastException e)
		{
			throw new ClassCastException(activity.toString() + " must implement OnWaypointActionListener");
		}
	}

	@Override
	public void onStart()
	{
		super.onStart();

		if (route == null)
		{
			Bundle args = getArguments();
			if (args != null)
				setRoute(application.getRoute(args.getInt("index")));
		}
		if (route != null)
			updateRouteDetails();
	}

	@Override
	public void onResume()
	{
		super.onResume();

		if (navigation)
		{
			application.registerReceiver(navigationReceiver, new IntentFilter(NavigationService.BROADCAST_NAVIGATION_STATUS));
			application.registerReceiver(navigationReceiver, new IntentFilter(NavigationService.BROADCAST_NAVIGATION_STATE));
		}
		else
		{
			fragmentHolderCallback.enableActionButton().setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v)
				{
					routeActionsCallback.onRouteNavigate(route);
				}
			});
		}

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getActivity());
		onSharedPreferenceChanged(settings, getString(R.string.pref_wakelock));
		PreferenceManager.getDefaultSharedPreferences(application).registerOnSharedPreferenceChangeListener(this);

		adapter.notifyDataSetChanged();
	}

	@Override
	public void onPause()
	{
		super.onPause();

		if (navigation)
		{
			application.unregisterReceiver(navigationReceiver);
		}
		else
		{
			fragmentHolderCallback.disableActionButton();
		}
		
		PreferenceManager.getDefaultSharedPreferences(application).unregisterOnSharedPreferenceChangeListener(this);
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
	{
		inflater.inflate(R.menu.route_menu, menu);
	}

	@Override
	public void onPrepareOptionsMenu(final Menu menu)
	{
		menu.findItem(R.id.action_navigate).setVisible(false);
		if (navigation)
		{
			menu.findItem(R.id.action_edit).setVisible(false);
			menu.findItem(R.id.action_edit_path).setVisible(false);
			menu.findItem(R.id.action_save).setVisible(false);
			menu.findItem(R.id.action_remove).setVisible(false);
		}
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.action_edit:
				routeActionsCallback.onRouteEdit(route);
				return true;
			case R.id.action_edit_path:
				routeActionsCallback.onRouteEditPath(route);
				return true;
			case R.id.action_save:
				routeActionsCallback.onRouteSave(route);
				return true;
			case R.id.action_remove:
				Androzic application = Androzic.getApplication();
				application.removeRoute(route);
				// "Close" fragment
				getFragmentManager().popBackStack();
				return true;
		}
		return false;
	}

	@Override
	public void onListItemClick(ListView lv, View v, int position, long id)
	{
		v.setTag("selected");
		selectedKey = position;
		selectedBackground = v.getBackground();
		v.setBackgroundColor(accentColor);
		// https://gist.github.com/mediavrog/9345938#file-iconizedmenu-java-L55
		MenuBuilder menu = new MenuBuilder(getActivity());
		menu.setCallback(this);
		MenuPopupHelper popup = new MenuPopupHelper(getActivity(), menu, v.findViewById(R.id.name));
		popup.setForceShowIcon(true);
		popup.setCallback(this);
		new SupportMenuInflater(getActivity()).inflate(navigation? R.menu.routewaypointnavigation_menu : R.menu.routewaypoint_menu, menu);
		popup.show();
	}

	@Override
	public boolean onMenuItemSelected(MenuBuilder builder, MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.action_view:
    			route.show = true;
				waypointActionsCallback.onWaypointView(route.getWaypoint(selectedKey));
				//TODO Sometimes we have to close it manually, sometimes not. Should investigate it.
				FragmentManager fm = getFragmentManager();
				if (fm != null)
					fm.popBackStack();
				return true;
			case R.id.action_navigate:
				if (navigation)
				{
					if (application.navigationService.navDirection == NavigationService.DIRECTION_REVERSE)
						selectedKey = route.length() - selectedKey - 1;
					application.navigationService.setRouteWaypoint(selectedKey);
	    			adapter.notifyDataSetChanged();
				}
				return true;
			case R.id.action_edit:
				routeActionsCallback.onRouteWaypointEdit(route, route.getWaypoint(selectedKey));
				return true;
		}
		return false;
	}

	@Override
	public void onMenuModeChange(MenuBuilder builder)
	{
	}

	@SuppressWarnings("deprecation")
	@Override
	public void onCloseMenu(MenuBuilder menu, boolean allMenusAreClosing)
	{
		try
		{
			ListView lv = getListView();
			if (allMenusAreClosing && lv != null)
			{
				View v = lv.findViewWithTag("selected");
				if (v != null)
				{
					v.setBackgroundDrawable(selectedBackground);
					v.setTag(null);
				}
			}
		}
		catch (IllegalStateException ignore)
		{
			// Ignore dismissing view after list view was destroyed
		}
	}

	@Override
	public boolean onOpenSubMenu(MenuBuilder menu)
	{
		return false;
	}

	public void setRoute(Route route)
	{
		this.route = route;
		
		navigation = application.isNavigatingViaRoute() && application.navigationService.navRoute == route;
		
		if (isVisible())
			updateRouteDetails();
	}

	private void updateRouteDetails()
	{
		AppCompatActivity activity = (AppCompatActivity) getActivity();
		activity.getSupportActionBar().setTitle(navigation ? "\u21d2 " + route.name : route.name);
		
		adapter = new WaypointListAdapter(activity, route);
		setListAdapter(adapter);
		
		if (navigation)
			getListView().setSelection(application.navigationService.navRouteCurrentIndex());

		activity.supportInvalidateOptionsMenu();
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
	{
		Resources resources = getResources();
		if (getString(R.string.pref_wakelock).equals(key))
		{
			getListView().setKeepScreenOn(navigation && sharedPreferences.getBoolean(key, resources.getBoolean(R.bool.def_wakelock)));
		}
	}

	private BroadcastReceiver navigationReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent)
		{
			Log.e(TAG, "Broadcast: " + intent.getAction());
			if (intent.getAction().equals(NavigationService.BROADCAST_NAVIGATION_STATE))
			{
				final int state = intent.getExtras().getInt("state");
				if (state == NavigationService.STATE_REACHED)
				{
					Toast.makeText(getActivity(), R.string.arrived, Toast.LENGTH_LONG).show();
					navigation = false;
					getActivity().supportInvalidateOptionsMenu();
				}
				adapter.notifyDataSetChanged();
			}
			if (intent.getAction().equals(NavigationService.BROADCAST_NAVIGATION_STATUS))
			{
				adapter.notifyDataSetChanged();
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
			mItemLayout = R.layout.list_item_route_waypoint;
			mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			mRoute = route;
		}

		public Waypoint getItem(int position)
		{
			if (navigation && application.navigationService.navDirection == NavigationService.DIRECTION_REVERSE)
				position = mRoute.length() - position - 1;
			return mRoute.getWaypoint(position);
		}

		@Override
		public long getItemId(int position)
		{
			if (navigation && application.navigationService.navDirection  == NavigationService.DIRECTION_REVERSE)
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
			Waypoint wpt = getItem(position);
			TextView text = (TextView) v.findViewById(R.id.name);
			if (text != null)
			{
				text.setText(wpt.name);
			}
			if (navigation && application.navigationService.isNavigatingViaRoute())
			{
				int progress = position - application.navigationService.navRouteCurrentIndex();
				if (position > 0)
				{
					double dist = progress == 0 ? application.navigationService.navDistance : mRoute.distanceBetween(position - 1, position);
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
						crs = application.navigationService.navBearing;
					else if (application.navigationService.navDirection == NavigationService.DIRECTION_FORWARD)
						crs = mRoute.course(position - 1, position);
					else
						crs = mRoute.course(position, position - 1);
					String course = StringFormatter.angleH(crs);
					text = (TextView) v.findViewById(R.id.course);
					if (text != null)
					{
						text.setText(course);
					}
				}
				if (progress >= 0)
				{
					double dist = application.navigationService.navDistance;
					if (progress > 0)
						dist += application.navigationService.navRouteDistanceLeftTo(position);
					String distance = StringFormatter.distanceH(dist);
					text = (TextView) v.findViewById(R.id.total_distance);
					if (text != null)
					{
						text.setText(distance);
					}
					int ete = progress == 0 ? application.navigationService.navETE : application.navigationService.navRouteWaypointETE(position);
					String s = StringFormatter.timeR(ete);
					text = (TextView) v.findViewById(R.id.ete);
					if (text != null)
					{
						text.setText(s);
					}
					int eta = application.navigationService.navETE;
					if (progress > 0 && eta < Integer.MAX_VALUE)
					{
						int t = application.navigationService.navRouteETETo(position);
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
						text.setText("\u2192 " + text.getText());
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
					String course = StringFormatter.angleH(crs);
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
