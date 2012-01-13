package com.androzic.data;

import java.util.ArrayList;
import java.util.List;

import com.androzic.util.Geo;

public class Track
{
	public String name;
	public String description;
	public boolean show;
	public int color = -1;
	public int width;
	
	public long maxPoints = 0;
	public double distance;
	public String filepath;
	public boolean removed = false;
	public boolean editing = false;
	public int editingPos = -1;

	private final List<TrackPoint> trackpoints = new ArrayList<TrackPoint>(0);
	private TrackPoint lastTrackPoint;

	public class TrackPoint
	{
		public boolean continous;
		public double latitude;
		public double longitude;
		public double elevation;
		public double speed;
		public long time;

		public TrackPoint()
		{
			continous = false;
			latitude = 0;
			longitude = 0;
			elevation = 0;
			speed = 0;
			time = 0;
		}

		public TrackPoint(boolean cont, double lat, double lon, double elev, double spd, long t)
		{
			continous = cont;
			latitude = lat;
			longitude = lon;
			elevation = elev;
			speed = spd;
			time = t;
		}
	};
	
	public Track()
	{
		this("", "", false);
	}

	public Track(String pname, String pdescr, boolean pshow)
	{
		name = pname;
		description = pdescr;
		show = pshow;
		distance = 0;
	}

	public Track(String pname, String pdescr, boolean pshow, long max)
	{
		this(pname, pdescr, pshow);
		maxPoints = max;
	}

	public List<TrackPoint> getPoints()
	{
		return trackpoints;
	}

	public void addTrackPoint(boolean continous, double lat, double lon, double elev, double speed, long time)
	{
		if (lastTrackPoint != null)
		{
			distance += Geo.distance(lastTrackPoint.latitude, lastTrackPoint.longitude, lat, lon);			
		}
		lastTrackPoint = new TrackPoint(continous, lat, lon, elev, speed, time);
		synchronized (trackpoints)
		{
			if (maxPoints > 0 && trackpoints.size() > maxPoints)
			{
				// TODO add correct cleaning if preferences changed
				distance -= Geo.distance(trackpoints.get(0).latitude, trackpoints.get(0).longitude, trackpoints.get(1).latitude, trackpoints.get(1).longitude);
				trackpoints.remove(0);
			}
			trackpoints.add(lastTrackPoint);
		}
	}

	public void clear()
	{
		synchronized (trackpoints)
		{
			trackpoints.clear();
		}
		lastTrackPoint = null;
		distance = 0;
	}

	public TrackPoint getPoint(int location) throws IndexOutOfBoundsException
	{
		return trackpoints.get(location);
	}

	public TrackPoint getLastPoint()
	{
		return lastTrackPoint;
	}

	public void cutAfter(int location)
	{
		List<TrackPoint> tps = new ArrayList<TrackPoint>(trackpoints.subList(0, location+1));
		trackpoints.clear();
		trackpoints.addAll(tps);
	}

	public void cutBefore(int location)
	{
		List<TrackPoint> tps = new ArrayList<TrackPoint>(trackpoints.subList(location, trackpoints.size()));
		trackpoints.clear();
		trackpoints.addAll(tps);
	}

}
