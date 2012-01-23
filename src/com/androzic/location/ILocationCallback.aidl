/*
 * Androzic - android navigation client that uses OziExplorer maps (ozf2, ozfx3).
 * Copyright (C) 2010-2012  Andrey Novikov <http://andreynovikov.info/>
 *
 * This file is part of Androzic application.
 *
 * Androzic is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * Androzic is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with Androzic.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.androzic.location;

import android.location.Location;

oneway interface ILocationCallback
{
	void onLocationChanged(in Location loc, boolean continous, float smoothspeed, float avgspeed);
	void onSensorChanged(float azimuth, float pitch, float roll);
	void onProviderChanged(String provider);
	void onProviderDisabled(String provider);
	void onProviderEnabled(String provider);
	void onGpsStatusChanged(String provider, int status, int fsats, int tsats);
}
