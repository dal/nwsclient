
package com.renderfast.nwsclient;

import java.io.*;

public class RadarStation extends Station
{
	private static String _dataFilePath = "/data/radar_stations.data";
	private static int _nameLength = 3; // i.e. OAK, SYR, etc...
	private static RadarStation _root;
	
	public String getDataFilePath()
	{
		return _dataFilePath;
	}
	
	public int getNameLength() {
		return _nameLength;
	}
	
	public RadarStation findNearest(double lat, double lon)
	{
		if (_root == null) {
			_root = new RadarStation();
			_root.init();
		}
		return (RadarStation)_root.nearestNeighbor(lat, lon);
	}
	
}

