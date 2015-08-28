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

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ListFragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.androzic.Androzic;
import com.androzic.R;
import com.androzic.data.Track;
import com.androzic.ui.FileListDialog;
import com.androzic.util.StringFormatter;
import com.daimajia.swipe.adapters.BaseSwipeAdapter;
import com.daimajia.swipe.util.Attributes;

public class TrackList extends ListFragment implements FileListDialog.OnFileListDialogListener
{
	private OnTrackActionListener trackActionsCallback;
	private TrackListAdapter adapter;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setRetainInstance(true);
		setHasOptionsMenu(true);
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

		TextView emptyView = (TextView) getListView().getEmptyView();
		if (emptyView != null)
			emptyView.setText(R.string.msg_empty_track_list);

		adapter = new TrackListAdapter(getActivity());
		setListAdapter(adapter);
		adapter.setMode(Attributes.Mode.Single);
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
			case R.id.action_load:
				TrackFileList fileListDialog = new TrackFileList(this);
				fileListDialog.show(getFragmentManager(), "track_file_list");
				return true;
			case R.id.action_export:
		        TrackExportDialog trackExportDialog = new TrackExportDialog();
		        trackExportDialog.show(getFragmentManager(), "track_export");
				return true;
			case R.id.action_expand:
				new AlertDialog.Builder(getActivity()).setIcon(android.R.drawable.ic_dialog_alert).setTitle(R.string.warning).setMessage(R.string.msg_expandcurrenttrack).setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which)
					{
						Androzic application = Androzic.getApplication();
						application.expandCurrentTrack();
					}
				}).setNegativeButton(R.string.no, null).show();
				return true;
			case R.id.action_clear:
				new AlertDialog.Builder(getActivity()).setIcon(android.R.drawable.ic_dialog_alert).setTitle(R.string.warning).setMessage(R.string.msg_clearcurrenttrack).setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which)
					{
						Androzic application = Androzic.getApplication();
						application.clearCurrentTrack();
					}
				}).setNegativeButton(R.string.no, null).show();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
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
		final Track track = application.getTrack(position);
		trackActionsCallback.onTrackDetails(track);
	}
	
	public class TrackListAdapter extends BaseSwipeAdapter implements View.OnClickListener
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
			mItemLayout = R.layout.list_item_track_swipe;
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
		public int getSwipeLayoutResourceId(int position)
		{
			return R.id.swipe;
		}

		@Override
		public View generateView(int position, ViewGroup parent)
		{
			View convertView = mInflater.inflate(mItemLayout, parent, false);
			return convertView;
		}

		@Override
		public void fillValues(int position, View convertView)
		{
			Track track = getItem(position);
			
			View actionView = convertView.findViewById(R.id.action_view);
			View actionEdit = convertView.findViewById(R.id.action_edit);
			View actionEditPath = convertView.findViewById(R.id.action_edit_path);
			View actionConvert = convertView.findViewById(R.id.action_track_to_route);
			View actionSave = convertView.findViewById(R.id.action_save);
			View actionRemove = convertView.findViewById(R.id.action_remove);
			actionView.setOnClickListener(this);
			actionEdit.setOnClickListener(this);
			actionEditPath.setOnClickListener(this);
			actionConvert.setOnClickListener(this);
			actionSave.setOnClickListener(this);
			actionRemove.setOnClickListener(this);

			TextView text = (TextView) convertView.findViewById(R.id.name);
			text.setText(track.name);
			String distance = StringFormatter.distanceH(track.distance);
			text = (TextView) convertView.findViewById(R.id.distance);
			text.setText(distance);
			text = (TextView) convertView.findViewById(R.id.filename);
			if (track.filepath != null)
			{
				String filepath = track.filepath.startsWith(application.dataPath) ? track.filepath.substring(application.dataPath.length() + 1, track.filepath.length()) : track.filepath;
				text.setText(filepath);
			}
			else
			{
				text.setText("");
			}
			ImageView icon = (ImageView) convertView.findViewById(R.id.icon);
			Bitmap bm = Bitmap.createBitmap((int) (40 * mDensity), (int) (40 * mDensity), Config.ARGB_8888);
			bm.eraseColor(Color.TRANSPARENT);
			Canvas bc = new Canvas(bm);
			mLinePaint.setColor(track.color);
			bc.drawPath(mLinePath, mLinePaint);
			icon.setImageBitmap(bm);
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
			closeItem(position);
			final Track track = application.getTrack(position);
			
			switch (v.getId())
			{
				case R.id.action_view:
					trackActionsCallback.onTrackView(track);
					break;
				case R.id.action_edit:
					trackActionsCallback.onTrackEdit(track);
					break;
				case R.id.action_edit_path:
					trackActionsCallback.onTrackEditPath(track);
					break;
				case R.id.action_track_to_route:
					trackActionsCallback.onTrackToRoute(track);
					break;
				case R.id.action_save:
					trackActionsCallback.onTrackSave(track);
					break;
				case R.id.action_remove:
					application.removeTrack(track);
					adapter.notifyDataSetChanged();
					break;
			}
		}
	}
}
