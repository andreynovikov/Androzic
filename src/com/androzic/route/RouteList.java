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

import net.londatiga.android.ActionItem;
import net.londatiga.android.QuickAction;
import net.londatiga.android.QuickAction.OnActionItemClickListener;
import android.app.Activity;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import com.androzic.Androzic;
import com.androzic.R;
import com.androzic.data.Route;
import com.androzic.util.StringFormatter;

public class RouteList extends ListActivity
{
	private static final int RESULT_START_ROUTE = 1;
	
	public static final int MODE_MANAGE = 1;
	public static final int MODE_START = 2;

	private static final int qaRouteDetails = 1;
	private static final int qaRouteNavigate = 2;
	private static final int qaRouteProperties = 3;
	private static final int qaRouteEdit = 4;
	private static final int qaRouteSave = 5;
	private static final int qaRouteRemove = 6;

	List<Route> routes = null;
	List<Map<String, String>> routeData = new ArrayList<Map<String, String>>();

	protected ExecutorService threadPool = Executors.newFixedThreadPool(2);
	final Handler handler = new Handler();

	private QuickAction quickAction;
	private int selectedKey;
	private Drawable selectedBackground;

	private final static String KEY_NAME = "NAME";
	private final static String KEY_DESC = "DESC";

	private int mode;

	@Override
	protected void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.list_with_empty_view);

		TextView emptyView = (TextView) getListView().getEmptyView();
		if (emptyView != null)
			emptyView.setText(R.string.msg_empty_route_list);

		mode = getIntent().getExtras().getInt("MODE");

		if (mode == MODE_START)
			setTitle(getString(R.string.selectroute_name));

		Resources resources = getResources();
		quickAction = new QuickAction(this);
		quickAction.addActionItem(new ActionItem(qaRouteDetails, getString(R.string.menu_details), resources.getDrawable(R.drawable.ic_menu_info_details)));
		quickAction.addActionItem(new ActionItem(qaRouteNavigate, getString(R.string.menu_navigate), resources.getDrawable(R.drawable.ic_menu_directions)));
		quickAction.addActionItem(new ActionItem(qaRouteProperties, getString(R.string.menu_properties), resources.getDrawable(R.drawable.ic_menu_edit)));
		quickAction.addActionItem(new ActionItem(qaRouteEdit, getString(R.string.menu_edit), resources.getDrawable(R.drawable.ic_menu_track)));
		quickAction.addActionItem(new ActionItem(qaRouteSave, getString(R.string.menu_save), resources.getDrawable(R.drawable.ic_menu_save)));
		quickAction.addActionItem(new ActionItem(qaRouteRemove, getString(R.string.menu_remove), resources.getDrawable(R.drawable.ic_menu_close_clear_cancel)));

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

		new Thread(new Runnable() {
			public void run()
			{
				Androzic application = (Androzic) getApplication();
				routes = application.getRoutes();

				/*
				 * Collections.sort(files, new Comparator() {
				 * @Override public int compare(Object o1, Object o2) { return
				 * ((File) o1).getName().compareToIgnoreCase(((File)
				 * o2).getName()); } });
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

	final Runnable updateResults = new Runnable() {
		public void run()
		{
			setListAdapter(new SimpleAdapter(RouteList.this, routeData, android.R.layout.simple_list_item_2, new String[] { KEY_NAME, KEY_DESC }, new int[] { android.R.id.text1, android.R.id.text2 }));
			getListView().setTextFilterEnabled(true);
		}
	};

	@Override
	public boolean onCreateOptionsMenu(final Menu menu)
	{
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.routelist_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.menuNewRoute:
				Androzic application = (Androzic) getApplication();
				Route route = new Route("New route", "", true);
				application.addRoute(route);
				int position = application.getRouteIndex(route);
				setResult(Activity.RESULT_OK, new Intent().putExtra("index", position));
				finish();
				return true;
			case R.id.menuLoadRoute:
				startActivity(new Intent(this, RouteFileList.class));
				return true;
		}
		return false;
	}
	
	@Override
	protected void onListItemClick(ListView lv, View v, int position, long id)
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
				startActivityForResult(new Intent(this, RouteStart.class).putExtra("index", position), RESULT_START_ROUTE);
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);

		switch (requestCode)
		{
			case RESULT_START_ROUTE:
				if (resultCode == RESULT_OK)
				{
					setResult(Activity.RESULT_OK, new Intent().putExtras(data.getExtras()));
					finish();
				}
				else
				{
					setResult(Activity.RESULT_CANCELED);
				}
				break;
		}
	}

	@Override
	protected void onStop()
	{
		super.onStop();
		routes = null;
		routeData.clear();

	}

	private OnActionItemClickListener routeActionItemClickListener = new OnActionItemClickListener() {
		@Override
		public void onItemClick(QuickAction source, int pos, int actionId)
		{
			final int position = selectedKey;
			final Route route = routes.get(position);
			final Androzic application = (Androzic) getApplication();

			switch (actionId)
			{
				case qaRouteDetails:
					startActivity(new Intent(RouteList.this, RouteDetails.class).putExtra("index", position));
					break;
				case qaRouteNavigate:
					startActivityForResult(new Intent(RouteList.this, RouteStart.class).putExtra("index", position), RESULT_START_ROUTE);
					break;
				case qaRouteProperties:
					startActivity(new Intent(RouteList.this, RouteProperties.class).putExtra("index", position));
					break;
				case qaRouteEdit:
					setResult(Activity.RESULT_OK, new Intent().putExtra("index", position));
					finish();
					break;
				case qaRouteSave:
					startActivity(new Intent(RouteList.this, RouteSave.class).putExtra("index", position));
					break;
				case qaRouteRemove:
					application.removeRoute(route);
					populateItems();
					break;
			}
		}
	};
}
