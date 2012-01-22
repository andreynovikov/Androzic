package com.androzic.util;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/**
 * Converts time in different formats to Delphi (Pascal) <code>TDateTime</code> and vice versa.
 * Take in mind that <code>TDateTime</code> is internally represented as <code>double</code>:
 * 
 * <p>The integral part of a TDateTime value is the number of days that have passed since
 * 12/30/1899. The fractional part of a TDateTime value is the time of day.
 * Following are some examples of TDateTime values and their corresponding dates and times:
 * <p>0 - 12/30/1899 0:00<br/>
 * 2.75 - 1/1/1900 18:00<br/>
 * -1.25 - 12/29/1899 6:00<br/>
 * 35065 - 1/1/1996 0:00
 *
 * @author Andrey Novikov
 */
public class TDateTime
{
	/*
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
		 * 25569 = 2.0 + 70*365 + 70 / 4
		 * 2.0		- 1/1/1900 0:00
		 * 70*365	- days in 70 years
		 * 70 / 4	- extra days in leap years
		 */
		return 25569 + time / 86400000.0; // 24*60*60*1000
	}

	/**
	 * Converts {@link java.util.Date Date} to TDateTime format
	 * 
	 * @param date time in Date format
	 * @return time in TDateTime format
	 */
	public static double toDateTime(final Date date)
	{
		Calendar zero = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		//12/30/1899 0:00
		zero.set(1899, Calendar.DECEMBER, 30, 0, 0, 0);
		zero.clear(Calendar.MILLISECOND);
		Calendar from = Calendar.getInstance();
		from.setTime(date);

        return (from.getTimeInMillis() - zero.getTimeInMillis()) / 86400000.0;
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

	/**
	 * Converts TDateTime time to {@link java.util.Date Date}
	 * 
	 * @param time in TDateTime format
	 * @return {@link java.util.Date Date}
	 */
	public static Date dateFromDateTime(double time)
	{
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(fromDateTime(time));
		return cal.getTime();
	}
}
