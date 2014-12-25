/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2013 Andrey Novikov <http://andreynovikov.info/>
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

import java.text.SimpleDateFormat;
import java.util.Date;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import com.androzic.data.Track;
import com.androzic.util.StringFormatter;

public class MapActivity extends ActionBarActivity implements View.OnClickListener, SeekBar.OnSeekBarChangeListener
{
	private static final String TAG = "MapActivity";

	private static final int RESULT_MANAGE_TRACKS = 0x600;

	// main preferences
	protected int showDistance;
	protected boolean showAccuracy;

	protected SeekBar trackBar;
	protected MapView map;

	protected Androzic application;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		Log.e(TAG, "onCreate()");

		application = (Androzic) getApplication();

		setContentView(R.layout.act_main);
		trackBar = (SeekBar) findViewById(R.id.trackbar);
		map = (MapView) findViewById(R.id.mapview);

		findViewById(R.id.finishtrackedit).setOnClickListener(this);
		findViewById(R.id.cutafter).setOnClickListener(this);
		findViewById(R.id.cutbefore).setOnClickListener(this);

		trackBar.setOnSeekBarChangeListener(this);
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		Log.e(TAG, "onResume()");

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);

		showDistance = Integer.parseInt(settings.getString(getString(R.string.pref_showdistance_int), getString(R.string.def_showdistance)));

		// prepare views
		if (application.editingTrack != null)
		{
			startEditTrack(application.editingTrack);
		}
	}

	private void startEditTrack(Track track)
	{
		setFollowing(false);
		application.editingTrack = track;
		application.editingTrack.editing = true;
		int n = application.editingTrack.getAllPoints().size() - 1;
		int p = application.editingTrack.editingPos >= 0 ? application.editingTrack.editingPos : n;
		application.editingTrack.editingPos = p;
		trackBar.setMax(n);
		trackBar.setProgress(0);
		trackBar.setProgress(p);
		trackBar.setKeyProgressIncrement(1);
		onProgressChanged(trackBar, p, false);
		findViewById(R.id.edittrack).setVisibility(View.VISIBLE);
		findViewById(R.id.trackdetails).setVisibility(View.VISIBLE);
		//updateGPSStatus();
		if (showDistance > 0)
			application.overlayManager.distanceOverlay.setEnabled(false);
		map.setFocusable(false);
		map.setFocusableInTouchMode(false);
		trackBar.requestFocus();
		//updateMapViewArea();
	}

	public void setFollowing(boolean follow)
	{
		if (application.editingRoute == null && application.editingTrack == null)
		{
			if (showDistance > 0 && application.overlayManager.distanceOverlay != null)
			{
				if (showDistance == 2 && !follow)
				{
					application.overlayManager.distanceOverlay.setAncor(application.getLocation());
					application.overlayManager.distanceOverlay.setEnabled(true);
				}
				else
				{
					application.overlayManager.distanceOverlay.setEnabled(false);
				}
			}
			map.setFollowing(follow);
		}
	}

	@Override
	public boolean onPrepareOptionsMenu(final Menu menu)
	{
		if (application.editingRoute != null || application.editingTrack != null)
			return false;

		menu.findItem(R.id.menuSetAnchor).setVisible(showDistance > 0 && !map.isFollowing());

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.menuSetAnchor:
				if (showDistance > 0)
				{
					application.overlayManager.distanceOverlay.setAncor(application.getMapCenter());
					application.overlayManager.distanceOverlay.setEnabled(true);
				}
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
			case RESULT_MANAGE_TRACKS:
				if (resultCode == RESULT_OK)
				{
					Bundle extras = data.getExtras();
					int index = extras.getInt("index");
					startEditTrack(application.getTrack(index));
				}
				break;
		}
	}

	@Override
	public void onClick(View v)
	{
		switch (v.getId())
		{
			case R.id.cutbefore:
				//application.editingTrack.cutBefore(trackBar.getProgress());
				int nb = application.editingTrack.getAllPoints().size() - 1;
				trackBar.setMax(nb);
				trackBar.setProgress(0);
				break;
			case R.id.cutafter:
				//application.editingTrack.cutAfter(trackBar.getProgress());
				int na = application.editingTrack.getAllPoints().size() - 1;
				trackBar.setMax(na);
				trackBar.setProgress(0);
				trackBar.setProgress(na);
				break;
			case R.id.finishtrackedit:
				application.editingTrack.editing = false;
				application.editingTrack.editingPos = -1;
				application.editingTrack = null;
				findViewById(R.id.edittrack).setVisibility(View.GONE);
				findViewById(R.id.trackdetails).setVisibility(View.GONE);
				//updateGPSStatus();
				if (showDistance == 2)
				{
					application.overlayManager.distanceOverlay.setEnabled(true);
				}
				map.setFocusable(true);
				map.setFocusableInTouchMode(true);
				map.requestFocus();
				break;
		}
	}

	@Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
	{
		switch (seekBar.getId())
		{
			case R.id.trackbar:
				if (fromUser)
				{
					application.editingTrack.editingPos = progress;
				}
				Track.TrackPoint tp = application.editingTrack.getPoint(progress);
				//double ele = tp.elevation * elevationFactor;
				((TextView) findViewById(R.id.tp_number)).setText("#" + (progress + 1));
				// FIXME Need UTM support here
				((TextView) findViewById(R.id.tp_latitude)).setText(StringFormatter.coordinate(tp.latitude));
				((TextView) findViewById(R.id.tp_longitude)).setText(StringFormatter.coordinate(tp.longitude));
				//((TextView) findViewById(R.id.tp_elevation)).setText(String.valueOf(Math.round(ele)) + " " + elevationAbbr);
				((TextView) findViewById(R.id.tp_time)).setText(SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.SHORT, SimpleDateFormat.SHORT).format(new Date(tp.time)));
				boolean mapChanged = application.setMapCenter(tp.latitude, tp.longitude, true, false, false);
				if (mapChanged)
					map.updateMapInfo();
				map.updateMapCenter();
				break;
		}
	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar)
	{
	}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar)
	{
	}
}
