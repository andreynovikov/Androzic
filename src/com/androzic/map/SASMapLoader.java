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

import com.androzic.map.sas.SASMap;

public class SASMapLoader
{
	public static SASMap load(File file) throws IOException
	{
		String name = file.getName();
		String ext = null;
		boolean ellipsoid = false;
		String[] zooms = file.list();
		int minZoom = Integer.MAX_VALUE;
		int maxZoom = Integer.MIN_VALUE;
		for (String zoom: zooms)
		{
			if ("ellipsoid".equals(zoom))
				ellipsoid = true;
			if (! zoom.startsWith("z"))
				continue;
			int z = Integer.parseInt(zoom.substring(1));
			if (z < minZoom)
				minZoom = z;
			if (z > maxZoom)
				maxZoom = z;
		}
		
		int[] corners = new int[4];
		ext = calculateCorners(file, maxZoom, corners);
		
		if (maxZoom > 0 && ext != null)
		{
			
			SASMap map = new SASMap(name, file.getAbsolutePath(), ext, minZoom - 1, maxZoom - 1);
			map.ellipsoid = ellipsoid;
			
			map.setCornersAmount(4);
			map.cornerMarkers[0].x = corners[0] * SASMap.TILE_WIDTH;
			map.cornerMarkers[0].y = corners[1] * SASMap.TILE_HEIGHT;
			map.cornerMarkers[1].x = corners[0] * SASMap.TILE_WIDTH;
			map.cornerMarkers[1].y = (corners[3] + 1) * SASMap.TILE_HEIGHT;
			map.cornerMarkers[2].x = (corners[2] + 1) * SASMap.TILE_WIDTH;
			map.cornerMarkers[2].y = (corners[3] + 1) * SASMap.TILE_HEIGHT;
			map.cornerMarkers[3].x = (corners[2] + 1) * SASMap.TILE_WIDTH;
			map.cornerMarkers[3].y = corners[1] * SASMap.TILE_HEIGHT;
			double[] ll = new double[2];
			for (int i = 0; i < 4; i++)
			{
				map.getLatLonByXY(map.cornerMarkers[i].x, map.cornerMarkers[i].y, ll);
				map.cornerMarkers[i].lat = ll[0];
				map.cornerMarkers[i].lon = ll[1];
			}

			return map;
		}
		else
		{
			throw new IOException("Invalid SAS cache dir: " + name);
		}
	}

	private static String calculateCorners(File file, int zoom, int[] corners)
	{
		int minX = Integer.MAX_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int maxY = Integer.MIN_VALUE;
		String ext = null;

		File root = new File(file, "z" + zoom);
		File[] x1024 = root.listFiles();
		if (x1024 == null)
			return null;

		for (File x1024file: x1024)
		{
			File[] xs = x1024file.listFiles();
			if (xs == null)
				continue;
			for (File xfile: xs)
			{
				int x = Integer.parseInt(xfile.getName().substring(1));
				if (x < minX)
					minX = x;
				if (x > maxX)
					maxX = x;
				
				File[] y1024 = xfile.listFiles();
				if (y1024 == null)
					continue;
				for (File y1024file: y1024)
				{
					String[] ys = y1024file.list();
					if (ys == null)
						continue;
					for (String yf: ys)
					{
						int dot = yf.lastIndexOf(".");
						int y = Integer.parseInt(yf.substring(1, dot));
						if (y < minY)
							minY = y;
						if (y > maxY)
							maxY = y;
						if (ext == null)
							ext = yf.substring(dot);
					}
				}
			}
		}

		corners[0] = minX;
		corners[1] = minY;
		corners[2] = maxX;
		corners[3] = maxY;

		return ext;
	}
}
