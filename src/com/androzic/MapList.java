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

package com.androzic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.actionbarsherlock.app.SherlockListActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleAdapter;

public class MapList extends SherlockListActivity
{
	List<com.androzic.map.Map> maps;
	List<Map<String, String>> mapData = new ArrayList<Map<String, String>>();
	
	protected ExecutorService threadPool = Executors.newFixedThreadPool(2);
	final Handler handler = new Handler();

	private final static String KEY_NAME = "NAME";
	private final static String KEY_DESC = "DESC";
	
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
	   			Bundle extras = getIntent().getExtras();
	   	        
	   	        if (extras != null && extras.getBoolean("pos"))
	   	        {
	   	        	double[] loc = application.getMapCenter();
	   	        	maps = application.getMaps(loc);
	   	        }
	   	        else
	   	        {
					maps = application.getMaps();
	   	        }
	   	        
				Map<String, String> group;
	   			mapData.clear();
	   			
	   			String mappath = application.getMapPath();
	   			
				for (com.androzic.map.Map map : maps)
				{
					String fn = new String(map.mappath);
					if (fn.startsWith(mappath))
					{
						fn = fn.substring(mappath.length() + 1);
					}
			    	group = new HashMap<String, String>();
					group.put(KEY_NAME, map.title);
					group.put(KEY_DESC, String.format("MPP: %.2f - %s", map.mpp, fn));
					mapData.add(group);
				}

				pd.dismiss(); 
				handler.post(updateResults);
			} 
		}).start(); 
	}

	final Runnable updateList = new Runnable() 
	{
		public void run() 
        {
			populateItems();
        }
	};

	final Runnable updateResults = new Runnable() 
	{
		public void run() 
        {
			setListAdapter(new SimpleAdapter(MapList.this, mapData, android.R.layout.simple_list_item_2, new String[] { KEY_NAME, KEY_DESC }, new int[]{ android.R.id.text1, android.R.id.text2 } ));
			getListView().setTextFilterEnabled(true);
        }
	};

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) 
	{
		super.onListItemClick(l, v, position, id);
		setResult(RESULT_OK, new Intent().putExtra("id", maps.get(position).id));
		finish();
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu)
	{
		MenuInflater inflater = getSupportMenuInflater();
		inflater.inflate(R.menu.maplist_menu, menu);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(final Menu menu)
	{
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		boolean useIndex = settings.getBoolean(getString(R.string.pref_usemapindex), getResources().getBoolean(R.bool.def_usemapindex));

		menu.findItem(R.id.menuResetMapIndex).setEnabled(useIndex);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.menuResetMapIndex:
				final ProgressDialog pd = new ProgressDialog(this);
				pd.setIndeterminate(true);
				pd.setMessage(getString(R.string.msg_initializingmaps));
				pd.show();

				new Thread(new Runnable() 
				{ 
					public void run() 
					{
						Androzic application = (Androzic) getApplication();
						application.resetMaps();

						pd.dismiss(); 
						handler.post(updateList);
					} 
				}).start(); 
				break;
		}
		return true;
	}

	@Override
	protected void onStop()
	{
		super.onStop();
		mapData.clear();
	}

}
