package com.androzic;

import com.androzic.data.Route;
import com.androzic.data.Waypoint;
import com.androzic.map.OnMapTileStateChangeListener;

public interface MapHolder extends OnMapTileStateChangeListener
{
	void setFollowing(boolean follow);
	void zoomMap(float factor);

	/**
	 * Called when current map have changed
	 * @param forced True if new map was selected by user
	 */
	void mapChanged(boolean forced);
	/**
	 * Called when location, zoom or other map conditions that need map redraw have changed
	 */
	void conditionsChanged();
	/**
	 * Call to force map refresh
	 */
	void refreshMap();
	void updateCoordinates(double[] latlon);
	void updateFileInfo();

	void mapTapped();
	boolean waypointTapped(Waypoint waypoint, int x, int y);
	boolean routeWaypointTapped(Route route, int index, int x, int y);
	boolean mapObjectTapped(long id, int x, int y);
}
