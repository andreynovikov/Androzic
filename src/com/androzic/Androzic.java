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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Stack;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
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
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Pair;
import android.view.WindowManager;
import android.widget.Toast;

import com.androzic.data.MapObject;
import com.androzic.data.Route;
import com.androzic.data.Track;
import com.androzic.data.Waypoint;
import com.androzic.data.WaypointSet;
import com.androzic.location.LocationService;
import com.androzic.map.Map;
import com.androzic.map.MapIndex;
import com.androzic.map.MockMap;
import com.androzic.map.online.OnlineMap;
import com.androzic.map.online.TileProvider;
import com.androzic.navigation.NavigationService;
import com.androzic.overlay.AccuracyOverlay;
import com.androzic.overlay.CurrentTrackOverlay;
import com.androzic.overlay.DistanceOverlay;
import com.androzic.overlay.LatLonGridOverlay;
import com.androzic.overlay.MapObjectsOverlay;
import com.androzic.overlay.MapOverlay;
import com.androzic.overlay.NavigationOverlay;
import com.androzic.overlay.OtherGridOverlay;
import com.androzic.overlay.RouteOverlay;
import com.androzic.overlay.ScaleOverlay;
import com.androzic.overlay.TrackOverlay;
import com.androzic.overlay.WaypointsOverlay;
import com.androzic.util.Astro.Zenith;
import com.androzic.util.CSV;
import com.androzic.util.CoordinateParser;
import com.androzic.util.FileUtils;
import com.androzic.util.Geo;
import com.androzic.util.OziExplorerFiles;
import com.androzic.util.StringFormatter;
import com.jhlabs.map.proj.ProjectionException;

public class Androzic extends BaseApplication
{
	public static final int PATH_DATA = 0x001;
	public static final int PATH_ICONS = 0x008;
	
	public static final int ORDER_SHOW_PREFERENCE = 0;
	public static final int ORDER_DRAW_PREFERENCE = 1;
		
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
	private double[] shouldBeVisible = new double[] {Double.NaN, Double.NaN};
	private double magneticDeclination = 0;
	
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
	
	// FIXME Put overlays in separate class
	public LatLonGridOverlay llGridOverlay;
	public OtherGridOverlay grGridOverlay;
	public CurrentTrackOverlay currentTrackOverlay;
	public NavigationOverlay navigationOverlay;
	public MapObjectsOverlay mapObjectsOverlay;
	public WaypointsOverlay waypointsOverlay;
	public DistanceOverlay distanceOverlay;
	public AccuracyOverlay accuracyOverlay;
	public ScaleOverlay scaleOverlay;
	public List<TrackOverlay> fileTrackOverlays = new ArrayList<TrackOverlay>();
	public List<RouteOverlay> routeOverlays = new ArrayList<RouteOverlay>();
	
	private Locale locale = null;
	private Handler handler = null;
	public String charset;

	public String dataPath;
	private String rootPath;
	private String mapPath;
	public String iconPath;
	public boolean mapsInited = false;
	public MapActivity mapActivity;
	private int screenSize;
	public Drawable customCursor = null;
	public boolean iconsEnabled = false;
	public int iconX = 0;
	public int iconY = 0;
	
	public boolean isPaid = false;

	protected boolean adjacentMaps = false;
	protected boolean cropMapBorder = true;
	protected boolean drawMapBorder = false;
	protected boolean mapGrid = false;
	protected boolean userGrid = false;
	protected int gridPrefer = 0;

	private Handler mapsHandler = new Handler();

	protected void setMapActivity(MapActivity activity)
	{
		mapActivity = activity;
		for (MapOverlay mo : fileTrackOverlays)
		{
			mo.setMapContext(mapActivity);
		}
		if (currentTrackOverlay != null)
		{
			currentTrackOverlay.setMapContext(mapActivity);
		}
		for (MapOverlay mo : routeOverlays)
		{
			mo.setMapContext(mapActivity);
		}
		if (navigationOverlay != null)
		{
			navigationOverlay.setMapContext(mapActivity);
		}
		if (waypointsOverlay != null)
		{
			waypointsOverlay.setMapContext(mapActivity);
		}
		if (distanceOverlay != null)
		{
			distanceOverlay.setMapContext(mapActivity);
		}
		if (accuracyOverlay != null)
		{
			accuracyOverlay.setMapContext(mapActivity);
		}
		if (mapObjectsOverlay != null)
		{
			mapObjectsOverlay.setMapContext(mapActivity);
		}
		if (scaleOverlay != null)
		{
			scaleOverlay.setMapContext(mapActivity);
		}
		initGrids();
	}
	
