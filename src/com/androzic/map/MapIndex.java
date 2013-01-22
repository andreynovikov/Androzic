/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2013  Andrey Novikov <http://andreynovikov.info/>
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

package com.androzic.map;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import com.androzic.util.FileList;
import com.androzic.util.MapFilenameFilter;

public class MapIndex implements Serializable
{
	private static final long serialVersionUID = 5L;
	
	private ArrayList<Map> maps;
	private String mapsRoot;
	private int hashCode;
	private Comparator<Map> comparator = new MapComparator();
	
	public MapIndex(String path, String charset)
	{
		maps = new ArrayList<Map>();
		mapsRoot = path;
		File root = new File(mapsRoot);
		List<File> files = FileList.getFileListing(root, new MapFilenameFilter());
		for (File file: files)
		{
			try
			{
				maps.add(MapLoader.load(file, charset));
			}
			catch (IOException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		hashCode = getMapsHash(files);
	}
	
	public static int getMapsHash(String path)
	{
		File root = new File(path);
		List<File> files = FileList.getFileListing(root, new MapFilenameFilter());
		return getMapsHash(files);
	}

	private static int getMapsHash(List<File> files)
	{
		int result = 13;
		for (File file: files)
		{
			result = 31 * result + file.getAbsolutePath().hashCode();
		}
		return result;
	}

	@Override
	public int hashCode()
	{
		return hashCode;
	}

	public void addMap(Map map)
	{
		if (! maps.contains(map))
			maps.add(map);
	}

	public void removeMap(Map map)
	{
		maps.remove(map);
	}

	public List<Map> getCoveringMaps(Map refMap, Map.Bounds area, boolean covered, boolean bestmap)
	{
		List<Map> llmaps = new ArrayList<Map>();

		for (Map map : maps)
		{
			if (map.mpp > 200 || map.equals(refMap))
				continue;
			double ratio = refMap.mpp / map.mpp;
			if (((! covered && ratio > 0.2) || ratio > 1) && ((bestmap || ! covered) && ratio < 5) && map.containsArea(area))
			{
				llmaps.add(map);
			}
		}

		Collections.sort(llmaps, comparator);
		Collections.reverse(llmaps);

		return llmaps;		
	}

	public List<Map> getMaps(double lat, double lon)
	{
		List<Map> llmaps = new ArrayList<Map>();
		
		for (Map map : maps)
		{
			if (map.coversLatLon(lat, lon))
			{
				llmaps.add(map);
			}
		}
		
		Collections.sort(llmaps, comparator);

		return llmaps;		
	}

	public List<Map> getMaps()
	{
		return maps;
	}

	public void cleanBadMaps()
	{
		for (Iterator<Map> iter = maps.iterator(); iter.hasNext();)
		{
			Map map = iter.next();
			if (map.loadError != null)
			{
				iter.remove();
			}
		}
	}
	
	private class MapComparator implements Comparator<Map>, Serializable
    {
		private static final long serialVersionUID = 1L;

		@Override
        public int compare(Map o1, Map o2)
        {
        	return Double.compare(o1.mpp, o2.mpp);
        }
    }
}
