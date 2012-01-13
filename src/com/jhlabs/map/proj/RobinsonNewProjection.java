/*
 * RobinsonProjection.java
 *
 * Created on March 12, 2007, 12:03 AM
 *
 */

package com.jhlabs.map.proj;

import Jama.Matrix;

import com.jhlabs.Point2D;
import com.jhlabs.map.MapMath;

import java.io.Serializable;

/**
 *
 * @author Bernhard Jenny, Institute of Cartography, ETH Zurich
 */
public class RobinsonNewProjection extends Projection implements Serializable {
    
    public final static double ROBINSON_SCALE = 0.8487;
    private static final double HEIGHT_TO_WIDTH = 0.5072;
    private final static double FXC = ROBINSON_SCALE;
    private static final double FYC = ROBINSON_SCALE * HEIGHT_TO_WIDTH * Math.PI;
    
    private static final double[] ROBINSON_PARALLELS_LENGTH = {
        1,
        0.9986,
        0.9954,
        0.99,
        0.9822,
        0.973,
        0.96,
        0.9427,
        0.9216,
        0.8962,
        0.8679,
        0.835,
        0.7986,
        0.7597,
        0.7186,
        0.6732,
        0.6213,
        0.5722,
        0.5322
    };
    
    private static final double[] ROBINSON_PARALLELS_DISTANCE = {
        0,
        0.062,
        0.124,
        0.186,
        0.248,
        0.31,
        0.372,
        0.434,
        0.4958,
        0.5571,
        0.6176,
        0.6769,
        0.7346,
        0.7903,
        0.8435,
        0.8936,
        0.9394,
        0.9761,
        1
    };

    private final static double X[] = {
        1, -5.67239e-12, -7.15511e-05, 3.11028e-06,
        0.9986, -0.000482241, -2.4897e-05, -1.33094e-06,
        0.9954, -0.000831031, -4.4861e-05, -9.86588e-07,
        0.99, -0.00135363, -5.96598e-05, 3.67749e-06,
        0.9822, -0.00167442, -4.4975e-06, -5.72394e-06,
        0.973, -0.00214869, -9.03565e-05, 1.88767e-08,
        0.96, -0.00305084, -9.00732e-05, 1.64869e-06,
        0.9427, -0.00382792, -6.53428e-05, -2.61493e-06,
        0.9216, -0.00467747, -0.000104566, 4.8122e-06,
        0.8962, -0.00536222, -3.23834e-05, -5.43445e-06,
        0.8679, -0.00609364, -0.0001139, 3.32521e-06,
        0.835, -0.00698325, -6.40219e-05, 9.34582e-07,
        0.7986, -0.00755337, -5.00038e-05, 9.35532e-07,
        0.7597, -0.00798325, -3.59716e-05, -2.27604e-06,
        0.7186, -0.00851366, -7.0112e-05, -8.63072e-06,
        0.6732, -0.00986209, -0.000199572, 1.91978e-05,
        0.6213, -0.010418, 8.83948e-05, 6.24031e-06,
        0.5722, -0.00906601, 0.000181999, 6.24033e-06,
        0.5322, 0., 0., 0.
    };

    private final static double Y[] = {
        0, 0.0124, 3.72529e-10, 1.15484e-09,
        0.062, 0.0124001, 1.76951e-08, -5.92321e-09,
        0.124, 0.0123998, -7.09668e-08, 2.25753e-08,
        0.186, 0.0124008, 2.66917e-07, -8.44523e-08,
        0.248, 0.0123971, -9.99682e-07, 3.15569e-07,
        0.31, 0.0124108, 3.73349e-06, -1.1779e-06,
        0.372, 0.0123598, -1.3935e-05, 4.39588e-06,
        0.434, 0.0125501, 5.20034e-05, -1.00051e-05,
        0.4968, 0.0123198, -9.80735e-05, 9.22397e-06,
        0.5571, 0.0120308, 4.02857e-05, -5.2901e-06,
        0.6176, 0.0120369, -3.90662e-05, 7.36117e-07,
        0.6769, 0.0117015, -2.80246e-05, -8.54283e-07,
        0.7346, 0.0113572, -4.08389e-05, -5.18524e-07,
        0.7903, 0.0109099, -4.86169e-05, -1.0718e-06,
        0.8435, 0.0103433, -6.46934e-05, 5.36384e-09,
        0.8936, 0.00969679, -6.46129e-05, -8.54894e-06,
        0.9394, 0.00840949, -0.000192847, -4.21023e-06,
        0.9761, 0.00616525, -0.000256001, -4.21021e-06,
        1., 0., 0., 0
    };
    
