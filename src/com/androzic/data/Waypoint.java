package com.androzic.data;

import java.util.Date;

public class Waypoint
{
	public String name = "";
	public String description = "";
	public double latitude = 0;
	public double longitude = 0;
	public boolean silent = false;
	public int proximity = 0;
	public String image = "";
	public boolean drawImage = false;
	public WaypointSet set = null;
	public int textcolor = Integer.MIN_VALUE;
	public int backcolor = Integer.MIN_VALUE;
	public Date date;

	public Waypoint()
	{
	}

	public Waypoint(double lat, double lon)
	{
		latitude = lat;
		longitude = lon;
	}

	public Waypoint(String aName, String aDescription, double lat, double lon)
	{
		name = aName;
		description = aDescription;
		latitude = lat;
		longitude = lon;
	}
}
