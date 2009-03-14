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

import net.rim.device.api.ui.*;
import java.util.*;
import java.io.*;
import net.rim.device.api.ui.component.*;
import net.rim.device.api.system.*;

public class BitmapProvider extends Thread
{

	private Hashtable bitmapStore;
	private Vector urls;
	private Vector fields;
	private Hashtable bitmaps;
	
	private boolean _stop = false;

	BitmapProvider() 
	{
		urls = new Vector();
		fields = new Vector();
		bitmaps = new Hashtable();
	}
	
	public synchronized void stop()
	{
		_stop = true;
	}
	
	public synchronized void getBitmap(final String url, final BitmapField field)
	{
		urls.addElement(url);
		fields.addElement(field);
		
		if (!this.isAlive())
			this.start();
		
	}
	
	public void run()
	{
		for(;;) {
			final int urlCount;
			String myUrl = null;
			BitmapField myField = null;
			boolean haveIt = false;
			
			// We don't want to miss any urls coming in...
			synchronized(this) {
				// Check for stop
				if (_stop) {
					break;
				}
				urlCount = urls.size();
				if (urlCount > 0) {
				// go get some bitmaps
					myUrl = (String)urls.firstElement();
					myField = (BitmapField)fields.firstElement();
					urls.removeElementAt(0);
					fields.removeElementAt(0);
					haveIt = bitmaps.containsKey(myUrl);
				}
			}
			
			// urlCount will be > 0 if we have anything to fetch...
			if (urlCount > 0 && myField != null && myUrl != null) {
				if (haveIt) {
					setBitmapField(myField, (Bitmap)bitmaps.get(myUrl));
				} else {
					fetchBitmap(myUrl, myField);
				}
			} else {
				// Wait a second
				try {
					sleep(1000);
				} catch (InterruptedException ie) {
					return;
				}
			}
		}
	}
	
	private void setBitmapField(final BitmapField field, final Bitmap bm)
	{
		// Set the bitmapField's bitmap
		UiApplication.getApplication().invokeLater(new Runnable() {
			public void run() {
				field.setBitmap(bm);
			}
		});
	}
	
	private void fetchBitmap(final String url, final BitmapField field)
	{
		
		try {
			
		} catch (Exception e) {
			System.err.println("Error fetching bitmap: "+e.getMessage());
			return;
		}
		
		byte[] buff;
		int len = 0;
		HttpHelper.Connection conn = null;
		try {
			conn = HttpHelper.getUrl(url);
			buff = new byte[100000];
			len = conn.is.read(buff, 0, 100000);
		} catch (IOException e) {
			System.err.println("Error reading bitmap buffer: "+e.toString());
			return;
		} finally {
			if (conn != null)
				conn.close();
		}
		
		if (len == 0) {
			System.err.println("BitmapProvider: Got 0 bytes of data, bailing out");
			return;
		}
		
		final Bitmap bm;
		try {
			bm = Bitmap.createBitmapFromBytes(buff, 0, -1, 1);
		} catch (Exception e) {
			System.err.println("Error decoding bitmap"+url+": "+e.toString());
			return;
		}
		
		synchronized(this) {
			bitmaps.put(url, bm);
		}
		//System.err.println("Got "+url);
		setBitmapField(field, bm);
	}

}

