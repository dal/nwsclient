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
import net.rim.blackberry.api.homescreen.HomeScreen;

public class BitmapProvider extends Thread
{
	
	private static int[] iconSize = { 48, 36 };
	
	private Vector urls;
	private Vector fields;
	private Hashtable bitmaps;
	private Vector homeScreen;
	
	private boolean _stop = false;

	BitmapProvider() 
	{
		urls = new Vector();
		fields = new Vector();
		bitmaps = new Hashtable();
		homeScreen = new Vector();
	}
	
	public synchronized void stop()
	{
		_stop = true;
	}
	
	public synchronized void getBitmap(final String url, final BitmapField field, 
													boolean doHomeScreen)
	{
		urls.addElement(url);
		fields.addElement(field);
		homeScreen.addElement(new Boolean(doHomeScreen));
		
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
			boolean doHomeScreen = false;
			
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
					Boolean tmpBool = (Boolean)homeScreen.firstElement();
					doHomeScreen = tmpBool.booleanValue();
					urls.removeElementAt(0);
					fields.removeElementAt(0);
					homeScreen.removeElementAt(0);
					haveIt = bitmaps.containsKey(myUrl);
				}
			}
			
			// urlCount will be > 0 if we have anything to fetch...
			if (urlCount > 0 && myField != null && myUrl != null) {
				if (haveIt) {
					EncodedImage img = (EncodedImage)bitmaps.get(myUrl);
					setBitmapField(myField, img);
					if (doHomeScreen)
						setRolloverIcon(img);
				} else {
					EncodedImage img = fetchBitmap(myUrl);
					if (img != null) {
						setBitmapField(myField, img);
						if (doHomeScreen)
							setRolloverIcon(img);
					}
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
	
	public void setRolloverIcon(final EncodedImage img)
	{
		int w = img.getWidth();
		int h = img.getHeight();
		int scale = 10000;
		
		if (w > iconSize[0]) {
			double sc = (double)iconSize[0] / w;
			scale = (int)(sc * 10000.);
		}
		
		if (h > iconSize[1]) {
			double sc = (double)iconSize[1] / h;
			scale = Math.max(scale, (int)(sc * 10000.));
		}
		
		EncodedImage copy = img.scaleImage32(scale, scale);
		
		HomeScreen.setRolloverIcon(copy.getBitmap(), 1);
	}
	
	private void setBitmapField(final BitmapField field, final EncodedImage img)
	{
		// Set the bitmapField's bitmap
		UiApplication.getApplication().invokeLater(new Runnable() {
			public void run() {
				field.setBitmap(img.getBitmap());
			}
		});
	}
	
	public EncodedImage fetchBitmap(final String url)
	{
		byte[] buff;
		int len = 0;
		HttpHelper.Connection conn = null;
		try {
			conn = HttpHelper.getUrl(url);
			buff = new byte[100000];
			len = conn.is.read(buff, 0, 100000);
		} catch (IOException e) {
			System.err.println("Error reading bitmap buffer: "+e.toString());
			return null;
		} finally {
			if (conn != null)
				conn.close();
		}
		
		if (len == 0) {
			System.err.println("BitmapProvider: Got 0 bytes of data, bailing out");
			return null;
		}
		
		final EncodedImage img;
		try {
			//bm = Bitmap.createBitmapFromBytes(buff, 0, -1, 1);
			img = EncodedImage.createEncodedImage(buff, 0, len);
		} catch (Exception e) {
			System.err.println("Error decoding bitmap"+url+": "+e.toString());
			return null;
		}
		
		synchronized(this) {
			bitmaps.put(url, img);
		}
		return img;
	}

}

