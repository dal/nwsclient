
package com.renderfast.nwsclient;

import java.util.*;
import java.lang.Math.*;
import net.rim.device.api.util.*;
import net.rim.device.api.math.*;
import java.io.*;

public abstract class Station
{
	
	private static final double nauticalMilePerLat = 3438.1598733552287;
	private static final double nauticalMilePerLongitude = 3443.9307042677865;
	private static final double milesPerNauticalMile = 1.15078;
	
	public String name;
	public double lat_;
	public double lon_;
	public Station left;
	public Station right;
	
	private static Station _root = null;
	
	public static double projectLat(double lat)
	{
		return MathUtilities.log(Math.tan(lat) + (1.0/Math.cos(lat)));
	}
	
	public abstract String getDataFilePath();
	
	public abstract int getNameLength();
	
	static double unprojectLat(double y)
	{
		// Decay method
		double mult = 0.999;
		double n = y*mult;
		while (n > 1.0e-100) {
			n *= mult;
			double test = projectLat(n);
			double check1 = y-test;
			double check2 = test-y;
			if (check1 > 0.0001 || check2 < 0.0001)
				return n;
			if (check2 > 0.75) {
				mult = 0.99;
			} else {
				mult = 0.999;
			}
		}
		return n;
	}
	
	public static double greatCircleDistance(double lat1, double lon1, double lat2, double lon2)
	{
		System.err.println("From "+String.valueOf(Math.toDegrees(lat1))+" "+String.valueOf(Math.toDegrees(lon1)));
		System.err.println("To   "+String.valueOf(Math.toDegrees(lat2))+" "+String.valueOf(Math.toDegrees(lon2)));
		double yDistance = (lat2 - lat1) * nauticalMilePerLat;
		double xDistance = (Math.cos(lat1) + Math.cos(lat2) * (lon2 - lon1) * (nauticalMilePerLongitude / 2.));
		return (Math.sqrt( yDistance * yDistance + xDistance * xDistance ) * milesPerNauticalMile);
	}
	
	public static String bearing(double lat1, double lon1, double lat2, double lon2)
	{
		// sin(lon1-lon2)*cos(lat2)
		//double x = Math.sin(lon1-lon2)*Math.cos(lat2); 
		//         math.cos(lat1) * math.sin(lat2) *math.sin(lat2) - math.sin(lat1) * math.cos(lat2) * math.cos(lon1-lon2)
        //double y = Math.cos(lat1) * Math.sin(lat2) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(lon1-lon2);
		double x = lon2 - lon1;
		double y = lat2 - lat1;
		int res = Fixed32.atand2(Fixed32.tenThouToFP((int)(x*10000.)), Fixed32.tenThouToFP((int)(y*10000.)));
		res = Fixed32.toRoundedInt(res);
		res = (res + 360) % 360;
		if (res > 337 || res <= 22) {
			return "N";
		} else if (res > 22 && res <= 67) {
			return "NE";
		} else if (res > 67 && res <= 112) {
			return "E";
		} else if (res > 112 && res <= 157) {
			return "SE";
		} else if (res > 157 && res <= 202) {
			return "S";
		} else if (res > 202 && res <= 247) {
			return "SW";
		} else if (res > 247 && res <= 292) {
			return "W";
		}
		return "NW";
	}
	
	public Station nearestNeighbor(double lat, double lon)
	{
		double[] point = { projectLat(Math.toRadians(lat)), Math.toRadians(lon) };
		Station best = null;
		Vector result = nearestNeighbor(point, 0, best, Double.MAX_VALUE);
		Station station = (Station)result.elementAt(0);
		return station;
	}
	
	public InputStream _getList() throws IOException
	{
		// create a temp weather station to get the getResourceAsStream class method
		String _dataFilePath = getDataFilePath();
		System.err.println("Opening data file "+_dataFilePath);
		InputStream is = this.getClass().getResourceAsStream( _dataFilePath );
		return is;
	}
	
