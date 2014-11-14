/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2013  Andrey Novikov <http://andreynovikov.info/>
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Stack;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.GeomagneticField;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Pair;
import android.view.WindowManager;
import android.widget.Toast;

import com.androzic.data.Bounds;
import com.androzic.data.MapObject;
import com.androzic.data.Route;
import com.androzic.data.Track;
import com.androzic.data.Waypoint;
import com.androzic.data.WaypointSet;
import com.androzic.location.ILocationListener;
import com.androzic.location.ILocationService;
import com.androzic.location.LocationService;
import com.androzic.map.Grid;
import com.androzic.map.Map;
import com.androzic.map.MapIndex;
import com.androzic.map.MapPoint;
import com.androzic.map.MockMap;
import com.androzic.map.OzfDecoder;
import com.androzic.map.SASMapLoader;
import com.androzic.map.online.OnlineMap;
import com.androzic.map.online.TileProvider;
import com.androzic.navigation.NavigationService;
import com.androzic.overlay.NavigationOverlay;
import com.androzic.overlay.OverlayManager;
import com.androzic.overlay.RouteOverlay;
import com.androzic.overlay.TrackOverlay;
import com.androzic.util.Astro.Zenith;
import com.androzic.util.CSV;
import com.androzic.util.CoordinateParser;
import com.androzic.util.FileUtils;
import com.androzic.util.Geo;
import com.androzic.util.OziExplorerFiles;
import com.androzic.util.StringFormatter;
import com.androzic.util.WaypointFileHelper;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.jhlabs.map.proj.Projection;
import com.jhlabs.map.proj.ProjectionException;

public class Androzic extends BaseApplication implements OnSharedPreferenceChangeListener
{
	private static final String TAG = "Androzic";

	public static final String BROADCAST_WAYPOINT_REMOVED = "com.androzic.waypointRemoved";

	public static final int PATH_DATA = 0x001;
	public static final int PATH_SAS = 0x002;
	public static final int PATH_ICONS = 0x008;
	
	public int coordinateFormat = 0;
	public int angleType = 0;
	public int sunriseType = 0;

	private List<TileProvider> onlineMaps;
	private OnlineMap onlineMap;
	private MapIndex maps;
	private List<Map> suitableMaps;
	private List<Map> coveringMaps;
	private Map currentMap;
	private boolean coveredAll;
	private boolean coveringBestMap;
	private double[] coveringLoc = new double[] {0.0, 0.0};
	private Rectangle coveringScreen = new Rectangle();
	private double[] mapCenter = new double[] {0.0, 0.0};
	private double[] location = new double[] {Double.NaN, Double.NaN};
	private double magneticDeclination = 0;

	private ILocationService locationService = null;
	private int magInterval;
	private long lastMagnetic = 0;

	public NavigationService navigationService = null;

	public Location lastKnownLocation;
	public boolean gpsEnabled;
	public int gpsStatus;
	public int gpsFSats;
	public int gpsTSats;
	public boolean gpsContinous;
	public boolean gpsGeoid;
	public boolean shouldEnableFollowing;
	
	@SuppressLint("UseSparseArrays")
	private AbstractMap<Long, MapObject> mapObjects = new HashMap<Long, MapObject>();
	private List<Waypoint> waypoints = new ArrayList<Waypoint>();
	private List<WaypointSet> waypointSets = new ArrayList<WaypointSet>();
	private WaypointSet defWaypointSet;
	private List<Track> tracks = new ArrayList<Track>();
	private List<Route> routes = new ArrayList<Route>();

	// Map activity state
	protected Route editingRoute = null;
	protected Track editingTrack = null;
	protected Stack<Waypoint> routeEditingWaypoints = null;
	
	// Plugins
	private AbstractMap<String, Intent> pluginPreferences = new HashMap<String, Intent>();
	private AbstractMap<String, Pair<Drawable, Intent>> pluginViews = new HashMap<String, Pair<Drawable, Intent>>();
	
	private boolean memmsg = false;
	
	private Locale locale = null;
	public String charset;

	public String dataPath;
	private String rootPath;
	private String mapPath;
	private String sasPath;
	public String iconPath;
	public boolean mapsInited = false;
	private MapHolder mapHolder;
	protected OverlayManager overlayManager;
	private int screenSize;
	public Drawable customCursor = null;
	public boolean iconsEnabled = false;
	public int iconX = 0;
	public int iconY = 0;
	
	public boolean isPaid = false;

	protected boolean adjacentMaps = false;
	protected boolean cropMapBorder = true;
	protected boolean drawMapBorder = false;

	private HandlerThread renderingThread;
	private HandlerThread longOperationsThread;
	private Handler mapsHandler;
	private Handler uiHandler;

	public Looper getRenderingThreadLooper()
	{
		//return Looper.getMainLooper();
		return renderingThread.getLooper();
	}

	public MapHolder getMapHolder()
	{
		return mapHolder;
	}

	protected void setMapHolder(MapHolder holder)
	{
		mapHolder = holder;
		if (currentMap != null && !currentMap.activated())
		{
			try
			{
				currentMap.activate(mapHolder.getMapView(), screenSize);
			}
			catch (final Throwable e)
			{
				e.printStackTrace();
				uiHandler.post(new MapActivationError(currentMap, e));
			}
		}
		overlayManager.initGrids(currentMap);
	}
	
	public java.util.Map<String, Intent> getPluginsPreferences()
	{
		return pluginPreferences;
	}
	
	public java.util.Map<String, Pair<Drawable, Intent>> getPluginsViews()
	{
		return pluginViews;
	}
	
	public Zenith getZenith()
	{
		switch (sunriseType)
		{
			case 0:
				return Zenith.OFFICIAL;
			case 1:
				return Zenith.CIVIL;
			case 2:
				return Zenith.NAUTICAL;
			case 3:
				return Zenith.ASTRONOMICAL;
			default:
				return Zenith.OFFICIAL;
		}
	}

	public long getNewUID()
	{
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		long uid = preferences.getLong(getString(R.string.app_lastuid), 0);
		uid++;
		Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
		editor.putLong(getString(R.string.app_lastuid), uid);
		editor.commit();
		return uid;
	}

	public long addMapObject(MapObject mapObject)
	{
		mapObject._id = getNewUID();
		synchronized (mapObjects)
		{
			mapObjects.put(mapObject._id, mapObject);
		}
		return mapObject._id;
	}
	
	public boolean removeMapObject(long id)
	{
		synchronized (mapObjects)
		{
			MapObject mo = mapObjects.remove(id);
			if (mo != null && mo.bitmap != null)
				mo.bitmap.recycle();
			return mo != null;
		}
	}
	
	/**
	 * Clear all map objects.
	 */
	public void clearMapObjects()
	{
		synchronized (mapObjects)
		{
			mapObjects.clear();
		}
	}
	
	public MapObject getMapObject(long id)
	{
		return mapObjects.get(id);
	}

	public Iterable<MapObject> getMapObjects()
	{
		return mapObjects.values();
	}

	public int addWaypoint(final Waypoint newWaypoint)
	{
		newWaypoint.set = defWaypointSet;
		synchronized (waypoints)
		{
			waypoints.add(newWaypoint);
		}
		return waypoints.lastIndexOf(newWaypoint);
	}

	public int addWaypoints(final List<Waypoint> newWaypoints)
	{
		if (newWaypoints != null)
		{
			for (Waypoint waypoint : newWaypoints)
				waypoint.set = defWaypointSet;
			synchronized (waypoints)
			{
				waypoints.addAll(newWaypoints);
			}
		}
		return waypoints.size() - 1;
	}

	public int addWaypoints(final List<Waypoint> newWaypoints, final WaypointSet waypointSet)
	{
		if (newWaypoints != null)
		{
			for (Waypoint waypoint : newWaypoints)
				waypoint.set = waypointSet;
			synchronized (waypoints)
			{
				waypoints.addAll(newWaypoints);
			}
			waypointSets.add(waypointSet);
		}
		return waypoints.size() - 1;
	}

	public boolean removeWaypoint(final Waypoint delWaypoint)
	{
		synchronized (waypoints)
		{
			return waypoints.remove(delWaypoint);
		}
	}
	
	public void removeWaypoint(final int delWaypoint)
	{
		synchronized (waypoints)
		{
			waypoints.remove(delWaypoint);
		}
	}

	/**
	 * Clear all waypoints.
	 */
	public void clearWaypoints()
	{
		synchronized (waypoints)
		{
			waypoints.clear();
		}
	}
	
