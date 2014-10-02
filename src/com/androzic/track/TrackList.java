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

package com.androzic.track;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.londatiga.android.ActionItem;
import net.londatiga.android.QuickAction;
import net.londatiga.android.QuickAction.OnActionItemClickListener;
import android.app.Activity;
import android.content.Context;
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
import com.androzic.data.Track;
import com.androzic.ui.FileListDialog;
import com.androzic.util.StringFormatter;

public class TrackList extends ListFragment implements FileListDialog.OnFileListDialogListener
{
	List<Track> tracks = null;

	private static final int qaTrackProperties = 1;
	private static final int qaTrackEdit = 2;
	private static final int qaTrackToRoute = 3;
	private static final int qaTrackSave = 4;
	private static final int qaTrackRemove = 5;

	private OnTrackActionListener trackActionsCallback;
	
	protected ExecutorService threadPool = Executors.newFixedThreadPool(2);
	final Handler handler = new Handler();

	private TrackListAdapter adapter;
	private QuickAction quickAction;
	private int selectedKey;
	private Drawable selectedBackground;

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
			emptyView.setText(R.string.msg_empty_track_list);

		Activity activity = getActivity();
		
		adapter = new TrackListAdapter(activity);
		setListAdapter(adapter);

		Resources resources = getResources();
		quickAction = new QuickAction(activity);
		quickAction.addActionItem(new ActionItem(qaTrackProperties, getString(R.string.menu_properties), resources.getDrawable(R.drawable.ic_action_edit)));
		quickAction.addActionItem(new ActionItem(qaTrackEdit, getString(R.string.menu_edit), resources.getDrawable(R.drawable.ic_action_track)));
		quickAction.addActionItem(new ActionItem(qaTrackToRoute, getString(R.string.menu_track2route), resources.getDrawable(R.drawable.ic_action_directions)));
		quickAction.addActionItem(new ActionItem(qaTrackSave, getString(R.string.menu_save), resources.getDrawable(R.drawable.ic_action_save)));
		quickAction.addActionItem(new ActionItem(qaTrackRemove, getString(R.string.menu_remove), resources.getDrawable(R.drawable.ic_action_cancel)));

		quickAction.setOnActionItemClickListener(trackActionItemClickListener);
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
			trackActionsCallback = (OnTrackActionListener) activity;
		}
		catch (ClassCastException e)
		{
			throw new ClassCastException(activity.toString() + " must implement OnTrackActionListener");
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
		inflater.inflate(R.menu.tracklist_menu, menu);
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.menuLoadTrack:
				TrackFileList fileListDialog = new TrackFileList(this);
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
	}

	private OnActionItemClickListener trackActionItemClickListener = new OnActionItemClickListener() {
		@Override
		public void onItemClick(QuickAction source, int pos, int actionId)
		{
			final int position = selectedKey;
			final Androzic application = Androzic.getApplication();
			final Track track = application.getTrack(position);

			switch (actionId)
			{
				case qaTrackProperties:
					trackActionsCallback.onTrackEdit(track);
					break;
				case qaTrackEdit:
					trackActionsCallback.onTrackEditPath(track);
					break;
				case qaTrackToRoute:
					trackActionsCallback.onTrackToRoute(track);
					break;
				case qaTrackSave:
					trackActionsCallback.onTrackSave(track);
					break;
				case qaTrackRemove:
					application.removeTrack(track);
					adapter.notifyDataSetChanged();
					break;
			}
		}
	};

	public class TrackListAdapter extends BaseAdapter
	{
		private LayoutInflater mInflater;
		private int mItemLayout;
		private float mDensity;
		private Path mLinePath;
		private Paint mLinePaint;
		private int mTrackWidth;
		private Androzic application;

		public TrackListAdapter(Context context)
		{
			mItemLayout = R.layout.list_item_track;
			mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			mDensity = context.getResources().getDisplayMetrics().density;

			SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);

			mLinePath = new Path();
			mLinePath.setLastPoint(12 * mDensity, 5 * mDensity);
			mLinePath.lineTo(24 * mDensity, 12 * mDensity);
			mLinePath.lineTo(15 * mDensity, 24 * mDensity);
			mLinePath.lineTo(28 * mDensity, 35 * mDensity);

			mTrackWidth = settings.getInt(context.getString(R.string.pref_tracking_linewidth), context.getResources().getInteger(R.integer.def_track_linewidth));
			mLinePaint = new Paint();
			mLinePaint.setAntiAlias(true);
			mLinePaint.setStrokeWidth(mTrackWidth * mDensity);
			mLinePaint.setStyle(Paint.Style.STROKE);
			mLinePaint.setColor(context.getResources().getColor(R.color.routeline));

			application = Androzic.getApplication();
		}

		@Override
		public Track getItem(int position)
		{
			return application.getTrack(position);
		}

		@Override
		public long getItemId(int position)
		{
			return position;
		}

		@Override
		public int getCount()
		{
			return application.getTracks().size();
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
			Track track = getItem(position);
			TextView text = (TextView) v.findViewById(R.id.name);
			text.setText(track.name);
			String distance = StringFormatter.distanceH(track.distance);
			text = (TextView) v.findViewById(R.id.distance);
			text.setText(distance);
			text = (TextView) v.findViewById(R.id.filename);
			if (track.filepath != null)
			{
				String filepath = track.filepath.startsWith(application.dataPath) ? track.filepath.substring(application.dataPath.length() + 1, track.filepath.length()) : track.filepath;
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
			mLinePaint.setColor(track.color);
			bc.drawPath(mLinePath, mLinePaint);
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
