
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
	
	public void setUseNws(boolean use)
	{
		_useNws = use;
	}
	
	public void setAutoUpdateIcon(boolean aui)
	{
		_autoUpdateIcon = aui;
	}

}

