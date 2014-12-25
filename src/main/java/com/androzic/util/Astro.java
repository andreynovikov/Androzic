package com.androzic.util;

/*
 * Copyright 2008-2009 Mike Reedell / LuckyCatLabs.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * 2010 Full redesign by Andrey Novikov to make it ten times faster and easier to use
 */

import java.util.Calendar;

import android.location.Location;

public class Astro
{
	/**
	 * Enumerated type that defines the available zeniths for computing the sunrise/sunset.
	 */
	public enum Zenith
	{
	    /** Astronomical sunrise/set is when the sun is 18 degrees below the horizon. */
	    ASTRONOMICAL(108d),

	    /** Nautical sunrise/set is when the sun is 12 degrees below the horizon. */
	    NAUTICAL(102d),

	    /** Civil sunrise/set (dawn/dusk) is when the sun is 6 degrees below the horizon. */
	    CIVIL(96d),

	    /** Official sunrise/set is when the sun is 50' below the horizon. */
	    OFFICIAL(90.8333d); // 90deg, 50'

	    private final double degrees;

	    private Zenith(double degrees)
	    {
	        this.degrees = degrees;
	    }

	    public double degrees()
	    {
	        return degrees;
	    }
	}

    /**
     * Checks if it is daytime for a time contained in <code>date</code> at the given location and date.
     * 
     * @param solarZenith <code>Zenith</code> enum corresponding to the type of sunrise/sunset to compute.
     * @param location <code>Location</code> object holding the location to check condition for. 
     * @param date <code>Calendar</code> object representing the date to check condition for.
     * 			   Date must have timezone set.
     * @return <code>true</code> if sunrise is before and sunset is after specified time.
     */
	public static boolean isDaytime(Zenith solarZenith, Location location, Calendar date)
	{
		double sunrise = computeSunriseTime(solarZenith, location, date);
		double sunset = computeSunsetTime(solarZenith, location, date);
		double now = (double) date.get(Calendar.HOUR_OF_DAY) + (double) date.get(Calendar.MINUTE) / 60;
		if (! Double.isNaN(sunrise) && ! Double.isNaN(sunset))
		{
			if (now < sunrise || now > sunset)
				return false;
		}
		return true;
	}

    /**
     * Computes the sunrise time for the given zenith at the given location and date.
     * 
     * @param solarZenith <code>Zenith</code> enum corresponding to the type of sunrise to compute.
     * @param location <code>Location</code> object holding the location to compute the sunrise for. 
     * @param date <code>Calendar</code> object representing the date to compute the sunrise for.
     * 			   Date must have timezone set.
     * @return the sunrise time, in hours, <code>NaN</code> if the sun does not rise on the given date.
     */
    public static double computeSunriseTime(Zenith solarZenith, Location location, Calendar date)
    {
        return computeSolarEventTime(solarZenith, location, date, true);
    }

    /**
     * Computes the sunset time for the given zenith at the given location and date.
     * 
     * @param solarZenith <code>Zenith</code> enum corresponding to the type of sunset to compute.
     * @param location <code>Location</code> object holding the location to compute the sunset for. 
     * @param date <code>Calendar</code> object representing the date to compute the sunset for.
     * 			   Date must have timezone set.
     * @return the sunset time, in hours, <code>NaN</code> if the sun does not rise on the given date.
     */
    public static double computeSunsetTime(Zenith solarZenith, Location location, Calendar date)
    {
        return computeSolarEventTime(solarZenith, location, date, false);
    }

    private static double computeSolarEventTime(Zenith solarZenith, Location location, Calendar date, boolean isSunrise)
    {
        double longitudeHour = getLongitudeHour(location, date, isSunrise);

        double meanAnomaly = getMeanAnomaly(longitudeHour);
        double sunTrueLong = getSunTrueLongitude(meanAnomaly);
        double cosineSunLocalHour = getCosineSunLocalHour(sunTrueLong, solarZenith, location);
        if ((cosineSunLocalHour < -1.0) || (cosineSunLocalHour > 1.0))
        {
            return Double.NaN;
        }

        double sunLocalHour = getSunLocalHour(cosineSunLocalHour, isSunrise);
        double localMeanTime = getLocalMeanTime(sunTrueLong, longitudeHour, sunLocalHour);
        double localTime = getLocalTime(localMeanTime, location, date);
        return localTime;
    }

    /**
     * Computes the base longitude hour, lngHour in the algorithm.
     * 
     * @return the longitude of the location of the solar event divided by 15 (deg/hour).
     */
    private static double getBaseLongitudeHour(Location location)
    {
        return location.getLongitude() / 15;
    }

    /**
     * Computes the longitude time, t in the algorithm.
     * 
     * @return longitudinal time.
     */
    private static double getLongitudeHour(Location location, Calendar date, Boolean isSunrise)
    {
        int offset = isSunrise ? 6 : 18;
        return getDayOfYear(date) + (offset - getBaseLongitudeHour(location)) / 24;
    }

    /**
     * Computes the mean anomaly of the Sun, M in the algorithm.
     * 
     * @return the suns mean anomaly, M.
     */
    private static double getMeanAnomaly(double longitudeHour)
    {
        return 0.9856 * longitudeHour - 3.289;
    }

