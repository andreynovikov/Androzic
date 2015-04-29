package com.androzic.ui;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
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

public class MarkerPicker extends DialogFragment implements OnItemClickListener, OnItemLongClickListener
{
	private List<String> names;
	private List<Bitmap> icons;
	private OnMarkerPickerDialogListener listener;

	public interface OnMarkerPickerDialogListener
	{
	    public void onMarkerSelected(String icon);
	}

    public MarkerPicker()
    {
        throw new RuntimeException("Unimplemented initialization context");
    }

    //FIXME Fix lint error
    @SuppressLint("ValidFragment")
	public MarkerPicker(OnMarkerPickerDialogListener listener)
	{
		this.listener = listener;
	}

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

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
	}

	@NonNull
	@SuppressLint("InflateParams")
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState)
	{
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle(getString(R.string.markericon_name));
		View view = getActivity().getLayoutInflater().inflate(R.layout.act_markericon, null);
		builder.setView(view);

		GridView grid = (GridView) view.findViewById(R.id.marker_grid);
		grid.setAdapter(new ImageAdapter(getActivity(), icons));
		grid.setOnItemClickListener(this);
		grid.setOnItemLongClickListener(this);

		return builder.create();
	}

	@Override
	public void onDestroyView()
	{
		if (getDialog() != null && getRetainInstance())
			getDialog().setDismissMessage(null);
		super.onDestroyView();
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
		listener.onMarkerSelected(names.get(position));
		dismiss();
	}

	@Override
	public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id)
	{
		String name = names.get(position);
		Toast.makeText(getActivity(), name.substring(0, name.lastIndexOf(".")), Toast.LENGTH_SHORT).show();
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

	private FilenameFilter iconFilter = new FilenameFilter() {

		@Override
		public boolean accept(final File dir, final String filename)
		{
			String lc = filename.toLowerCase(Locale.getDefault());
			return lc.endsWith(".png");
		}

	};
}
