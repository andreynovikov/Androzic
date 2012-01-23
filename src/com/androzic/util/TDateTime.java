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

package com.androzic.util;

/**
 * Converts time in different formats to Delphi (Pascal) <code>TDateTime</code> and vice versa.
 * Take in mind that <code>TDateTime</code> is internaly represented as <code>double</code>.
 * 
 * @author Andrey Novikov
 */
public class TDateTime
{
	/*
	 * Delphi stores date and time values in the TDateTime type. The integral
	 * part of a TDateTime value is the number of days that have passed since
	 * 12/30/1899. The fractional part of a TDateTime value is the time of day.
	 * Following are some examples of TDateTime values and their corresponding
	 * dates and times:
	 * 0 - 12/30/1899 0:00
	 * 2.75 - 1/1/1900 18:00
	 * -1.25 - 12/29/1899 6:00
	 * 35065 - 1/1/1996 0:00
	 */

	/**
	 * Converts conventional Unix milliseconds to TDateTime format
	 * 
	 * @param time milliseconds since January 1, 1970
	 * @return time in TDateTime format
	 */
	public static double toDateTime(final long time)
	{
		/*
		 * 25569.5 = 2.0 + 70*365 + 70 / 4
		 * 2.0		- 1/1/1900 0:00
		 * 70*365	- days in 70 years
		 * 70 / 4	- extra days in leap years
		 * 
		 * WARNING:
		 * My test has shown that 0.5 is excessive. I do not know if the test is correct or not,
		 * but for my task I had to remove it. So take care.
		 */
		return 25569 + time / 86400000.0; // 24*60*60*1000
	}

	/**
	 * Converts TDateTime time to conventional Unix milliseconds
	 * 
	 * @param time in TDateTime format
	 * @return time milliseconds since January 1, 1970
	 */
	public static long fromDateTime(double time)
	{
		return Math.round((time - 25569) * 86400000); // 24*60*60*1000
	}

}
