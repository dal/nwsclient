/**
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

import net.rim.device.api.util.*;
import java.util.*;

public class NwsClientOptions implements Persistable
{
	// I wanted to use a stack for this but it's not persistable
	// RIM doesn't implement a deque
	private Vector _recentLocations;
	private final int MAX_LOCATIONS = 10;
	//private boolean _autoRefresh = true;
	private boolean _useNws = true;
	private boolean _autoUpdateIcon = true;
	private boolean _metric = false;
	private int _minFontSize = 10;
	private boolean _changeAppRolloverIcon = true;
	private boolean _changeAppName = true;
	
	public NwsClientOptions()
	{
		_recentLocations = new Vector();
	}
	
	public LocationData getCurrentLocation()
	{
		if (_recentLocations.size() <= 0)
			return null;
		return (LocationData)_recentLocations.firstElement();
	}
	
	// Adds the current location to the last point in the history, removing any
	// previous occurrences. Returns the location
	public synchronized LocationData setCurrentLocation(LocationData loc)
	{
		int pos = _recentLocations.indexOf(loc);
		if (pos != -1) {
			loc = (LocationData)_recentLocations.elementAt(pos);
			_recentLocations.removeElementAt(pos);
			_recentLocations.insertElementAt(loc, 0);
		} else {
			_recentLocations.insertElementAt(loc, 0);
		}
		
		while (_recentLocations.size() > MAX_LOCATIONS)
			_recentLocations.removeElementAt(_recentLocations.size()-1);
		
		return loc;
	}
	
	public Vector getLocations()
	{
		return _recentLocations;
	}
	
	public boolean useNws()
	{
		return _useNws;
	}
	
	public boolean autoUpdateIcon()
	{
		return _autoUpdateIcon;
	}
	
	public boolean metric()
	{
		return _metric;
	}
	
	public int minFontSize()
	{
		return _minFontSize;
	}
	
	public boolean changeAppRolloverIcon()
	{
		return _changeAppRolloverIcon;
	}
	
	public boolean changeAppName()
	{
		return _changeAppName;
	}
	
	public void setUseNws(boolean use)
	{
		_useNws = use;
	}
	
	public void setAutoUpdateIcon(boolean aui)
	{
		_autoUpdateIcon = aui;
	}
	
	public void setMetric(boolean metric)
	{
		_metric = metric;
	}
	
	public void setMinFontSize(int size)
	{
		_minFontSize = size;
	}
	
	public void setChangeAppRolloverIcon(boolean ci)
	{
		_changeAppRolloverIcon = ci;
	}
	
	public void setChangeAppName(boolean cn)
	{
		_changeAppName = cn;
	}

}