    /**
     * Spline coefficients for computing the length of parallels.
     */
    private double[][] lengthSpline;
    
    /**
     * Spline coefficients for computing the distance of parallels from the
     * equator.
     */
    private double[][] distSpline;
    
    /**
     * The distance between parallels for which a reference value is defined.
     */
    private static final double LAT_INC = Math.toRadians(5);
    
    private static final int NODES = 18;
    
    private static final double EPS = 1e-8;

    public static void main(String[] args) {
        RobinsonNewProjection proj = new RobinsonNewProjection();
        System.out.println("length spline");
        for (int i = 0; i < proj.lengthSpline.length; i++) {
            System.out.format("%20.16e\t", proj.lengthSpline[i][0]);
            System.out.format("%20.16e\t", proj.lengthSpline[i][1] / 5);
            System.out.format("%20.16e\t", proj.lengthSpline[i][2] / (5*5));
            System.out.format("%20.16e\t", proj.lengthSpline[i][3] / (5*5*5));

            System.out.println();
        }

        System.out.println();
        System.out.println("length spline difference to proj4");
        int c = 0;
        for (int i = 0; i < proj.lengthSpline.length; i++) {
            System.out.format("%20.16e\t",proj.lengthSpline[i][0] - X[c++]);
            System.out.format("%20.16e\t",proj.lengthSpline[i][1] - X[c++] * 5);
            System.out.format("%20.16e\t",proj.lengthSpline[i][2] - X[c++] * 5 * 5);
            System.out.format("%20.16e\t",proj.lengthSpline[i][3] - X[c++] * 5 * 5 * 5);

            System.out.println();
        }

        System.out.println();
        System.out.println("distance spline");
        for (int i = 0; i < proj.distSpline.length; i++) {
            System.out.format("%20.16e\t", proj.distSpline[i][0]);
            System.out.format("%20.16e\t", proj.distSpline[i][1] / 5);
            System.out.format("%20.16e\t", proj.distSpline[i][2] / (5*5));
            System.out.format("%20.16e\t", proj.distSpline[i][3] / (5*5*5));

            System.out.println();
        }

        System.out.println();
        System.out.println("distance spline difference to proj4");
        c = 0;
        for (int i = 0; i < proj.lengthSpline.length; i++) {
            System.out.format("%20.16e\t",proj.distSpline[i][0] - Y[c++]);
            System.out.format("%20.16e\t",proj.distSpline[i][1] - Y[c++] * 5);
            System.out.format("%20.16e\t",proj.distSpline[i][2] - Y[c++] * 5 * 5);
            System.out.format("%20.16e\t",proj.distSpline[i][3] - Y[c++] * 5 * 5 * 5);

            System.out.println();
        }
    }
    /**
     * Creates a new instance of RobinsonProjection
     */
    public RobinsonNewProjection() {
        updateSplineTables();
    }
    
    public String getName() {
        return "Robinson";
    }
    
    /**
     * Returns true if this projection has an inverse
     */
    public boolean hasInverse() {
        return true;
    }
    
    /**
     */
    public Point2D.Double project(double x, double y, Point2D.Double dst) {
        
        dst.x = FXC * getLongitudeScaleFactor(y) * x;
        dst.y = FYC * getLatitudeScaleFactor(y);
        if (y < 0.0) {
            dst.y = -dst.y;
        }
        return dst;
        
    }
    
    /**
     * Evaluate the cubic spline Y = a+b*t+c*t*t+d*t*t*t for segment i.
     * @param abcd The spline coefficients. Each row contains a, b, c, d.
     * @param i The segment for which to evluate the spline.
     * @t The spline parameter [0..1]
     * @return The value defined by the spline.
     */
    private double poly(double[][] abcd, int i, double t) {
        final double[] abcd_row = abcd[i];
        return abcd_row[0]+t*(abcd_row[1]+t*(abcd_row[2]+t*abcd_row[3]));
    }
    
