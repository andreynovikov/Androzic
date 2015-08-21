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

package com.androzic.track;

import java.util.Date;
import java.util.Locale;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.androzic.Androzic;
import com.androzic.FragmentHolder;
import com.androzic.R;
import com.androzic.data.Track;
import com.androzic.util.Geo;
import com.androzic.util.MeanValue;
import com.androzic.util.StringFormatter;

public class TrackDetails extends Fragment
{
	private FragmentHolder fragmentHolderCallback;
	private OnTrackActionListener trackActionsCallback;

	private Track track;
	private Drawable fabDrawable;
	private FloatingActionButton fab;

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
		return inflater.inflate(R.layout.track_details, container, false);
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
			trackActionsCallback = (OnTrackActionListener) activity;
		}
		catch (ClassCastException e)
		{
			throw new ClassCastException(activity.toString() + " must implement OnTrackActionListener");
		}
	}

	@Override
	public void onStart()
	{
		super.onStart();

		if (track != null)
			updateTrackDetails();
	}

	@Override
	public void onResume()
	{
		super.onResume();

		fab = fragmentHolderCallback.enableActionButton();
		fabDrawable = fab.getDrawable();
		fab.setImageResource(R.drawable.ic_visibility_white_24dp);
		fab.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v)
			{
				trackActionsCallback.onTrackView(track);
			}
		});
	}

	@Override
	public void onPause()
	{
		super.onPause();
		
		fab.setImageDrawable(fabDrawable);
		fragmentHolderCallback.disableActionButton();
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
	{
		inflater.inflate(R.menu.track_menu, menu);
		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public void onPrepareOptionsMenu(final Menu menu)
	{
		menu.findItem(R.id.action_view).setVisible(false);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.action_edit:
				trackActionsCallback.onTrackEdit(track);
				return true;
			case R.id.action_edit_path:
				trackActionsCallback.onTrackEditPath(track);
				return true;
			case R.id.action_track_to_route:
				trackActionsCallback.onTrackToRoute(track);
				return true;
			case R.id.action_save:
				trackActionsCallback.onTrackSave(track);
				return true;
			case R.id.action_remove:
				Androzic application = Androzic.getApplication();
				application.removeTrack(track);
				// "Close" fragment
				getFragmentManager().popBackStack();
				return true;
		}
		return false;
	}

	public void setTrack(Track track)
	{
		this.track = track;
		
		if (isVisible())
		{
			updateTrackDetails();
		}
	}

	private void updateTrackDetails()
	{
		AppCompatActivity activity = (AppCompatActivity) getActivity();
		Resources resources = getResources();
		
		activity.getSupportActionBar().setTitle(track.name);

		View view = getView();

		int pointCount = track.getPointCount();
		((TextView) view.findViewById(R.id.point_count)).setText(resources.getQuantityString(R.plurals.numberOfPoints, pointCount, pointCount));

		String distance = StringFormatter.distanceH(track.distance);
		((TextView) view.findViewById(R.id.distance)).setText(distance);

		Track.TrackPoint ftp = track.getPoint(0);
		Track.TrackPoint ltp = track.getLastPoint();

		String start_coords = StringFormatter.coordinates(" ", ftp.latitude, ftp.longitude);
		((TextView) view.findViewById(R.id.start_coordinates)).setText(start_coords);
		String finish_coords = StringFormatter.coordinates(" ", ltp.latitude, ltp.longitude);
		((TextView) view.findViewById(R.id.finish_coordinates)).setText(finish_coords);

		Date start_date = new Date(ftp.time);
		((TextView) view.findViewById(R.id.start_date)).setText(DateFormat.getDateFormat(activity).format(start_date)+" "+DateFormat.getTimeFormat(activity).format(start_date));
		Date finish_date = new Date(ltp.time);
		((TextView) view.findViewById(R.id.finish_date)).setText(DateFormat.getDateFormat(activity).format(finish_date)+" "+DateFormat.getTimeFormat(activity).format(finish_date));

		long elapsed = (ltp.time - ftp.time) / 1000;
		String timeSpan;
		if (elapsed < 24 * 60 * 60 * 3)
		{
			timeSpan = DateUtils.formatElapsedTime(elapsed);
		}
		else
		{
			timeSpan = DateUtils.formatDateRange(activity, ftp.time, ltp.time, DateUtils.FORMAT_ABBREV_MONTH);
		}
		((TextView) view.findViewById(R.id.time_span)).setText(timeSpan);

		// Gather statistics
		int segmentCount = 0;
		double minElevation = Double.MAX_VALUE;
		double maxElevation = Double.MIN_VALUE;
		double maxSpeed = 0;
				
		MeanValue mv = new MeanValue();

		for (Track.TrackSegment segment : track.getSegments())
		{
			Track.TrackPoint ptp = null;
			if (segment.independent)
				segmentCount++;

			for (Track.TrackPoint tp : segment.getPoints())
			{
				if (ptp != null)
				{
					double d = Geo.distance(tp.latitude, tp.longitude, ptp.latitude, ptp.longitude);
					double speed = d / ((tp.time - ptp.time) / 1000);
					if (speed == Double.POSITIVE_INFINITY)
						continue;
					mv.addValue(speed);
					if (speed > maxSpeed)
						maxSpeed = speed;
				}
				ptp = tp;
				if (tp.elevation < minElevation && tp.elevation != 0)
					minElevation = tp.elevation;
				if (tp.elevation > maxElevation)
					maxElevation = tp.elevation;
			}
		}

		double averageSpeed = mv.getMeanValue();

		((TextView) view.findViewById(R.id.segment_count)).setText(resources.getQuantityString(R.plurals.numberOfSegments, segmentCount, segmentCount));

		((TextView) view.findViewById(R.id.max_elevation)).setText(StringFormatter.elevationH(maxElevation));
		((TextView) view.findViewById(R.id.min_elevation)).setText(StringFormatter.elevationH(minElevation));

		((TextView) view.findViewById(R.id.max_speed)).setText(String.format(Locale.getDefault(), "%s: %s", resources.getString(R.string.max_speed), StringFormatter.speedH(maxSpeed)));
		((TextView) view.findViewById(R.id.average_speed)).setText(String.format(Locale.getDefault(), "%s: %s", resources.getString(R.string.average_speed), StringFormatter.speedH(averageSpeed)));
	}
}
