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

package com.androzic.waypoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.app.ExpandableListActivity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.ExpandableListView.ExpandableListContextMenuInfo;

import com.androzic.Androzic;
import com.androzic.R;
import com.androzic.data.Waypoint;
import com.androzic.data.WaypointSet;
import com.androzic.navigation.NavigationService;
import com.androzic.util.Geo;
import com.androzic.util.StringFormatter;

public class WaypointList extends ExpandableListActivity
{
	protected Map<Long, Waypoint> waypoints = new HashMap<Long, Waypoint>();
	protected List<Map<String, String>> setData = new ArrayList<Map<String, String>>();
	protected List<List<Map<String, String>>> wptData = new ArrayList<List<Map<String, String>>>();
	
	protected ExecutorService threadPool = Executors.newFixedThreadPool(2);
	final Handler handler = new Handler();

	private final static String KEY_NAME = "NAME";
	private final static String KEY_DESC = "DESC";

    private ExpandableListAdapter adapter;
    
	private boolean sortByDistance = false;
	
	@Override
	protected void onCreate(final Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState);
		registerForContextMenu(getExpandableListView());
	}
	
	@Override
	protected void onResume()
	{
		populateItems();
		super.onResume();
	}
	
	private void populateItems()
	{
		final ProgressDialog pd = new ProgressDialog(this);
		pd.setIndeterminate(true);
		pd.setMessage(getString(R.string.msg_wait)); 
		pd.show();
		
		new Thread(new Runnable() 
		{ 
			public void run() 
			{
	   			setData.clear();
				wptData.clear();
				waypoints.clear();
	   			
				Androzic application = Androzic.getApplication();
				final double[] loc = application.getLocation();
				
				List<WaypointSet> wptSets = application.getWaypointSets();
				
				for (WaypointSet set : wptSets)
				{
		            Map<String, String> setMap = new HashMap<String, String>();
		            setData.add(setMap);
		            setMap.put(KEY_NAME, set.name);
					
		            List<Waypoint> wpts = application.getWaypoints(set);

		            Collections.sort(wpts, new Comparator<Waypoint>()
	                {
	                    @Override
	                    public int compare(Waypoint o1, Waypoint o2)
	                    {
	                    	if (sortByDistance)
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

		            List<Map<String, String>> children = new ArrayList<Map<String, String>>();
					int grps = wptData.size();

					for (Waypoint wpt : wpts)
					{
						double dist = Geo.distance(loc[0], loc[1], wpt.latitude, wpt.longitude);
						double bearing = Geo.bearing(loc[0], loc[1], wpt.latitude, wpt.longitude);
						Map<String, String> child = new HashMap<String, String>();
						child.put(KEY_NAME, wpt.name);
						String desc = StringFormatter.coordinates(application.coordinateFormat, " ", wpt.latitude, wpt.longitude)
									+ " | " + StringFormatter.distanceH(dist)+" "+StringFormatter.bearingSimpleH(bearing);
						child.put(KEY_DESC, desc);
		                children.add(child);
						long key = (children.size() << 10) + grps;
						waypoints.put(key, wpt);
					}
					wptData.add(children);
				}

				pd.dismiss(); 
				handler.post(updateResults);
			} 
		}).start(); 
	}

	final Runnable updateResults = new Runnable() 
	{
		public void run() 
        {
	        // Set up our adapter
	        adapter = new SimpleExpandableListAdapter(
	                WaypointList.this,
	                setData,
	                android.R.layout.simple_expandable_list_item_1,
	                new String[] { KEY_NAME, KEY_DESC },
	                new int[] { android.R.id.text1, android.R.id.text2 },
	                wptData,
	                android.R.layout.simple_expandable_list_item_2,
	                new String[] { KEY_NAME, KEY_DESC },
	                new int[] { android.R.id.text1, android.R.id.text2 }
	                );
	        setListAdapter(adapter);
			getExpandableListView().expandGroup(0);
        }
	};

	@Override
	public boolean onChildClick(ExpandableListView parent, View v, int groupPosition, int childPosition, long id)
	{
		long key = ((childPosition + 1) << 10) + groupPosition;
		Androzic application = (Androzic) getApplication();
		int position = application.getWaypointIndex(waypoints.get(key));
		startActivity(new Intent(this, WaypointProperties.class).putExtra("INDEX", position));
		return true;
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu)
	{
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.sort_menu, menu);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(final Menu menu)
	{
		menu.findItem(R.id.menuSortAz).setEnabled(sortByDistance);
		menu.findItem(R.id.menuSortSize).setEnabled(! sortByDistance);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.menuSortAz:
				sortByDistance = false;
				break;
			case R.id.menuSortSize:
				sortByDistance = true;
				break;
		}
		populateItems();
		return true;
	}
				
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo)
    {
    	ExpandableListContextMenuInfo info = (ExpandableListContextMenuInfo) menuInfo;
        int type = ExpandableListView.getPackedPositionType(info.packedPosition);

    	MenuInflater inflater = getMenuInflater();
        if (type == ExpandableListView.PACKED_POSITION_TYPE_CHILD)
        {
        	inflater.inflate(R.menu.waypoint_context_menu, menu);
        }
        else if (type == ExpandableListView.PACKED_POSITION_TYPE_GROUP)
        {
        	inflater.inflate(R.menu.waypointset_context_menu, menu);
    		int grouppos = ExpandableListView.getPackedPositionGroup(info.packedPosition);
       		menu.findItem(R.id.menuWaypointSetRemove).setEnabled(grouppos > 0);
        }
    }

    public boolean onContextItemSelected(final MenuItem item)
    {
    	ExpandableListContextMenuInfo info = (ExpandableListContextMenuInfo) item.getMenuInfo();
		int grouppos = ExpandableListView.getPackedPositionGroup(info.packedPosition);
		int childpos = ExpandableListView.getPackedPositionChild(info.packedPosition);
		long key = ((childpos + 1) << 10) + grouppos;
        Waypoint waypoint = waypoints.get(key);
		Androzic application = (Androzic) getApplication();
		int position = application.getWaypointIndex(waypoints.get(key));

    	switch (item.getItemId())
    	{
    		case R.id.menuWaypointVisible:
    			application.ensureVisible(waypoint);
				finish();
				return true;
			case R.id.menuWaypointNavigate:
				// FIXME context!
				startService(new Intent(this, NavigationService.class).setAction(NavigationService.NAVIGATE_WAYPOINT).putExtra("index", position));
				finish();
				return true;
    		case R.id.menuWaypointProperties:
    	        startActivity(new Intent(this, WaypointProperties.class).putExtra("INDEX", position));
    			return true;
			case R.id.menuWaypointShare:
				Intent i=new Intent(android.content.Intent.ACTION_SEND);
				i.setType("text/plain");
				i.putExtra(Intent.EXTRA_SUBJECT, R.string.currentloc);
				String pos = StringFormatter.coordinates(application.coordinateFormat, " ", waypoint.latitude, waypoint.longitude);
				i.putExtra(Intent.EXTRA_TEXT, waypoint.name + " @ " + pos);
				startActivity(Intent.createChooser(i, getString(R.string.menu_share)));
				return true;
    		case R.id.menuWaypointRemove:
    			application.removeWaypoint(waypoint);
    			populateItems();
    			return true;
    		case R.id.menuWaypointSetRemove:
    			application.removeWaypointSet(grouppos);
    			populateItems();
    			return true;
    		default:
    			return super.onContextItemSelected(item);
    	}
    }

	@Override
	protected void onStop()
	{
		super.onStop();
		waypoints.clear();
		wptData.clear();
		setData.clear();
	}

}
