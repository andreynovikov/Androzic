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

package com.androzic.route;

import com.androzic.data.Route;
import com.androzic.data.Waypoint;

public interface OnRouteActionListener
{
	void onRouteDetails(Route route);
	void onRouteNavigate(Route route);
	void onRouteNavigate(Route route, int direction, int waypointIndex);
	void onRouteEdit(Route route);
	void onRouteEditPath(Route route);
	void onRouteSave(Route route);
	void onRouteWaypointEdit(Route route, Waypoint waypoint);
}
