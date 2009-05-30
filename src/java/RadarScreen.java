/**
 * RadarScreen - displays a radar image for a given location
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

import net.rim.device.api.system.Bitmap;
import net.rim.device.api.system.RadioInfo;
import net.rim.device.api.ui.*;
import net.rim.device.api.ui.component.Dialog;
import net.rim.device.api.ui.component.LabelField;
import net.rim.device.api.ui.component.RichTextField;
import net.rim.device.api.ui.UiApplication;
import java.io.IOException;
import net.rim.device.api.ui.Graphics;

public class RadarScreen extends AbstractScreen
{
	private static final int UPDATE_INTERVAL = 300000; // 5 minutes
	private static final String NWS_URL = "http://radar.weather.gov/ridge";
	private static final int MAX_TRIES = 99;
	
	private Thread _workerThread = null;
	
	private LocationData _loc = null;
	private LocationData _newLocation = null;
	private String _radarStation = null;
	
	private boolean _workerBusy = false;
	
	private class WorkerThread implements Runnable 
	{
		private Bitmap fetchBitmap(final String url) throws IOException
		{
			byte[] buff = new byte[100000];
			int len = 0;
			HttpHelper.Connection conn = null;
			int tries = 0;
			while(tries < MAX_TRIES) {
				try {
					tries += 1;
					conn = HttpHelper.getUrl(url);
					len = conn.is.read(buff, 0, 100000);
					tries = MAX_TRIES; // success
				} catch (IOException e) {
					System.err.println("Error reading bitmap buffer: "+e.toString());
					if (tries >= MAX_TRIES) {
						throw new IOException(e.getMessage());
					} else {
						try {
							Thread.sleep(1000); // wait a second...
						} catch(InterruptedException ie) {
							break;
						}
					}
				} finally {
					if (conn != null)
						conn.close();
				}
			}
			
			if (len == 0) {
				System.err.println("BitmapProvider: Got 0 bytes of data, bailing out");
				throw new IOException("Got 0 bytes of data");
			}
			
			final Bitmap bm;
			try {
				bm = Bitmap.createBitmapFromBytes(buff, 0, -1, 1);
			} catch (Exception e) {
				System.err.println("Error decoding bitmap"+url+": "+e.toString());
				throw new IOException(e.getMessage());
			}
			
			return bm;
		}
		
		public void getDisplayRadar(final String station)
		{
			final String radarUrl = NWS_URL + "/lite/N0R/"+station+"_0.png";
			Bitmap radarBm = null;
			try {
				radarBm = fetchBitmap(radarUrl);
			} catch(final IOException ioe) {
				if (radarBm == null) {
					UiApplication.getUiApplication().invokeLater(new Runnable() {
						public void run()
						{
							setStatusVisible(false);
							deleteAll();
							add(new RichTextField("Error getting radar data from \n"
									+radarUrl+":\n"+ioe.getMessage()));
						}
					} );
				}
				return;
			}
			if (radarBm != null) {	
				final BitmapScrollField radarField = new BitmapScrollField(radarBm);
				synchronized (UiApplication.getEventLock()) {
					deleteAll();
					setStatusVisible(false);
					setTitleText(station+" radar");
					add(radarField);
					radarField.centerView();
					radarField.setFocus();
				}
			}
		}
		
		public void run()
		{
			while(true) {
				LocationData newLoc = getNewLocation();
				if (newLoc != null && newLoc != _loc) {
					UiApplication.getUiApplication().invokeLater(new Runnable() {
						public void run()
						{
							setStatusText("Getting imagery. . .");
							setStatusVisible(true);
						}
					} );
					_workerBusy = true;
					_loc = newLoc;
					setNewLocation(null);
					RadarStation tmp = new RadarStation();
					RadarStation rdr = tmp.findNearest(_loc.getLat(), _loc.getLon());
					_radarStation = rdr.getName();
				} else {
					System.err.println("No new location"); // debug debug 
				}
				
				if (_radarStation != null) {
					// do update
					if (RadioInfo.isDataServiceOperational()) {
						_workerBusy = true;
						getDisplayRadar(_radarStation);
					} else {
						// wait for radio service
						try {
							Thread.sleep(4000); // sleep for 4 seconds
						} catch (InterruptedException e) {
							continue;
						}
					}
				}
				
				_workerBusy = false;
				try {
					Thread.sleep(UPDATE_INTERVAL);
				} catch(InterruptedException e) {
					continue;
				}
			}
		}
	}
	
	public RadarScreen()
	{
		super("NWSClient", "loading...");
	}
	
	public void setNewLocation(LocationData loc)
	{
		synchronized(this) {
			_newLocation = loc;
		}
	}
	
	public LocationData getNewLocation()
	{
		synchronized(this) {
			return _newLocation;
		}
	}
	
	public void showRadar(LocationData loc)
	{
		if (_workerThread == null) {
			_workerThread = new Thread(new WorkerThread());
			_workerThread.start();
		}
		
		if (_workerBusy) {
			synchronized (UiApplication.getEventLock()) {
				Dialog.alert("Currently fetching radar.");
			}
			return;
		}
		
		setNewLocation(loc);
		_workerThread.interrupt();
	}
	
	
}

