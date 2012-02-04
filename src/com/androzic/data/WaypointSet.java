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

package com.androzic.data;

import java.io.File;

public class WaypointSet
{
	public String name;
	public String path;

	public WaypointSet(File file)
	{
		path = file.getAbsolutePath();
		name = file.getName();
		name = name.substring(0, name.lastIndexOf("."));
	}

	public WaypointSet(String name)
	{
		this.name = name;
	}

	public WaypointSet(String path, String name)
	{
		this.path = path;
		this.name = name;
	}
}
