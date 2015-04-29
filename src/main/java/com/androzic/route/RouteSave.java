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

package com.androzic.route;

import java.io.File;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.androzic.Androzic;
import com.androzic.R;
import com.androzic.data.Route;
import com.androzic.util.FileUtils;
import com.androzic.util.OziExplorerFiles;

public class RouteSave extends DialogFragment
{
	private TextView filename;
	private Route route;
	
	public RouteSave()
	{
		throw new RuntimeException("Unimplemented initialization context");
	}

    //FIXME Fix lint error
    @SuppressLint("ValidFragment")
	public RouteSave(Route route)
	{
		this.route = route;
		setRetainInstance(true);
	}

	@NonNull
	@SuppressLint("InflateParams")
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState)
	{
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle(getString(R.string.saveroute_name));
		final View view = getActivity().getLayoutInflater().inflate(R.layout.act_save, null);

		filename = (TextView) view.findViewById(R.id.filename_text);

		if (route.filepath != null)
		{
			File file = new File(route.filepath);
			filename.setText(file.getName());
		}
		else
		{
			filename.setText(FileUtils.sanitizeFilename(route.name) + ".rt2");
		}

		builder.setView(view);
		builder.setPositiveButton(R.string.save, saveOnClickListener);
		builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener()
		{
			@Override
			public void onClick(DialogInterface dialog, int which)
			{
				getDialog().cancel();
			}
		});

		return builder.create();
	}

	@Override
	public void onDestroyView()
	{
		if (getDialog() != null && getRetainInstance())
			getDialog().setDismissMessage(null);
		super.onDestroyView();
	}

	private DialogInterface.OnClickListener saveOnClickListener = new DialogInterface.OnClickListener()
	{

		@Override
		public void onClick(DialogInterface dialog, int which)
        {
    		String fname = filename.getText().toString();
    		fname = fname.replace("../", "");
    		fname = fname.replace("/", "");
    		if ("".equals(fname))
    			return;
    		
    		try
    		{
    			Androzic application = Androzic.getApplication();
    			File dir = new File(application.dataPath);
    			if (! dir.exists())
    				dir.mkdirs();
    			File file = new File(dir, fname);
    			if (! file.exists())
    			{
    				file.createNewFile();
    			}
    			if (file.canWrite())
    			{
    				OziExplorerFiles.saveRouteToFile(file, application.charset, route);
    				route.filepath = file.getAbsolutePath();
    			}
        		dismiss();
    		}
    		catch (Exception e)
    		{
    			Toast.makeText(getActivity(), R.string.err_write, Toast.LENGTH_LONG).show();
    			Log.e("ANDROZIC", e.toString(), e);
    		}
        }
	};
}
