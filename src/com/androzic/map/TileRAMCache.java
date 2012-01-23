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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A thread-safe cache for tiles with a fixed size and LRU policy.
 */
public class TileRAMCache
{
	/**
	 * Load factor of the internal HashMap.
	 */
	private static final float LOAD_FACTOR = 1.1f;

	private final int capacity;
	private LinkedHashMap<Long, Tile> map;

	/**
	 * Constructs an tile cache with a fixes size and LRU policy.
	 * 
	 * @param capacity
	 *            the maximum number of entries in the cache.
	 * @throws IllegalArgumentException
	 *             if the capacity is negative.
	 */
	public TileRAMCache(int capacity)
	{
		if (capacity < 0)
		{
			throw new IllegalArgumentException();
		}
		this.capacity = capacity;
		this.map = createMap(this.capacity);
	}

	private LinkedHashMap<Long, Tile> createMap(final int initialCapacity)
	{
		return new LinkedHashMap<Long, Tile>(initialCapacity + 1, LOAD_FACTOR, true) {
			private static final long serialVersionUID = 2L;

			@Override
			public Tile remove(Object key) {
				Tile tile = super.remove(key);
				tile.bitmap.recycle();
				tile.bitmap = null;
				return tile;
			}

			@Override
			protected boolean removeEldestEntry(Map.Entry<Long, Tile> eldest)
			{
				return size() > initialCapacity;
			}
		};
	}

	/**
	 * @param key
	 *            key of the image whose presence in the cache should be tested.
	 * @return true if the cache contains an image for the specified key, false
	 *         otherwise.
	 * @see Map#containsKey(Object)
	 */
	public synchronized boolean containsKey(long key)
	{
		return map.containsKey(key);
	}

	/**
	 * Clear the cache.
	 */
	synchronized void clear()
	{
		if (this.map != null)
		{
			for (Tile tile : this.map.values())
			{
				tile.bitmap.recycle();
				tile.bitmap = null;
			}
			this.map.clear();
		}
	}

	/**
	 * Destroy the cache at the end of its lifetime.
	 */
	public synchronized void destroy()
	{
		clear();
		this.map = null;
	}

	/**
	 * @param tile
	 *            key of the image whose data should be returned.
	 * @return the tile
	 * @see Map#get(Object)
	 */
	public synchronized Tile get(long key)
	{
		return map.get(key);
	}

	/**
	 * @param tile
	 *            tile that should be cached.
	 * @see Map#put(Object, Object)
	 */
	public synchronized void put(Tile tile)
	{
		if (capacity > 0)
		{
			long key = Tile.getKey(tile.x, tile.y, tile.zoomLevel);
			if (map.containsKey(key))
			{
				// the item is already in the cache
				return;
			}
			this.map.put(key, tile);
		}
	}
}