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

package com.androzic.track;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.app.Activity;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.AdapterView.AdapterContextMenuInfo;

import com.androzic.Androzic;
import com.androzic.R;
import com.androzic.data.Track;
import com.androzic.util.StringFormatter;

public class TrackList extends ListActivity
{
	List<Track> tracks = null;
	List<Map<String, String>> trackData = new ArrayList<Map<String, String>>();
	
	protected ExecutorService threadPool = Executors.newFixedThreadPool(2);
	final Handler handler = new Handler();

	private final static String KEY_NAME = "NAME";
	private final static String KEY_DESC = "DESC";
	
	@Override
	protected void onCreate(final Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState);
		registerForContextMenu(getListView());
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
				Androzic application = (Androzic) getApplication();
				tracks = application.getTracks();

/*
				Collections.sort(files, new Comparator()
                        {
                            @Override
                            public int compare(Object o1, Object o2)
                            {
                        	return ((File) o1).getName().compareToIgnoreCase(((File) o2).getName());
                            }
                        });
*/            	
				Map<String, String> group;
	   			
	   			trackData.clear();
	   			
				for (Track track : tracks)
				{
					group = new HashMap<String, String>();
					group.put(KEY_NAME, track.name);
					String desc = StringFormatter.distanceH(track.distance);
					group.put(KEY_DESC, desc);
					trackData.add(group);
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
			setListAdapter(new SimpleAdapter(TrackList.this, trackData, android.R.layout.simple_list_item_2, new String[] { KEY_NAME, KEY_DESC }, new int[]{ android.R.id.text1, android.R.id.text2 } ));
			getListView().setTextFilterEnabled(true);
        }
	};

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) 
	{
		super.onListItemClick(l, v, position, id);
        startActivity(new Intent(this, TrackProperties.class).putExtra("INDEX", position));
	}

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo)
    {
    	MenuInflater inflater = getMenuInflater();
    	inflater.inflate(R.menu.track_context_menu, menu);
    }

    public boolean onContextItemSelected(final MenuItem item)
    {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        final int position = info.position;
		final Track track = tracks.get(position);
		final Androzic application = (Androzic) getApplication();

    	switch (item.getItemId())
    	{
    		case R.id.menuTrackProperties:
    	        startActivity(new Intent(this, TrackProperties.class).putExtra("INDEX", position));
    			return true;
    		case R.id.menuTrackEdit:
				setResult(Activity.RESULT_OK, new Intent().putExtra("index", position));
				finish();
				return true;
    		case R.id.menuTrackToRoute:
    	        startActivity(new Intent(this, TrackToRoute.class).putExtra("INDEX", position));
    			finish();
    			return true;
    		case R.id.menuTrackSave:
    	        startActivity(new Intent(this, TrackSave.class).putExtra("INDEX", position));
    			return true;
    		case R.id.menuTrackRemove:
    			tracks.remove(position);
    			application.removeTrack(track);
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
		tracks = null;
		trackData.clear();
	}
	
}