	public List<MapOverlay> getOverlays(int order)
	{
		List<MapOverlay> overlays = new ArrayList<MapOverlay>();
		if (order == ORDER_DRAW_PREFERENCE)
		{
			if (llGridOverlay != null)
				overlays.add(llGridOverlay);
			if (grGridOverlay != null)
				overlays.add(grGridOverlay);
			if (accuracyOverlay != null)
				overlays.add(accuracyOverlay);
			overlays.addAll(fileTrackOverlays);
			if (currentTrackOverlay != null)
				overlays.add(currentTrackOverlay);
			overlays.addAll(routeOverlays);
			if (navigationOverlay != null)
				overlays.add(navigationOverlay);
			if (waypointsOverlay != null)
				overlays.add(waypointsOverlay);
			if (scaleOverlay != null)
				overlays.add(scaleOverlay);
			if (mapObjectsOverlay != null)
				overlays.add(mapObjectsOverlay);
			if (distanceOverlay != null)
				overlays.add(distanceOverlay);
		}
		else
		{
			if (accuracyOverlay != null)
				overlays.add(accuracyOverlay);
			if (distanceOverlay != null)
				overlays.add(distanceOverlay);
			if (scaleOverlay != null)
				overlays.add(scaleOverlay);
			if (navigationOverlay != null)
				overlays.add(navigationOverlay);
			if (currentTrackOverlay != null)
				overlays.add(currentTrackOverlay);
			overlays.addAll(routeOverlays);
			if (waypointsOverlay != null)
				overlays.add(waypointsOverlay);
			overlays.addAll(fileTrackOverlays);
			if (mapObjectsOverlay != null)
				overlays.add(mapObjectsOverlay);
			if (grGridOverlay != null)
				overlays.add(grGridOverlay);
			if (llGridOverlay != null)
				overlays.add(llGridOverlay);
		}
		return overlays;
	}
	
	private ExecutorService executorThread = Executors.newSingleThreadExecutor();

