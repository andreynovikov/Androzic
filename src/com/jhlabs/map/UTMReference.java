package com.jhlabs.map;

import java.text.DecimalFormat;

/**
 * This class is part of the Jcoord package. Visit the <a
 * href="http://www.jstott.me.uk/jcoord/">Jcoord</a> website for more
 * information.
 * 
 * (c) 2006 Jonathan Stott
 */

public class UTMReference
{
	private double easting;
	private double northing;
	private char latZone;
	private int lngZone;

	public UTMReference(int lngZone, char latZone, double easting, double northing) throws ReferenceException
	{

		if (lngZone < 1 || lngZone > 60)
		{
			throw new ReferenceException("Longitude zone (" + lngZone + ") is not defined on the UTM grid.");
		}

		if (latZone < 'C' || latZone > 'X')
		{
			throw new ReferenceException("Latitude zone (" + latZone + ") is not defined on the UTM grid.");
		}

		if (easting < 0.0 || easting > 1000000.0)
		{
			throw new ReferenceException("Easting (" + easting + ") is not defined on the UTM grid.");
		}

		if (northing < 0.0 || northing > 10000000.0)
		{
			throw new ReferenceException("Northing (" + northing + ") is not defined on the UTM grid.");
		}

		this.easting = easting;
		this.northing = northing;
		this.latZone = latZone;
		this.lngZone = lngZone;
	}

	public GeodeticPosition toLatLng()
	{
		double UTM_F0 = 0.9996;
		double a = Ellipsoid.WGS_1984.equatorRadius;
		double eSquared = Ellipsoid.WGS_1984.eccentricity2;
		double ePrimeSquared = eSquared / (1.0 - eSquared);
		double e1 = (1 - Math.sqrt(1 - eSquared)) / (1 + Math.sqrt(1 - eSquared));
		double x = easting - 500000.0;;
		double y = northing;
		int zoneNumber = lngZone;
		char zoneLetter = latZone;

		double longitudeOrigin = (zoneNumber - 1.0) * 6.0 - 180.0 + 3.0;

		// Correct y for southern hemisphere
		if ((zoneLetter - 'N') < 0)
		{
			y -= 10000000.0;
		}

		double m = y / UTM_F0;
		double mu = m / (a * (1.0 - eSquared / 4.0 - 3.0 * eSquared * eSquared / 64.0 - 5.0 * Math.pow(eSquared, 3.0) / 256.0));

		double phi1Rad = mu + (3.0 * e1 / 2.0 - 27.0 * Math.pow(e1, 3.0) / 32.0) * Math.sin(2.0 * mu)
				+ (21.0 * e1 * e1 / 16.0 - 55.0 * Math.pow(e1, 4.0) / 32.0) * Math.sin(4.0 * mu) + (151.0 * Math.pow(e1, 3.0) / 96.0)
				* Math.sin(6.0 * mu);

		double n = a / Math.sqrt(1.0 - eSquared * Math.sin(phi1Rad) * Math.sin(phi1Rad));
		double t = Math.tan(phi1Rad) * Math.tan(phi1Rad);
		double c = ePrimeSquared * Math.cos(phi1Rad) * Math.cos(phi1Rad);
		double r = a * (1.0 - eSquared) / Math.pow(1.0 - eSquared * Math.sin(phi1Rad) * Math.sin(phi1Rad), 1.5);
		double d = x / (n * UTM_F0);

		double latitude = (phi1Rad - (n * Math.tan(phi1Rad) / r)
				* (d * d / 2.0 - (5.0 + (3.0 * t) + (10.0 * c) - (4.0 * c * c) - (9.0 * ePrimeSquared)) * Math.pow(d, 4.0) / 24.0 + (61.0
						+ (90.0 * t) + (298.0 * c) + (45.0 * t * t) - (252.0 * ePrimeSquared) - (3.0 * c * c))
						* Math.pow(d, 6.0) / 720.0))
				* (180.0 / Math.PI);

		double longitude = longitudeOrigin
				+ ((d - (1.0 + 2.0 * t + c) * Math.pow(d, 3.0) / 6.0 + (5.0 - (2.0 * c) + (28.0 * t) - (3.0 * c * c)
						+ (8.0 * ePrimeSquared) + (24.0 * t * t))
						* Math.pow(d, 5.0) / 120.0) / Math.cos(phi1Rad)) * (180.0 / Math.PI);

		return new GeodeticPosition(latitude, longitude);
	}

