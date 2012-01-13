package com.androzic.data;

public class Situation extends Waypoint
{
	public double speed;
	public double track;

	public Situation()
	{
		super();
		speed = 0;
		track = 0;
	}

	public Situation(String name)
	{
		this();
		this.name = name;
	}
}