    /**
     * Returns the scale factor for the longitude computed with a cubic spline
     * interpolation.
     * @param lat The latitude for which the factor is computed in radians.
     * @return The scale factor.
     */
    public double getLongitudeScaleFactor(double lat) {
        final double latAbs = lat < 0. ? -lat : lat;
        final int piecesCount = lengthSpline.length;
        int i = (int)(latAbs / LAT_INC);
        if (i >= piecesCount)
            i = piecesCount-1;
        final double t = latAbs/LAT_INC - i;
        return poly(lengthSpline, i, t);
    }
    
    /**
     * Returns the scale factor for the latitude computed with a cubic spline
     * interpolation.
     * @param lat The latitude for which the factor is computed.
     * @return The scale factor.
     */
    public double getLatitudeScaleFactor(double lat) {
        final double latAbs = lat < 0. ? -lat : lat;
        final int piecesCount = this.distSpline.length;
        int i = (int)(latAbs / LAT_INC);
        if (i >= piecesCount)
            i = piecesCount-1;
        final double t = latAbs/LAT_INC - i;
        return poly(this.distSpline, i, t);
    }
    
    private double getX(int id) {
        return ROBINSON_PARALLELS_LENGTH[id];
    }
    
    private double getY(int id) {
        return ROBINSON_PARALLELS_DISTANCE[id];
    }
    
     public Point2D.Double projectInverse(double x, double y, Point2D.Double lp) {
        
        lp.x = x / FXC;
        lp.y = Math.abs(y / FYC);
        if (lp.y >= 1.0) { // simple pathologic cases
            if (lp.y > 1.000001) {
                lp.x = Double.NaN;
                lp.y = Double.NaN;
                return lp;
//                throw new ProjectionException();
            } else {
                lp.y = y < 0. ? -MapMath.HALFPI : MapMath.HALFPI;
                lp.x /= this.getX(NODES);
            }
        } else { // general problem
            // in Y space, reduce to table interval
            int i;
            for (i = (int)(lp.y * NODES);;) {
                if (this.getY(i) > lp.y)
                    i--;
                else if (this.getY(i+1) <= lp.y)
                    i++;
                else
                    break;
            }

            final double[] splineCoeffs = this.distSpline[i];
            double Tc0 = splineCoeffs[0];
            final double Tc1 = splineCoeffs[1];
            final double Tc2 = splineCoeffs[2];
            final double Tc3 = splineCoeffs[3];
            
            // first guess, linear interp
            final double Yi1 = this.getY(i+1);
            double t1, t = (lp.y - Tc0)/(Yi1 - Tc0);
            
            // make into root: find x for y = 0 of f(x)=spline(x)-Tc0
            Tc0 -= lp.y;
            for (;;) { // Newton-Raphson
                final double f = Tc0 + t * (Tc1 + t * (Tc2 + t * Tc3));
                final double fder = Tc1 + t * (Tc2 + Tc2 + t * 3. * Tc3);
                t -= t1 = f / fder;
                if (Math.abs(t1) < EPS)
                    break;
            }
            lp.y = Math.toRadians(5 * (i + t)); // could be more efficient
            if (y < 0.)
                lp.y = -lp.y;
           
            lp.x /= poly(this.lengthSpline, i, t);
        }
        return lp;
    }

    private void updateSplineTables() {
        
        // curvature or slope of meridians at the equator
        final double startSlope = 0d;
        
        // curvature or slope of the meridians at the poles
        // compute a spline with a curvature of 0 at the end
        final double endSlope = Double.NaN;
        
        // scale parallel length parameters to 0..1
        double[] xArray = (double[])ROBINSON_PARALLELS_LENGTH.clone();
        double maxX = xArray[0];
        for (int i = 1; i < xArray.length; i++) {
            if (xArray[i] > maxX)
                maxX = xArray[i];
        }
        for (int i = 0; i < xArray.length; i++) {
            xArray[i] /= maxX;
        }
        
        this.lengthSpline = computeCubicSpline(startSlope, endSlope, xArray);
        
        // scale parallel length parameters to 0..1
        double[] yArray = (double[])ROBINSON_PARALLELS_DISTANCE.clone();
        double maxY = yArray[0];
        for (int i = 1; i < yArray.length; i++) {
            if (yArray[i] > maxY)
                maxY = yArray[i];
        }
        for (int i = 0; i < yArray.length; i++) {
            yArray[i] /= maxY;
        }
        this.distSpline = computeNaturalCubicSpline(yArray);
    }
    
