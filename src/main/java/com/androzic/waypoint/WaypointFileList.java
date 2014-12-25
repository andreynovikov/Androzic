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

package com.androzic.waypoint;

import android.annotation.SuppressLint;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.androzic.Androzic;
import com.androzic.R;
import com.androzic.ui.FileListDialog;
import com.androzic.util.WaypointFileHelper;
import com.androzic.util.WaypointFilenameFilter;

public class WaypointFileList extends FileListDialog
{
	public WaypointFileList()
	{
		throw new RuntimeException("Unimplemented initialization context");
	}

	//FIXME Fix lint error
	@SuppressLint("ValidFragment")

	public WaypointFileList(OnFileListDialogListener listener)
	{
		super(R.string.loadwaypoints_name, listener);
	}

	@Override
	protected FilenameFilter getFilenameFilter()
	{
		return new WaypointFilenameFilter();
	}

	@Override
	protected String getPath()
	{
		Androzic application = Androzic.getApplication();
		return application.dataPath;
	}

	@Override
	protected void loadFile(File file)
	{
		try
		{
			int count = WaypointFileHelper.loadFile(file);
			onFileLoaded(count);
		}
		catch (IllegalArgumentException e)
		{
			getActivity().runOnUiThread(wrongFormat);
		}
		catch (SAXException e)
		{
			getActivity().runOnUiThread(wrongFormat);
			e.printStackTrace();
		}
		catch (IOException e)
		{
			getActivity().runOnUiThread(readError);
			e.printStackTrace();
		}
		catch (ParserConfigurationException e)
		{
			getActivity().runOnUiThread(readError);
			e.printStackTrace();
		}
	}
}