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
import net.rim.device.api.ui.Field;
import net.rim.device.api.ui.component.Dialog;
//import net.rim.device.api.ui.component.NullField;
import net.rim.device.api.ui.UiApplication;
import java.io.IOException;
import net.rim.device.api.ui.Graphics;

public class RadarScreen extends AbstractScreen
{
	private final int UPDATE_INTERVAL = 300000; // 5 minutes
	private final String NWS_URL = "http://radar.weather.gov/ridge/";
	
	private Thread _workerThread = null;
	
	private LocationData _loc = null;
	private LocationData _newLocation = null;
	private String _radarStation = null;
	
	private Bitmap _background;
	private Bitmap _overlay;
	
	private boolean _workerBusy = false;
	
	private class WorkerThread implements Runnable
	{
		private Bitmap fetchBitmap(final String url)
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
			
			final Bitmap bm;
			try {
				bm = Bitmap.createBitmapFromBytes(buff, 0, -1, 1);
			} catch (Exception e) {
				System.err.println("Error decoding bitmap"+url+": "+e.toString());
				return null;
			}
			
			return bm;
		}
		
		public void getDisplayRadar(final String station)
		{
			String radarUrl = NWS_URL + "/RadarImg/N0R/"+station+"_N0R_0.gif";
			Bitmap radarBm = fetchBitmap(radarUrl);
			int width = radarBm.getWidth();
			int height = radarBm.getHeight();
			final Bitmap compositeBm = new Bitmap(radarBm.getWidth(), radarBm.getHeight());
			Graphics g = new Graphics(compositeBm);
			g.drawBitmap(0, 0, width, height, _background, 0, 0);
			g.drawBitmap(0, 0, width, height, radarBm, 0, 0);
			//g.drawBitmap(0, 0, width, height, _overlay, 0, 0);
			if (radarBm != null) {
				UiApplication.getUiApplication().invokeLater(new Runnable() {
					public void run()
					{
						setTitleText(station+" radar");
						//NullField nullField1 = new NullField(Field.FIELD_LEFT);
						//add(nullField1);
						BitmapScrollField radarField = new BitmapScrollField(compositeBm);
						add(radarField);
						radarField.setFocus();
						//NullField nullField2 = new NullField(Field.FIELD_RIGHT);
						//add(nullField2);
					}
				});
			}
		}
		
		public void getOverlayImages(final String station)
		{
			/* 
			 * The overlay images are not likely to change as often as the radar
			 * so we'll get them once and cache them.
			 */
			String overlayUrl = NWS_URL + "/Overlays";
			String topoUrl = overlayUrl + "/Topo/Short/"+station+"_Topo_Short.jpg";
			String countiesUrl = overlayUrl + "/County/Short/"+station+"_County_Short.gif";
			String highwaysUrl = overlayUrl + "/Highways/Short/"+station+"_Highways_Short.gif";
			String citiesUrl = overlayUrl + "/Cities/Short/"+station+"_City_Short.gif";
			
			_background = fetchBitmap(topoUrl);
			if (_background == null) {
				System.err.println("Background bitmap came up empty");
				return;
			}
			
			// Layer all the overlays into one bitmap image
			_overlay = new Bitmap(_background.getWidth(), _background.getHeight());
			Graphics g = new Graphics(_overlay);
			Bitmap countiesBm = fetchBitmap(countiesUrl);
			if (countiesBm != null)
				g.drawBitmap(0, 0, countiesBm.getWidth(), countiesBm.getHeight(), countiesBm, 0, 0);
			Bitmap highwaysBm = fetchBitmap(highwaysUrl);
			if (highwaysBm != null)
				g.drawBitmap(0, 0, highwaysBm.getWidth(), highwaysBm.getHeight(), highwaysBm, 0, 0);
			Bitmap citiesBm = fetchBitmap(citiesUrl);
			if (citiesBm != null)
				g.drawBitmap(0, 0, citiesBm.getWidth(), citiesBm.getHeight(), citiesBm, 0, 0);
			
		}
		
		public void run()
		{
			while(true) {
				LocationData newLoc = getNewLocation();
				if (newLoc != null && newLoc != _loc) {
					_workerBusy = true;
					_loc = newLoc;
					setNewLocation(null);
					RadarStation tmp = new RadarStation();
					RadarStation rdr = tmp.findNearest(_loc.getLat(), _loc.getLon());
					if (_radarStation != rdr.getName()) {
						_radarStation = rdr.getName();
						getOverlayImages(_radarStation);
					}
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
		super("NWSClient", "loading...", (HORIZONTAL_SCROLL | VERTICAL_SCROLL));
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

