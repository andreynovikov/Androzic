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

	public WaypointSet(String path, String name)
	{
		this.path = path;
		this.name = name;
	}
}