    private static double[][] computeNaturalCubicSpline(double[] values) {
        return computeCubicSpline(Double.NaN, Double.NaN, values);
    }
    
    /**
     * Compute the spline coefficients for a cubic spline.
     * The spline coefficients are computed with a system of linear
     * equations. See http://mathworld.wolfram.com/CubicSpline.html
     * The coefficients are a, b, c, d for Yi(t)=ai+bi*t+ci*t*t+di*t*t*t
     * @param startSlope Slope of the interpolated curve at the start in radians.
     * If startSlope is NaN, the second derivative at the start of the curve is set to 0.
     * @param endSlope Slope of the interpolated curve at the end in radians. 
     * If endSlope is NaN, the second derivative at the end of the curve is set to 0.
     * @param values The values for which to compute an interpolating spline curve.
     * The values are assumed to be equally distant.
     */
    private static double[][] computeCubicSpline(double startSlope, 
            double endSlope, double[] values) {
        
        // solve the system A * D = B
        // see http://mathworld.wolfram.com/CubicSpline.html
        
        final int n = values.length;
        final int n_1 = n-1;
        
        // setup matrix A
        double[][] A = new double[n][n];
        if(Double.isNaN(startSlope)) {
            // the second derivative at the start is 0 if no start slope is provided
            A[0][0] = 2;
            A[0][1] = 1;
        } else {
            // use equation 7 of http://mathworld.wolfram.com/CubicSpline.html to fix
            // the start and end slopes
            // Y0'(0) = 0 -> 1 * D0 = b0 = 0
            A[0][0] = 1;  
        }
        for (int i = 1; i < n_1; i++) {
            A[i][i-1] = 1;
            A[i][i] = 4;
            A[i][i+1] = 1;
        }
        if(Double.isNaN(endSlope)) {
            // the second derivative at the end is 0 if no end slope is provided
            A[n_1][n_1-1] = 1;
            A[n_1][n_1] = 2;
        } else {
            A[n_1][n_1] = 1;  
        }
        Matrix mat_a = new Matrix(A);
        
        // setup matrix B, which is a vector
        double[] B = new double[n];
        if(Double.isNaN(startSlope)) {
            B[0] = 3 * (values[1] - values[0]); // second derivate is 0
        } else {
            B[0] = startSlope; // slope at start
        }
        for (int i = 1; i < n_1; i++) {
            B[i] = 3 * (values[i+1] - values[i-1]);
        }
        if(Double.isNaN(endSlope)) {
            // the second derivative at the end is 0 if no end slope is provided
            B[n_1] = 3 * (values[n_1] - values[n_1-1]);
        } else {
            B[n_1] = endSlope;
        }
        Matrix mat_b = new Matrix(B, n);
        
        // solve for D
        Matrix mat_d = mat_a.inverse().times(mat_b);
        
        // compute the spline coefficients a, b, c, d
        double[][] abcd = computeSplineCoefficients(mat_d.getArray(), values);
        
        return abcd;
    }
    
    /**
     * Compute the spline coefficients a, b, c, d from the matrix D and the
     * vector v, which contains the values to interpolate.
     *
     */
    private static double[][] computeSplineCoefficients(double[][] D, double[] v) {
        // compute the spline coefficients a, b, c, d
        // a = yi
        // b = Di
        // c = 3(yi+1 - yi) - 2dDi - Di+1
        // d = 2(yi - yi+1) + dDi + Di+1 = -2(yi+1 - yi) + dDi + Di+1
        final int coefCount = v.length-1;
        double[][] abcd = new double[coefCount][4];
        for (int i = 0; i < coefCount; i++) {
            final double di = D[i][0];
            final double diplus1 = D[i+1][0];
            final double[] abcd_row = abcd[i];
            final double valDif = v[i+1] - v[i];
            abcd_row[0] = v[i];
            abcd_row[1] = di;
            abcd_row[2] = 3 * valDif - 2 * di - diplus1;
            abcd_row[3] = -2 * valDif + di + diplus1;
        }
        return abcd;
    }

    public String toString() {
        return "Robinson";
    }
}