    /**
     * Computes the true longitude of the sun, L in the algorithm, at the given location, adjusted to fit in
     * the range [0-360].
     * 
     * @param meanAnomaly the suns mean anomaly.
     * @return the suns true longitude.
     */
    private static double getSunTrueLongitude(double meanAnomaly)
    {
        double sinMeanAnomaly = Math.sin(Math.toRadians(meanAnomaly));
        double sinDoubleMeanAnomaly = Math.sin(Math.toRadians(meanAnomaly) * 2);

        double firstPart = meanAnomaly + (sinMeanAnomaly * 1.916);
        double secondPart = sinDoubleMeanAnomaly * 0.02 + 282.634;
        double trueLongitude = firstPart + secondPart;

        if (trueLongitude > 360) {
            trueLongitude -= 360;
        }
        return trueLongitude;
    }

    /**
     * Computes the suns right ascension, RA in the algorithm, adjusting for the quadrant of L and turning it
     * into degree-hours. Will be in the range [0,360].
     * 
     * @param sunTrueLong Suns true longitude
     * @return suns right ascension in degree-hours.
     */
    private static double getRightAscension(double sunTrueLong)
    {
        double tanL = Math.tan(Math.toRadians(sunTrueLong));

        double innerParens = Math.toDegrees(tanL) * 0.91764;
        double rightAscension = Math.atan(Math.toRadians(innerParens));
        rightAscension = Math.toDegrees(rightAscension);

        if (rightAscension < 0)
        {
            rightAscension += 360;
        }
        else if (rightAscension > 360)
        {
            rightAscension -= 360;
        }

        double longitudeQuadrant = Math.floor(sunTrueLong / 90) * 90;
        double rightAscensionQuadrant = Math.floor(rightAscension / 90) * 90;

        return (rightAscension + longitudeQuadrant - rightAscensionQuadrant) / 15;
    }

    private static double getCosineSunLocalHour(double sunTrueLong, Zenith zenith, Location location)
    {
        double sinSunDeclination = getSinOfSunDeclination(sunTrueLong);
        double cosineSunDeclination = getCosineOfSunDeclination(sinSunDeclination);

        double cosineZenith = Math.cos(Math.toRadians(zenith.degrees()));
        double sinLatitude = Math.sin(Math.toRadians(location.getLatitude()));
        double cosLatitude = Math.cos(Math.toRadians(location.getLatitude()));

        double sinDeclinationTimesSinLat = sinSunDeclination * sinLatitude;
        return (cosineZenith - sinDeclinationTimesSinLat) / (cosineSunDeclination * cosLatitude);
    }

    private static double getSinOfSunDeclination(double sunTrueLong)
    {
        return Math.sin(Math.toRadians(sunTrueLong)) * 0.39782;
    }

    private static double getCosineOfSunDeclination(double sinSunDeclination)
    {
        return Math.cos(Math.asin(sinSunDeclination));
    }

    private static double getSunLocalHour(double cosineSunLocalHour, Boolean isSunrise)
    {
        double localHour = Math.toDegrees(Math.acos(cosineSunLocalHour));
        if (isSunrise)
        {
            localHour = 360 - localHour;
        }
        return localHour / 15;
    }

    private static double getLocalMeanTime(double sunTrueLong, double longitudeHour, double sunLocalHour)
    {
        double localMeanTime = sunLocalHour + getRightAscension(sunTrueLong) - longitudeHour * 0.06571 - 6.622;
        if (localMeanTime < 0)
        {
            localMeanTime += 24;
        }
        else if (localMeanTime > 24)
        {
            localMeanTime -= 24;
        }
        return localMeanTime;
    }

    private static double getLocalTime(double localMeanTime, Location location, Calendar date)
    {
        double utcTime = localMeanTime - getBaseLongitudeHour(location);
        double utcOffSet = getUTCOffSet(date);
        double utcOffSetTime = utcTime + utcOffSet;
        return adjustForDST(utcOffSetTime, date);
    }

    private static double adjustForDST(double localMeanTime, Calendar date)
    {
        double localTime = localMeanTime;
        if (date.getTimeZone().inDaylightTime(date.getTime()))
        {
            localTime++;
        }
        if (localTime > 24.0)
        {
            localTime -= 24;
        }
        return localTime;
    }

    /**
     * Returns the local rise/set time in the HH:MM form.
     * 
     * @param localTime <code>double</code> representation of the local rise/set time.
     * @return <code>String</code> representation of the local rise/set time in HH:MM format.
     */
    public static String getLocalTimeAsString(double localTime)
    {
        int hour = (int) Math.floor(localTime);
        int minutes = (int) Math.round((localTime - hour) * 60);

        if (minutes == 60)
        {
            minutes = 0;
            hour++;
        }

        String minuteString = minutes < 10 ? "0" + String.valueOf(minutes) : String.valueOf(minutes);
        String hourString = (hour < 10) ? "0" + String.valueOf(hour) : String.valueOf(hour);
        return hourString + ":" + minuteString;
    }

    private static int getDayOfYear(Calendar date)
    {
        return date.get(Calendar.DAY_OF_YEAR);
    }

    private static double getUTCOffSet(Calendar date)
    {
        return date.get(Calendar.ZONE_OFFSET) / 3600000;
    }

}
