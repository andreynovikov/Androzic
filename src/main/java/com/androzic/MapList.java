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

package com.androzic;

import java.io.File;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.ListFragment;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.androzic.map.BaseMap;
import com.androzic.map.OnMapActionListener;
import com.androzic.map.online.OnlineMap;

public class MapList extends ListFragment
{
	private final Handler handler = new Handler();

	private OnMapActionListener mapActionsCallback;
	private MapListAdapter adapter;
	private TreeNode<BaseMap> mapsTree = new TreeNode<>();
	private MapComparator mapComparator = new MapComparator();
	private boolean populated;
	private TreeNode<BaseMap> currentTree;
	private ProgressBar progressBar;
	private int shortAnimationDuration;
	
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setRetainInstance(true);
		shortAnimationDuration = getResources().getInteger(android.R.integer.config_shortAnimTime);
		currentTree = mapsTree;
		populated = false;
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		View view = inflater.inflate(R.layout.list_with_empty_view_and_progressbar, container, false);
		
		progressBar = (ProgressBar) view.findViewById(R.id.progressbar);

		view.setFocusableInTouchMode(true);
		view.requestFocus();
		view.setOnKeyListener(new View.OnKeyListener() {
			@Override
			public boolean onKey(View v, int keyCode, KeyEvent event)
			{
				if (event.getAction() == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_BACK)
				{
					if (currentTree != mapsTree)
					{
						currentTree = currentTree.parent;
						adapter.notifyDataSetChanged();
						return true;
					}
				}
				return false;
			}
		});