	// TODO Should we keep it? Not used anymore...
	/**
	 * Clear waypoints from specific waypoint set.
	 * @param set waypoint set
	 */
	public void clearWaypoints(WaypointSet set)
	{
		for (Iterator<Waypoint> iter = waypoints.iterator(); iter.hasNext();)
		{
			Waypoint wpt = iter.next();
			if (wpt.set == set)
			{
				iter.remove();
			}
		}	
	}
	
	/**
	 * Clear waypoints from default waypoint set.
	 */
	public void clearDefaultWaypoints()
	{
		clearWaypoints(defWaypointSet);
	}
	
	public Waypoint getWaypoint(final int index)
	{
		return waypoints.get(index);
	}

	public int getWaypointIndex(Waypoint wpt)
	{
		return waypoints.indexOf(wpt);
	}

	public List<Waypoint> getWaypoints()
	{
		return waypoints;
	}

	public List<Waypoint> getWaypoints(WaypointSet set)
	{
		List<Waypoint> wpts = new ArrayList<Waypoint>();
		synchronized (waypoints)
		{
			for (Waypoint wpt : waypoints)
			{
				if (wpt.set == set)
				{
					wpts.add(wpt);
				}
			}
		}
		return wpts;
	}

	public List<Waypoint> getDefaultWaypoints()
	{
		return getWaypoints(defWaypointSet);
	}

	public boolean hasWaypoints()
	{
		return waypoints.size() > 0;
	}

	public void saveWaypoints(WaypointSet set)
	{
		try
		{
			if (set.path == null)
				set.path = dataPath + File.separator + FileUtils.sanitizeFilename(set.name) + ".wpt";
			File file = new File(set.path);
			File dir = file.getParentFile();
			if (! dir.exists())
				dir.mkdirs();
			if (! file.exists())
				file.createNewFile();
			if (file.canWrite())
				OziExplorerFiles.saveWaypointsToFile(file, charset, getWaypoints(set));
		}
		catch (Exception e)
		{
			Toast.makeText(this, getString(R.string.err_write), Toast.LENGTH_LONG).show();
			Log.e("ANDROZIC", e.toString(), e);
		}
	}

	public void saveWaypoints()
	{
		for (WaypointSet wptset : waypointSets)
		{
			saveWaypoints(wptset);
		}
		overlayManager.onWaypointsChanged();
	}

	public void saveDefaultWaypoints()
	{
		saveWaypoints(defWaypointSet);
	}

	public boolean ensureVisible(MapObject waypoint)
	{
		return ensureVisible(waypoint.latitude, waypoint.longitude);
	}

	public boolean ensureVisible(double lat, double lon)
	{
		if (mapHolder != null)
			mapHolder.setFollowing(false);
		return setMapCenter(lat, lon, true, true, false);
	}
	
	public int addWaypointSet(final WaypointSet newWaypointSet)
	{
		waypointSets.add(newWaypointSet);
		return waypointSets.lastIndexOf(newWaypointSet);
	}

	public List<WaypointSet> getWaypointSets()
	{
		return waypointSets;
	}

	public void removeWaypointSet(final int index)
	{
		if (index == 0)
			throw new IllegalArgumentException("Default waypoint set should be never removed");
		final WaypointSet wptset = waypointSets.remove(index);
		for (Iterator<Waypoint> iter = waypoints.iterator(); iter.hasNext();)
		{
			Waypoint wpt = iter.next();
			if (wpt.set == wptset)
			{
				iter.remove();
			}
		}
	}
	
	private void clearWaypointSets()
	{
		waypointSets.clear();
	}
	
	public int addTrack(final Track track)
	{
		tracks.add(track);
		TrackOverlay trackOverlay = new TrackOverlay(track);
		overlayManager.fileTrackOverlays.add(trackOverlay);
		return tracks.lastIndexOf(track);
	}
	
	/**
	 * Notify overlay that track properties have changed
	 * @param track Changed track
	 */
	public void dispatchTrackPropertiesChanged(Track track)
	{
		for (Iterator<TrackOverlay> iter = overlayManager.fileTrackOverlays.iterator(); iter.hasNext();)
		{
			TrackOverlay to = iter.next();
			if (to.getTrack() == track)
			{
				to.onTrackPropertiesChanged();
			}
		}
	}

	public boolean removeTrack(final Track track)
	{
		track.removed = true;
		boolean removed = tracks.remove(track);
		if (removed)
		{
			for (Iterator<TrackOverlay> iter = overlayManager.fileTrackOverlays.iterator(); iter.hasNext();)
			{
				TrackOverlay to = iter.next();
				if (to.getTrack().removed)
				{
					to.onBeforeDestroy();
					iter.remove();
				}
			}			
		}
		return removed;
	}
	
	public void clearTracks()
	{
		for (Track track : tracks)
		{
			track.removed = true;			
		}
		tracks.clear();
		for (Iterator<TrackOverlay> iter = overlayManager.fileTrackOverlays.iterator(); iter.hasNext();)
		{
			TrackOverlay to = iter.next();
			if (to.getTrack().removed)
			{
				to.onBeforeDestroy();
				iter.remove();
			}
		}			
	}
	
	public Track getTrack(final int index)
	{
		return tracks.get(index);
	}

	public int getTrackIndex(final Track track)
	{
		return tracks.indexOf(track);
	}
	
	public List<Track> getTracks()
	{
		return tracks;
	}

	public boolean hasTracks()
	{
		return tracks.size() > 0;
	}

	public Route trackToRoute2(Track track, float sensitivity) throws IllegalArgumentException
	{
		Route route = new Route();
		List<Track.TrackPoint> points = track.getAllPoints();
		Track.TrackPoint tp = points.get(0);
		route.addWaypoint("RWPT", tp.latitude, tp.longitude).proximity = 0;

		if (points.size() < 2)
			throw new IllegalArgumentException("Track too short");
		
		tp = points.get(points.size()-1);
		route.addWaypoint("RWPT", tp.latitude, tp.longitude).proximity = points.size()-1;
		
		int prx = Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(this).getString(getString(R.string.pref_navigation_proximity), getString(R.string.def_navigation_proximity)));
		double proximity = prx * sensitivity;
		boolean peaks = true;
		int s = 1;
		
