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
import net.rim.device.api.ui.container.VerticalFieldManager;
import net.rim.device.api.ui.component.Dialog;
import net.rim.device.api.ui.component.LabelField;
import net.rim.device.api.ui.component.RichTextField;
import net.rim.device.api.ui.component.GaugeField;
//import net.rim.device.api.ui.component.NullField;
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
	
	private Bitmap _background;
	private Bitmap _overlay;
	
	private GaugeField _progressGaugeField;
	
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
			final String radarUrl = NWS_URL + "/RadarImg/N0R/"+station+"_N0R_0.gif";
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
			int width = radarBm.getWidth();
			int height = radarBm.getHeight();
			final Bitmap compositeBm = new Bitmap(radarBm.getWidth(), radarBm.getHeight());
			Graphics g = new Graphics(compositeBm);
			g.drawBitmap(0, 0, width, height, _background, 0, 0);
			g.drawBitmap(0, 0, width, height, radarBm, 0, 0);
			g.drawBitmap(0, 0, width, height, _overlay, 0, 0);
			if (radarBm != null) {
				UiApplication.getUiApplication().invokeLater(new Runnable() {
					public void run()
					{
						deleteAll();
						setStatusVisible(false);
						setTitleText(station+" radar");
						BitmapScrollField radarField = new BitmapScrollField(compositeBm);
						add(radarField);
						radarField.centerView();
						radarField.setFocus();
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
			
			try {
				_background = fetchBitmap(topoUrl);
				setProgress(1);
			} catch(IOException ioe) {
				System.err.println("Exception fetching background image: "+ioe.getMessage());
			}
			if (_background == null) {
				System.err.println("Background bitmap came up empty");
				return;
			}
			
			// Layer all the overlays into one bitmap image
			int width = _background.getWidth();
			int height = _background.getHeight();
			_overlay = new Bitmap(width, height);
			_overlay.createAlpha(Bitmap.ALPHA_BITDEPTH_8BPP);
			int [] data = new int[width * height];
			for(int i=0; i < data.length; i++)
				data[i] = 0x00000000; // no alpha
			_overlay.setARGB(data, 0, width, 0, 0, width, height);
			Graphics g = new Graphics(_overlay);
			Bitmap countiesBm = null;
			Bitmap highwaysBm = null; 
			Bitmap citiesBm = null;
			try {
				countiesBm = fetchBitmap(countiesUrl);
				highwaysBm = fetchBitmap(highwaysUrl);
				citiesBm = fetchBitmap(citiesUrl);
			} catch(IOException ioe) {
				System.err.println("Error fetching bitmap: "+ioe.getMessage());
			}
			if (countiesBm != null)
				g.drawBitmap(0, 0, countiesBm.getWidth(), countiesBm.getHeight(), countiesBm, 0, 0);
			setProgress(2);
			if (highwaysBm != null)
				g.drawBitmap(0, 0, highwaysBm.getWidth(), highwaysBm.getHeight(), highwaysBm, 0, 0);
			setProgress(3);
			if (citiesBm != null)
				g.drawBitmap(0, 0, citiesBm.getWidth(), citiesBm.getHeight(), citiesBm, 0, 0);
			setProgress(4);
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
					System.err.println("New location: "+newLoc.getIcao()); // debug debug
					_workerBusy = true;
					_loc = newLoc;
					setNewLocation(null);
					RadarStation tmp = new RadarStation();
					RadarStation rdr = tmp.findNearest(_loc.getLat(), _loc.getLon());
					if (_radarStation != rdr.getName()) {
						_radarStation = rdr.getName();
						getOverlayImages(_radarStation);
					} else {
						System.err.println("Radar station is the same");
					}
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
		//VerticalFieldManager mainCol = new VerticalFieldManager(Manager.USE_ALL_HEIGHT);
		_progressGaugeField = new GaugeField("Loading", 0, 5, 0, GaugeField.PERCENT);
		//add(mainCol);
		//mainCol.add(_progressGaugeField);
		add(_progressGaugeField);
	}
	
	public void setProgress(int value)
	{
		if (_progressGaugeField.isVisible()) {
			synchronized(UiApplication.getEventLock()) {
				_progressGaugeField.setValue(value);
			}
		}
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