	public static UTMReference toUTMRef(GeodeticPosition pos) throws ReferenceException
	{

		double longitude = pos.lon;
		double latitude = pos.lat;

		if (latitude < -80 || latitude > 84)
		{
			throw new ReferenceException("Latitude (" + latitude + ") falls outside the UTM grid.");
		}

		if (longitude == 180.0)
		{
			longitude = -180.0;
		}

		double UTM_F0 = 0.9996;
		double a = Ellipsoid.WGS_1984.equatorRadius;
		double eSquared = Ellipsoid.WGS_1984.eccentricity2;

		double latitudeRad = latitude * (Math.PI / 180.0);
		double longitudeRad = longitude * (Math.PI / 180.0);
		int longitudeZone = (int) Math.floor((longitude + 180.0) / 6.0) + 1;

		// Special zone for Norway
		if (latitude >= 56.0 && latitude < 64.0 && longitude >= 3.0 && longitude < 12.0)
		{
			longitudeZone = 32;
		}

		// Special zones for Svalbard
		if (latitude >= 72.0 && latitude < 84.0)
		{
			if (longitude >= 0.0 && longitude < 9.0)
			{
				longitudeZone = 31;
			}
			else if (longitude >= 9.0 && longitude < 21.0)
			{
				longitudeZone = 33;
			}
			else if (longitude >= 21.0 && longitude < 33.0)
			{
				longitudeZone = 35;
			}
			else if (longitude >= 33.0 && longitude < 42.0)
			{
				longitudeZone = 37;
			}
		}

		double longitudeOrigin = (longitudeZone - 1) * 6 - 180 + 3;
		double longitudeOriginRad = longitudeOrigin * (Math.PI / 180.0);

		char UTMZone = UTMReference.getUTMLatitudeZoneLetter(latitude);

		double ePrimeSquared = (eSquared) / (1 - eSquared);

		double n = a / Math.sqrt(1 - eSquared * Math.sin(latitudeRad) * Math.sin(latitudeRad));
		double t = Math.tan(latitudeRad) * Math.tan(latitudeRad);
		double c = ePrimeSquared * Math.cos(latitudeRad) * Math.cos(latitudeRad);
		double A = Math.cos(latitudeRad) * (longitudeRad - longitudeOriginRad);

		double M = a
				* ((1 - eSquared / 4 - 3 * eSquared * eSquared / 64 - 5 * eSquared * eSquared * eSquared / 256) * latitudeRad
						- (3 * eSquared / 8 + 3 * eSquared * eSquared / 32 + 45 * eSquared * eSquared * eSquared / 1024)
						* Math.sin(2 * latitudeRad) + (15 * eSquared * eSquared / 256 + 45 * eSquared * eSquared * eSquared / 1024)
						* Math.sin(4 * latitudeRad) - (35 * eSquared * eSquared * eSquared / 3072) * Math.sin(6 * latitudeRad));

		double UTMEasting = (UTM_F0
				* n
				* (A + (1 - t + c) * Math.pow(A, 3.0) / 6 + (5 - 18 * t + t * t + 72 * c - 58 * ePrimeSquared) * Math.pow(A, 5.0)
						/ 120) + 500000.0);

		double UTMNorthing = (UTM_F0 * (M + n
				* Math.tan(latitudeRad)
				* (A * A / 2 + (5 - t + (9 * c) + (4 * c * c)) * Math.pow(A, 4.0) / 24 + (61 - (58 * t) + (t * t) + (600 * c) - (330 * ePrimeSquared))
						* Math.pow(A, 6.0) / 720)));

		// Adjust for the southern hemisphere
		if (latitude < 0)
		{
			UTMNorthing += 10000000.0;
		}

		return new UTMReference(longitudeZone, UTMZone, UTMEasting, UTMNorthing);
	}

	public static String toUTMRefString(GeodeticPosition geodeticPosition) throws ReferenceException
	{
		return toUTMRef(geodeticPosition).toString();
	}

