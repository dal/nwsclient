/**
 * A class that represents a tree of weather station objects
 */


package com.renderfast.nwsclient;

import java.util.*;
import java.lang.Math.*;
import net.rim.device.api.util.*; // for log()
import java.io.*;
import java.io.DataInputStream.*;

public class WeatherStation extends Object
{
	private static WeatherStation _root = null;
	
	private static String _weatherStationFilePath = "/data/weather_stations.data";
	
	public String name;
	public double lat;
	public double lon;
	public WeatherStation left;
	public WeatherStation right;
	
	static double projectLat(double lat)
	{
		return MathUtilities.log(Math.tan(lat) + (1.0/Math.cos(lat)));
	}
	
	double pow(double x, double y)
	{   
		// pow method from java.net
		//Convert the real power to a fractional form
		int den = 1024; //declare the denominator to be 1024
	
		/*Conveniently 2^10=1024, so taking the square root 10
		times will yield our estimate for n.  In our example
		n^3=8^2    n^1024 = 8^683.*/
	
		int num = (int)(y*den); // declare numerator
	
		int iterations = 10;  /*declare the number of square root
			iterations associated with our denominator, 1024.*/
	
		double n = Double.MAX_VALUE; /* we initialize our         
			estimate, setting it to max*/
	
		while( n >= Double.MAX_VALUE && iterations > 1) {
			/*  We try to set our estimate equal to the right
			hand side of the equation (e.g., 8^2048).  If this
			number is too large, we will have to rescale. */
	
			n = x;
					
			for( int i=1; i < num; i++ )n*=x;
	
			/*here, we handle the condition where our starting
			point is too large*/
			if( n >= Double.MAX_VALUE ) {
				iterations--;  /*reduce the iterations by one*/
							
				den = (int)(den / 2);  /*redefine the denominator*/
							
				num = (int)(y*den); //redefine the numerator
			}
		}
			
		/*************************************************
		** We now have an appropriately sized right-hand-side.
		** Starting with this estimate for n, we proceed.
		**************************************************/
			
		for( int i = 0; i < iterations; i++ )
		{
			n = Math.sqrt(n);
		}
			
		// Return our estimate
		return n;
	}

	/*
	static double sinh(double z)
	{
		return 0.5 * (pow(Math.E, z) + pow(Math.E, -1*z));
	}
	
	static double unprojectLat(double y)
	{
		return Math.atan(sinh(y));
	}
	*/
	
	public WeatherStation() {
		lat = 0.;
		lon = 0.;
		left = null;
		right = null;
	}
	
	public String getName() { return name; }
	
	public double getCoord(int axis) {
		if (axis == 0)
			return lat;
		return lon;
	}
	
	public double distanceTo(double plat, double plon)
	{
		// return squared distance
		return ((lat - plat)*(lat - plat) + (lon - plon)*(lon - plon));
	}
	
	public String nearestNeighbor(double lat, double lon)
	{
		double[] point = { projectLat(Math.toRadians(lat)), Math.toRadians(lon) };
		WeatherStation best = null;
		Vector result = this.nearestNeighbor(point, 0, best, Double.MAX_VALUE);
		WeatherStation station = (WeatherStation)result.elementAt(0);
		Double bestDist = (Double)result.elementAt(1);
		System.err.println(station.getName()+" "+String.valueOf(station.getCoord(0))+
			" "+String.valueOf(station.getCoord(1))+" dist: "+bestDist.toString());
		return station.getName();
	}
	
	private Vector nearestNeighbor(double[] point, int depth, 
									WeatherStation currentBest, double bestDist) 
	{
		System.err.println(this.getName());
		int axis = depth % 2;
		depth += 1;
		WeatherStation child, otherChild;
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
			currentBest = (WeatherStation)result.elementAt(0);
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
					currentBest = (WeatherStation)result.elementAt(0);
					bestDist = tmp.doubleValue();
				}
			}
		}
		
		Vector ret = new Vector();
		ret.addElement(currentBest);
		ret.addElement(new Double(bestDist));
		return ret;
	}
	
	public InputStream getList() throws IOException
	{
		InputStream is = getClass().getResourceAsStream( _weatherStationFilePath );
		return is;
	}
	
	public static String findNearest(double lat, double lon)
	{
		InputStream inputStream = null;
		try {
			if (_root == null) {
				WeatherStation tmp = new WeatherStation();
				inputStream = tmp.getList(); 
				DataInputStream din = new DataInputStream(inputStream);
				int[] goUp = { 0 };
				_root = _initTree(din, goUp);
			}
			return _root.nearestNeighbor(lat, lon);
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
		return "";
	}
	
	
	public static WeatherStation _initTree(final DataInputStream din, int[] goUp) throws IOException {
		
		// goUp codes: n > 0, n < 64 = left child, n levels up 
		//             n > 64  = right child, n-64 levels up
		
		byte[] buff = new byte[4]; // weather station name
		int bytes = din.read(buff);
		if (bytes == -1)
			return null;
		WeatherStation sta = new WeatherStation();
		sta.name = new String(buff);
		sta.lat = din.readDouble();
		sta.lon = din.readDouble();
		
		byte[] buff2 = new byte[1]; // weather station name
		bytes = din.read(buff2);
		if (bytes == -1)
			return sta; // we're done
		
		goUp[0] = (int)buff2[0];
		
		if (goUp[0] == 0) {
			sta.left = _initTree(din, goUp);
		}
		
		if (goUp[0] == 0x40) {
			sta.right = _initTree(din, goUp);
		}
		
		goUp[0]--;
		return sta;
	}
	
}

