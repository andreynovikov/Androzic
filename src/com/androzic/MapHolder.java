package com.androzic;

import com.androzic.data.Waypoint;

public interface MapHolder
{
	MapView getMapView();
	void setFollowing(boolean follow);
	void zoomMap(float factor);
	void zoomIn();
	void zoomOut();
	void previousMap();
	void nextMap();
	void updateCoordinates(double[] latlon);
	void updateFileInfo();
	
	boolean waypointTapped(Waypoint waypoint, int x, int y);
	boolean routeWaypointTapped(int route, int index, int x, int y);
	boolean mapObjectTapped(long id, int x, int y);
}
