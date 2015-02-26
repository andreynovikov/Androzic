/*
 * Copyright 2010, 2011, 2012, 2013 mapsforge.org
 * Copyright 2014 Ludwig M Brinckmann
 * Copyright 2015 Andrey Novikov
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.mapsforge.map.layer.cache;

import org.mapsforge.core.graphics.TileBitmap;
import org.mapsforge.map.layer.queue.Job;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class MutableTwoLevelTileCache implements TileCache
{
	private TileCache firstLevelTileCache;
	private TileCache secondLevelTileCache;
	private final Set<Job> workingSet;

	public MutableTwoLevelTileCache()
	{
		this.workingSet = Collections.synchronizedSet(new HashSet<Job>());
	}

	public void setFirstLevelCache(TileCache firstLevelTileCache)
	{
		if (this.firstLevelTileCache != null)
			this.firstLevelTileCache.destroy();
		this.firstLevelTileCache = firstLevelTileCache;
		updateWorkingSets();
	}

	public void setSecondLevelCache(TileCache secondLevelTileCache)
	{
		if (this.secondLevelTileCache != null)
			this.secondLevelTileCache.destroy();
		this.secondLevelTileCache = secondLevelTileCache;
	}

	@Override
	public boolean containsKey(Job key)
	{
		if (this.firstLevelTileCache == null)
			return false;
		if (this.firstLevelTileCache.containsKey(key))
			return true;
		if (this.secondLevelTileCache == null)
			return false;
		return this.secondLevelTileCache.containsKey(key);
	}

	@Override
	public void destroy()
	{
		if (this.firstLevelTileCache != null)
			this.firstLevelTileCache.destroy();
		if (this.secondLevelTileCache != null)
			this.secondLevelTileCache.destroy();
	}

	@Override
	public TileBitmap get(Job key)
	{
		if (this.firstLevelTileCache == null)
			return null;

		TileBitmap returnBitmap = this.firstLevelTileCache.get(key);
		if (returnBitmap != null)
			return returnBitmap;

		if (this.secondLevelTileCache == null)
			return null;

		returnBitmap = this.secondLevelTileCache.get(key);
		if (returnBitmap != null)
		{
			this.firstLevelTileCache.put(key, returnBitmap);
			return returnBitmap;
		}
		return null;
	}

	@Override
	public int getCapacity()
	{
		if (this.firstLevelTileCache == null)
			return 0;
		if (this.secondLevelTileCache == null)
			return this.firstLevelTileCache.getCapacity();
		return Math.max(this.firstLevelTileCache.getCapacity(), this.secondLevelTileCache.getCapacity());
	}

	@Override
	public int getCapacityFirstLevel()
	{
		if (this.firstLevelTileCache == null)
			return 0;
		return this.firstLevelTileCache.getCapacity();
	}

	@Override
	public TileBitmap getImmediately(Job key)
	{
		if (this.firstLevelTileCache == null)
			return null;
		return firstLevelTileCache.get(key);
	}

	@Override
	public void purge()
	{
		if (this.firstLevelTileCache != null)
			this.firstLevelTileCache.purge();
		if (this.secondLevelTileCache != null)
			this.secondLevelTileCache.purge();
	}

	@Override
	public void put(Job key, TileBitmap bitmap)
	{
		if (this.firstLevelTileCache == null)
			return;
		if (this.workingSet.contains(key))
		{
			this.firstLevelTileCache.put(key, bitmap);
		}
		if (this.secondLevelTileCache != null)
			this.secondLevelTileCache.put(key, bitmap);
	}

	@Override
	public void setWorkingSet(Set<Job> newWorkingSet)
	{
		this.workingSet.clear();
		this.workingSet.addAll(newWorkingSet);
		updateWorkingSets();
	}

	private void updateWorkingSets()
	{
		if (this.firstLevelTileCache == null)
			return;
		this.firstLevelTileCache.setWorkingSet(this.workingSet);
		if (this.secondLevelTileCache == null)
			return;
		for (Job job : workingSet)
		{
			if (!firstLevelTileCache.containsKey(job) && secondLevelTileCache.containsKey(job))
			{
				TileBitmap tileBitmap = secondLevelTileCache.get(job);
				if (tileBitmap != null)
				{
					firstLevelTileCache.put(job, tileBitmap);
				}
			}
		}
	}
}