		while (peaks)
		{
			peaks = false;
			//Log.d("ANDROZIC", s+","+peaks);
			for (int i = s; i > 0; i--)
			{
				Waypoint sp = route.getWaypoint(i-1);
				Waypoint fp = route.getWaypoint(i);
				if (fp.silent)
					continue;
				double c = Geo.bearing(sp.latitude, sp.longitude, fp.latitude, fp.longitude);
				double xtkMin = 0, xtkMax = 0;
				int tpMin = 0, tpMax = 0;
				//Log.d("ANDROZIC", "vector: "+i+","+c);
				//Log.d("ANDROZIC", sp.name+"-"+fp.name+","+sp.proximity+"-"+fp.proximity);
				for (int j = sp.proximity; j < fp.proximity; j++)
				{
					tp = points.get(j);
					double b = Geo.bearing(tp.latitude, tp.longitude, fp.latitude, fp.longitude);
					double d = Geo.distance(tp.latitude, tp.longitude, fp.latitude, fp.longitude);
					double xtk = Geo.xtk(d, c, b);
					if (xtk != Double.NEGATIVE_INFINITY && xtk < xtkMin)
					{
						xtkMin = xtk;
						tpMin = j;
					}
					if (xtk != Double.NEGATIVE_INFINITY && xtk > xtkMax)
					{
						xtkMax = xtk;
						tpMax = j;
					}
				}
				// mark this vector to skip it on next pass
				if (xtkMin >= -proximity && xtkMax <= proximity)
				{
					fp.silent = true;
					continue;
				}
				if (xtkMin < -proximity)
				{
					tp = points.get(tpMin);
					route.insertWaypoint(i-1, "RWPT", tp.latitude, tp.longitude).proximity = tpMin;
					//Log.w("ANDROZIC", "min peak: "+s+","+tpMin+","+xtkMin);
					s++;
					peaks = true;
				}
				if (xtkMax > proximity)
				{
					tp = points.get(tpMax);
					int after = xtkMin < -proximity && tpMin < tpMax ? i : i-1;
					route.insertWaypoint(after, "RWPT", tp.latitude, tp.longitude).proximity = tpMax;
					//Log.w("ANDROZIC", "max peak: "+s+","+tpMax+","+xtkMax);
					s++;
					peaks = true;
				}
			}
			//Log.d("ANDROZIC", s+","+peaks);
			if (s > 500) peaks = false;
		}
		s = 0;
		for (Waypoint wpt : route.getWaypoints())
		{
			wpt.name += s;
			wpt.proximity = prx;
			wpt.silent = false;
			s++;
		}
		route.name = "RT_"+track.name;
		route.show = true;
		return route;
	}

	public Route trackToRoute(Track track, float sensitivity) throws IllegalArgumentException
	{
		Route route = new Route();
		List<Track.TrackPoint> points = track.getAllPoints();
		Track.TrackPoint lrp = points.get(0);
		route.addWaypoint("RWPT0", lrp.latitude, lrp.longitude);

		if (points.size() < 2)
			throw new IllegalArgumentException("Track too short");
		
		Track.TrackPoint cp = points.get(1);
		Track.TrackPoint lp = lrp;
		Track.TrackPoint tp = null;
		int i = 1;
		int prx = Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(this).getString(getString(R.string.pref_navigation_proximity), getString(R.string.def_navigation_proximity)));
		double proximity = prx * sensitivity;
		double d = 0, t = 0, b, pb = 0, cb = -1, icb = 0, xtk = 0;
		
		while (i < points.size())
		{
			cp = points.get(i);
			d += Geo.distance(lp.latitude, lp.longitude, cp.latitude, cp.longitude);
			b = Geo.bearing(lp.latitude, lp.longitude, cp.latitude, cp.longitude);
			t += Geo.turn(pb, b);
			if (Math.abs(t) >= 360)
			{
				t = t - 360*Math.signum(t);
			}
			//Log.d("ANDROZIC", i+","+b+","+t);
			lp = cp;
			pb = b;
			i++;

			// calculate initial track
			if (cb < 0)
			{
				if (d > proximity)
				{
					cb = Geo.bearing(lrp.latitude, lrp.longitude, cp.latitude, cp.longitude);
					pb = cb;
					t = 0;
					icb = cb + 180;
					if (icb >= 360) icb -= 360;
					// Log.w("ANDROZIC", "Found vector:" + cb);
				}
				continue;
			}
			// find turn
			if (Math.abs(t) > 10)
			{
				if (tp == null)
				{
					tp = cp;
					// Log.w("ANDROZIC", "Found turn: "+i);
					continue;
				}
			}
			else if (tp != null && xtk < proximity / 10)
			{
				tp = null;
				xtk = 0;
				// Log.w("ANDROZIC", "Reset turn: "+i);
			}
			// if turn in progress check xtk
			if (tp != null)
			{
				double xd = Geo.distance(cp.latitude, cp.longitude, tp.latitude, tp.longitude);
				double xb = Geo.bearing(cp.latitude, cp.longitude, tp.latitude, tp.longitude);
				xtk = Geo.xtk(xd, icb, xb);
				// turned at sharp angle
				if (xtk == Double.NEGATIVE_INFINITY)
					xtk = Geo.xtk(xd, cb, xb);
				// Log.w("ANDROZIC", "XTK: "+xtk);
				if (Math.abs(xtk) > proximity * 3)
				{
					lrp = tp;
					route.addWaypoint("RWPT"+route.length(), lrp.latitude, lrp.longitude);
					cb = Geo.bearing(lrp.latitude, lrp.longitude, cp.latitude, cp.longitude);
					// Log.e("ANDROZIC", "Set WPT: "+(route.length()-1)+","+cb);
					pb = cb;
					t = 0;
					icb = cb + 180;
					if (icb >= 360) icb -= 360;
					tp = null;
					d = 0;
					xtk = 0;
				}
				continue;
			}
			// if still direct but pretty far away add a point
			if (d > proximity * 200)
			{
				lrp = cp;
				route.addWaypoint("RWPT"+route.length(), lrp.latitude, lrp.longitude);
				// Log.e("ANDROZIC", "Set WPT: "+(route.length()-1));
				d = 0;
			}
		}
		lrp = points.get(i-1);
		route.addWaypoint("RWPT"+route.length(), lrp.latitude, lrp.longitude);
		route.name = "RT_"+track.name;
		route.show = true;
		return route;
	}
	
	public int addRoute(final Route route)
	{
		routes.add(route);
		RouteOverlay routeOverlay = new RouteOverlay(route);
		overlayManager.routeOverlays.add(routeOverlay);
		return routes.lastIndexOf(route);
	}

	/**
	 * Notify overlay that route properties have changed
	 * @param route Changed route
	 */
	public void dispatchRoutePropertiesChanged(Route route)
	{
		for (Iterator<RouteOverlay> iter = overlayManager.routeOverlays.iterator(); iter.hasNext();)
		{
			RouteOverlay to = iter.next();
			if (to.getRoute() == route)
			{
				to.onRoutePropertiesChanged();
			}
		}
	}

	public boolean removeRoute(final Route route)
	{
		route.removed = true;
		boolean removed = routes.remove(route);
		if (removed)
		{
			for (Iterator<RouteOverlay> iter = overlayManager.routeOverlays.iterator(); iter.hasNext();)
			{
				RouteOverlay to = iter.next();
				if (to.getRoute().removed)
				{
					to.onBeforeDestroy();
					iter.remove();
				}
			}
		}
		return removed;
	}
	
	public void clearRoutes()
	{
		for (Route route : routes)
		{
			route.removed = true;
		}
		routes.clear();
		for (Iterator<RouteOverlay> iter = overlayManager.routeOverlays.iterator(); iter.hasNext();)
		{
			RouteOverlay to = iter.next();
			if (to.getRoute().removed)
			{
				to.onBeforeDestroy();
				iter.remove();
			}
		}			
	}
	
	public Route getRoute(final int index)
	{
		return routes.get(index);
	}
	
	public Route getRouteByFile(String filepath)
	{
		for (Route route : routes)
		{
			if (filepath.equals(route.filepath))
				return route;
		}
		return null;
	}

	public int getRouteIndex(final Route route)
	{
		return routes.indexOf(route);
	}
	
	public List<Route> getRoutes()
	{
		return routes;
	}

	public boolean hasRoutes()
	{
		return routes.size() > 0;
	}

	public double getDeclination()
	{
		if (angleType == 0)
		{
			double lat = Double.isNaN(location[0]) ? mapCenter[0] : location[0];
			double lon = Double.isNaN(location[1]) ? mapCenter[1] : location[1];
			magneticDeclination = getDeclination(lat, lon);
		}		
		return magneticDeclination;
	}


	public double getDeclination(double lat, double lon)
	{
		GeomagneticField mag = new GeomagneticField((float) lat, (float) lon, 0.0f, System.currentTimeMillis());
		return mag.getDeclination();
	}

	public double fixDeclination(double declination)
	{
		if (angleType == 1)
		{
			declination -= magneticDeclination;
			declination = (declination + 360.0) % 360.0;
		}
		return declination;
	}

	public double[] getLocation()
	{
		double[] res = new double[2];
		res[0] = Double.isNaN(location[0]) ? mapCenter[0] : location[0];
		res[1] = Double.isNaN(location[1]) ? mapCenter[1] : location[1];
		return res;
	}
	
	public Location getLocationAsLocation()
	{
		Location loc = new Location("fake");
		loc.setLatitude(Double.isNaN(location[0]) ? mapCenter[0] : location[0]);
		loc.setLongitude(Double.isNaN(location[1]) ? mapCenter[1] : location[1]);
		return loc;
	}
	
	public void initializeMapCenter()
	{
		double[] coordinate = null;
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		String loc = sharedPreferences.getString(getString(R.string.loc_last), null);
		if (loc != null)
		{
			coordinate = CoordinateParser.parse(loc);
			setMapCenter(coordinate[0], coordinate[1], true, true, true);
		}
		if (coordinate == null)
		{
			setMapCenter(0, 0, true, true, true);
		}
	}
	
	public double[] getMapCenter()
	{
		double[] res = new double[2];
		res[0] = mapCenter[0];
		res[1] = mapCenter[1];
		return res;
	}
	
	/**
	 * Sets map center to specified coordinates
	 * @param lat New latitude
	 * @param lon New longitude
	 * @param checkcoverage Check if map covers specified location
	 * @param reindex Recreate index of maps for new location
	 * @param findbest Look for best map in new location
	 * @return true if current map was changed
	 */
	public boolean setMapCenter(double lat, double lon, boolean checkcoverage, boolean reindex, boolean findbest)
	{
		mapCenter[0] = lat;
		mapCenter[1] = lon;
		return checkcoverage ? updateLocationMaps(reindex, findbest) : false;
	}

	/**
	 * Updates available map list for current location
	 * 
	 * @param reindex
	 *            Recreate index of maps for current location 
	 * @param findbest
	 *            Look for best map in current location
	 * @return true if current map was changed
	 */
	public boolean updateLocationMaps(boolean reindex, boolean findbest)
	{
		if (maps == null)
			return false;
		
		boolean covers = currentMap != null && currentMap.coversLatLon(mapCenter[0], mapCenter[1]);

		if (!covers || reindex || findbest)
			suitableMaps = maps.getMaps(mapCenter[0], mapCenter[1]);

		if (covers && !findbest)
			return false;

		Map newMap = null;
		if (suitableMaps.size() > 0)
		{
			newMap = suitableMaps.get(0);
		}
		if (newMap == null)
		{
			newMap = MockMap.getMap(mapCenter[0], mapCenter[1]);
		}
		return setMap(newMap);
	}
	
	public boolean scrollMap(int dx, int dy, boolean checkcoverage)
	{
		if (currentMap != null)
		{
			int[] xy = new int[2];
			double[] ll = new double[2];
			
			currentMap.getXYByLatLon(mapCenter[0], mapCenter[1], xy);
			currentMap.getLatLonByXY(xy[0] + dx, xy[1] + dy, ll);

			if (ll[0] > 90.0) ll[0] = 90.0;
			if (ll[0] < -90.0) ll[0] = -90.0;
			if (ll[1] > 180.0) ll[1] = 180.0;
			if (ll[1] < -180.0) ll[1] = -180.0;
			
			return setMapCenter(ll[0], ll[1], checkcoverage, false, false);
		}
		return false;
	}

	public int[] getXYbyLatLon(double lat, double lon)
	{
		int[] xy = new int[] {0, 0};
		getXYbyLatLon(lat, lon, xy);
		return xy;
	}
	
	public void getXYbyLatLon(double lat, double lon, int[] xy)
	{
		if (currentMap != null)
		{
			currentMap.getXYByLatLon(lat, lon, xy);
		}
	}
	
	public double getZoom()
	{
		if (currentMap != null)
			return currentMap.getZoom();
		else
			return 0.0;
	}
	
	public boolean setZoom(double zoom)
	{
		if (zoom == getZoom())
			return false;
		if (currentMap != null)
		{
			currentMap.setZoom(zoom);
			coveringMaps = null;
			return true;
		}
		return false;
	}
	
	public boolean zoomIn()
	{
		if (currentMap != null)
		{
			double zoom = getNextZoom();
			if (zoom > 0)
			{
				currentMap.setZoom(zoom);
				coveringMaps = null;
				return true;
			}
		}
		return false;
	}
	
	public boolean zoomOut()
	{
		if (currentMap != null)
		{
			double zoom = getPrevZoom();
			if (zoom > 0)
			{
				currentMap.setZoom(zoom);
				coveringMaps = null;
				return true;
			}
		}
		return false;
	}
	
	public double getNextZoom()
	{
		if (currentMap != null)
			return currentMap.getNextZoom();
		else
			return 0.0;
	}

	public double getPrevZoom()
	{
		if (currentMap != null)
			return currentMap.getPrevZoom();
		else
			return 0.0;
	}

	public boolean zoomBy(float factor)
	{
		if (currentMap != null)
		{
			currentMap.zoomBy(factor);
			coveringMaps = null;
			return true;
		}
		return false;
	}

	public List<TileProvider> getOnlineMaps()
	{
		return onlineMaps;
	}

	public String getMapTitle()
	{
		if (currentMap != null)
			return currentMap.title;
		else
			return null;		
	}
	
	public Map getCurrentMap()
	{
		return currentMap;
	}
	
	public Collection<Map> getMaps()
	{
		return maps.getMaps();
	}
			
	public List<Map> getMaps(double[] loc)
	{
		return maps.getMaps(loc[0], loc[1]);
	}
	
	public boolean prevMap()
	{
		updateLocationMaps(true, false);
		int id = 0;
		if (currentMap != null)
		{
			int pos = suitableMaps.indexOf(currentMap);
			if (pos >= 0 && pos < suitableMaps.size()-1)
			{
				id = suitableMaps.get(pos+1).id;
			}
			else
			{
				id = suitableMaps.get(0).id;
			}
		}
		else if (suitableMaps.size() > 0)
		{
			id = suitableMaps.get(suitableMaps.size()-1).id;
		}
		if (id != 0)
			return selectMap(id);
		else
			return false;
	}

	public boolean nextMap()
	{
		updateLocationMaps(true, false);
		int id = 0;
		if (currentMap != null)
		{
			int pos = suitableMaps.indexOf(currentMap);
			if (pos > 0)
			{
				id = suitableMaps.get(pos-1).id;
			}
			else
			{
				id = suitableMaps.get(0).id;
			}
		}
		else if (suitableMaps.size() > 0)
		{
			id = suitableMaps.get(0).id;
		}
		if (id != 0)
			return selectMap(id);
		else
			return false;
	}

	/**
	 * Sets map if it available for current location.
	 * @param id ID of a map to set
	 * @return true if map was changed
	 */
	public boolean selectMap(int id)
	{
		if (currentMap != null && currentMap.id == id)
			return false;
		
		Map newMap = null;
		for (Map map : suitableMaps)
		{
			if (map.id == id)
			{
				newMap = map;
				break;
			}
		}
		return setMap(newMap);
	}
	
	public boolean loadMap(int id)
	{
		Map newMap = null;
		for (Map map : maps.getMaps())
		{
			if (map.id == id)
			{
				newMap = map;
				break;
			}
		}
		return loadMap(newMap);
	}

	public boolean loadMap(Map newMap)
	{
		boolean newmap = setMap(newMap);
		if (currentMap != null)
		{
			currentMap.getMapCenter(mapCenter);
			suitableMaps = maps.getMaps(mapCenter[0], mapCenter[1]);
			coveringMaps = null;
		}
		return newmap;
	}

	synchronized boolean setMap(final Map newMap)
	{
		// TODO should override equals()?
		if (newMap != null && ! newMap.equals(currentMap))
		{
			Log.i(TAG, "Set map: " + newMap.title);
			if (mapHolder != null)
			{
				try
				{
					newMap.activate(mapHolder.getMapView(), screenSize);
				}
				catch (final Throwable e)
				{
					e.printStackTrace();
					uiHandler.post(new MapActivationError(newMap, e));
					return false;
				}
			}
			if (currentMap != null)
			{
				currentMap.deactivate();
			}
			coveringMaps = null;
			currentMap = newMap;
			overlayManager.initGrids(currentMap);
			return true;
		}
		return false;
	}
	
	public void setOnlineMap(String provider)
	{
		if (onlineMaps == null || maps == null)
			return;
		for (TileProvider map : onlineMaps)
		{
			if (provider.equals(map.code))
			{
				boolean s = currentMap == onlineMap;
				if (onlineMap != null)
					maps.removeMap(onlineMap);
				byte zoom = (byte) PreferenceManager.getDefaultSharedPreferences(this).getInt(getString(R.string.pref_onlinemapscale), getResources().getInteger(R.integer.def_onlinemapscale));
				onlineMap = new OnlineMap(map, zoom);
				maps.addMap(onlineMap);
				if (s)
					setMap(onlineMap);
			}
		}
	}
	
	public void removeOnlineMap()
	{
		maps.removeMap(onlineMap);
		if (currentMap == onlineMap)
		{
			updateLocationMaps(true, true);
			onlineMap.deactivate();
			onlineMap = null;
		}
	}
	
	/*
	 * Clip map to corners
	 * Draw corners
	 * Show adjacent maps
	 * Adjacent maps diff factor
	 */
	
	private void updateCoveringMaps()
	{
		if (mapsHandler.hasMessages(1))
			mapsHandler.removeMessages(1);

		Message m = Message.obtain(mapsHandler, new Runnable() {
			@Override
			public void run()
			{
				Log.i(TAG, "updateCoveringMaps()");
				Bounds area = new Bounds();
				int[] xy = new int[2];
				double[] ll = new double[2];
				currentMap.getXYByLatLon(mapCenter[0], mapCenter[1], xy);
				currentMap.getLatLonByXY(xy[0] + (int) coveringScreen.left, xy[1] + (int) coveringScreen.top, ll);
				area.maxLat = ll[0];
				area.minLon = ll[1];
				currentMap.getLatLonByXY(xy[0] + (int) coveringScreen.right, xy[1] + (int) coveringScreen.bottom, ll);
				area.minLat = ll[0];
				area.maxLon = ll[1];
				List<Map> cmr = new ArrayList<Map>();
				if (coveringMaps != null)
					cmr.addAll(coveringMaps);
				List<Map> cma = maps.getCoveringMaps(currentMap, area, coveredAll, coveringBestMap);
				Iterator<Map> icma = cma.iterator();
				while (icma.hasNext())
				{
					Map map = icma.next();
					try
					{
						if (!map.activated())
							map.activate(mapHolder.getMapView(), screenSize);
						double zoom = map.mpp / currentMap.mpp * currentMap.getZoom();
						if (zoom != map.getZoom())
							map.setTemporaryZoom(zoom);
						cmr.remove(map);
					}
					catch (Exception e)
					{
						icma.remove();
						e.printStackTrace();
					}
				}
				synchronized (Androzic.this)
				{
					for (Map map : cmr)
					{
						if (map != currentMap)
							map.deactivate();
					}
					coveringMaps = cma;
				}
				if (mapHolder != null)
					mapHolder.refreshMap();
			}
		});
		m.what = 1;
		mapsHandler.sendMessage(m);
	}
	
	public void drawMap(MapView.Viewport viewport, boolean bestmap, Canvas c)
	{
		Map cm = currentMap;
		
		if (cm != null)
		{
			if (adjacentMaps)
			{
				int l = -(viewport.width / 2 + viewport.lookAheadXY[0]);
				int t = -(viewport.height / 2 + viewport.lookAheadXY[1]);
				int r = l + viewport.width;
				int b = t + viewport.height;
				
				if (coveringMaps == null || viewport.mapCenter[0] != coveringLoc[0] || viewport.mapCenter[1] != coveringLoc[1] || coveringBestMap != bestmap || 
					l != coveringScreen.left || t != coveringScreen.top || r != coveringScreen.right || b != coveringScreen.bottom)
				{
					coveringScreen.left = l;
					coveringScreen.top = t;
					coveringScreen.right = r;
					coveringScreen.bottom = b;
					coveringLoc[0] = viewport.mapCenter[0];
					coveringLoc[1] = viewport.mapCenter[1];
					coveringBestMap = bestmap;
					updateCoveringMaps();
				}
			}
			try
			{
				if (coveringMaps != null && !coveringMaps.isEmpty())
				{
					boolean drawn = false;
					for (Map map : coveringMaps)
					{
						if (! drawn && coveringBestMap && map.mpp < cm.mpp)
						{
							coveredAll = cm.drawMap(viewport.mapCenter, viewport.lookAheadXY, viewport.width, viewport.height, cropMapBorder, drawMapBorder, c);
							drawn = true;
						}
						map.drawMap(viewport.mapCenter, viewport.lookAheadXY, viewport.width, viewport.height, cropMapBorder, drawMapBorder, c);
					}
					if (! drawn)
					{
						coveredAll = cm.drawMap(viewport.mapCenter, viewport.lookAheadXY, viewport.width, viewport.height, cropMapBorder, drawMapBorder, c);
					}
				}
				else
				{
					coveredAll = cm.drawMap(viewport.mapCenter, viewport.lookAheadXY, viewport.width, viewport.height, cropMapBorder, drawMapBorder, c);
				}
			}
			catch (OutOfMemoryError err)
			{
	        	if (! memmsg && mapHolder != null)
	        		mapHolder.getMapView().getHandler().post(new Runnable() {

						@Override
						public void run()
						{
			        		Toast.makeText(Androzic.this, R.string.err_nomemory, Toast.LENGTH_LONG).show();
						}
					});
	        	memmsg = true;
	        	err.printStackTrace();
			}
		}
	}
	
	private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent)
		{
			String action = intent.getAction();
			Log.e(TAG, "Broadcast: " + action);
			if (action.equals(NavigationService.BROADCAST_NAVIGATION_STATE))
			{
				int state = intent.getExtras().getInt("state");
				switch (state)
				{
					case NavigationService.STATE_STARTED:
						if (overlayManager.navigationOverlay == null)
						{
							overlayManager.navigationOverlay = new NavigationOverlay();
							if (mapHolder != null)
								overlayManager.navigationOverlay.onMapChanged();
						}
						break;
					case NavigationService.STATE_REACHED:
						Toast.makeText(Androzic.this, R.string.arrived, Toast.LENGTH_LONG).show();
					case NavigationService.STATE_STOPED:
						if (overlayManager.navigationOverlay != null)
						{
							overlayManager.navigationOverlay.onBeforeDestroy();
							overlayManager.navigationOverlay = null;
						}
						break;
				}
			}
		}
	};

	public boolean isLocating()
	{
		return locationService != null && locationService.isLocating();
	}

	public void enableLocating(boolean enable)
	{
		if (locationService == null)
			bindService(new Intent(this, LocationService.class), locationConnection, BIND_AUTO_CREATE);
		String action = enable ? LocationService.ENABLE_LOCATIONS : LocationService.DISABLE_LOCATIONS;
		startService(new Intent(this, LocationService.class).setAction(action));
		Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
		editor.putBoolean(getString(R.string.lc_locate), enable);
		editor.commit();
	}

	public ILocationService getLocationService()
	{
		return locationService;
	}

	public float getHDOP()
	{
		if (locationService != null)
			return locationService.getHDOP();
		else
			return Float.NaN;
	}

	public float getVDOP()
	{
		if (locationService != null)
			return locationService.getVDOP();
		else
			return Float.NaN;
	}

	private ServiceConnection locationConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder binder)
		{
			locationService = (ILocationService) binder;
			locationService.registerLocationCallback(locationListener);
			Log.d(TAG, "Location service connected");
		}

		public void onServiceDisconnected(ComponentName className)
		{
			locationService = null;
			Log.d(TAG, "Location service disconnected");
		}
	};

	private ILocationListener locationListener = new ILocationListener() {
		@Override
		public void onGpsStatusChanged(String provider, final int status, final int fsats, final int tsats)
		{
			if (LocationManager.GPS_PROVIDER.equals(provider))
			{
				gpsStatus = status;
				gpsFSats = fsats;
				gpsTSats = tsats;
			}
		}

		@Override
		public void onLocationChanged(final Location location, final boolean continous, final boolean geoid, final float smoothspeed, final float avgspeed)
		{
			Log.d(TAG, "Location arrived");

			final long lastLocationMillis = location.getTime();

			if (angleType == 1 && lastLocationMillis - lastMagnetic >= magInterval)
			{
				GeomagneticField mag = new GeomagneticField((float) location.getLatitude(), (float) location.getLongitude(), (float) location.getAltitude(), System.currentTimeMillis());
				magneticDeclination = mag.getDeclination();
				lastMagnetic = lastLocationMillis;
			}

			Androzic.this.location[0] = location.getLatitude();
			Androzic.this.location[1] = location.getLongitude();

			shouldEnableFollowing = shouldEnableFollowing || lastKnownLocation == null;

			lastKnownLocation = location;
			gpsEnabled = gpsEnabled || LocationManager.GPS_PROVIDER.equals(location.getProvider());
			gpsContinous = continous;
			gpsGeoid = geoid;

			if (overlayManager.accuracyOverlay != null && location.hasAccuracy())
			{
				overlayManager.accuracyOverlay.setAccuracy(location.getAccuracy());
			}
		}

		@Override
		public void onProviderChanged(String provider)
		{
		}

		@Override
		public void onProviderDisabled(String provider)
		{
			if (LocationManager.GPS_PROVIDER.equals(provider))
			{
				Log.i(TAG, "GPS provider disabled");
				gpsEnabled = false;
			}
		}

		@Override
		public void onProviderEnabled(String provider)
		{
			if (LocationManager.GPS_PROVIDER.equals(provider))
			{
				Log.i(TAG, "GPS provider enabled");
				gpsEnabled = true;
			}
		}
	};

	public boolean isTracking()
	{
		return locationService != null && locationService.isTracking();
	}

	public void enableTracking(boolean enable)
	{
		String action = enable ? LocationService.ENABLE_TRACK : LocationService.DISABLE_TRACK;
		startService(new Intent(this, LocationService.class).setAction(action));
		Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
		editor.putBoolean(getString(R.string.lc_track), enable);
		editor.commit();
	}
	
	public void expandCurrentTrack()
	{
		if (locationService != null)
		{
			Track track = locationService.getTrack();
			track.show = true;
			overlayManager.currentTrackOverlay.setTrack(track);
		}
	}

	public void clearCurrentTrack()
	{
		if (overlayManager.currentTrackOverlay != null)
			overlayManager.currentTrackOverlay.clear();
		if (locationService != null)
			locationService.clearTrack();
	}

	/**
	 * Retrieves last known location without enabling location providers.
	 * @return Most precise last known location or null if it is not available
	 */
	public Location getLastKnownSystemLocation()
	{
		LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		List<String> providers = lm.getProviders(true);
		Location l = null;

		for (int i = providers.size() - 1; i >= 0; i--)
		{
			l = lm.getLastKnownLocation(providers.get(i));
			if (l != null)
				break;
		}

		return l;
	}

	public boolean isNavigating()
	{
		return navigationService != null && navigationService.isNavigating();		
	}

	public boolean isNavigatingViaRoute()
	{
		return navigationService != null && navigationService.isNavigatingViaRoute();
	}

	public void initializeNavigation()
	{
		bindService(new Intent(this, NavigationService.class), navigationConnection, BIND_AUTO_CREATE);
		registerReceiver(broadcastReceiver, new IntentFilter(NavigationService.BROADCAST_NAVIGATION_STATUS));
		registerReceiver(broadcastReceiver, new IntentFilter(NavigationService.BROADCAST_NAVIGATION_STATE));

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		String navWpt = settings.getString(getString(R.string.nav_wpt), "");
		if (!"".equals(navWpt))
		{
			Waypoint waypoint = new Waypoint();
			waypoint.name = navWpt;
			waypoint.latitude = (double) settings.getFloat(getString(R.string.nav_wpt_lat), 0);
			waypoint.longitude = (double) settings.getFloat(getString(R.string.nav_wpt_lon), 0);
			waypoint.proximity = settings.getInt(getString(R.string.nav_wpt_prx), 0);
			startNavigation(waypoint);
		}

		String navRoute = settings.getString(getString(R.string.nav_route), "");
		if (!"".equals(navRoute) && settings.getBoolean(getString(R.string.pref_navigation_loadlast), getResources().getBoolean(R.bool.def_navigation_loadlast)))
		{
			int ndir = settings.getInt(getString(R.string.nav_route_dir), 0);
			int nwpt = settings.getInt(getString(R.string.nav_route_wpt), -1);
			try
			{
				Route route = getRouteByFile(navRoute);
				if (route != null)
				{
					route.show = true;
				}
				else
				{
					File rtf = new File(navRoute);
					// FIXME It's bad - it can be not a first route in a file
					route = OziExplorerFiles.loadRoutesFromFile(rtf, charset).get(0);
					addRoute(route);
				}
				startNavigation(route, ndir, nwpt);
			}
			catch (Exception e)
			{
				Log.e(TAG, "Failed to start navigation", e);
			}
		}
	}

	public void startNavigation(Waypoint waypoint)
	{
		Intent i = new Intent(this, NavigationService.class).setAction(NavigationService.NAVIGATE_MAPOBJECT);
		i.putExtra(NavigationService.EXTRA_NAME, waypoint.name);
		i.putExtra(NavigationService.EXTRA_LATITUDE, waypoint.latitude);
		i.putExtra(NavigationService.EXTRA_LONGITUDE, waypoint.longitude);
		i.putExtra(NavigationService.EXTRA_PROXIMITY, waypoint.proximity);
		startService(i);

		Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
		editor.putString(getString(R.string.nav_route), "");
		editor.putString(getString(R.string.nav_wpt), waypoint.name);
		editor.putInt(getString(R.string.nav_wpt_prx), waypoint.proximity);
		editor.putFloat(getString(R.string.nav_wpt_lat), (float) waypoint.latitude);
		editor.putFloat(getString(R.string.nav_wpt_lon), (float) waypoint.longitude);
		editor.commit();
	}

	public void startNavigation(MapObject mapObject)
	{
		Intent i = new Intent(this, NavigationService.class).setAction(NavigationService.NAVIGATE_MAPOBJECT_WITH_ID);
		i.putExtra(NavigationService.EXTRA_ID, mapObject._id);
		startService(i);
	}

	public void startNavigation(Route route)
	{
		startNavigation(route, 0, -1);
	}

	public void startNavigation(Route route, int direction, int waypointIndex)
	{
    	route.show = true;
		int rt = getRouteIndex(route);
		Intent i = new Intent(this, NavigationService.class).setAction(NavigationService.NAVIGATE_ROUTE);
		i.putExtra(NavigationService.EXTRA_ROUTE_INDEX, rt);
		i.putExtra(NavigationService.EXTRA_ROUTE_DIRECTION, direction);
		i.putExtra(NavigationService.EXTRA_ROUTE_START, waypointIndex);
		startService(i);

		Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
		editor.putString(getString(R.string.nav_wpt), "");
		editor.putString(getString(R.string.nav_route), "");
		if (route.filepath != null)
		{
			editor.putString(getString(R.string.nav_route), route.filepath);
			editor.putInt(getString(R.string.nav_route_idx), getRouteIndex(route));
			editor.putInt(getString(R.string.nav_route_dir), direction);
			editor.putInt(getString(R.string.nav_route_wpt), waypointIndex);
		}
		editor.commit();
	}

	public void stopNavigation()
	{
		navigationService.stopNavigation();

		Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
		editor.putString(getString(R.string.nav_wpt), "");
		editor.putString(getString(R.string.nav_route), "");
		editor.commit();
	}

	private ServiceConnection navigationConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service)
		{
			navigationService = ((NavigationService.LocalBinder) service).getService();
			Log.d(TAG, "Navigation service connected");
		}

		public void onServiceDisconnected(ComponentName className)
		{
			navigationService = null;
			Log.d(TAG, "Navigation service disconnected");
		}
	};

	public void setRootPath(String path)
	{
		rootPath = path;
	}

	public String getRootPath()
	{
		return rootPath;
	}

	public void setDataPath(int pathtype, String path)
	{
		if ((pathtype & PATH_DATA) > 0)
			dataPath = path;
		if ((pathtype & PATH_ICONS) > 0)
			iconPath = path;
		if ((pathtype & PATH_SAS) > 0)
			sasPath = path;
	}

	public boolean setMapPath(String path)
	{
		String newPath = path;
		if (mapPath == null || ! mapPath.equals(newPath))
		{
			mapPath = newPath;
			if (mapsInited)
			{
				resetMaps();
				return true;
			}
		}
		return false;
	}

	public String getMapPath()
	{
		return mapPath;
	}

	public void initializeMaps()
	{
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		boolean useIndex = settings.getBoolean(getString(R.string.pref_usemapindex), getResources().getBoolean(R.bool.def_usemapindex));
		maps = null;
		File index = new File(rootPath, "maps.idx");
		if (useIndex && index.exists())
		{
			try
			{
				//com.esotericsoftware.minlog.Log.DEBUG();
		    	Kryo kryo = new Kryo();
		    	kryo.register(MapIndex.class);
		    	kryo.register(Map.class);
		    	kryo.register(Grid.class);
		    	kryo.register(MapPoint.class);
		    	kryo.register(MapPoint[].class);
		    	kryo.register(Projection.class);
		    	kryo.register(Integer.class);
		    	kryo.register(String.class);
		    	kryo.register(ArrayList.class);
		    	kryo.register(HashSet.class);
		    	kryo.register(HashMap.class);
		    	Input input = new Input(new FileInputStream(index));
		    	maps = kryo.readObject(input, MapIndex.class);
		    	input.close();

		    	int hash = MapIndex.getMapsHash(mapPath);
				if (hash != maps.hashCode())
				{
					maps = null;
				}
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
		if (maps == null)
		{
			maps = new MapIndex(mapPath, charset);
			StringBuilder sb = new StringBuilder();
			for (Map mp : maps.getMaps())
			{
				if (mp.loadError != null)
				{
					String fn = new String(mp.mappath);
					if (fn.startsWith(mapPath))
					{
						fn = fn.substring(mapPath.length() + 1);
					}
					sb.append("<b>");
					sb.append(fn);
					sb.append(":</b> ");
					if (mp.loadError instanceof ProjectionException)
					{
						sb.append("projection error: ");					
					}
					sb.append(mp.loadError.getMessage());
					sb.append("<br />\n");
				}
			}
			if (sb.length() > 0)
			{
				maps.cleanBadMaps();
				startActivity(new Intent(this, ErrorDialog.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("title", getString(R.string.badmaps)).putExtra("message", sb.toString()));
			}
			
			if (useIndex)
			{
			    try
			    {
			    	Kryo kryo = new Kryo();
			    	kryo.register(MapIndex.class);
			    	kryo.register(Map.class);
			    	kryo.register(Grid.class);
			    	kryo.register(MapPoint.class);
			    	kryo.register(MapPoint[].class);
			    	kryo.register(Projection.class);
			    	kryo.register(Integer.class);
			    	kryo.register(String.class);
			    	kryo.register(ArrayList.class);
			    	kryo.register(HashSet.class);
			    	kryo.register(HashMap.class);
			    	Output output = new Output(new FileOutputStream(index));
			    	kryo.writeObject(output, maps);
			    	output.close();
			    }
			    catch (IOException e)
			    {
			    	e.printStackTrace();
			    }
			}
		}

		// SAS maps
		File sasRoot = new File(sasPath);
		File[] files = sasRoot.listFiles();
		if (files != null)
		{
			for (File file: files)
			{
				if (file.isDirectory())
				{
					try
					{
						maps.addMap(SASMapLoader.load(file));
					}
					catch (IOException e)
					{
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}
				
		// Online maps
		onlineMaps = new ArrayList<TileProvider>();
		boolean useOnline = settings.getBoolean(getString(R.string.pref_useonlinemap), getResources().getBoolean(R.bool.def_useonlinemap));
		String current = settings.getString(getString(R.string.pref_onlinemap), getResources().getString(R.string.def_onlinemap));
		byte zoom = (byte) settings.getInt(getString(R.string.pref_onlinemapscale), getResources().getInteger(R.integer.def_onlinemapscale));
		TileProvider curProvider = null;
		String[] om = this.getResources().getStringArray(R.array.online_maps);
		for (String s : om)
		{
			TileProvider provider = TileProvider.fromString(s);
			if (provider != null)
			{
				onlineMaps.add(provider);
				if (current.equals(provider.code))
					curProvider = provider;
			}
		}
		File mapproviders = new File(rootPath, "providers.dat");
		if (mapproviders.exists())
		{
			try
			{
				BufferedReader reader = new BufferedReader(new FileReader(mapproviders));
			    String line;
			    while ((line = reader.readLine()) != null)
				{
			    	line = line.trim();
			    	if (line.startsWith("#") || "".equals(line))
			    		continue;
					TileProvider provider = TileProvider.fromString(line);
					if (provider != null)
					{
						onlineMaps.add(provider);
				        if (current.equals(provider.code))
					        curProvider = provider;
					}
				}
			    reader.close();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
		if (useOnline && ! onlineMaps.isEmpty())
		{
			if (curProvider == null)
				curProvider = onlineMaps.get(0);
			onlineMap = new OnlineMap(curProvider, zoom);
			maps.addMap(onlineMap);
		}
		suitableMaps = new ArrayList<Map>();
		coveredAll = true;
		coveringBestMap = true;
		mapsInited = true;
	}

	public void resetMaps()
	{
		File index = new File(rootPath, "maps.idx");
		if (index.exists())
			index.delete();
		initializeMaps();
	}

	/**
	 * Copies file assets from installation package to filesystem.
	 */
	public void copyAssets(String folder, File path)
	{
		AssetManager assetManager = getAssets();
		String[] files = null;
		try
		{
			files = assetManager.list(folder);
		}
		catch (IOException e)
		{
			Log.e("Androzic", "Failed to get assets list", e);
			return;
		}
		for (int i = 0; i < files.length; i++)
		{
			try
			{
				InputStream in = assetManager.open(folder + "/" + files[i]);
				OutputStream out = new FileOutputStream(new File(path, files[i]));
				byte[] buffer = new byte[1024];
				int read;
				while ((read = in.read(buffer)) != -1)
				{
					out.write(buffer, 0, read);
				}
				in.close();
				out.flush();
				out.close();
			}
			catch (Exception e)
			{
				Log.e("Androzic", "Asset copy error", e);
			}
		}
	}

	void installData()
	{
		defWaypointSet = new WaypointSet(dataPath + File.separator + "myWaypoints.wpt", "myWaypoints");
		waypointSets.add(defWaypointSet);
		
		File icons = new File(iconPath, "icons.dat");
		if (icons.exists())
		{
			try
			{
				BufferedReader reader = new BufferedReader(new FileReader(icons));
			    String[] fields = CSV.parseLine(reader.readLine());
			    if (fields.length == 3)
			    {
			    	iconsEnabled = true;
			    	iconX = Integer.parseInt(fields[0]);
			    	iconY = Integer.parseInt(fields[1]);
			    }
			    reader.close();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}

		
		File datums = new File(rootPath, "datums.dat");
		if (datums.exists())
		{
			try
			{
				OziExplorerFiles.loadDatums(datums);
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
		File cursor = new File(rootPath, "cursor.png");
		if (cursor.exists())
		{
			try
			{
				customCursor = new BitmapDrawable(getResources(), cursor.getAbsolutePath());
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
		//installRawResource(R.raw.datums, "datums.xml");
	}

	void installRawResource(final int id, final String path)
	{
		try
		{
			// TODO Needs versioning
			openFileInput(path).close();
		}
		catch (Exception e)
		{

		}
		finally
		{
			InputStream in = getResources().openRawResource(id);
			FileOutputStream out = null;

			try
			{
				out = openFileOutput(path, MODE_PRIVATE);

				int size = in.available();

				byte[] buffer = new byte[size];
				in.read(buffer);
				in.close();

				out.write(buffer);
				out.close();

			}
			catch (Exception ex)
			{
			}
		}
	}
	
	/**
	 * Load default and selected waypoint files.
	 */
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public void initializeWaypoints()
	{
		if (waypoints.size() > 0)
			return;

		File wptFile = new File(dataPath, "myWaypoints.wpt");
		if (wptFile.exists() && wptFile.canRead())
		{
			try
			{
				addWaypoints(OziExplorerFiles.loadWaypointsFromFile(wptFile, charset));
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
		
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
		{
			// load selected waypoint sets
			SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
			Set<String> sets = settings.getStringSet(getString(R.string.wpt_sets), new HashSet<String>());
			for (String path : sets)
			{
				File file = new File(path);
				try
				{
					if (file.exists() && file.canRead())
						WaypointFileHelper.loadFile(file);
				}
				catch (Exception e)
				{
					// We ignore all exceptions on this stage
					e.printStackTrace();
				}
			}
		}
	}
	
	public void initializePlugins()
	{
		PackageManager packageManager = getPackageManager();
		List<ResolveInfo> plugins;
		Intent initializationIntent = new Intent("com.androzic.plugins.action.INITIALIZE");

		// enumerate initializable plugins
		plugins = packageManager.queryBroadcastReceivers(initializationIntent, 0);
		for (ResolveInfo plugin : plugins)
		{
			// send initialization broadcast, we send it directly instead of sending
			// one broadcast for all plugins to wake up stopped plugins:
			// http://developer.android.com/about/versions/android-3.1.html#launchcontrols
			Intent intent = new Intent();
			intent.setClassName(plugin.activityInfo.packageName, plugin.activityInfo.name);
			intent.setAction("com.androzic.plugins.action.INITIALIZE");
			sendBroadcast(intent);
		}
		
		// enumerate plugins with preferences
		plugins = packageManager.queryIntentActivities(new Intent("com.androzic.plugins.preferences"), 0);
		for (ResolveInfo plugin : plugins)
		{
            Intent intent = new Intent();
            intent.setClassName(plugin.activityInfo.packageName, plugin.activityInfo.name);
			pluginPreferences.put(plugin.activityInfo.loadLabel(packageManager).toString(), intent);
		}

		// enumerate plugins with views
		plugins = packageManager.queryIntentActivities(new Intent("com.androzic.plugins.view"), 0);
		for (ResolveInfo plugin : plugins)
		{
			// get menu icon
			Drawable icon = null;
			try
			{
				Resources resources = packageManager.getResourcesForApplication(plugin.activityInfo.applicationInfo);
				int id = resources.getIdentifier("ic_menu_view", "drawable", plugin.activityInfo.packageName);
				if (id != 0)
					icon = resources.getDrawable(id);
			}
			catch (Resources.NotFoundException e)
			{
				e.printStackTrace();
			}
			catch (PackageManager.NameNotFoundException e)
			{
				e.printStackTrace();
			}			

			Intent intent = new Intent();
            intent.setClassName(plugin.activityInfo.packageName, plugin.activityInfo.name);
            Pair<Drawable, Intent> pair = new Pair<Drawable, Intent>(icon, intent);
			pluginViews.put(plugin.activityInfo.loadLabel(packageManager).toString(), pair);
		}
	}


	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
	{
		Resources resources = getResources();
		
		if (getString(R.string.pref_folder_data).equals(key))
		{
			setDataPath(Androzic.PATH_DATA, sharedPreferences.getString(key, resources.getString(R.string.def_folder_data)));
		}
		else if (getString(R.string.pref_folder_icon).equals(key))
		{
			setDataPath(Androzic.PATH_ICONS, sharedPreferences.getString(key, resources.getString(R.string.def_folder_icon)));
		}
		else if (getString(R.string.pref_unitcoordinate).equals(key))
		{
			coordinateFormat = Integer.parseInt(sharedPreferences.getString(key, "0"));			
		}
		else if (getString(R.string.pref_unitangle).equals(key))
		{
			angleType = Integer.parseInt(sharedPreferences.getString(key, "0"));
		}
		else if (getString(R.string.pref_grid_mapshow).equals(key))
		{
			overlayManager.mapGrid = sharedPreferences.getBoolean(key, false);
			overlayManager.initGrids(currentMap);
		}
		else if (getString(R.string.pref_grid_usershow).equals(key))
		{
			overlayManager.userGrid = sharedPreferences.getBoolean(key, false);
			overlayManager.initGrids(currentMap);
		}
		else if (getString(R.string.pref_grid_preference).equals(key))
		{
			overlayManager.gridPrefer = Integer.parseInt(sharedPreferences.getString(key, "0"));
			overlayManager.initGrids(currentMap);
		}
		else if (getString(R.string.pref_grid_userscale).equals(key) || getString(R.string.pref_grid_userunit).equals(key) || getString(R.string.pref_grid_usermpp).equals(key))
		{
			overlayManager.initGrids(currentMap);
		}
		else if (getString(R.string.pref_useonlinemap).equals(key))
		{
			boolean online = sharedPreferences.getBoolean(key, false);
			if (online)
				setOnlineMap(sharedPreferences.getString(getString(R.string.pref_onlinemap), resources.getString(R.string.def_onlinemap)));
			else
				removeOnlineMap();
		}
		else if (getString(R.string.pref_onlinemap).equals(key) || getString(R.string.pref_onlinemapscale).equals(key))
		{
			setOnlineMap(sharedPreferences.getString(getString(R.string.pref_onlinemap), resources.getString(R.string.def_onlinemap)));
		}
		else if (getString(R.string.pref_mapadjacent).equals(key))
		{
			adjacentMaps = sharedPreferences.getBoolean(key, resources.getBoolean(R.bool.def_mapadjacent));
		}
		else if (getString(R.string.pref_mapcropborder).equals(key))
		{
			cropMapBorder = sharedPreferences.getBoolean(key, resources.getBoolean(R.bool.def_mapcropborder));
		}
		else if (getString(R.string.pref_mapdrawborder).equals(key))
		{
			drawMapBorder = sharedPreferences.getBoolean(key, resources.getBoolean(R.bool.def_mapdrawborder));
		}
		else if (getString(R.string.pref_showwaypoints).equals(key))
		{
			overlayManager.setWaypointsOverlayEnabled(sharedPreferences.getBoolean(key, true));
		}
		else if (getString(R.string.pref_showcurrenttrack).equals(key))
		{
			overlayManager.setCurrentTrackOverlayEnabled(sharedPreferences.getBoolean(key, true));
		}
		else if (getString(R.string.pref_showaccuracy).equals(key))
		{
			overlayManager.setAccuracyOverlayEnabled(sharedPreferences.getBoolean(key, true));
		}
		else if (getString(R.string.pref_showdistance_int).equals(key))
		{
			int showDistance = Integer.parseInt(sharedPreferences.getString(key, getString(R.string.def_showdistance)));
			overlayManager.setDistanceOverlayEnabled(showDistance > 0);
		}
		overlayManager.onPreferencesChanged(sharedPreferences);
	}	

	@Override
	public void onConfigurationChanged(Configuration newConfig)
	{
		super.onConfigurationChanged(newConfig);
		if (locale != null)
		{
			newConfig.locale = locale;
		    Locale.setDefault(locale);
			getBaseContext().getResources().updateConfiguration(newConfig, getBaseContext().getResources().getDisplayMetrics());
		}
	}

	@Override
	public void onCreate()
	{
		super.onCreate();
		Log.e("ANDROZIC","Application onCreate()");

		try
		{
			OzfDecoder.useNativeCalls();
		}
		catch (UnsatisfiedLinkError e)
		{
			Toast.makeText(Androzic.this, "Failed to initialize native library: " + e.getMessage(), Toast.LENGTH_LONG).show();
		}
		
		renderingThread = new HandlerThread("RenderingThread");
		renderingThread.start();
		
		longOperationsThread = new HandlerThread("LongOperationsThread");
		longOperationsThread.start();
		
		uiHandler = new Handler();
		mapsHandler = new Handler(longOperationsThread.getLooper());

		setInstance(this);
		
        String intentToCheck = "com.androzic.donate";
        String myPackageName = getPackageName();
        PackageManager pm = getPackageManager();
        PackageInfo pi;
		try
		{
			pi = pm.getPackageInfo(intentToCheck, 0);
	        isPaid = (pm.checkSignatures(myPackageName, pi.packageName) == PackageManager.SIGNATURE_MATCH);
		}
		catch (NameNotFoundException e)
		{
		}

		File sdcard = Environment.getExternalStorageDirectory();
		Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(this, sdcard.getAbsolutePath()));
		
		WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
		if (wm != null)
		{
			DisplayMetrics metrics = new DisplayMetrics();
			wm.getDefaultDisplay().getMetrics(metrics);
			screenSize = metrics.widthPixels * metrics.heightPixels;
		}
		else
		{
			screenSize = 480 * 800;
		}

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		Resources resources = getBaseContext().getResources();
		Configuration config = resources.getConfiguration();

		charset = settings.getString(getString(R.string.pref_charset), "UTF-8");
		String lang = settings.getString(getString(R.string.pref_locale), "");
		if (! "".equals(lang) && ! config.locale.getLanguage().equals(lang))
		{
			locale = new Locale(lang);
		    Locale.setDefault(locale);
		    config.locale = locale;
		    resources.updateConfiguration(config, resources.getDisplayMetrics());
		}
		
		magInterval = resources.getInteger(R.integer.def_maginterval) * 1000;
		
		overlayManager = new OverlayManager(longOperationsThread.getLooper());
		
		//TODO Initialize all suitable settings
		onSharedPreferenceChanged(settings, getString(R.string.pref_unitcoordinate));
		onSharedPreferenceChanged(settings, getString(R.string.pref_unitangle));
		onSharedPreferenceChanged(settings, getString(R.string.pref_mapadjacent));
		onSharedPreferenceChanged(settings, getString(R.string.pref_mapcropborder));
		onSharedPreferenceChanged(settings, getString(R.string.pref_mapdrawborder));
		onSharedPreferenceChanged(settings, getString(R.string.pref_showwaypoints));
		onSharedPreferenceChanged(settings, getString(R.string.pref_showcurrenttrack));
		onSharedPreferenceChanged(settings, getString(R.string.pref_showaccuracy));
		onSharedPreferenceChanged(settings, getString(R.string.pref_showdistance_int));

		settings.registerOnSharedPreferenceChangeListener(this);
		
		//navEnabled = navigationService != null && navigationService.isNavigating();
	}
	
	@SuppressLint("NewApi")
	public void clear()
	{
		// send finalization broadcast
		sendBroadcast(new Intent("com.androzic.plugins.action.FINALIZE"));

		// clear services
		unregisterReceiver(broadcastReceiver);

		overlayManager.clear();

		Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();

		if (navigationService != null)
		{
			if (navigationService.isNavigatingViaRoute() && navigationService.navRoute.filepath != null)
			{
				// save active route point
				editor.putInt(getString(R.string.nav_route_wpt), navigationService.navCurrentRoutePoint);
			}
			unbindService(navigationConnection);
			navigationService = null;
		}

		if (locationService != null)
		{
			locationService.unregisterLocationCallback(locationListener);
			unbindService(locationConnection);
			locationService = null;
		}

		stopService(new Intent(this, NavigationService.class));
		stopService(new Intent(this, LocationService.class));

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
		{
			// save opened waypoint sets
			HashSet<String> sets = new HashSet<String>();
			for (int i = 1; i < waypointSets.size(); i++)
			{
				WaypointSet set = waypointSets.get(i);
				if (set.path != null)
					sets.add(set.path);
			}
			editor.putStringSet(getString(R.string.wpt_sets), sets);
		}

		// clear data
		clearRoutes();
		clearTracks();
		clearWaypoints();
		clearWaypointSets();
		clearMapObjects();
		
		// save last location
		editor.putString(getString(R.string.loc_last), StringFormatter.coordinates(0, " ", mapCenter[0], mapCenter[1]));
		editor.commit();
		
		mapHolder = null;
		currentMap = null;
		suitableMaps = null;
		maps = null;
		mapsInited = false;
		memmsg = false;
	}

	private class MapActivationError implements Runnable
	{
		private Map map;
		private Throwable e;

		MapActivationError(Map map, Throwable e)
		{
			this.map = map;
			this.e = e;
		}

		@Override
		public void run()
		{
			Toast.makeText(Androzic.this, map.imagePath + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
		}
	}
}