	/**
	 * Work out the UTM latitude zone from the latitude.
	 * 
	 * @param latitude
	 *            the latitude to find the UTM latitude zone for
	 * @return the UTM latitude zone for the given latitude
	 * @since 1.0
	 */
	public static char getUTMLatitudeZoneLetter(double latitude)
	{
		if ((84 >= latitude) && (latitude >= 72))
			return 'X';
		else if ((72 > latitude) && (latitude >= 64))
			return 'W';
		else if ((64 > latitude) && (latitude >= 56))
			return 'V';
		else if ((56 > latitude) && (latitude >= 48))
			return 'U';
		else if ((48 > latitude) && (latitude >= 40))
			return 'T';
		else if ((40 > latitude) && (latitude >= 32))
			return 'S';
		else if ((32 > latitude) && (latitude >= 24))
			return 'R';
		else if ((24 > latitude) && (latitude >= 16))
			return 'Q';
		else if ((16 > latitude) && (latitude >= 8))
			return 'P';
		else if ((8 > latitude) && (latitude >= 0))
			return 'N';
		else if ((0 > latitude) && (latitude >= -8))
			return 'M';
		else if ((-8 > latitude) && (latitude >= -16))
			return 'L';
		else if ((-16 > latitude) && (latitude >= -24))
			return 'K';
		else if ((-24 > latitude) && (latitude >= -32))
			return 'J';
		else if ((-32 > latitude) && (latitude >= -40))
			return 'H';
		else if ((-40 > latitude) && (latitude >= -48))
			return 'G';
		else if ((-48 > latitude) && (latitude >= -56))
			return 'F';
		else if ((-56 > latitude) && (latitude >= -64))
			return 'E';
		else if ((-64 > latitude) && (latitude >= -72))
			return 'D';
		else if ((-72 > latitude) && (latitude >= -80))
			return 'C';
		else
			return 'Z';
	}

	
	public static char getUTMNorthingZoneLetter(boolean shemi, double northing)
	{
		if (shemi)
		{
			if ((10000000.0 > northing) && (northing >= 9100000.0))
				return 'M';
			else if ((9100000.0 > northing) && (northing >= 8200000.0))
				return 'L';
			else if ((8200000.0 > northing) && (northing >= 7300000.0))
				return 'K';
			else if ((7300000.0 > northing) && (northing >= 6400000.0))
				return 'J';
			else if ((6400000.0 > northing) && (northing >= 5500000.0))
				return 'H';
			else if ((5500000.0 > northing) && (northing >= 4600000.0))
				return 'G';
			else if ((4600000.0 > northing) && (northing >= 3700000.0))
				return 'F';
			else if ((3700000.0 > northing) && (northing >= 2800000.0))
				return 'E';
			else if ((2800000.0 > northing) && (northing >= 2000000.0))
				return 'D';
			else if ((2000000.0 > northing) && (northing >= 1100000.0))
				return 'C';
			else
				return 'B';			
		}
		else
		{
			//FIXME Calculate correct limit instead of 9000000.0
			if ((9000000.0 >= northing) && (northing >= 7900000.0))
				return 'X';
			else if ((7900000.0 > northing) && (northing >= 7000000.0))
				return 'W';
			else if ((7000000.0 > northing) && (northing >= 6200000.0))
				return 'V';
			else if ((6200000.0 > northing) && (northing >= 5300000.0))
				return 'U';
			else if ((5300000.0 > northing) && (northing >= 4400000.0))
				return 'T';
			else if ((4400000.0 > northing) && (northing >= 3500000.0))
				return 'S';
			else if ((3500000.0 > northing) && (northing >=2600000.0))
				return 'R';
			else if ((2600000.0 > northing) && (northing >=1700000.0))
				return 'Q';
			else if ((1700000.0 > northing) && (northing >= 800000.0))
				return 'P';
			else if ((800000.0 > northing) && (northing >= 0))
				return 'N';
			else
				return 'Z';
		}
	}
	
	final private static DecimalFormat df = new DecimalFormat("00");

	/**
	 * Convert this UTM reference to a String representation for printing out.
	 * 
	 * @return a String representation of this UTM reference
	 * @since 1.0
	 */
	public String toString()
	{
		return lngZone + Character.toString(latZone) + " " + df.format(easting) + " " + df.format(northing);
	}

	/**
	 * Get the easting.
	 * 
	 * @return the easting
	 * @since 1.0
	 */
	public double getEasting()
	{
		return easting;
	}

	/**
	 * Get the northing.
	 * 
	 * @return the northing
	 * @since 1.0
	 */
	public double getNorthing()
	{
		return northing;
	}

	/**
	 * Get the latitude zone character.
	 * 
	 * @return the latitude zone character
	 * @since 1.0
	 */
	public char getLatZone()
	{
		return latZone;
	}

	/**
	 * Get the longitude zone number.
	 * 
	 * @return the longitude zone number
	 * @since 1.0
	 */
	public int getLngZone()
	{
		return lngZone;
	}
	
	public boolean isSouthernHemisphere()
	{
		return ((latZone - 'N') < 0);
	}
}
