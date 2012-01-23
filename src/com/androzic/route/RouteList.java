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

package com.androzic.route;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.androzic.Androzic;
import com.androzic.MapActivity;
import com.androzic.R;
import com.androzic.data.Route;
import com.androzic.util.StringFormatter;

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

public class RouteList extends ListActivity
{
	public static final int MODE_MANAGE = 1;
	public static final int MODE_START = 2;
	
	List<Route> routes = null;
	List<Map<String, String>> routeData = new ArrayList<Map<String, String>>();

	protected ExecutorService threadPool = Executors.newFixedThreadPool(2);
	final Handler handler = new Handler();

	private final static String KEY_NAME = "NAME";
	private final static String KEY_DESC = "DESC";
	
	private int mode;

	@Override
	protected void onCreate(final Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState);

		mode = getIntent().getExtras().getInt("MODE");

		if (mode == MODE_MANAGE)
			registerForContextMenu(getListView());
		
		if (mode == MODE_START)
			setTitle(getString(R.string.selectroute_name));
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
				routes = application.getRoutes();

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
	   			
	   			routeData.clear();
	   			
				for (Route route : routes)
				{
					group = new HashMap<String, String>();
					group.put(KEY_NAME, route.name);
					String desc = StringFormatter.distanceH(route.distance);
					group.put(KEY_DESC, desc);
					routeData.add(group);
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
			setListAdapter(new SimpleAdapter(RouteList.this, routeData, android.R.layout.simple_list_item_2, new String[] { KEY_NAME, KEY_DESC }, new int[]{ android.R.id.text1, android.R.id.text2 } ));
			getListView().setTextFilterEnabled(true);
        }
	};

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) 
	{
		super.onListItemClick(l, v, position, id);
		switch (mode)
		{
			case MODE_MANAGE:
		        startActivity(new Intent(this, RouteProperties.class).putExtra("index", position));
		        break;
			case MODE_START:
				startActivityForResult(new Intent(this, RouteStart.class).putExtra("index", position), MapActivity.RESULT_START_ROUTE);
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);

		switch (requestCode)
		{
			case MapActivity.RESULT_START_ROUTE:
				if (resultCode == RESULT_OK)
				{
					setResult(Activity.RESULT_OK, new Intent().putExtras(data.getExtras()));
					finish();
				}
				else
				{
					setResult(Activity.RESULT_CANCELED);
				}
		}
	}

	@Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo)
    {
    	MenuInflater inflater = getMenuInflater();
    	inflater.inflate(R.menu.route_context_menu, menu);
    }
	
    public boolean onContextItemSelected(final MenuItem item)
    {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        final int position = info.position;
		final Route route = routes.get(position);
		final Androzic application = (Androzic) getApplication();

    	switch (item.getItemId())
    	{
    		case R.id.menuRouteProperties:
    	        startActivity(new Intent(this, RouteProperties.class).putExtra("index", position));
    			return true;
    		case R.id.menuRouteEdit:
				setResult(Activity.RESULT_OK, new Intent().putExtra("index", position));
				finish();
				return true;    			
    		case R.id.menuRouteSave:
    	        startActivity(new Intent(this, RouteSave.class).putExtra("index", position));
    			return true;
    		case R.id.menuRouteRemove:
    			application.removeRoute(route);
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
		routes = null;
		routeData.clear();
		
	}

}
