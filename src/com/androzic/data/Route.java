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

import java.util.ArrayList;
import java.util.List;

import com.androzic.util.Geo;

public class Route
{
	public String name;
	public String description;
	public boolean show;
	public int wptColor = -1;
	public int lineColor = -1;
	public int width;
	
	public double distance;
	public String filepath = null;
	public boolean removed = false;
	public boolean editing = false;

	private final List<Waypoint> waypoints = new ArrayList<Waypoint>(0);
	private Waypoint lastWaypoint;
	
	public Route()
	{
		this("", "", false);
	}

	public Route(String name, String description, boolean show)
	{
		this.name = name;
		this.description = description;
		this.show = show;
		distance = 0;
	}

	public List<Waypoint> getWaypoints()
	{
		return waypoints;
	}

	public void addWaypoint(Waypoint waypoint)
	{
		if (lastWaypoint != null)
		{
			distance += Geo.distance(lastWaypoint.latitude, lastWaypoint.longitude, waypoint.latitude, waypoint.longitude);
		}
		lastWaypoint = waypoint;
		waypoints.add(lastWaypoint);		
	}
	
	public void addWaypoint(int pos, Waypoint waypoint)
	{
		waypoints.add(pos, waypoint);
		lastWaypoint = waypoints.get(waypoints.size()-1);
		distance = distanceBetween(0, waypoints.size()-1);
	}

	public Waypoint addWaypoint(String name, double lat, double lon)
	{
		Waypoint waypoint = new Waypoint(name, "", lat, lon);
		addWaypoint(waypoint);
		return waypoint;
	}

	public void insertWaypoint(Waypoint waypoint)
	{
		if (waypoints.size() < 2)
		{
			addWaypoint(waypoint);
			return;
		}
		int after = waypoints.size() - 1;
		double xtk = Double.MAX_VALUE;
		synchronized (waypoints)
		{
			for (int i = 0; i < waypoints.size()-1; i++)
			{
				double distance = Geo.distance(waypoint.latitude, waypoint.longitude, waypoints.get(i+1).latitude, waypoints.get(i+1).longitude);
				double bearing1 = Geo.bearing(waypoint.latitude, waypoint.longitude, waypoints.get(i+1).latitude, waypoints.get(i+1).longitude);
				double dtk1 = Geo.bearing(waypoints.get(i).latitude, waypoints.get(i).longitude, waypoints.get(i+1).latitude, waypoints.get(i+1).longitude);
				double cxtk1 = Math.abs(Geo.xtk(distance, dtk1, bearing1));
				double bearing2 = Geo.bearing(waypoint.latitude, waypoint.longitude, waypoints.get(i).latitude, waypoints.get(i).longitude);
				double dtk2 = Geo.bearing(waypoints.get(i+1).latitude, waypoints.get(i+1).longitude, waypoints.get(i).latitude, waypoints.get(i).longitude);
				double cxtk2 = Math.abs(Geo.xtk(distance, dtk2, bearing2));
				
				if (cxtk2 != Double.POSITIVE_INFINITY && cxtk1 < xtk)
				{
					xtk = cxtk1;
					after = i;
				}
			}
		}
		waypoints.add(after+1, waypoint);
		lastWaypoint = waypoints.get(waypoints.size()-1);
		distance = distanceBetween(0, waypoints.size()-1);
	}

	public Waypoint insertWaypoint(String name, double lat, double lon)
	{
		Waypoint waypoint = new Waypoint(name, "", lat, lon);
		insertWaypoint(waypoint);
		return waypoint;
	}
	
	public void insertWaypoint(int after, Waypoint waypoint)
	{
		waypoints.add(after+1, waypoint);
		lastWaypoint = waypoints.get(waypoints.size()-1);
		distance = distanceBetween(0, waypoints.size()-1);
	}

	public Waypoint insertWaypoint(int after, String name, double lat, double lon)
	{
		Waypoint waypoint = new Waypoint(name, "", lat, lon);
		insertWaypoint(after, waypoint);
		return waypoint;
	}

	public void removeWaypoint(Waypoint waypoint)
	{
		waypoints.remove(waypoint);
		if (waypoints.size() > 0)
		{
			lastWaypoint = waypoints.get(waypoints.size()-1);
			distance = distanceBetween(0, waypoints.size()-1);
		}
	}
	
	public Waypoint getWaypoint(int index) throws IndexOutOfBoundsException
	{
		return waypoints.get(index);
	}
	
	public int length()
	{
		return waypoints.size();
	}

	public void clear()
	{
		synchronized (waypoints)
		{
			waypoints.clear();
		}
		lastWaypoint = null;
		distance = 0;
	}

	public double distanceBetween(int first, int last)
	{
		double dist = 0.0;
		synchronized (waypoints)
		{
			for (int i = first; i < last; i++)
			{
				dist += Geo.distance(waypoints.get(i).latitude, waypoints.get(i).longitude, waypoints.get(i+1).latitude, waypoints.get(i+1).longitude);
			}
		}
		return dist;
	}

	public double course(int prev, int next)
	{
		synchronized (waypoints)
		{
			return Geo.bearing(waypoints.get(prev).latitude, waypoints.get(prev).longitude, waypoints.get(next).latitude, waypoints.get(next).longitude);
		}
	}
}
