package com.androzic.ui;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.androzic.Androzic;
import com.androzic.R;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;

public class MarkerPickerActivity extends Activity implements OnItemClickListener, OnItemLongClickListener
{
	private GridView grid;
	private List<String> names;
	private List<Bitmap> icons;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.act_markericon);

		names = new ArrayList<String>();
		icons = new ArrayList<Bitmap>();
		
		Androzic application = Androzic.getApplication();
		File dir = new File(application.iconPath);
		
		List<File> result = new ArrayList<File>();
		
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

		grid = (GridView) findViewById(R.id.marker_grid);
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
		setResult(Activity.RESULT_OK, new Intent().putExtra("icon", names.get(position)));
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
