package com.jhlabs.map.proj;

public class UniversalTransverseMercatorProjection extends TransverseMercatorProjection
{
    public void initialize() {
    	// TODO
//    	if (!P->es) E_ERROR(-34);
    	if (utmzone < 0)
    	{
    		int zone = (int) getZoneFromNearestMeridian(projectionLongitude*RTD);
    		setUTMZone(zone);
    	}
        super.initialize();
    }

    public void setIsSouth(boolean south) {
    	falseNorthing = south ? 10000000. : 0.;
    }
    
    public String toString() {
        return "Universal Transverse Mercator";
    }
}
