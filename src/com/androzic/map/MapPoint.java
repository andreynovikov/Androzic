package com.androzic.map;

import java.io.Serializable;

public class MapPoint implements Serializable
{
	private static final long serialVersionUID = 1L;

	int		x;
	int		y;

	double	lat;
	double	lon;
	
	int zone;
	double	n;
	double	e;
	int hemisphere;

	public MapPoint()
	{
	}

	public MapPoint(MapPoint mp)
	{
		this.x = mp.x;
		this.y = mp.y;
		this.zone = mp.zone;
		this.n = mp.n;
		this.e = mp.e;
		this.hemisphere = mp.hemisphere;
		this.lat = mp.lat;
		this.lon = mp.lon;
	}
}
