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

import java.util.Hashtable;
import java.util.LinkedList;

import com.androzic.map.Tile;
import com.androzic.map.TileRAMCache;

import android.util.Log;
import android.view.View;

public class TileController extends Thread
{
	LinkedList<Tile> pendingList;
	Hashtable<Long, Tile> tileMap;
	Thread mThreada;
	Thread mThreadb;
	Thread mThreadc;
	Thread mThreadd;
	private View view;
	private TileProvider provider;
	private TileRAMCache cache;

	public TileController()
	{
		pendingList = new LinkedList<Tile>();
		tileMap = new Hashtable<Long, Tile>();
		mThreada = new Thread(this);
		mThreada.start();
		mThreadb = new Thread(this);
		mThreadb.start();
		mThreadc = new Thread(this);
		mThreadc.start();
		mThreadd = new Thread(this);
		mThreadd.start();
	}

	final Runnable update = new Runnable() {
		public void run()
		{
			view.postInvalidate();
		}
	};

	public void run()
	{
		while (!this.isInterrupted())
		{
			try
			{
				Tile t;
				synchronized (pendingList)
				{
					t = pendingList.poll();
				}
				if (t == null)
				{
					synchronized (this)
					{
						wait();
					}
					continue;
				}
				tileMap.remove(t.getKey());
				TileFactory.downloadTile(provider, t);
				if (t.bitmap != null)
				{
					TileFactory.saveTile(provider, t);
					cache.put(t);
					if (view.getHandler() != null)
						view.getHandler().post(update);
				}
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
	}

	/**
	 * Interrupts all the Threads
	 */
	public void interrupt()
	{
		mThreada.interrupt();
		mThreadb.interrupt();
		mThreadc.interrupt();
		mThreadd.interrupt();
	}

	/**
	 * 
	 * @param tx
	 *            The X position of the Tile to draw
	 * @param ty
	 *            The Y position of the Tile to draw
	 * @param tz
	 *            The Zoom value of the Tile to draw
	 * @return The recovered Tile
	 */
	public Tile getTile(int tx, int ty, byte tz)
	{
		long key = Tile.getKey(tx, ty, tz);
		Tile t = cache.get(key);
		if (t == null)
		{
			t = tileMap.get(key);
		}
		if (t == null)
		{
			t = new Tile(tx, ty, tz);
			TileFactory.loadTile(provider, t);
			if (t.bitmap == null)
			{
				tileMap.put(key, t);
				Log.e("OSM","Size: "+tileMap.size());
				synchronized (pendingList)
				{
					pendingList.add(t);
				}
				synchronized (this)
				{
					notifyAll();
				}
			}
			else
			{
				cache.put(t);
			}
		}
		return t;
	}

	/**
	 * Reset the Tiles to 0
	 */
	public void reset()
	{
		tileMap = new Hashtable<Long, Tile>();
		pendingList = new LinkedList<Tile>();
	}

	public void setView(View view)
	{
		this.view = view;
	}

	public void setCache(TileRAMCache cache)
	{
		this.cache = cache;
	}

	public void setProvider(TileProvider provider)
	{
		this.provider = provider;
	}
}