	protected void notifyOverlays()
	{
		final List<MapOverlay> overlays = getOverlays(ORDER_SHOW_PREFERENCE);
		final boolean[] states = new boolean[overlays.size()];
		int i = 0;
    	for (MapOverlay mo : overlays)
    	{
   			states[i] = mo.setEnabled(false);
   			i++;
    	}
		executorThread.execute(new Runnable() {
			public void run()
			{
				int j = 0;
		    	for (MapOverlay mo : overlays)
		    	{
		   			mo.onMapChanged();
	   				mo.setEnabled(states[j]);
		   			j++;
		    	}
			}
		});
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

	public int getWaypointCount(WaypointSet set)
	{
		int n = 0;
		synchronized (waypoints)
		{
			for (Waypoint wpt : waypoints)
			{
				if (wpt.set == set)
				{
					n++;
				}
			}
		}
		return n;
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
	}

	public void saveDefaultWaypoints()
	{
		saveWaypoints(defWaypointSet);
	}

	public void ensureVisible(MapObject waypoint)
	{
		ensureVisible(waypoint.latitude, waypoint.longitude);
	}

	public void ensureVisible(double lat, double lon)
	{
		shouldBeVisible[0] = lat;
		shouldBeVisible[1] = lon;
	}
	
	public boolean hasEnsureVisible()
	{
		return ! Double.isNaN(shouldBeVisible[0]);
	}
	
	public double[] getEnsureVisible()
	{
		return shouldBeVisible;
	}
	
	public void clearEnsureVisible()
	{
		shouldBeVisible[0] = Double.NaN;
		shouldBeVisible[1] = Double.NaN;
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
	
	public int addTrack(final Track newTrack)
	{
		tracks.add(newTrack);
		return tracks.lastIndexOf(newTrack);
	}
	
	public boolean removeTrack(final Track delTrack)
	{
		delTrack.removed = true;
		return tracks.remove(delTrack);
	}
	
	public void clearTracks()
	{
		for (Track track : tracks)
		{
			track.removed = true;			
		}
		tracks.clear();
	}
	
	public Track getTrack(final int index)
	{
		return tracks.get(index);
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
		List<Track.TrackPoint> points = track.getPoints();
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
		List<Track.TrackPoint> points = track.getPoints();
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
	
	public int addRoute(final Route newRoute)
	{
		routes.add(newRoute);
		return routes.lastIndexOf(newRoute);
	}
	
	public boolean removeRoute(final Route delRoute)
	{
		delRoute.removed = true;
		return routes.remove(delRoute);
	}
	
	public void addRoutes(final Iterable<Route> newRoutes)
	{
		Iterator<Route> iterator = newRoutes.iterator();
		while (iterator.hasNext())
			routes.add(iterator.next());
	}

	public void clearRoutes()
	{
		for (Route route : routes)
		{
			route.removed = true;
		}
		routes.clear();
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
			float lat = (float) (Double.isNaN(location[0]) ? mapCenter[0] : location[0]);
			float lon = (float) (Double.isNaN(location[1]) ? mapCenter[1] : location[1]);
			GeomagneticField mag = new GeomagneticField(lat, lon, 0.0f, System.currentTimeMillis());
			magneticDeclination = mag.getDeclination();
		}		
		return magneticDeclination;
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
	
	void setLocation(Location loc, boolean updatemag)
	{
		location[0] = loc.getLatitude();
		location[1] = loc.getLongitude();
		if (updatemag && angleType == 1)
		{
			GeomagneticField mag = new GeomagneticField((float) location[0], (float) location[1], (float) loc.getAltitude(), System.currentTimeMillis());
			magneticDeclination = mag.getDeclination();
		}
	}
	
	public void initializeMapCenter()
	{
		double[] coordinate = null;
		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		String loc = sharedPreferences.getString(getString(R.string.loc_last), null);
		if (loc != null)
		{
			coordinate = CoordinateParser.parse(loc);
			setMapCenter(coordinate[0], coordinate[1], true, true);
		}
		if (coordinate == null)
		{
			setMapCenter(0, 0, true, true);
		}
	}
	
	public double[] getMapCenter()
	{
		double[] res = new double[2];
		res[0] = mapCenter[0];
		res[1] = mapCenter[1];
		return res;
	}
	
	public boolean setMapCenter(double lat, double lon, boolean reindex, boolean findbest)
	{
		mapCenter[0] = lat;
		mapCenter[1] = lon;
		return updateLocationMaps(reindex, findbest);
	}

	/**
	 * Updates available map list for current location
	 * 
	 * @param findbest
	 *            Look for better map in current location
	 * @param findbest2 
	 * @return true if current map was changed
	 */
	public boolean updateLocationMaps(boolean reindex, boolean findbest)
	{
		if (maps == null)
			return false;
		
		boolean covers = currentMap != null && currentMap.coversLatLon(mapCenter[0], mapCenter[1]);

		if (reindex || findbest)
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
	
	public boolean scrollMap(int dx, int dy)
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
			
			return setMapCenter(ll[0], ll[1], false, false);
		}
		return false;
	}

	public int[] getXYbyLatLon(double lat, double lon)
	{
		int[] xy = new int[] {0, 0};
		if (currentMap != null)
		{
			currentMap.getXYByLatLon(lat, lon, xy);
		}
		return xy;
	}
	
	public double getZoom()
	{
		if (currentMap != null)
			return currentMap.getZoom();
		else
			return 0.0;
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
	
	public List<Map> getMaps()
	{
		return maps.getMaps();
	}
			
	public List<Map> getMaps(double[] loc)
	{
		return maps.getMaps(loc[0], loc[1]);
	}
	
	public boolean nextMap()
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

	public boolean prevMap()
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
		boolean newmap = setMap(newMap);
		if (currentMap != null)
		{
			int x = currentMap.getScaledWidth() / 2;
			int y = currentMap.getScaledHeight() / 2;
			currentMap.getLatLonByXY(x, y, mapCenter);
			suitableMaps = maps.getMaps(mapCenter[0], mapCenter[1]);
			coveringMaps = null;
		}
		return newmap;
	}

	protected void initGrids()
	{
		llGridOverlay = null;
		grGridOverlay = null;
		if (mapGrid && currentMap != null && currentMap.llGrid != null && currentMap.llGrid.enabled && mapActivity != null)
		{
			LatLonGridOverlay llgo = new LatLonGridOverlay(mapActivity);
			llgo.setGrid(currentMap.llGrid);
			llGridOverlay = llgo;
		}
		if (mapGrid && currentMap != null && currentMap.grGrid != null && currentMap.grGrid.enabled && mapActivity != null && (! userGrid || gridPrefer == 0))
		{
			OtherGridOverlay ogo = new OtherGridOverlay(mapActivity);
			ogo.setGrid(currentMap.grGrid);
			grGridOverlay = ogo;
		}
		else if (userGrid && currentMap != null && mapActivity != null)
		{
			SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
			OtherGridOverlay ogo = new OtherGridOverlay(mapActivity);
			Map.Grid grid = currentMap.new Grid();
			grid.color1 = 0xFF0000FF;
			grid.color2 = 0xFF0000FF;
			grid.color3 = 0xFF0000FF;
			grid.enabled = true;
			grid.spacing = Integer.parseInt(settings.getString(getString(R.string.pref_grid_userscale), getResources().getString(R.string.def_grid_userscale)));
			int distanceIdx = Integer.parseInt(settings.getString(getString(R.string.pref_grid_userunit), "0"));
			grid.spacing *= Double.parseDouble(getResources().getStringArray(R.array.distance_factors_short)[distanceIdx]);
			grid.maxMPP = Integer.parseInt(settings.getString(getString(R.string.pref_grid_usermpp), getResources().getString(R.string.def_grid_usermpp)));
			ogo.setGrid(grid);
			grGridOverlay = ogo;
		}
	}
	
	synchronized private boolean setMap(final Map newMap)
	{
		// TODO should override equals()?
		if (newMap != null && ! newMap.equals(currentMap) && mapActivity != null)
		{
			Log.d("ANDROZIC", "Set map: " + newMap);
			try
			{
				newMap.activate(mapActivity.map, screenSize);
			}
			catch (final Throwable e)
			{
				e.printStackTrace();
				handler.post(new Runnable() {
				    @Override
				    public void run() {
						Toast.makeText(Androzic.this, newMap.imagePath+": "+e.getMessage(), Toast.LENGTH_LONG).show();
				    }
				  });
				return false;
			}
			if (currentMap != null)
			{
				currentMap.deactivate();
			}
			coveringMaps = null;
			currentMap = newMap;
			initGrids();
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
				maps.removeMap(onlineMap);
				byte zoom = (byte) PreferenceManager.getDefaultSharedPreferences(this).getInt(getString(R.string.pref_onlinemapscale), getResources().getInteger(R.integer.def_onlinemapscale));
				onlineMap = new OnlineMap(map, zoom);
				maps.addMap(onlineMap);
				if (s)
					setMap(onlineMap);
			}
		}
	}
	
	/*
	 * ���������:
	 * Clip map to corners
	 * Draw corners
	 * Show adjacent maps
	 * Adjacent maps diff factor
	 */
	
	private void updateCoveringMaps()
	{
		if (!mapsHandler.hasMessages(1))
		{
			Message m = Message.obtain(mapsHandler, new Runnable() {
				@Override
				public void run()
				{
					Map.Bounds area = new Map.Bounds();
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
							if (! map.activated())
								map.activate(mapActivity.map, screenSize);
							double zoom = map.mpp / currentMap.mpp * currentMap.getZoom();
							if (zoom != map.getZoom())
								map.setTemporaryZoom(zoom);
							cmr.remove(map);
						}
						catch (Exception e)
						{
							cma.remove(map);
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
				}
			});
			m.what = 1;
			mapsHandler.sendMessage(m);
		}
	}
	
	public void drawMap(double[] loc, int[] lookAhead, boolean bestmap, int width, int height, Canvas c)
	{
		Map cm = currentMap;
		
		if (cm != null)
		{
			if (adjacentMaps)
			{
				int l = -(width / 2 + lookAhead[0]);
				int t = -(height / 2 + lookAhead[1]);
				int r = l + width;
				int b = t + height;
				if (coveringMaps == null || loc[0] != coveringLoc[0] || loc[1] != coveringLoc[1] || coveringBestMap != bestmap || 
					l != coveringScreen.left || t != coveringScreen.top || r != coveringScreen.right || b != coveringScreen.bottom)
				{
					coveringScreen.left = l;
					coveringScreen.top = t;
					coveringScreen.right = r;
					coveringScreen.bottom = b;
					coveringLoc[0] = loc[0];
					coveringLoc[1] = loc[1];
					coveringBestMap = bestmap;
					updateCoveringMaps();
				}
			}
			try
			{
				if (coveringMaps != null && ! coveringMaps.isEmpty())
				{
					boolean drawn = false;
					for (Map map : coveringMaps)
					{
						if (! drawn && coveringBestMap && map.mpp < cm.mpp)
						{
							coveredAll = cm.drawMap(loc, lookAhead, width, height, cropMapBorder, drawMapBorder, c);
							drawn = true;
						}
						map.drawMap(loc, lookAhead, width, height, cropMapBorder, drawMapBorder, c);
					}
					if (! drawn)
					{
						coveredAll = cm.drawMap(loc, lookAhead, width, height, cropMapBorder, drawMapBorder, c);
					}
				}
				else
				{
					coveredAll = cm.drawMap(loc, lookAhead, width, height, cropMapBorder, drawMapBorder, c);
				}
			}
			catch (OutOfMemoryError err)
			{
	        	if (! memmsg && mapActivity != null)
	        		mapActivity.runOnUiThread(new Runnable() {

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
	
	public void clear()
	{
		// send finalization broadcast
		sendBroadcast(new Intent("com.androzic.plugins.action.FINALIZE"));

		clearRoutes();
		clearTracks();
		clearWaypoints();
		clearWaypointSets();
		clearMapObjects();
		Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
		editor.putString(getString(R.string.loc_last), StringFormatter.coordinates(0, " ", mapCenter[0], mapCenter[1]));
		editor.commit();			
		
		stopService(new Intent(this, NavigationService.class));
		stopService(new Intent(this, LocationService.class));
		
		llGridOverlay = null;
		grGridOverlay = null;
		mapActivity = null;
		currentMap = null;
		suitableMaps = null;
		maps = null;
		mapsInited = false;
		memmsg = false;
	}

	public void enableLocating(boolean enable)
	{
		String action = enable ? LocationService.ENABLE_LOCATIONS : LocationService.DISABLE_LOCATIONS;
		startService(new Intent(this, LocationService.class).setAction(action));
	}

	public void enableTracking(boolean enable)
	{
		String action = enable ? LocationService.ENABLE_TRACK : LocationService.DISABLE_TRACK;
		startService(new Intent(this, LocationService.class).setAction(action));
	}
	
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
				FileInputStream fs = new FileInputStream(index);
				ObjectInputStream in = new ObjectInputStream(fs);
				maps = (MapIndex) in.readObject();
				in.close();
				int hash = MapIndex.getMapsHash(mapPath);
				if (hash != maps.hashCode())
				{
					maps = null;
				}
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
			catch (ClassNotFoundException e)
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
			    	FileOutputStream fs = new FileOutputStream(index);
			    	ObjectOutputStream out = new ObjectOutputStream(fs);
			    	out.writeObject(maps);
			    	out.close();
			    }
			    catch (IOException e)
			    {
			    	e.printStackTrace();
			    }
			}
		}

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
		suitableMaps = maps.getMaps();
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

		setInstance(this);
		handler = new Handler();
		
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
			screenSize = 320 * 480;
		}

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		Configuration config = getBaseContext().getResources().getConfiguration();

		charset = settings.getString(getString(R.string.pref_charset), "UTF-8");
		String lang = settings.getString(getString(R.string.pref_locale), "");
		if (! "".equals(lang) && ! config.locale.getLanguage().equals(lang))
		{
			locale = new Locale(lang);
		    Locale.setDefault(locale);
		    config.locale = locale;
		    getBaseContext().getResources().updateConfiguration(config, getBaseContext().getResources().getDisplayMetrics());
		}
	}	
}
