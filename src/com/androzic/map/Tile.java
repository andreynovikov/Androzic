/*
 * Copyright 2010 mapsforge.org
 *
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

package com.androzic.map;

import android.graphics.Bitmap;

/**
 * A tile represents a rectangular part of the world map. All tiles can be
 * identified by their X and Y number together with their zoom level. The actual
 * area that a tile covers on a map depends on the underlying map projection.
 */
public class Tile
{
	public Bitmap bitmap = null;
	
	private final int hashCode;

	/**
	 * X number of this tile.
	 */
	public final int x;

	/**
	 * Y number of this tile.
	 */
	public final int y;

	/**
	 * Zoom level of this tile.
	 */
	public final byte zoomLevel;

	/**
	 * Constructs an immutable tile with the specified XY number and zoom level.
	 * 
	 * @param x
	 *            the X number of the tile.
	 * @param y
	 *            the Y number of the tile.
	 * @param zoomLevel
	 *            the zoom level of the tile.
	 */
	public Tile(int x, int y, byte zoomLevel)
	{
		this.x = x;
		this.y = y;
		this.zoomLevel = zoomLevel;
		this.hashCode = calculateHashCode();
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
		{
			return true;
		}
		else if (!(obj instanceof Tile))
		{
			return false;
		}
		else
		{
			Tile other = (Tile) obj;
			if (this.x != other.x)
			{
				return false;
			}
			else if (this.y != other.y)
			{
				return false;
			}
			else if (this.zoomLevel != other.zoomLevel)
			{
				return false;
			}
			return true;
		}
	}

	@Override
	public int hashCode()
	{
		return this.hashCode;
	}

	@Override
	public String toString()
	{
		return this.zoomLevel + "/" + this.x + "/" + this.y;
	}
	
	public static long getKey(int tx, int ty, byte tz)
	{
		return (long) tz << 48 | (long) ty << 24 | tx & 0xFFFFFF;
	}

	public long getKey()
	{
		return Tile.getKey(x, y, zoomLevel);
	}

	/**
	 * Calculates the hash value of this object.
	 * 
	 * @return the hash value of this object.
	 */
	private int calculateHashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (this.x ^ (this.x >>> 32));
		result = prime * result + (int) (this.y ^ (this.y >>> 32));
		result = prime * result + this.zoomLevel;
		return result;
	}
}