
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
		InputStream is;
		try {
			is = NwsClient.getUrl(url);
		} catch (Exception e) {
			System.err.println("Error fetching bitmap: "+e.getMessage());
			return;
		}
		
		byte[] buff;
		int len = 0;
		try {
			buff = new byte[100000];
			len = is.read(buff, 0, 100000);
			is.close();
		} catch (IOException e) {
			System.err.println("Error reading bitmap buffer: "+e.toString());
			return;
		}
		
		if (len == 0) {
			System.err.println("BitmapProvider: Got 0 bytes of data, bailing out");
			return;
		}
		
		final Bitmap bm;
		try {
			bm = Bitmap.createBitmapFromBytes(buff, 0, -1, 1);
		} catch(Exception e) {
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

