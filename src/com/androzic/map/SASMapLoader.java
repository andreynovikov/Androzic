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
			if (ext == null)
				ext = searchForTile(file, zoom);
		}
		if (maxZoom > 0 && ext != null)
		{
			SASMap map = new SASMap(name, file.getAbsolutePath(), ext, minZoom, maxZoom);
			map.ellipsoid = ellipsoid;
			return map;
		}
		else
		{
			throw new IOException("Invalid SAS cache dir: " + name);
		}
	}

	private static String searchForTile(File file, String zoom)
	{
		File root = new File(file, zoom);
		File[] x1024 = root.listFiles();
		if (x1024 == null)
			return null;
		for (File x1024file: x1024)
		{
			File[] x = x1024file.listFiles();
			if (x == null)
				continue;
			for (File xfile: x)
			{
				File[] y1024 = xfile.listFiles();
				if (y1024 == null)
					continue;
				for (File y1024file: y1024)
				{
					String[] y = y1024file.list();
					if (y == null)
						continue;
					return y[0].substring(y[0].lastIndexOf("."));
				}
			}
		}
		return null;
	}
}
