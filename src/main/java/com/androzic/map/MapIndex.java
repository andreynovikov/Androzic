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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.annotation.SuppressLint;

import com.androzic.data.Bounds;
import com.androzic.map.forge.ForgeMap;
import com.androzic.map.ozf.Grid;
import com.androzic.map.ozf.OzfMap;
import com.androzic.util.FileList;
import com.androzic.util.MapFilenameFilter;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.jhlabs.map.proj.Projection;

public class MapIndex implements Serializable
{
	private static final long serialVersionUID = 9L;
	
	private HashSet<Integer>[][] maps;
	private HashMap<Integer,BaseMap> mapIndex;
	private int hashCode;
	private transient Comparator<BaseMap> comparator = new MapComparator();

	@SuppressWarnings("unused")
	MapIndex()
	{
	}

	@SuppressLint("UseSparseArrays")
	@SuppressWarnings("unchecked")
	public MapIndex(String path, String charset)
	{
		maps = new HashSet[181][361];
		mapIndex = new HashMap<>();
		File root = new File(path);
		List<File> files = FileList.getFileListing(root, new MapFilenameFilter());
		for (File file: files)
		{
			try
			{
				BaseMap map = MapLoader.load(file, charset);
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

	public void addMap(BaseMap map)
	{
		if (mapIndex.containsKey(map.id))
			return;

		mapIndex.put(map.id, map);
		if (map.loadError != null)
			return;

		map.initialize();
		// Error can be raised during initialization.
		if (map.loadError != null)
			return;

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
				HashSet<Integer> lli;
				try
				{
					lli = maps[lat + 90][lon + 180];
				}
				catch (IndexOutOfBoundsException e)
				{
					e.printStackTrace();
					map.loadError = e;
					return;
				}
				if (lli == null)
				{
					lli = new HashSet<>();
					maps[lat + 90][lon + 180] = lli;
				}
				lli.add(map.id);
			}
		}
	}

	public void removeMap(BaseMap map)
	{
		mapIndex.remove(map.id);
		if (map.loadError != null)
			return;
		map.destroy();
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
	}

	public List<BaseMap> getCoveringMaps(BaseMap refMap, Bounds area, boolean covered, boolean bestmap)
	{
		List<BaseMap> llmaps = new ArrayList<>();
		Set<BaseMap> llmapsidx = new HashSet<>();

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
						BaseMap map = mapIndex.get(id);
						if (llmapsidx.contains(map))
							continue;
						if (map.equals(refMap))
							continue;
						double ratio = map.getCoveringRatio(refMap.mpp);
						// If map has smaller scale and reference map covers all screen do not use it
						if ((covered || ratio < 0.2) && ratio < 0.99)
							continue;

						// If map has bigger scale but best map is not enabled do not use it
						if ((! bestmap && covered) || ratio > 5d)
							continue;

						// If map does not cover referencing area do not use it
						// We put it at last place because this check is expensive
						if (!map.containsArea(area))
							continue;

						llmaps.add(map);
						llmapsidx.add(map);
					}
				}
			}
		}

		Collections.sort(llmaps, comparator);
		Collections.reverse(llmaps);

		return llmaps;
	}

	public List<BaseMap> getMaps(double latitude, double longitude)
	{
		List<BaseMap> llmaps = new ArrayList<>();
		
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
						BaseMap map = mapIndex.get(id);
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

	public Collection<BaseMap> getMaps()
	{
		return mapIndex.values();
	}

	public void cleanBadMaps()
	{
		HashSet<BaseMap> badMaps = new HashSet<>();
		
		for (Integer id : mapIndex.keySet())
		{
			BaseMap map = mapIndex.get(id);
			if (map.loadError != null)
			{
				badMaps.add(map);
			}
		}
		for (BaseMap map : badMaps)
		{
			removeMap(map);
		}		
	}

	public void clear()
	{
		for (BaseMap map : mapIndex.values())
			map.destroy();
		mapIndex.clear();
		for (int lat = 0; lat < 181; lat++)
		{
			for (int lon = 0; lon < 361; lon++)
			{
				HashSet<Integer> lli = maps[lat][lon];
				if (lli != null)
				{
					lli.clear();
					maps[lat][lon] = null;
				}
			}
		}
		maps = null;
	}

	public static MapIndex loadIndex(File file) throws Throwable
	{
		//com.esotericsoftware.minlog.Log.DEBUG();
		Kryo kryo = new Kryo();
		kryo.register(MapIndex.class);
		kryo.register(BaseMap.class);
		kryo.register(OzfMap.class);
		kryo.register(ForgeMap.class);
		kryo.register(Grid.class);
		kryo.register(MapPoint.class);
		kryo.register(MapPoint[].class);
		kryo.register(Projection.class);
		kryo.register(Integer.class);
		kryo.register(String.class);
		kryo.register(ArrayList.class);
		kryo.register(HashSet.class);
		kryo.register(HashMap.class);
		Input input = new Input(new FileInputStream(file));
		MapIndex index = kryo.readObject(input, MapIndex.class);
		input.close();
		for (BaseMap map : index.getMaps())
			map.initialize();
		return index;
	}

	public static void saveIndex(MapIndex index, File file) throws Throwable
	{
		Kryo kryo = new Kryo();
		kryo.register(MapIndex.class);
		kryo.register(BaseMap.class);
		kryo.register(OzfMap.class);
		kryo.register(ForgeMap.class);
		kryo.register(Grid.class);
		kryo.register(MapPoint.class);
		kryo.register(MapPoint[].class);
		kryo.register(Projection.class);
		kryo.register(Integer.class);
		kryo.register(String.class);
		kryo.register(ArrayList.class);
		kryo.register(HashSet.class);
		kryo.register(HashMap.class);
		Output output = new Output(new FileOutputStream(file));
		kryo.writeObject(output, index);
		output.close();
	}

	private class MapComparator implements Comparator<BaseMap>, Serializable
    {
		private static final long serialVersionUID = 3L;

		@Override
        public int compare(BaseMap o1, BaseMap o2)
        {
        	int res = Double.compare(o1.getAbsoluteMPP(), o2.getAbsoluteMPP());
	        if (res != 0)
		        return res;
	        res = o1.getPriority() - o2.getPriority();
	        if (res != 0)
		        return res;
	        return o1.title.compareTo(o2.title);
        }
    }
}
