/**
 * A class that represents a tree of weather station objects
 */


package com.renderfast.nwsclient;

import java.util.*;
import java.lang.Math.*;
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
		double[] point = { lat, lon };
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
		try {
			if (_root == null) {
				WeatherStation tmp = new WeatherStation();
				InputStream inputStream = tmp.getList(); 
				DataInputStream din = new DataInputStream(inputStream);
				_root = _initTree(din);
				inputStream.close();
			}
			return _root.nearestNeighbor(lat, lon);
		} catch (IOException ioe) {
			System.err.println("IO Error reading file");
		}
		return "";
	}
	
	
	public static WeatherStation _initTree(final DataInputStream din) throws IOException {
		
		byte[] buff = new byte[4]; // weather station name
		int bytes = din.read(buff);
		if (bytes == -1)
			return null;
		WeatherStation sta = new WeatherStation();
		sta.name = new String(buff);
		sta.lat = din.readDouble();
		sta.lon = din.readDouble();
		for (int i=0; i < 255; i++) {
			char c = (char)din.readByte();
			if (c == '<') {
				sta.left = _initTree(din);
			} else if (c == '>') {
				sta.right = _initTree(din);
			} else if (c == '\n') {
				break;
			}
		}
		return sta;
	}
	
}

