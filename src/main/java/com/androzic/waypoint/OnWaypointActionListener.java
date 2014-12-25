/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2012 Andrey Novikov <http://andreynovikov.info/>
 * 
 * This file is part of Androzic application.
 * 
 * Androzic is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Androzic is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Androzic. If not, see <http://www.gnu.org/licenses/>.
 */

package com.androzic.waypoint;

import com.androzic.data.Waypoint;

public interface OnWaypointActionListener
{
	/**
	 * Position map so that waypoint is visible
	 */
	void onWaypointView(Waypoint waypoint);
	/**
	 * Open waypoint information dialog
	 */
	void onWaypointShow(Waypoint waypoint);
	void onWaypointDetails(Waypoint waypoint);
	void onWaypointNavigate(Waypoint waypoint);
	void onWaypointEdit(Waypoint waypoint);
	void onWaypointShare(Waypoint waypoint);
	void onWaypointRemove(Waypoint waypoint);
}
