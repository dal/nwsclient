/**
 * WeatherStation - A class that represents a tree of weather station objects
 *
 * Copyright (c) 2009 Doug Letterman
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.renderfast.nwsclient;

public class WeatherStation extends Station
{	
	private static String _dataFilePath = "/data/weather_stations.data";
	private static int _nameLength = 4; // i.e. KOAK, KSYR, etc...
	private static WeatherStation _root;
	
	public String getDataFilePath()
	{
		return _dataFilePath;
	}
	
	public int getNameLength() {
		return _nameLength;
	}
	
	public WeatherStation findNearest(double lat, double lon)
	{
		if (_root == null) {
			_root = new WeatherStation();
			_root.init();
		}
		return (WeatherStation)_root.nearestNeighbor(lat, lon);
	}
	
}

