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

package com.androzic.track;

import android.annotation.SuppressLint;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.androzic.Androzic;
import com.androzic.R;
import com.androzic.data.Track;
import com.androzic.ui.FileListDialog;
import com.androzic.util.GpxFiles;
import com.androzic.util.KmlFiles;
import com.androzic.util.OziExplorerFiles;
import com.androzic.util.TrackFilenameFilter;

public class TrackFileList extends FileListDialog
{
	public TrackFileList()
	{
		throw new RuntimeException("Unimplemented initialization context");
	}

	//FIXME Fix lint error
	@SuppressLint("ValidFragment")
	public TrackFileList(OnFileListDialogListener listener)
	{
		super(R.string.loadtrack_name, listener);
	}

	@Override
	protected FilenameFilter getFilenameFilter()
	{
		return new TrackFilenameFilter();
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
		List<Track> tracks = null;
		try
		{
			String lc = file.getName().toLowerCase(Locale.getDefault());
			if (lc.endsWith(".plt"))
			{
				tracks = new ArrayList<Track>();
			    tracks.add(OziExplorerFiles.loadTrackFromFile(file, application.charset));
			}
			else if (lc.endsWith(".kml"))
			{
				tracks = KmlFiles.loadTracksFromFile(file);
			}
			else if (lc.endsWith(".gpx"))
			{
				tracks = GpxFiles.loadTracksFromFile(file);
			}
			if (tracks.size() > 0)
			{
				for (Track track: tracks)
				{
					application.addTrack(track);
				}
				onFileLoaded(tracks.size());
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