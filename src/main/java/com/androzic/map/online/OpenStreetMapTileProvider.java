/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2015 Andrey Novikov <http://andreynovikov.info/>
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

import android.support.annotation.Nullable;

public class OpenStreetMapTileProvider extends TileProvider
{
	private String[] servers = {"a", "b", "c"};
	private int nextServer = 0;

	public OpenStreetMapTileProvider()
	{
		name = "OpenStreetMap";
		code = "osm";
		license = "\u00a9 <a href=\"http://www.openstreetmap.org/copyright\">OpenStreetMap</a> contributors";
	}

	@Override
	public void activate()
	{
	}

	@Override
	public void deactivate()
	{
	}

	@Nullable
	@Override
	public String getTileUri(int x, int y, byte z)
	{
		nextServer++;
		if (servers.length <= nextServer)
			nextServer = 0;
		return String.format("http://%s.tile.openstreetmap.org/%d/%d/%d.png", servers[nextServer], z, x, y);
	}
}
