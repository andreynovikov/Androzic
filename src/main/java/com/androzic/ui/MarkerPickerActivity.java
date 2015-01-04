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

package com.androzic.ui;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.Toast;

import com.androzic.Androzic;
import com.androzic.R;

public class MarkerPickerActivity extends Activity implements OnItemClickListener, OnItemLongClickListener
{
	private List<String> names;
	private List<Bitmap> icons;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.act_markericon);

		names = new ArrayList<>();
		icons = new ArrayList<>();
		
		Androzic application = Androzic.getApplication();
		File dir = new File(application.markerPath);
		
		List<File> result = new ArrayList<>();
		
		File[] files = dir.listFiles(iconFilter);
		if (files != null)
			result.addAll(Arrays.asList(files));
		Collections.sort(result);
		
		for (File file : result)
		{
			Bitmap b = BitmapFactory.decodeFile(file.getAbsolutePath());
			if (b != null)
			{
				names.add(file.getName());
				icons.add(b);
			}
		}

		GridView grid = (GridView) findViewById(R.id.marker_grid);
		grid.setAdapter(new ImageAdapter(this, icons));
		grid.setOnItemClickListener(this);
		grid.setOnItemLongClickListener(this);
	}
	
	@Override
	public void onDestroy()
	{
		super.onDestroy();
		for (Bitmap b : icons)
		{
			b.recycle();
		}
		names.clear();
		icons.clear();
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id)
	{
		setResult(RESULT_OK, new Intent().putExtra("marker", names.get(position)));
		finish();
	}

	@Override
	public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id)
	{
		String name = names.get(position);
		Toast.makeText(this, name.substring(0, name.lastIndexOf(".")), Toast.LENGTH_SHORT).show();
		return true;
	}

	private class ImageAdapter extends BaseAdapter
	{
		private Context context;
		private List<Bitmap> images;
		
		public ImageAdapter(Context context, List<Bitmap> images)
		{
			this.context = context;
			this.images = images;
		}

		@Override
		public int getCount()
		{
			return images.size();
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent)
		{
			ImageView view = (ImageView) convertView;
	        if (convertView == null)
	        {
	            view = new ImageView(context);
	        }

	        view.setImageBitmap(images.get(position));

			return view;
		}

		@Override
		public Object getItem(int position)
		{
			return null;
		}

		@Override
		public long getItemId(int position)
		{
			return 0;
		}
	}
	
	private FilenameFilter iconFilter = new FilenameFilter()
	{

		@Override
		public boolean accept(final File dir, final String filename)
		{
			String lc = filename.toLowerCase();
			return lc.endsWith(".png");
		}

	};
}
