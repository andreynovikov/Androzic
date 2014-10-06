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

package com.androzic.waypoint;

import java.io.File;
import java.util.Collections;
import java.util.Comparator;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentActivity;
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
import android.widget.BaseExpandableListAdapter;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.androzic.Androzic;
import com.androzic.R;
import com.androzic.data.Waypoint;
import com.androzic.data.WaypointSet;
import com.androzic.ui.ExpandableListFragment;
import com.androzic.ui.FileListDialog;
import com.androzic.util.Geo;
import com.androzic.util.StringFormatter;

public class WaypointList extends ExpandableListFragment implements OnItemLongClickListener, FileListDialog.OnFileListDialogListener, MenuBuilder.Callback, MenuPresenter.Callback
{
	private static final int DIALOG_WAYPOINT_PROJECT = 1;
	
	private OnWaypointActionListener waypointActionsCallback;
	
	private WaypointExpandableListAdapter adapter;

	private long selectedKey;
	private int selectedSetKey;
	private Drawable selectedBackground;

	private int mSortMode = -1;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setRetainInstance(true);
		setHasOptionsMenu(true);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);
		
		FragmentActivity activity = getActivity();
		
		adapter = new WaypointExpandableListAdapter(activity);
		setListAdapter(adapter);

		getExpandableListView().setOnItemLongClickListener(this);
	}

	@Override
	public void onAttach(Activity activity)
	{
		super.onAttach(activity);
		
		// This makes sure that the container activity has implemented
		// the callback interface. If not, it throws an exception
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
		
		Androzic application = Androzic.getApplication();
		application.saveWaypoints();

		application.registerReceiver(broadcastReceiver, new IntentFilter(Androzic.BROADCAST_WAYPOINT_REMOVED));

		mSortMode = -1;
		adapter.sort(0);
		getExpandableListView().expandGroup(0);
	}

	@Override
	public void onResume()
	{
		super.onResume();
		adapter.notifyDataSetChanged();
	}

	@Override
	public void onStop()
	{
		super.onStop();

		Androzic application = Androzic.getApplication();
		application.unregisterReceiver(broadcastReceiver);
	}

	@Override
	public boolean onChildClick(ExpandableListView parent, View v, int groupPosition, int childPosition, long id)
	{
		v.setTag("selected");
		selectedKey = adapter.getCombinedChildId(groupPosition, childPosition);
		selectedBackground = v.getBackground();
		int l = v.getPaddingLeft();
		int t = v.getPaddingTop();
		int r = v.getPaddingRight();
		int b = v.getPaddingBottom();
		v.setBackgroundResource(R.drawable.list_selector_background_focus);
		v.setPadding(l, t, r, b);
		// https://gist.github.com/mediavrog/9345938#file-iconizedmenu-java-L55
		MenuBuilder menu = new MenuBuilder(getActivity());
		menu.setCallback(this);
		MenuPopupHelper popup = new MenuPopupHelper(getActivity(), menu, v.findViewById(R.id.name));
		popup.setForceShowIcon(true);
		popup.setCallback(this);
		new SupportMenuInflater(getActivity()).inflate(R.menu.waypoint_menu, menu);
		popup.show();
		return true;
	}

	@Override
	public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id)
	{
		long pos = getExpandableListView().getExpandableListPosition(position);
		if (ExpandableListView.getPackedPositionType(pos) == ExpandableListView.PACKED_POSITION_TYPE_GROUP)
		{
			selectedSetKey = ExpandableListView.getPackedPositionGroup(pos);
			// https://gist.github.com/mediavrog/9345938#file-iconizedmenu-java-L55
			MenuBuilder menu = new MenuBuilder(getActivity());
			menu.setCallback(this);
			MenuPopupHelper popup = new MenuPopupHelper(getActivity(), menu, view.findViewById(android.R.id.text1));
			popup.setForceShowIcon(true);
			new SupportMenuInflater(getActivity()).inflate(R.menu.waypointset_menu, menu);
			popup.show();
		}
		return true;
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
	{
		inflater.inflate(R.menu.waypointlist_menu, menu);
		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public void onPrepareOptionsMenu(final Menu menu)
	{
		if (mSortMode != -1)
		{
			Drawable icon = menu.findItem(mSortMode).getIcon();
			menu.findItem(R.id.action_sort).setIcon(icon);
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		FragmentActivity activity = getActivity();
		switch (item.getItemId())
		{
			case R.id.action_sort_alpha:
				adapter.sort(0);
				mSortMode = R.id.action_sort_alpha;
				activity.supportInvalidateOptionsMenu();
				return true;
			case R.id.action_sort_size:
				adapter.sort(1);
				mSortMode = R.id.action_sort_size;
				activity.supportInvalidateOptionsMenu();
				return true;
			case R.id.action_load_waypoints:
				WaypointFileList fileListDialog = new WaypointFileList(this);
				fileListDialog.show(getFragmentManager(), "dialog");
				return true;
			case R.id.action_new_waypoint_set:
				final EditText textEntryView = new EditText(activity);
				textEntryView.setSingleLine(true);
				textEntryView.setPadding(8, 0, 8, 0);
				new AlertDialog.Builder(activity).setTitle(R.string.name).setView(textEntryView).setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton)
					{
						String name = textEntryView.getText().toString();
						if (!"".equals(name))
						{
							WaypointSet set = new WaypointSet(name);
							Androzic application = Androzic.getApplication();
							application.addWaypointSet(set);
							adapter.notifyDataSetChanged();
						}
					}
				}).setNegativeButton(R.string.cancel, null).create().show();
				return true;
			case R.id.action_new_waypoint:
				waypointActionsCallback.onWaypointEdit(new Waypoint());
				return true;
			case R.id.action_project_waypoint:
				WaypointProject waypointProjectDialog = new WaypointProject();
				waypointProjectDialog.setTargetFragment(this, DIALOG_WAYPOINT_PROJECT);
				waypointProjectDialog.show(getFragmentManager(), "dialog");
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		switch(requestCode)
		{
			case DIALOG_WAYPOINT_PROJECT:
				adapter.notifyDataSetChanged();
				break;
		}
	}

	@Override
	public void onFileLoaded(int count)
	{
		adapter.notifyDataSetChanged();
	}

	@Override
	public boolean onMenuItemSelected(MenuBuilder builder, MenuItem item)
	{
		Androzic application = Androzic.getApplication();
		Waypoint waypoint = (Waypoint) adapter.getChild(ExpandableListView.getPackedPositionGroup(selectedKey), ExpandableListView.getPackedPositionChild(selectedKey));

		switch (item.getItemId())
		{
			case R.id.action_view:
				waypointActionsCallback.onWaypointView(waypoint);
				return true;
			case R.id.action_navigate:
				waypointActionsCallback.onWaypointNavigate(waypoint);
				return true;
			case R.id.action_edit:
				waypointActionsCallback.onWaypointEdit(waypoint);
				return true;
			case R.id.action_share:
				waypointActionsCallback.onWaypointShare(waypoint);
				return true;
			case R.id.action_delete:
				waypointActionsCallback.onWaypointRemove(waypoint);
				adapter.notifyDataSetChanged();
				return true;
			case R.id.action_clear:
				WaypointSet set = application.getWaypointSets().get(selectedSetKey);
				application.clearWaypoints(set);
				application.saveWaypoints(set);
				adapter.notifyDataSetChanged();
				return true;
			case R.id.action_remove:
				if (selectedSetKey > 0)
				{
					application.removeWaypointSet(selectedSetKey);
					adapter.notifyDataSetChanged();
				}
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

	private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent)
		{
			adapter.notifyDataSetChanged();
		}
	};

	public class WaypointExpandableListAdapter extends BaseExpandableListAdapter
	{
		private LayoutInflater mInflater;
		private int mExpandedGroupLayout;
		private int mCollapsedGroupLayout;
		private int mChildLayout;
		private float mDensity;
		private Paint mBorderPaint;
		private Paint mFillPaint;
		private int mPointWidth;
		private Androzic application;
		private double[] loc;

		public WaypointExpandableListAdapter(Context context)
		{
			mExpandedGroupLayout = android.R.layout.simple_expandable_list_item_1;
			mCollapsedGroupLayout = android.R.layout.simple_expandable_list_item_1;
			mChildLayout = R.layout.waypoint_list_item;
			mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			mDensity = context.getResources().getDisplayMetrics().density;

			SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);

			mPointWidth = settings.getInt(context.getString(R.string.pref_waypoint_width), context.getResources().getInteger(R.integer.def_waypoint_width));
			mFillPaint = new Paint();
			mFillPaint.setAntiAlias(false);
			mFillPaint.setStrokeWidth(1);
			mFillPaint.setStyle(Paint.Style.FILL_AND_STROKE);
			mFillPaint.setColor(settings.getInt(context.getString(R.string.pref_waypoint_color), context.getResources().getColor(R.color.waypoint)));
			mBorderPaint = new Paint();
			mBorderPaint.setAntiAlias(false);
			mBorderPaint.setStrokeWidth(1);
			mBorderPaint.setStyle(Paint.Style.STROKE);
			mBorderPaint.setColor(context.getResources().getColor(R.color.waypointtext));
			mBorderPaint.setColor(settings.getInt(context.getString(R.string.pref_waypoint_namecolor), context.getResources().getColor(R.color.waypointtext)));

			application = Androzic.getApplication();
			loc = application.getLocation();
		}

		@Override
		public Object getChild(int groupPosition, int childPosition)
		{
			return application.getWaypoints(application.getWaypointSets().get(groupPosition)).get(childPosition);
		}

		@Override
		public long getChildId(int groupPosition, int childPosition)
		{
			return childPosition;
		}

		@Override
		public int getChildrenCount(int groupPosition)
		{
			return application.getWaypointCount(application.getWaypointSets().get(groupPosition));
		}

		@Override
		public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent)
		{
			View v;
			if (convertView == null)
			{
				v = mInflater.inflate((isLastChild) ? mChildLayout : mChildLayout, parent, false);
			}
			else
			{
				v = convertView;
			}
			Waypoint wpt = (Waypoint) getChild(groupPosition, childPosition);
			TextView text = (TextView) v.findViewById(R.id.name);
			if (text != null)
			{
				text.setText(wpt.name);
			}
			String coordinates = StringFormatter.coordinates(application.coordinateFormat, " ", wpt.latitude, wpt.longitude);
			text = (TextView) v.findViewById(R.id.coordinates);
			if (text != null)
			{
				text.setText(coordinates);
			}
			double dist = Geo.distance(loc[0], loc[1], wpt.latitude, wpt.longitude);
			double bearing = Geo.bearing(loc[0], loc[1], wpt.latitude, wpt.longitude);
			String distance = StringFormatter.distanceH(dist) + " " + StringFormatter.bearingSimpleH(bearing);
			text = (TextView) v.findViewById(R.id.distance);
			if (text != null)
			{
				text.setText(distance);
			}
			ImageView icon = (ImageView) v.findViewById(R.id.icon);
			Bitmap b = null;
			if (application.iconsEnabled && wpt.drawImage)
			{
				BitmapFactory.Options options = new BitmapFactory.Options();
				options.inScaled = false;
				b = BitmapFactory.decodeFile(application.iconPath + File.separator + wpt.image, options);
			}
			int h = b != null ? b.getHeight() : 30;
			Bitmap bm = Bitmap.createBitmap((int) (40 * mDensity), h, Config.ARGB_8888);
			bm.eraseColor(Color.TRANSPARENT);
			Canvas bc = new Canvas(bm);
			if (b != null)
			{
				b.setDensity(Bitmap.DENSITY_NONE);
				int l = (int) ((38 * mDensity - b.getWidth()) / 2);
				bc.drawBitmap(b, null, new Rect(l, 0, b.getWidth() + l, b.getHeight()), null);
			}
			else
			{
				int tc = 0, bgc = 0;
				if (wpt.textcolor != Integer.MIN_VALUE)
				{
					tc = mBorderPaint.getColor();
					mBorderPaint.setColor(wpt.textcolor);
				}
				if (wpt.backcolor != Integer.MIN_VALUE)
				{
					bgc = mFillPaint.getColor();
					mFillPaint.setColor(wpt.backcolor);
				}
				Rect rect = new Rect(0, 0, mPointWidth, mPointWidth);
				bc.translate((38 * mDensity - mPointWidth) / 2, (30 - mPointWidth) / 2);
				bc.drawRect(rect, mBorderPaint);
				rect.inset(1, 1);
				bc.drawRect(rect, mFillPaint);
				if (wpt.textcolor != Integer.MIN_VALUE)
				{
					mBorderPaint.setColor(tc);
				}
				if (wpt.backcolor != Integer.MIN_VALUE)
				{
					mFillPaint.setColor(bgc);
				}
			}
			icon.setImageBitmap(bm);

			return v;
		}

		@Override
		public Object getGroup(int groupPosition)
		{
			return application.getWaypointSets().get(groupPosition);
		}

		@Override
		public int getGroupCount()
		{
			return application.getWaypointSets().size();
		}

		@Override
		public long getGroupId(int groupPosition)
		{
			return groupPosition;
		}

		@Override
		public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent)
		{
			View v;
			if (convertView == null)
			{
				v = mInflater.inflate((isExpanded) ? mExpandedGroupLayout : mCollapsedGroupLayout, parent, false);
			}
			else
			{
				v = convertView;
			}

			TextView text = (TextView) v.findViewById(android.R.id.text1);
			if (text != null)
			{
				text.setText(((WaypointSet) getGroup(groupPosition)).name);
			}

			return v;
		}

		@Override
		public boolean isChildSelectable(int groupPosition, int childPosition)
		{
			return true;
		}

		@Override
		public boolean hasStableIds()
		{
			return true;
		}

		public void sort(final int type)
		{
			Collections.sort(application.getWaypoints(), new Comparator<Waypoint>() {
				@Override
				public int compare(Waypoint o1, Waypoint o2)
				{
					if (type == 1)
					{
						// TODO cache distances
						double dist1 = Geo.distance(loc[0], loc[1], o1.latitude, o1.longitude);
						double dist2 = Geo.distance(loc[0], loc[1], o2.latitude, o2.longitude);
						return (Double.compare(dist1, dist2));
					}
					else
					{
						return (o1.name.compareToIgnoreCase(o2.name));
					}
				}
			});
			notifyDataSetChanged();
		}
	}
}