		return view;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);
		setHasOptionsMenu(true);

		TextView emptyView = (TextView) getListView().getEmptyView();
		if (emptyView != null)
			emptyView.setText(R.string.msg_empty_map_list);

		Activity activity = getActivity();

		adapter = new MapListAdapter(activity);
		setListAdapter(adapter);
	}
	
	@Override
	public void onAttach(Activity activity)
	{
		super.onAttach(activity);

		// This makes sure that the container activity has implemented
		// the callback interface. If not, it throws an exception
		try
		{
			mapActionsCallback = (OnMapActionListener) activity;
		}
		catch (ClassCastException e)
		{
			throw new ClassCastException(activity.toString() + " must implement OnMapActionListener");
		}
	}

	@Override
	public void onResume()
	{
		super.onResume();
		((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(R.string.maplist_name);
		if (!populated)
			populateItems();
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
	{
		inflater.inflate(R.menu.maplist_menu, menu);
	}

	@Override
	public void onPrepareOptionsMenu(final Menu menu)
	{
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getActivity());
		boolean useIndex = settings.getBoolean(getString(R.string.pref_usemapindex), getResources().getBoolean(R.bool.def_usemapindex));
		menu.findItem(R.id.action_reset_index).setEnabled(useIndex);
	}
	
	@SuppressLint("NewApi")
	private void crossfade(boolean direct)
	{
		View listView = adapter.getCount() > 0 ? getListView() : getListView().getEmptyView();
		final View from = direct ? progressBar : listView;
		final View to = direct ? listView : progressBar;

		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB_MR1)
		{
			from.setVisibility(View.GONE);
			to.setVisibility(View.VISIBLE);
		}
		else
		{
			// Set the content view to 0% opacity but visible, so that it is visible
			// (but fully transparent) during the animation.
			to.setAlpha(0f);
			to.setVisibility(View.VISIBLE);
	
			// Animate the content view to 100% opacity, and clear any animation
			// listener set on the view.
			to.animate().alpha(1f).setDuration(shortAnimationDuration).setListener(null);
	
			// Animate the loading view to 0% opacity. After the animation ends,
			// set its visibility to GONE as an optimization step (it won't
			// participate in layout passes, etc.)
			from.animate().alpha(0f).setDuration(shortAnimationDuration).setListener(new AnimatorListenerAdapter() {
				@Override
				public void onAnimationEnd(Animator animation)
				{
					from.setVisibility(View.GONE);
				}
			});
		}
	}

	private void populateItems()
	{
		crossfade(false);
		
		new Thread(new Runnable() 
		{
			public void run() 
			{
				populated = true;
				Androzic application = Androzic.getApplication();
				TreeNode<BaseMap> onlinemaps = null;
				String mappath = application.getMapPath();
	   	        
				for (BaseMap map : application.getMaps())
				{
					if (map instanceof OnlineMap)
					{
						if (onlinemaps == null)
							onlinemaps = mapsTree.addChild(getResources().getString(R.string.online_maps));
						onlinemaps.addChild(map.path, map);
					}
					else
					{
						String fn = map.path;
						if (fn.startsWith(mappath))
						{
							fn = fn.substring(mappath.length() + 1);
						}
						String[] components = fn.split(File.separator);
						TreeNode<BaseMap> folder = mapsTree;
						for (int i = 0; i < components.length - 1; i++)
						{
							TreeNode<BaseMap> subfolder = folder.findChild(components[i]);
							if (subfolder == null)
								subfolder = folder.addChild(components[i]);
							folder = subfolder;
						}
						folder.addChild(components[components.length - 1], map);
					}
					
					mapsTree.sort(mapComparator);
					currentTree = mapsTree;
				}

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
			adapter.notifyDataSetChanged();
			crossfade(true);
        }
	};

	@Override
	public void onListItemClick(ListView l, View v, int position, long id) 
	{
		TreeNode<BaseMap> item = currentTree.children.get(position);
		if (item.data != null)
		{
			mapActionsCallback.onMapSelected(item.data);
			getFragmentManager().popBackStack();
		}
		else
		{
			item.sort(mapComparator);
			currentTree = item;
			adapter.notifyDataSetChanged();
		}
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.action_reset_index:
				crossfade(false);
				mapsTree.clear();
				currentTree = mapsTree;
				adapter.notifyDataSetChanged();

				new Thread(new Runnable() 
				{ 
					public void run() 
					{
						Androzic application = Androzic.getApplication();
						application.resetMaps();
						handler.post(updateList);
					} 
				}).start(); 
				break;
		}
		return true;
	}

	public class MapListAdapter extends BaseAdapter
	{
		private static final int VIEW_TYPE_FOLDER = 0;
		private static final int VIEW_TYPE_MAP = 1;

		private LayoutInflater mInflater;
		private double ppcm;

		public MapListAdapter(Context context)
		{
			mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

			SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
			Resources resources = getResources();
			boolean scaleInMeters = settings.getBoolean(getString(R.string.pref_maplistscale), resources.getBoolean(R.bool.def_maplistscale));
			
			DisplayMetrics metrics = resources.getDisplayMetrics();
			ppcm = metrics.xdpi / 2.54;
			if (!scaleInMeters)
				ppcm *= 100;
		}

		@Override
		public int getViewTypeCount()
		{
			return 2;
		}

		@Override
		public int getItemViewType(int position)
		{
			TreeNode<BaseMap> item = getItem(position);
			if (item.data != null)
			{
				return VIEW_TYPE_MAP;
			}
			else
			{
				return VIEW_TYPE_FOLDER;
			}
		}

		@Override
		public TreeNode<BaseMap> getItem(int position)
		{
			return currentTree.children.get(position);
		}

		@Override
		public long getItemId(int position)
		{
			return currentTree.children.get(position).getId();
		}

		@Override
		public int getCount()
		{
			return currentTree.children.size();
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent)
		{
			MapListItemHolder itemHolder;
			TreeNode<BaseMap> item = getItem(position);

			int type = getItemViewType(position);
			if (convertView == null)
			{
				itemHolder = new MapListItemHolder();
				if (type == VIEW_TYPE_FOLDER)
				{
					convertView = mInflater.inflate(R.layout.list_item_folder, parent, false);
					itemHolder.name = (TextView) convertView.findViewById(R.id.name);
				}
				else if (type == VIEW_TYPE_MAP)
				{
					convertView = mInflater.inflate(R.layout.list_item_map, parent, false);
					itemHolder.name = (TextView) convertView.findViewById(R.id.name);
					itemHolder.scale = (TextView) convertView.findViewById(R.id.scale);
					itemHolder.filename = (TextView) convertView.findViewById(R.id.filename);
				}
				else
				{
					return null;
				}
				convertView.setTag(itemHolder);
			}
			else
			{
				itemHolder = (MapListItemHolder) convertView.getTag();
			}

			if (type == VIEW_TYPE_FOLDER)
			{
				itemHolder.name.setText(item.name);
			}
			else if (type == VIEW_TYPE_MAP)
			{
				itemHolder.name.setText(item.data.title);
				itemHolder.filename.setText(item.name);
				itemHolder.scale.setText(String.format("1:%,d", (int) (item.data.getAbsoluteMPP() * ppcm)));
			}

			return convertView;
		}
	
		@Override
		public boolean hasStableIds()
		{
			return true;
		}
	}

	private static class MapListItemHolder
	{
		TextView name;
		TextView scale;
		TextView filename;
	}

	public class TreeNode<T>
	{
		String name;
		T data;
		TreeNode<T> parent;
		List<TreeNode<T>> children;

		public TreeNode()
		{
			this.children = new LinkedList<>();
		}

		public TreeNode(String name)
		{
			this();
			this.name = name;
		}

		public TreeNode(String name, T data)
		{
			this(name);
			this.data = data;
		}

		public TreeNode<T> addChild(String name)
		{
			TreeNode<T> childNode = new TreeNode<>(name);
			childNode.parent = this;
			children.add(childNode);
			return childNode;
		}

		public TreeNode<T> addChild(String name, T child)
		{
			TreeNode<T> childNode = new TreeNode<>(name, child);
			childNode.parent = this;
			children.add(childNode);
			return childNode;
		}

		public TreeNode<T> findChild(String name)
		{
			for (TreeNode<T> child : children)
			{
				if (name.equals(child.name))
					return child;
			}
			return null;
		}

		public long getId()
		{
			if (data != null)
				return data.hashCode();
			else
				return name.hashCode();
		}

		public void clear()
		{
			for (TreeNode<T> child : children)
			{
				child.clear();
			}
			children.clear();
		}

		public void sort(Comparator<TreeNode<T>> comparator)
		{
			Collections.sort(children, comparator);
		}
	}

	class MapComparator implements Comparator<TreeNode<BaseMap>>
	{
		@Override
		public int compare(TreeNode<BaseMap> a, TreeNode<BaseMap> b)
		{
			if (a.data != null && b.data != null)
				return a.data.title.compareToIgnoreCase(b.data.title);
			if (a.data != null)
				return 1;
			if (b.data != null)
				return -1;
			return a.name.compareToIgnoreCase(b.name);
		}
	}
}
