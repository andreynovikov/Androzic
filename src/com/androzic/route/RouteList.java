/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2012 Andrey Novikov <http://andreynovikov.info/>
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

package com.androzic.route;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.ListFragment;
import android.support.v7.internal.view.SupportMenuInflater;
import android.support.v7.internal.view.menu.MenuBuilder;
import android.support.v7.internal.view.menu.MenuPopupHelper;
import android.support.v7.internal.view.menu.MenuPresenter;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.androzic.Androzic;
import com.androzic.R;
import com.androzic.data.Route;
import com.androzic.ui.FileListDialog;
import com.androzic.util.StringFormatter;
import com.shamanland.fab.FloatingActionButton;
import com.shamanland.fab.ShowHideOnScroll;

public class RouteList extends ListFragment implements OnItemLongClickListener, FileListDialog.OnFileListDialogListener, MenuBuilder.Callback, MenuPresenter.Callback
{
	private OnRouteActionListener routeActionsCallback;

	protected ExecutorService threadPool = Executors.newFixedThreadPool(2);
	final Handler handler = new Handler();

	private RouteListAdapter adapter;
	private int selectedKey;
	private Drawable selectedBackground;
	private int accentColor;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setRetainInstance(true);
		setHasOptionsMenu(true);
		accentColor = getResources().getColor(R.color.theme_accent_color);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		return inflater.inflate(R.layout.list_with_empty_view_and_fab, container, false);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);

		TextView emptyView = (TextView) getListView().getEmptyView();
		if (emptyView != null)
			emptyView.setText(R.string.msg_empty_route_list);

		FloatingActionButton fab = (FloatingActionButton) getView().findViewById(R.id.actionButton);
		getListView().setOnTouchListener(new ShowHideOnScroll(fab));
		fab.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v)
			{
				Androzic application = Androzic.getApplication();
				Route route = new Route("New route", "", true);
				application.addRoute(route);
				routeActionsCallback.onRouteEdit(route);
			}
		});

		Activity activity = getActivity();

		adapter = new RouteListAdapter(activity);
		setListAdapter(adapter);
	}

	@Override
	public void onAttach(Activity activity)
	{
		super.onAttach(activity);

		// This makes sure that the container activity has implemented
		// the callback interface. If not, it throws an exception
		try
		{
			routeActionsCallback = (OnRouteActionListener) activity;
		}
		catch (ClassCastException e)
		{
			throw new ClassCastException(activity.toString() + " must implement OnRouteActionListener");
		}
	}

	@Override
	public void onResume()
	{
		super.onResume();
		adapter.notifyDataSetChanged();
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
	{
		inflater.inflate(R.menu.routelist_menu, menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.action_load_route:
				RouteFileList fileListDialog = new RouteFileList(this);
				fileListDialog.show(getFragmentManager(), "dialog");
				return true;
		}
		return false;
	}

	@Override
	public void onFileLoaded(int count)
	{
		adapter.notifyDataSetChanged();
	}

	@Override
	public void onListItemClick(ListView lv, View v, int position, long id)
	{
		final Androzic application = Androzic.getApplication();
		final Route route = application.getRoute(position);
		routeActionsCallback.onRouteDetails(route);
	}
	
	@Override
	public boolean onItemLongClick(AdapterView<?> lv, View v, int position, long id)
	{
		v.setTag("selected");
		selectedKey = position;
		selectedBackground = v.getBackground();
		v.setBackgroundColor(accentColor);
		// https://gist.github.com/mediavrog/9345938#file-iconizedmenu-java-L55
		MenuBuilder menu = new MenuBuilder(getActivity());
		menu.setCallback(this);
		MenuPopupHelper popup = new MenuPopupHelper(getActivity(), menu, v.findViewById(R.id.actions));
		popup.setForceShowIcon(true);
		popup.setCallback(this);
		new SupportMenuInflater(getActivity()).inflate(R.menu.route_menu, menu);
		popup.show();
		return true;
	}

	@Override
	public boolean onMenuItemSelected(MenuBuilder builder, MenuItem item)
	{
		final Androzic application = Androzic.getApplication();
		final Route route = application.getRoute(selectedKey);
		
		switch (item.getItemId())
		{
			case R.id.action_navigate:
				routeActionsCallback.onRouteNavigate(route);
				return true;
			case R.id.action_edit:
				routeActionsCallback.onRouteEdit(route);
				return true;
			case R.id.action_edith_path:
				routeActionsCallback.onRouteEditPath(route);
				return true;
			case R.id.action_save:
				routeActionsCallback.onRouteSave(route);
				return true;
			case R.id.action_remove:
				application.removeRoute(route);
				adapter.notifyDataSetChanged();
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

	@Override
	public boolean onOpenSubMenu(MenuBuilder menu)
	{
		return false;
	}

	public class RouteListAdapter extends BaseAdapter implements View.OnClickListener
	{
		private LayoutInflater mInflater;
		private int mItemLayout;
		private float mDensity;
		private Path mLinePath;
		private Paint mFillPaint;
		private Paint mLinePaint;
		private Paint mBorderPaint;
		private int mPointWidth;
		private int mRouteWidth;
		private Androzic application;

		public RouteListAdapter(Context context)
		{
			mItemLayout = R.layout.route_list_item;
			mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			mDensity = context.getResources().getDisplayMetrics().density;

			SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);

			mLinePath = new Path();
			mLinePath.setLastPoint(12 * mDensity, 5 * mDensity);
			mLinePath.lineTo(24 * mDensity, 12 * mDensity);
			mLinePath.lineTo(15 * mDensity, 24 * mDensity);
			mLinePath.lineTo(28 * mDensity, 35 * mDensity);

			mPointWidth = settings.getInt(context.getString(R.string.pref_waypoint_width), context.getResources().getInteger(R.integer.def_waypoint_width));
			mRouteWidth = settings.getInt(context.getString(R.string.pref_route_linewidth), context.getResources().getInteger(R.integer.def_route_linewidth));
			mFillPaint = new Paint();
			mFillPaint.setAntiAlias(false);
			mFillPaint.setStrokeWidth(1);
			mFillPaint.setStyle(Paint.Style.FILL_AND_STROKE);
			mFillPaint.setColor(context.getResources().getColor(R.color.routewaypoint));
			mLinePaint = new Paint();
			mLinePaint.setAntiAlias(true);
			mLinePaint.setStrokeWidth(mRouteWidth * mDensity);
			mLinePaint.setStyle(Paint.Style.STROKE);
			mLinePaint.setColor(context.getResources().getColor(R.color.routeline));
			mBorderPaint = new Paint();
			mBorderPaint.setAntiAlias(true);
			mBorderPaint.setStrokeWidth(1);
			mBorderPaint.setStyle(Paint.Style.STROKE);
			mBorderPaint.setColor(context.getResources().getColor(R.color.routeline));

			application = Androzic.getApplication();
		}

		@Override
		public Route getItem(int position)
		{
			return application.getRoute(position);
		}

		@Override
		public long getItemId(int position)
		{
			return position;
		}

		@Override
		public int getCount()
		{
			return application.getRoutes().size();
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
			}
			Route route = getItem(position);
			
			View actions = v.findViewById(R.id.actions);
			actions.setOnClickListener(this);

			TextView text = (TextView) v.findViewById(R.id.name);
			text.setText(route.name);
			String distance = StringFormatter.distanceH(route.distance);
			text = (TextView) v.findViewById(R.id.distance);
			text.setText(distance);
			text = (TextView) v.findViewById(R.id.filename);
			if (route.filepath != null)
			{
				String filepath = route.filepath.startsWith(application.dataPath) ? route.filepath.substring(application.dataPath.length() + 1, route.filepath.length()) : route.filepath;
				text.setText(filepath);
			}
			else
			{
				text.setText("");
			}
			ImageView icon = (ImageView) v.findViewById(R.id.icon);
			Bitmap bm = Bitmap.createBitmap((int) (40 * mDensity), (int) (40 * mDensity), Config.ARGB_8888);
			bm.eraseColor(Color.TRANSPARENT);
			Canvas bc = new Canvas(bm);
			mLinePaint.setColor(route.lineColor);
			mBorderPaint.setColor(route.lineColor);
			bc.drawPath(mLinePath, mLinePaint);
			int half = Math.round(mPointWidth / 4);
			bc.drawCircle(12 * mDensity, 5 * mDensity, half, mFillPaint);
			bc.drawCircle(12 * mDensity, 5 * mDensity, half, mBorderPaint);
			bc.drawCircle(24 * mDensity, 12 * mDensity, half, mFillPaint);
			bc.drawCircle(24 * mDensity, 12 * mDensity, half, mBorderPaint);
			bc.drawCircle(15 * mDensity, 24 * mDensity, half, mFillPaint);
			bc.drawCircle(15 * mDensity, 24 * mDensity, half, mBorderPaint);
			bc.drawCircle(28 * mDensity, 35 * mDensity, half, mFillPaint);
			bc.drawCircle(28 * mDensity, 35 * mDensity, half, mBorderPaint);
			icon.setImageBitmap(bm);

			return v;
		}

		@Override
		public boolean hasStableIds()
		{
			return true;
		}

		@Override
		public void onClick(View v)
		{
			ListView lv = getListView();
			int position = lv.getPositionForView((View) v.getParent());
			int child = position - lv.getFirstVisiblePosition() + lv.getHeaderViewsCount();
			if (child < 0 || child >= lv.getChildCount())
				return;
			onItemLongClick(lv, lv.getChildAt(child), position, getItemId(position));
		}
	}
}