	public void init()
	{
		InputStream inputStream = null; 
		try {
			inputStream = _getList(); 
			DataInputStream din = new DataInputStream(inputStream);
			int[] goUp = { 0 };
			_initTree(this, din, goUp);
		} catch (IOException ioe) {
			System.err.println("IO Error reading file");
		} finally {
			if (inputStream != null) {
				try {
					// Make sure we always close the weather stations list
					inputStream.close();
				} catch (Exception e) { }
			}
		}
	}
	
	private Station _initTree(Station sta, final DataInputStream din, int[] goUp) throws IOException {
		
		// In the first iteration the passed in station is actually this, the root
		// goUp codes: n > 0, n < 64 = left child, n levels up 
		//             n > 64  = right child, n-64 levels up
		
		byte[] buff = new byte[getNameLength()]; // weather station name
		int bytes = din.read(buff);
		if (bytes == -1)
			return null;
		if (sta == null) {
			try {
				sta = (Station)getClass().newInstance();
			} catch(InstantiationException e) {
				System.err.println("Error instantiating station subclass in _initTree");
				return null;
			} catch(IllegalAccessException e) {
				System.err.println("Illegal access instantiating station subclass in _initTree");
				return null;
			}
		}
		sta.name = new String(buff);
		sta.lat_ = din.readDouble();
		sta.lon_ = din.readDouble();
		
		byte[] buff2 = new byte[1]; // goUp code
		bytes = din.read(buff2);
		if (bytes == -1)
			return sta; // we're done
		
		goUp[0] = (int)buff2[0];
		
		if (goUp[0] == 0) {
			sta.left = _initTree(null, din, goUp);
		}
		
		if (goUp[0] == 0x40) {
			sta.right = _initTree(null, din, goUp);
		}
		
		goUp[0]--;
		return sta;
	}
	
	public Station() {
		lat_ = 0.;
		lon_ = 0.;
		left = null;
		right = null;
	}
	
	public String getName() { return name; }
	
	public double getLat() { return unprojectLat(lat_); }
	
	public double getLon() { return lon_; }
	
	public double getCoord(int axis) {
		if (axis == 0)
			return lat_;
		return lon_;
	}
	
	public double distanceTo(double plat, double plon)
	{
		// return squared distance
		return ((lat_ - plat)*(lat_ - plat) + (lon_ - plon)*(lon_ - plon));
	}
	
	private Vector nearestNeighbor(double[] point, int depth, 
									Station currentBest, double bestDist) 
	{
		int axis = depth % 2;
		depth += 1;
		Station child, otherChild;
		child = null;
		otherChild = null;
		
		if (point[axis] <= this.getCoord(axis)) {
			otherChild = this.right;
			if (this.left != null) {
				child = this.left;
			}
		} else {
			otherChild = this.left;
			if (this.right != null) {
				child = this.right;
			}
		}
		
		if (child == null) {
			double dist = this.distanceTo(point[0], point[1]);
			if (currentBest == null || dist*dist < bestDist) {
				bestDist = dist;
				currentBest = this;
			}
			Vector ret = new Vector();
			ret.addElement(currentBest);
			ret.addElement(new Double(bestDist));
			return ret;
		} else {
			// Recurse!
			Vector result = child.nearestNeighbor(point, depth, currentBest, bestDist);
			currentBest = (Station)result.elementAt(0);
			Double tmp = (Double)result.elementAt(1);
			bestDist = tmp.doubleValue();
		}
		
		double dist = this.distanceTo(point[0], point[1]); 
		if (dist < bestDist) {
			bestDist = dist;
			currentBest = this;
		}
		
		if (otherChild != null) {
			double bound = point[axis] - this.getCoord(axis);
			if ((bound*bound) <= bestDist) {
				// uh oh, there could be closer point on the other side of the splitting plane
				Vector result = otherChild.nearestNeighbor(point, depth, currentBest, bestDist);
				Double tmp = (Double)result.elementAt(1);
				if (tmp.doubleValue() < bestDist) {
					currentBest = (Station)result.elementAt(0);
					bestDist = tmp.doubleValue();
				}
			}
		}
		
		Vector ret = new Vector();
		ret.addElement(currentBest);
		ret.addElement(new Double(bestDist));
		return ret;
	}
}
