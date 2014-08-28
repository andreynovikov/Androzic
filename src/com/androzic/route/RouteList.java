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

import net.londatiga.android.ActionItem;
import net.londatiga.android.QuickAction;
import net.londatiga.android.QuickAction.OnActionItemClickListener;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
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
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.androzic.Androzic;
import com.androzic.R;
import com.androzic.data.Route;
import com.androzic.util.StringFormatter;

public class RouteList extends ListFragment
{
	public static final int MODE_MANAGE = 1;
	public static final int MODE_START = 2;

	private static final int qaRouteDetails = 1;
	private static final int qaRouteNavigate = 2;
	private static final int qaRouteProperties = 3;
	private static final int qaRouteEdit = 4;
	private static final int qaRouteSave = 5;
	private static final int qaRouteRemove = 6;

	private OnRouteActionListener routeActionsCallback;

	protected ExecutorService threadPool = Executors.newFixedThreadPool(2);
	final Handler handler = new Handler();

	private RouteListAdapter adapter;
	private QuickAction quickAction;
	private int selectedKey;
	private Drawable selectedBackground;

	private int mode;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setRetainInstance(true);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		return inflater.inflate(R.layout.list_with_empty_view, container, false);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);
		setHasOptionsMenu(true);

		TextView emptyView = (TextView) getListView().getEmptyView();
		if (emptyView != null)
			emptyView.setText(R.string.msg_empty_route_list);

		Activity activity = getActivity();

		mode = activity.getIntent().getExtras().getInt("MODE");

		if (mode == MODE_START)
			activity.setTitle(getString(R.string.selectroute_name));

		adapter = new RouteListAdapter(activity);
		setListAdapter(adapter);

		Resources resources = getResources();
		quickAction = new QuickAction(activity);
		quickAction.addActionItem(new ActionItem(qaRouteDetails, getString(R.string.menu_details), resources.getDrawable(R.drawable.ic_action_list)));
		quickAction.addActionItem(new ActionItem(qaRouteNavigate, getString(R.string.menu_navigate), resources.getDrawable(R.drawable.ic_action_directions)));
		quickAction.addActionItem(new ActionItem(qaRouteProperties, getString(R.string.menu_properties), resources.getDrawable(R.drawable.ic_action_edit)));
		quickAction.addActionItem(new ActionItem(qaRouteEdit, getString(R.string.menu_edit), resources.getDrawable(R.drawable.ic_action_track)));
		quickAction.addActionItem(new ActionItem(qaRouteSave, getString(R.string.menu_save), resources.getDrawable(R.drawable.ic_action_save)));
		quickAction.addActionItem(new ActionItem(qaRouteRemove, getString(R.string.menu_remove), resources.getDrawable(R.drawable.ic_action_cancel)));

		quickAction.setOnActionItemClickListener(routeActionItemClickListener);
		quickAction.setOnDismissListener(new PopupWindow.OnDismissListener() {
			@Override
			public void onDismiss()
			{
				View v = getListView().findViewWithTag("selected");
				if (v != null)
				{
					v.setBackgroundDrawable(selectedBackground);
					v.setTag(null);
				}
			}
		});
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
		if (mode == MODE_MANAGE)
		{
			inflater.inflate(R.menu.routelist_menu, menu);
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.menuNewRoute:
				Androzic application = Androzic.getApplication();
				Route route = new Route("New route", "", true);
				application.addRoute(route);
				routeActionsCallback.onRouteEdit(route);
				return true;
			case R.id.menuLoadRoute:
				startActivityForResult(new Intent(getActivity(), RouteFileList.class), RouteListActivity.RESULT_LOAD_ROUTE);
				return true;
		}
		return false;
	}

	@Override
	public void onListItemClick(ListView lv, View v, int position, long id)
	{
		switch (mode)
		{
			case MODE_MANAGE:
				v.setTag("selected");
				selectedKey = position;
				selectedBackground = v.getBackground();
				int l = v.getPaddingLeft();
				int t = v.getPaddingTop();
				int r = v.getPaddingRight();
				int b = v.getPaddingBottom();
				v.setBackgroundResource(R.drawable.list_selector_background_focus);
				v.setPadding(l, t, r, b);
				quickAction.show(v);
				break;
			case MODE_START:
				Androzic application = Androzic.getApplication();
				Route route = application.getRoute(position);
				routeActionsCallback.onRouteNavigate(route);
				break;
		}
	}

	private OnActionItemClickListener routeActionItemClickListener = new OnActionItemClickListener() {
		@Override
		public void onItemClick(QuickAction source, int pos, int actionId)
		{
			Androzic application = Androzic.getApplication();
			Route route = application.getRoute(selectedKey);

			switch (actionId)
			{
				case qaRouteDetails:
					routeActionsCallback.onRouteDetails(route);
					break;
				case qaRouteNavigate:
					routeActionsCallback.onRouteNavigate(route);
					break;
				case qaRouteProperties:
					routeActionsCallback.onRouteEdit(route);
					break;
				case qaRouteEdit:
					routeActionsCallback.onRouteEditPath(route);
					break;
				case qaRouteSave:
					routeActionsCallback.onRouteSave(route);
					break;
				case qaRouteRemove:
					application.removeRoute(route);
					adapter.notifyDataSetChanged();
					break;
			}
		}
	};

	public class RouteListAdapter extends BaseAdapter
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
	}
}
