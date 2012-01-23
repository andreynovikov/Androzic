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

package com.androzic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Stack;

import com.androzic.data.Route;
import com.androzic.data.Track;
import com.androzic.data.Waypoint;
import com.androzic.map.Map;
import com.androzic.map.MapIndex;
import com.androzic.map.online.OnlineMap;
import com.androzic.map.online.TileProvider;

public class MapState
{
	// Activity state
	Track editingTrack;
	Route editingRoute;
	Stack<Waypoint> routeEditingWaypoints;
	
	Track currentTrack;

	// Application state
	List<Waypoint> waypoints = new ArrayList<Waypoint>();
	List<Track> tracks = new ArrayList<Track>();
	List<Route> routes = new ArrayList<Route>();
	
	Waypoint navWaypoint = null;
	Waypoint prevWaypoint = null;
	Route navRoute = null;
	boolean centeredOn = false;
	boolean hasCompass = false;

	int navDirection = 0;
	int navCurrentRoutePoint = -1;
	double navDistance = -1;
	
	Locale locale = null;
	String waypointPath;
	String trackPath;
	String routePath;
	String rootPath;
	String mapPath;
	String iconPath;
	boolean mapsInited;

	MapIndex maps;
	Map currentMap;
	List<TileProvider> onlineMaps = new ArrayList<TileProvider>();
	OnlineMap onlineMap;
	List<Map> suitableMaps = new ArrayList<Map>();
	boolean memmsg;
}
