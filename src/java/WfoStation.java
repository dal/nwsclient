
package com.renderfast.nwsclient;

import java.io.*;

public class WfoStation extends Station
{
	private static String _dataFilePath = "/data/wfo.data";
	private static int _nameLength = 5; // i.e. CAMTR = CA (state) + MTR (name)
	private static WfoStation _root;
	
	private static String forecastUrl = "http://www.weather.gov/view/prodsByState.php?state=";
	
	public String getDataFilePath()
	{
		return _dataFilePath;
	}
	
	public int getNameLength() {
		return _nameLength;
	}
	
	public WfoStation findNearest(double lat, double lon)
	{
		if (_root == null) {
			_root = new WfoStation();
			_root.init();
		}
		return (WfoStation)_root.nearestNeighbor(lat, lon);
	}
	
	public String getForecastDiscussionLink()
	{
		String state = getName().substring(0,2);
		String station = getName().substring(2,5);
		return (forecastUrl + state + "&prodtype=discussion#AFD"+station);
	}
	
}

