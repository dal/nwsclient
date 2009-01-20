
package com.renderfast.nwsclient;

import net.rim.device.api.ui.*;

import java.util.*;
import java.io.*;
import net.rim.device.api.ui.component.*;
import net.rim.blackberry.api.homescreen.*;
import net.rim.device.api.system.RuntimeStore.*;
import net.rim.device.api.system.*;

public class IconUpdater extends Thread
{
	private static final long ID = 0x40360238fa3ebcc6L; // com.renderfast.nwsclient
	private static final int interval = 3600000; // one hour in milliseconds
	private long lastUpdate = 0;
	private LocationData _location;
	
	IconUpdater()
	{
		_location = null;
	}
	
	public void setIcon()
	{
		System.err.println("Setting icon for "+_location.getCountry()+" "+_location.getArea()+" "+_location.getLocality());
		// Get our current temp
		try {
			Hashtable conditions = NwsClient.getParseCurrentConditions(_location);
			if (conditions != null && conditions.containsKey("temperature")) {
				String temp = (String)conditions.get("temperature");
				Bitmap bg = Bitmap.getBitmapResource("icon.png");
				Graphics gfx = new Graphics(bg);
				FontFamily fontfam[] = FontFamily.getFontFamilies();
				Font smallFont = fontfam[0].getFont(FontFamily.SCALABLE_FONT, 12);
				gfx.setFont(smallFont);
				gfx.drawText(temp, 24, 12);
				HomeScreen.updateIcon(bg, 1);
			}
		} catch (IOException e) {
			System.err.println("Error getting icon current conditions: " + e.toString());
		} catch (ParseError pe) {
			System.err.println("Error parsing current conditions: "+pe.toString());
		}
		
	}
	
	public void run()
	{
		for(;;) {
			try {
				// Check for a new location coming in...
				LocationData newLoc = (LocationData)RuntimeStore.getRuntimeStore().get( ID );
				if (newLoc != null && newLoc != _location) {
					synchronized(this) {
						_location = newLoc;
						lastUpdate = 0;
					}
				}
				
				if (_location == null)
					continue;
				
				// Time to update?
				long now = new Date().getTime();
				long thisInterval = now - lastUpdate;
				
				//System.err.println("Interval is "+thisInterval+" for "+
				//	_location.getCountry()+" "+_location.getArea()+
				//	" "+_location.getLocality());
				
				if (thisInterval >= interval) {
					setIcon();
					lastUpdate = now;
				} else {
					// Wait four seconds
					sleep(4000);
				}
			} catch (InterruptedException ie) {
				return;
			}
		}
	}

}



