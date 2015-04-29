/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2014 Andrey Novikov <http://andreynovikov.info/>
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

package com.androzic.ui;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.androzic.R;
import com.androzic.util.FileList;

public abstract class FileListDialog extends DialogFragment implements OnItemClickListener
{
	private final static String KEY_FILE = "FILE";
	private final static String KEY_PATH = "DIR";

	List<File> files = null;
	List<Map<String, String>> fileData = new ArrayList<Map<String, String>>();

	ViewGroup dialogView;
	ListView listView;
	ProgressBar progressBar;

	protected ExecutorService threadPool = Executors.newFixedThreadPool(2);
	final Handler handler = new Handler();
	private int shortAnimationDuration;

	private int title;
	private OnFileListDialogListener listener;

	public interface OnFileListDialogListener
	{
	    public void onFileLoaded(int count);
	}

    public FileListDialog()
    {
        throw new RuntimeException("Unimplemented initialization context");
    }

	public FileListDialog(int title, OnFileListDialogListener listener)
	{
		this.title = title;
		this.listener = listener;
	}

	@SuppressLint("InflateParams")
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState)
	{
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle(title);
		dialogView = (ViewGroup) getActivity().getLayoutInflater().inflate(R.layout.list_with_empty_view_and_progressbar, null);
		builder.setView(dialogView);

		listView = (ListView) dialogView.findViewById(android.R.id.list);
		progressBar = (ProgressBar) dialogView.findViewById(R.id.progressbar);

		listView.setOnItemClickListener(this);

		shortAnimationDuration = getResources().getInteger(android.R.integer.config_shortAnimTime);

		return builder.create();
	}

	@Override
	public void onResume()
	{
		super.onResume();
		
		listView.setVisibility(View.GONE);
		progressBar.setVisibility(View.VISIBLE);
		
		threadPool.execute(new Runnable() {
			public void run()
			{
				File root = new File(getPath());
				files = FileList.getFileListing(root, getFilenameFilter());
				/*
				 * Collections.sort(files, new Comparator()
				 * {
				 * 
				 * @Override
				 * public int compare(Object o1, Object o2)
				 * {
				 * return ((File) o1).getName().compareToIgnoreCase(((File) o2).getName());
				 * }
				 * });
				 */
				Map<String, String> group;

				for (File file : files)
				{
					group = new HashMap<String, String>();
					group.put(KEY_FILE, file.getName());
					group.put(KEY_PATH, file.getParent());
					fileData.add(group);
				}

				handler.post(updateResults);
			}
		});
	}
	
	@Override
	public void onPause()
	{
		super.onPause();
		files = null;
		fileData.clear();
	}

	final Runnable updateResults = new Runnable() {
		public void run()
		{
			TextView emptyView = (TextView) dialogView.findViewById(android.R.id.empty);
			emptyView.setText(R.string.msg_empty_map_list);
			listView.setEmptyView(emptyView);
			listView.setAdapter(new SimpleAdapter(getActivity(), fileData, android.R.layout.simple_list_item_2, new String[] { KEY_FILE, KEY_PATH }, new int[] { android.R.id.text1, android.R.id.text2 }));
			crossfade(true);
		}
	};

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id)
	{
		final File file = files.get(position);

		if (!file.exists())
		{
			Toast.makeText(getActivity(), R.string.err_nofile, Toast.LENGTH_LONG).show();
			dismiss();
		}

		crossfade(false);

		threadPool.execute(new Runnable() 
		{
			public void run() 
			{
				loadFile(file);
				dismiss();
			};
		});

	}
	
	@Override
	public void onDestroyView()
	{
		if (getDialog() != null && getRetainInstance())
			getDialog().setDismissMessage(null);
		super.onDestroyView();
	}

	protected void onFileLoaded(final int count)
	{
		getActivity().runOnUiThread(new Runnable() {
			@Override
			public void run()
			{
				listener.onFileLoaded(count);
			}			
		});
	}

	abstract protected FilenameFilter getFilenameFilter();

	abstract protected String getPath();

	abstract protected void loadFile(File file);

	@SuppressLint("NewApi")
	private void crossfade(boolean direct)
	{
		final View from = direct ? progressBar : listView;
		final View to = direct ? listView : progressBar;

		if (!direct)
		{
    		dialogView.setMinimumWidth(dialogView.getWidth());
    		dialogView.setMinimumHeight(dialogView.getHeight());
		}

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

	protected final Runnable wrongFormat = new Runnable() {
		public void run()
		{
			Toast.makeText(getActivity(), R.string.err_wrongformat, Toast.LENGTH_LONG).show();
		}
	};

	protected final Runnable readError = new Runnable() {
		public void run()
		{
			Toast.makeText(getActivity(), R.string.err_read, Toast.LENGTH_LONG).show();
		}
	};

}
