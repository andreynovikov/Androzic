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

import android.annotation.SuppressLint;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.androzic.Androzic;
import com.androzic.R;
import com.androzic.data.Route;
import com.androzic.ui.FileListDialog;
import com.androzic.util.GpxFiles;
import com.androzic.util.KmlFiles;
import com.androzic.util.OziExplorerFiles;
import com.androzic.util.RouteFilenameFilter;

public class RouteFileList extends FileListDialog
{
    public RouteFileList()
    {
        throw new RuntimeException("Unimplemented initialization context");
    }

    //FIXME Fix lint error
    @SuppressLint("ValidFragment")
    public RouteFileList(OnFileListDialogListener listener)
	{
		super(R.string.loadroute_name, listener);
	}

	@Override
	protected FilenameFilter getFilenameFilter()
	{
		return new RouteFilenameFilter();
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
		Androzic application = Androzic.getApplication();
	    List<Route> routes = null;
		try
		{
			String lc = file.getName().toLowerCase(Locale.getDefault());
			if (lc.endsWith(".rt2") || lc.endsWith(".rte"))
			{
				routes = OziExplorerFiles.loadRoutesFromFile(file, application.charset);
			}
			else if (lc.endsWith(".kml"))
			{
				routes = KmlFiles.loadRoutesFromFile(file);
			}
			else if (lc.endsWith(".gpx"))
			{
				routes = GpxFiles.loadRoutesFromFile(file);
			}
			if (routes.size() > 0)
			{
				for (Route route: routes)
				{
					application.addRoute(route);
				}
				onFileLoaded(routes.size());
			}
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
