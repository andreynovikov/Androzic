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
