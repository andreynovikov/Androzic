package com.androzic.waypoint;

import com.androzic.data.Waypoint;

public interface OnWaypointActionListener
{
	void onWaypointView(Waypoint waypoint);
	void onWaypointNavigate(Waypoint waypoint);
	void onWaypointEdit(Waypoint waypoint);
	void onWaypointShare(Waypoint waypoint);
	void onWaypointRemove(Waypoint waypoint);
}
