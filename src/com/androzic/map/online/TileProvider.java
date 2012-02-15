/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2012  Andrey Novikov <http://andreynovikov.info/>
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

package com.androzic.map.online;

import java.util.ArrayList;
import java.util.Locale;

import com.androzic.util.CSV;

public class TileProvider
{
	public ArrayList<String> servers = new ArrayList<String>();
	public String path;
	public String name;
	public String code;
	public String secret;
	public byte minZoom;
	public byte maxZoom;
	public boolean inverseY = false;
	public boolean ellipsoid = false;
	public int tileSize = 25000;
	private int nextServer = 0;
	//TODO Better initialization?
	private String locale = Locale.getDefault().toString();
	
	public String getTileUri(int x, int y, byte z)
	{
		String uri = path;
        if (! servers.isEmpty())
        {
        	if (servers.size() <= nextServer)
        		nextServer = 0;
            uri = uri.replace("{$s}", servers.get(nextServer));
        	nextServer++;
        }
    	if (inverseY)
    		y = (int) (Math.pow(2, z) - 1 - y);
    	uri = uri.replace("{$l}", locale);
    	uri = uri.replace("{$z}", String.valueOf(z));
    	uri = uri.replace("{$x}", String.valueOf(x));
    	uri = uri.replace("{$y}", String.valueOf(y));
    	if (uri.contains("{$q}"))
    		uri = uri.replace("{$q}", encodeQuadTree(z, x, y));
    	if (uri.contains("{$g}") && secret != null)
    	{
    		int stringlen = (3 * x + y) & 7;
    		uri = uri.replace("{$g}", secret.substring(0, stringlen));
    	}
    	
		return uri;
	}
	
	public static TileProvider fromString(String s)
	{
		TileProvider provider = new TileProvider();
		String[] fields = CSV.parseLine(s);
		if (fields.length < 6)
			return null;
		if ("".equals(fields[0]) || "".equals(fields[1]) || "".equals(fields[5]))
			return null;
		provider.name = fields[0];
		provider.code = fields[1];
		provider.path = fields[5];
		provider.path = provider.path.replace("{comma}", ",");
		try
		{
			provider.minZoom = (byte) Integer.parseInt(fields[2]);
			provider.maxZoom = (byte) Integer.parseInt(fields[3]);
			if (! "".equals(fields[4]))
				provider.tileSize = Integer.parseInt(fields[4]);
		}
		catch (NumberFormatException e)
		{
			return null;
		}
		if (fields.length > 6 && ! "".equals(fields[6]))
			provider.servers.add(fields[6]);
		if (fields.length > 7 && ! "".equals(fields[7]))
			provider.servers.add(fields[7]);
		if (fields.length > 8 && ! "".equals(fields[8]))
			provider.servers.add(fields[8]);
		if (fields.length > 9 && ! "".equals(fields[9]))
			provider.servers.add(fields[9]);
		provider.inverseY = fields.length > 10 && "yinverse".equals(fields[10]);
		provider.ellipsoid = fields.length > 10 && "ellipsoid".equals(fields[10]);
		if (fields.length > 11 && ! "".equals(fields[11]))
			provider.secret = fields[11];

		return provider;
	}
	
	// WMS layers : http://whoots.mapwarper.net/tms/{$z}/{$x}/{$y}/ {layer}/{Path}
	// 1. Landsat http://onearth.jpl.nasa.gov/wms.cgi global_mosaic (NOT WORK)
	// 2. Genshtab http://wms.latlon.org gshtab
	private static final char[] NUM_CHAR = { '0', '1', '2', '3' };

	/**
	* See: http://msdn.microsoft.com/en-us/library/bb259689.aspx
	* @param zoom
	* @param tilex
	* @param tiley
	* @return quadtree encoded tile number
	*
	*/
	private static String encodeQuadTree(int zoom, int tilex, int tiley)
	{
		char[] tileNum = new char[zoom];
		for (int i = zoom - 1; i >= 0; i--)
		{
			// Binary encoding using ones for tilex and twos for tiley. if a bit
			// is set in tilex and tiley we get a three.
			int num = (tilex % 2) | ((tiley % 2) << 1);
			tileNum[i] = NUM_CHAR[num];
			tilex >>= 1;
			tiley >>= 1;
		}
		return new String(tileNum);
	}
}
