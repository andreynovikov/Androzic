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

package com.androzic.map;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import android.annotation.SuppressLint;

import com.androzic.data.Bounds;
import com.androzic.util.FileList;
import com.androzic.util.MapFilenameFilter;

public class MapIndex implements Serializable
{
	private static final long serialVersionUID = 6L;
	
	private HashSet<Integer>[][] maps;
	private HashMap<Integer,Map> mapIndex;
	private String mapsRoot;
	private int hashCode;
	private transient Comparator<Map> comparator = new MapComparator();
	
	public MapIndex()
	{
	}

	@SuppressLint("UseSparseArrays")
	@SuppressWarnings("unchecked")
	public MapIndex(String path, String charset)
	{
		maps = new HashSet[181][361];
		mapIndex = new HashMap<Integer,Map>();
		mapsRoot = path;
		File root = new File(mapsRoot);
		List<File> files = FileList.getFileListing(root, new MapFilenameFilter());
		for (File file: files)
		{
			try
			{
				Map map = MapLoader.load(file, charset);
				addMap(map);
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
		if (! mapIndex.containsKey(map.id))
		{
			// TODO Use corner markers instead
			Bounds bounds = map.getBounds();
			int minLat = (int) Math.floor(bounds.minLat);
			int maxLat = (int) Math.ceil(bounds.maxLat);
			int minLon = (int) Math.floor(bounds.minLon);
			int maxLon = (int) Math.ceil(bounds.maxLon);
			for (int lat = minLat; lat <= maxLat; lat++)
			{
				for (int lon = minLon; lon <= maxLon; lon++)
				{
					//Log.e("MAP", lat + " " + lon + "|" + (lat+90) + " " + (lon+180));
					HashSet<Integer> lli = maps[lat+90][lon+180];
					if (lli == null)
					{
						lli = new HashSet<Integer>();
						maps[lat+90][lon+180] = lli;
					}
					lli.add(map.id);
				}
			}
			mapIndex.put(map.id, map);
		}
	}

	public void removeMap(Map map)
	{
		// TODO Use corner markers instead
		Bounds bounds = map.getBounds();
		int minLat = (int) Math.floor(bounds.minLat);
		int maxLat = (int) Math.ceil(bounds.maxLat);
		int minLon = (int) Math.floor(bounds.minLon);
		int maxLon = (int) Math.ceil(bounds.maxLon);
		for (int lat = minLat; lat <= maxLat; lat++)
		{
			for (int lon = minLon; lon <= maxLon; lon++)
			{
				HashSet<Integer> lli = maps[lat+90][lon+180];
				if (lli != null)
					lli.remove(map.id);
			}
		}
		mapIndex.remove(map.id);
	}

	public List<Map> getCoveringMaps(Map refMap, Bounds area, boolean covered, boolean bestmap)
	{
		List<Map> llmaps = new ArrayList<Map>();

		int minLat = (int) Math.floor(area.minLat);
		int maxLat = (int) Math.ceil(area.maxLat);
		int minLon = (int) Math.floor(area.minLon);
		int maxLon = (int) Math.ceil(area.maxLon);
		
		for (int lat = minLat; lat <= maxLat; lat++)
		{
			for (int lon = minLon; lon <= maxLon; lon++)
			{
				HashSet<Integer> lli = maps[lat+90][lon+180];
				if (lli != null)
				{
					for (Integer id : lli)
					{
						Map map = mapIndex.get(id);
						if (map.mpp > 200 || map.equals(refMap))
							continue;
						double ratio = refMap.mpp / map.mpp;
						if (((! covered && ratio > 0.2) || ratio > 1) && ((bestmap || ! covered) && ratio < 5) && map.containsArea(area))
						{
							llmaps.add(map);
						}
					}
				}
			}
		}

		Collections.sort(llmaps, comparator);
		Collections.reverse(llmaps);

		return llmaps;		
	}

	public List<Map> getMaps(double latitude, double longitude)
	{
		List<Map> llmaps = new ArrayList<Map>();
		
		int minLat = (int) Math.floor(latitude);
		int maxLat = (int) Math.ceil(latitude);
		int minLon = (int) Math.floor(longitude);
		int maxLon = (int) Math.ceil(longitude);

		for (int lat = minLat; lat <= maxLat; lat++)
		{
			for (int lon = minLon; lon <= maxLon; lon++)
			{
				HashSet<Integer> lli = maps[lat+90][lon+180];
				if (lli != null)
				{
					for (Integer id : lli)
					{
						Map map = mapIndex.get(id);
						if (!llmaps.contains(map) && map.coversLatLon(latitude, longitude))
						{
							llmaps.add(map);
						}
					}
				}
			}
		}
		
		Collections.sort(llmaps, comparator);

		return llmaps;		
	}

	public Collection<Map> getMaps()
	{
		return mapIndex.values();
	}

	public void cleanBadMaps()
	{
		HashSet<Map> badMaps = new HashSet<Map>();
		
		for (Integer id : mapIndex.keySet())
		{
			Map map = mapIndex.get(id);
			if (map.loadError != null)
			{
				badMaps.add(map);
			}
		}
		for (Map map : badMaps)
		{
			removeMap(map);
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
