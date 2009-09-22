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
import net.rim.device.api.ui.container.*;
import net.rim.device.api.ui.component.*;
import net.rim.device.api.xml.parsers.*;
import net.rim.device.api.system.*;
import net.rim.device.api.i18n.*;
import net.rim.blackberry.api.browser.*; // for urlencoding
import net.rim.device.api.ui.component.*;
import net.rim.device.api.ui.component.ChoiceField.*;
import net.rim.blackberry.api.homescreen.*; // for updating the application icon
import net.rim.device.api.i18n.SimpleDateFormat.*;
import net.rim.device.api.system.ApplicationDescriptor.*;
import net.rim.blackberry.api.browser.Browser;
import net.rim.blackberry.api.browser.BrowserSession;
import net.rim.device.api.math.Fixed32;
import javax.microedition.global.*;
import javax.microedition.io.*;
import java.util.*;
import java.io.*;
import java.lang.Math.*;
import net.rim.device.api.util.*;
import org.w3c.dom.*;
import org.xml.sax.*; // for SAXException

import com.renderfast.nwsclient.resource.nwsclientResource;

public class NwsClient extends UiApplication
{
	// Class variables
	
	private static final long ID = 0x40360238fa3ebcc6L; // com.renderfast.nwsclient
	
	private static final String GOOGLE_MAPS_URL = "http://maps.google.com/maps/geo";
	
	private static final String GOOGLE_API_KEY = 
		"ABQIAAAAC8JsE5tvhHeNFm7ZGLaE4hRb6y4KxHYjOJR6okNA-FLzn8UPtxTXruj85ZyxdVMqDazcxknt-CapTQ";
	
	private static final String NWS_XML_URL = "http://www.weather.gov/forecasts/xml/SOAP_server/ndfdXMLclient.php";
	private static final String NWS_DAY_URL = "http://www.weather.gov/forecasts/xml/SOAP_server/ndfdSOAPclientByDay.php";
	private static final String NWS_RADAR_URL = "http://radar.weather.gov/ridge/radar_lite.php?product=N0R&loop=no&rid=";
	
	private static final String NWS_CURRENT_URL = "http://www.weather.gov/xml/current_obs/";
	
	private static final String GOOGLE_WEATHER_URL = "http://www.google.com/ig/api";
	
	private static final String GOOGLE_URL = "http://www.google.com";
	
	private static final int UPDATE_INTERVAL = 3600000; // one hour in milliseconds
	
	private static PersistentObject store;
	
	private static NwsClientOptions options;

	// Instance variables
	
	private Thread _workerThread;
	
	private String _newLocation;
	
	private BitmapProvider _bitmapProvider;
	
	private NwsClientScreen _mainScreen;
	
	private OptionsScreen _optionsScreen;
	
	private EditField _newLocField;
	
	private static ResourceBundle _resources = ResourceBundle.getBundle(nwsclientResource.BUNDLE_ID, nwsclientResource.BUNDLE_NAME);
	
	private boolean _foreground = false;
	
	private boolean _workerBusy = false;
	
	/**
	 * Initiate or reload our persistent store
	 */
	
	static {
		getOptionsFromStore();
	}
	
	// Inner Classes
	
	/**
	 * NWS NDFD often indexes weather observations by a range of times from 
	 * start time out to a given time interval. This class is a simple struct
	 * for storing those time keys.
	 */
	private static class TimeKey
	{
		public Calendar startTime;
		public String periodName;
	};
	
	/**
	 * Class for storing weather observations. Each has the type of observation,
	 * the time interval, the value, and the relevant units (i.e. 'Celsius').
	 */
	private static class Observation
	{
		public TimeKey time;
		public String value;
		public String units;
		public String url;
	};
	
	/**
	 * The main application screen.
	 */
	private final class NwsClientScreen extends AbstractScreen
	{
		public NwsClientScreen(String title, String status)
		{
			super(title, status);
		}
		
		
		protected void makeMenu(Menu menu, int instance)
		{
			menu.add(optionsMenuItem_);
			menu.add(refreshMenuItem_);
			menu.addSeparator();
			super.makeMenu(menu, instance);
		}
		
	};
	
	/**
	 * The OptionsScreen allows the user to change their location and set some 
	 * other NwsClient preferences.
	 */
	private class OptionsScreen extends MainScreen
	{
		private ObjectChoiceField _recentLocationsChoiceField;
		private CheckboxField _useNwsCheckBox;
		private CheckboxField _autoUpdateIconCheckBox;
		private CheckboxField _changeAppRolloverIconCheckBox;
		private CheckboxField _changeAppNameCheckBox;
		private CheckboxField _metricCheckBox;
		private ObjectChoiceField _minFontSizeChoiceField;
		
		// For the sake of simplicity I will use array indexes for font size:
		// index * 2 + 10 = font size
		private String _fontSizes[] = { "10", "12", "14", "16", "18", "20" };
		
		final class recentLocListener implements FieldChangeListener {
			public void fieldChanged(Field field, int context) {
				try {
					
					if (context == FieldChangeListener.PROGRAMMATIC)
						return;
					
					ObjectChoiceField ocf = (ObjectChoiceField) field;
					int idx = ocf.getSelectedIndex();
					Vector locations = options.getLocations();
					if (idx < locations.size()) {
						
						if (_workerBusy) {
							// warn if we're already fetching weather
							synchronized(UiApplication.getEventLock()) {
								Dialog.alert(_resources.getString(nwsclientResource.BUSY));
							}
							return;
						}
						
						final LocationData newLoc = (LocationData)locations.elementAt(idx);
						options.setCurrentLocation(newLoc);
						_optionsScreen.storeInterfaceValues();
						if (_optionsScreen.isDisplayed())
							_optionsScreen.close();
						
						// Tell the icon updater about the new location
						store.setContents(options);
						store.commit();
						if (checkDataConnectionAndWarn()) {
							refreshWeather();
						}
					}
				} catch (ClassCastException ce) {
					// This is not a choice "commit" operation
				}
			}
		};
		
		/**
		 * OptionsScreen constructor.
		 */
		public OptionsScreen()
		{
			//super("NWSClient "+_resources.getString(nwsclientResource.OPTIONS), "");
			super();
			_newLocField = new EditField((_resources.getString(nwsclientResource.LOCATION)+": "), 
							null, Integer.MAX_VALUE, EditField.FILTER_DEFAULT);
			add(_newLocField);
			
			_recentLocationsChoiceField = new ObjectChoiceField();
			_recentLocationsChoiceField.setLabel(_resources.getString(nwsclientResource.RECENT_LOCATIONS)+":");
			_recentLocationsChoiceField.setChangeListener(new recentLocListener());
			add(_recentLocationsChoiceField);
			
			String helpText = "\nValid locations include City, State (e.g. \"New "+
				"York, NY\"); airport codes (e.g. \"JFK\"); zip code; "+
				"or international City, Country (e.g. \"London, GB\" or "+
				"\"Moscow\").\n National Weather Service forecast data is only "+
				"available for the United States. For all other locations "+
				"iGoogle weather data will be used.";
			
			RichTextField _helpLabel = new RichTextField(helpText, Field.NON_FOCUSABLE);
			Font small = _helpLabel.getFont().derive(Font.PLAIN, 11);
			_helpLabel.setFont(small);
			add(_helpLabel);
			
			_useNwsCheckBox = new CheckboxField("Use NWS data if available", options.useNws());
			add(_useNwsCheckBox);
			_autoUpdateIconCheckBox = new CheckboxField("Update the temperature when NWSClient is not running", options.autoUpdateIcon());
			add(_autoUpdateIconCheckBox);
			_metricCheckBox = new CheckboxField("Temperature in Celsius", options.metric());
			add(_metricCheckBox);
			_changeAppRolloverIconCheckBox = new CheckboxField("App rollover icon shows current conditions", options.changeAppRolloverIcon());
			add(_changeAppRolloverIconCheckBox);
			_changeAppNameCheckBox = new CheckboxField("App name is weather info", options.changeAppName());
			add(_changeAppNameCheckBox);
			
			_minFontSizeChoiceField = new ObjectChoiceField();
			_minFontSizeChoiceField.setLabel("Min font size:");
			add(_minFontSizeChoiceField);
			_minFontSizeChoiceField.setChoices(_fontSizes);
		}
		
		protected void makeMenu(Menu menu, int instance) {			
			menu.add(_newLocationMenuItem);
			MenuItem showLicenseMenuItem = new MenuItem(_resources.getString(nwsclientResource.ABOUT), 100, 10) {
				public void run()
				{
					displayLicense();
				}
			};
			menu.add(showLicenseMenuItem);
			super.makeMenu(menu, instance);
		}
		
		protected boolean keyChar(char key, int status, int time)
		{
			// UiApplication.getUiApplication().getActiveScreen().
			if (getLeafFieldWithFocus() == _newLocField && key == Characters.ENTER) {
				storeInterfaceValues();
				_newLocationMenuItem.run();
				return true; //I've absorbed this event, so return true
			} else {
				return super.keyChar(key, status, time);
			}
		}
		
		protected void setRecentLocationsChoiceField()
		{
			Vector locs = options.getLocations();
			String locArr[];
			locArr = new String[locs.size()];
			
			for (int i=0; i < locs.size(); i++) {
				LocationData loc = (LocationData)locs.elementAt(i);
				String address = loc.getLocality()+", "+loc.getArea();
				if (loc.getArea().equals(""))
					address = loc.getLocality(); 
				if (!loc.getCountry().equals("US"))
					address = loc.getLocality() + ", " + loc.getCountry();
				locArr[i] = address;
			}
			_recentLocationsChoiceField.setChoices(locArr);
		}
		
		public boolean storeInterfaceValues()
		{
			boolean changed = false;
			boolean useNws = options.useNws();
			if (useNws != _useNwsCheckBox.getChecked()) {
				changed = true;
				options.setUseNws(_useNwsCheckBox.getChecked());
			}
			boolean autoUpdateIcon = options.autoUpdateIcon();
			if (autoUpdateIcon != _autoUpdateIconCheckBox.getChecked()) {
				changed = true;
				options.setAutoUpdateIcon(_autoUpdateIconCheckBox.getChecked());
			}
			boolean metric = options.metric();
			if (metric != _metricCheckBox.getChecked()) {
				changed = true;
				options.setMetric(_metricCheckBox.getChecked());
			}
			int minFontSize = options.minFontSize();
			if (minFontSize != (_minFontSizeChoiceField.getSelectedIndex() * 2 + 10)) {
				changed = true;
				options.setMinFontSize(_minFontSizeChoiceField.getSelectedIndex() * 2 + 10);
			}
			boolean changeAppName = options.changeAppName();
			if (changeAppName != _changeAppNameCheckBox.getChecked()) {
				changed = true;
				options.setChangeAppName(_changeAppNameCheckBox.getChecked());
			}
			boolean changeAppRolloverIcon = options.changeAppRolloverIcon();
			if (changeAppRolloverIcon != _changeAppRolloverIconCheckBox.getChecked()) {
				changed = true;
				options.setChangeAppRolloverIcon(_changeAppRolloverIconCheckBox.getChecked());
			}
			return changed;
		}
		
		public void setInterfaceValues()
		{
			setRecentLocationsChoiceField();
			_useNwsCheckBox.setChecked(options.useNws());
			_metricCheckBox.setChecked(options.metric());
			_autoUpdateIconCheckBox.setChecked(options.autoUpdateIcon());
			_newLocField.setText("");
			_minFontSizeChoiceField.setSelectedIndex((options.minFontSize() - 10)/2);
		}
		
		public void save()
		{
			if (_workerBusy) {
				synchronized(UiApplication.getEventLock()) {
					Dialog.alert(_resources.getString(nwsclientResource.BUSY));
				}
				return;
			}
			
			if (storeInterfaceValues()) {
				store.setContents(options);
				store.commit();
				if (checkDataConnectionAndWarn()) {
					refreshWeather(); // refresh
				}
			}
		}
		
	};
	
	/**
	 * Thread invoked by the alternate, non-gui entry point to 
	 * NWSClient. It will fetch the temperature and any weather alerts (US only)
	 * and update the application icon accordingly.
	 */
	class IconUpdaterThread implements Runnable
	{
		
		private LocationData location_;
		private boolean _firstInit = true;
		
		IconUpdaterThread()
		{
			// empty constructor
		}
		
		public void doUpdateIcon()
		{
			// Get the current temperature
			try {
				Hashtable conditions = getParseCurrentConditions(location_);
				Vector alerts = getAlerts(location_);
				boolean alert = (alerts.size() > 0);
				if (conditions != null && conditions.containsKey("temperature")) {
					String temp = (String)conditions.get("temperature");
					String cond = (String)conditions.get("condition");
					
					updateIcon(location_, temp, cond, alert);
					
					if (options.changeAppRolloverIcon()) {
						String rolloverIconUrl = (String)conditions.get("icon_url");
						// get the rollover icon
						final EncodedImage img = _bitmapProvider.fetchBitmap(rolloverIconUrl);
						_bitmapProvider.setRolloverIcon(img);
					}
					
				} else {
					System.err.println("Error getting icon current conditions: null current conditions");
				}
			} catch (ParseError pe) {
				System.err.println("Error parsing current conditions for icon: "+pe.getMessage());
			} catch (IOException ioe) {
				System.err.println("I/O Error getting current conditions for icon: "+ioe.getMessage());
			} catch (Exception e) {
				System.err.println("Error getting current conditions for icon: " + e.getMessage());
			}
			
		}
		
		public void run()
		{
			for(;;) {
				try {
					// Check the runtime store for new options
					getOptionsFromStore();
					// Make sure the user wants us to update the icon and we have data service
					if (options.autoUpdateIcon() == false 
						|| !RadioInfo.isDataServiceOperational()) 
					{
						// do nothing but sleep for ten second intervals
						Thread.sleep(10000);
						continue;
					}
					
					// Check for a new location coming in...
					LocationData newLoc = options.getCurrentLocation();
					// If there's any change to the location (including update) re-fetch it
					if (newLoc != null && (newLoc != location_ 
						|| newLoc.getLastUpdated() != location_.getLastUpdated())) 
					{
						location_ = newLoc;
					}
					
					if (location_ == null) {
						Thread.sleep(10000); // wait 10 seconds for incoming location
						continue;
					}
					
					// Time to update?
					long now = System.currentTimeMillis();
					long thisInterval = now - location_.getLastUpdated();
					
					getOptionsFromStore();
					if (thisInterval >= UPDATE_INTERVAL || _firstInit) {
						_firstInit = false;
						doUpdateIcon();
						location_.setLastUpdated(now);
					} else {
						// Wait four seconds
						Thread.sleep(UPDATE_INTERVAL);
					}
				} catch (InterruptedException ie) {
					return;
				}
			}
		}
		
	}
	
	/**
	 * This does the work of connecting to the data service and getting updated
	 * forecasts and current conditions in a separate thread from the UI.
	 */
	class WorkerThread extends Thread
	{
		boolean stop_ = false;
		
		public void findNewLocation(final String newLocationInput)
		{
			synchronized(UiApplication.getEventLock()) {
				_mainScreen.setStatusText(_resources.getString(nwsclientResource.GETTING_LOCATION));
				_mainScreen.setStatusVisible(true);
			}
			LocationData newLoc = null;
			try {
				newLoc = getLocationData(newLocationInput);
				if (newLoc.getCountry().equals("US")) {
					// Get the ICAO name of the weather station...
					findNearestWeatherStation(newLoc);
				}
			} catch (AmbiguousLocationException e) {
				// choose a more specific address?
				invokeLater(new Runnable() {
					public void run() {
						// Remove the getting location message...
						_mainScreen.setStatusVisible(false);
						Dialog.alert("Choose a more specific address");
					}
				});
			} catch (NotFoundException e) {
				// Couldn't find it at all...
				invokeLater(new Runnable() {
					public void run() {
						_mainScreen.setStatusVisible(false);
						Dialog.alert("Could not find location");
					}
				});
			} catch (Exception e) {
				final String msg = e.getMessage();
				UiApplication.getUiApplication().invokeLater(new Runnable() {
					public void run() {
						// Remove the getting location message...
						_mainScreen.setStatusVisible(false);
						Dialog.alert("Error getting location: "+msg);
					}
				});
			}
			
			if (newLoc != null) {
				
				UiApplication.getUiApplication().invokeLater(new Runnable() {
					public void run() {
						_mainScreen.setStatusVisible(false);
					}
				});
				
				UiApplication.getUiApplication().invokeLater(new Runnable() {
					public void run() {
						if (_optionsScreen.isDisplayed())
							_optionsScreen.close();
					}
				});
				options.setCurrentLocation(newLoc);
				store.setContents(options);
				store.commit();
			}
		}
		
		public void run()
		{
			for(;;) {
				
				String newLocationInput = getNewLocation();
				if (newLocationInput != null) {
					_workerBusy = true;
					findNewLocation(newLocationInput);
					setNewLocation(null); // clear this out
				}
				
				LocationData location_ = options.getCurrentLocation();
				
				if (location_ == null) {
					// wait for valid location input
					_workerBusy = false;
					try {
						Thread.sleep(UPDATE_INTERVAL);
						continue;
					} catch (InterruptedException e) {
						continue;
					}
				}
				
				if (RadioInfo.isDataServiceOperational()) {
					_workerBusy = true;
					getDisplayWeather(location_);
					_workerBusy = false;
					try {
						Thread.sleep(UPDATE_INTERVAL);
					} catch (InterruptedException e) {
						continue;
					}
				} else {
					_workerBusy = false;
					// wait for radio service
					synchronized(UiApplication.getEventLock()) {
						_mainScreen.setStatusText(_resources.getString(nwsclientResource.WAITING_FOR_DATA));
						_mainScreen.setStatusVisible(true);
					}
					try {
						Thread.sleep(4000); // sleep for 4 seconds
					} catch (InterruptedException e) {
						continue;
					}
					
					if (start_ && input_ != null) {
						UiApplication.getUiApplication().invokeLater(new Runnable() {
							public void run() {
								final MessageScreen msgScreen = new MessageScreen("Getting location...");
								pushScreen(msgScreen);
							}
						});
						LocationData newLoc = null;
						try {
							newLoc = getLocationData(input_);
							if (newLoc.getCountry().equals("US")) {
								// Get the ICAO name of the weather station...
								findNearestWeatherStation(newLoc);
							}
						} catch (AmbiguousLocationException e) {
							// choose a more specific address?
							invokeLater(new Runnable() {
								public void run() {
									// Remove the getting location message...
									UiApplication.getUiApplication().popScreen(UiApplication.getUiApplication().getActiveScreen());
									Dialog.alert("Choose a more specific address");
								}
							});
						} catch (NotFoundException e) {
							// Couldn't find it at all...
							invokeLater(new Runnable() {
								public void run() {
									UiApplication.getUiApplication().popScreen(UiApplication.getUiApplication().getActiveScreen());
									Dialog.alert("Could not find location");
								}
							});
						} catch (Exception e) {
							final String msg = e.getMessage();
							UiApplication.getUiApplication().invokeLater(new Runnable() {
								public void run() {
									// Remove the getting location message...
									UiApplication.getUiApplication().popScreen(UiApplication.getUiApplication().getActiveScreen());
									Dialog.alert("Error getting location: "+msg);
								}
							});
						}
						
						if (newLoc != null) {
							
							UiApplication.getUiApplication().invokeLater(new Runnable() {
								public void run() {
									// Remove the getting location message...
									UiApplication.getUiApplication().popScreen(UiApplication.getUiApplication().getActiveScreen());
								}
							});
							
							UiApplication.getUiApplication().invokeLater(new Runnable() {
								public void run() {
									if (optionsScreen_.isDisplayed())
										optionsScreen_.close();
								}
							});
							options.setCurrentLocation(newLoc);
							store.setContents(options);
							store.commit();
							refreshWeather();
						}
						start_ = false;
					}
				}
			}
		}
		
	}
	
	// STATIC METHODS
	
	 /**
	 * Instantiate the new application object and enter the event loop.
	 * <p>
	 * NwsClient has two entry points. nwsclientgui starts the main application
	 * interface while nwsclient runs the application icon updater daemon. See
	 * the NwsClient constructor for more information.
	 *
	 * @param args "gui" loads the main application interface
	 * If args length == 0 then NwsClient starts the icon updater daemon.
	 */
	public static void main(String[] args)
	{
		if ( args != null && args.length > 0 && args[0].equals("gui") ){
			//main entry point
			new NwsClient(false).enterEventDispatcher();
		} else {
			new NwsClient(true).enterEventDispatcher();
		}
	}
	
	public static String fahrenheitToCelsius(int f)
	{
		int celsius = (int)(((double)f - 32.0) / 1.8 + 0.5);
		return String.valueOf(celsius);
	}
	
	public static synchronized void getOptionsFromStore()
	{
		store = PersistentStore.getPersistentObject(ID);
		Object sync = PersistentStore.getSynchObject();
		synchronized(sync) {
			if(store.getContents()==null) {
				store.setContents( new NwsClientOptions() );
			}
			options = (NwsClientOptions)store.getContents();
		}
	}
	
	public static synchronized void updateIcon(final LocationData loc, 
								String temp, String condition, boolean alert)
	{
		if (!HomeScreen.supportsIcons()) 
			return;
		
		// bigIcon likely to be true on the Blackberry Storm
		boolean bigIcon = (HomeScreen.getPreferredIconWidth() > 48);
		
		int lOffset = (bigIcon) ? 36 : 22; // left offset, two chars long
		FontFamily fontfam[] = FontFamily.getFontFamilies();
		int smallSize = (bigIcon) ? 18 : 12;
		Font smallFont = fontfam[0].getFont(FontFamily.SCALABLE_FONT, smallSize);
		
		// Strip off anything after a decimal point
		int decPos = temp.indexOf('.');
		if (decPos != -1) {
			// We've got a decimal point, get rid of it
			temp = temp.substring(0, decPos);
		}
		
		if (temp.length() == 1) {
			// Single digits!
			lOffset = (bigIcon) ? 41 : 25;
		} else if (temp.length() == 3) { 
			// move to the left if 3 chars long
			lOffset = (bigIcon) ? 32 : 20;
			smallSize = (bigIcon) ? 16 : 10;
			smallFont = fontfam[0].getFont(FontFamily.SCALABLE_FONT, smallSize);
		} else if (temp.length() > 3) {
			// Crazy temperature!
			lOffset = 32;
			temp = "err";
			smallSize = (bigIcon) ? 16 : 10;
			smallFont = fontfam[0].getFont(FontFamily.SCALABLE_FONT, smallSize);
		}
		Bitmap bg = Bitmap.getBitmapResource("icon.png");
		Graphics gfx = new Graphics(bg);
		if (alert) {
			gfx.setColor(0xffff33); // yellow text
			if (bigIcon)
				gfx.fillArc(8, 39, 28, 28, 0, 360);
			else
				gfx.fillArc(5, 18, 16, 16, 0, 360);
			gfx.setColor(0xff3333); // red background
			if (bigIcon)
				gfx.drawArc(10, 41, 24, 24, 0, 360);
			else
				gfx.drawArc(6, 19, 14, 14, 0, 360);
			Font boldFont = smallFont.derive(Font.BOLD);
			gfx.setFont(boldFont);
			if (bigIcon) {
				gfx.drawText("!", 18, 44);
			} else {
				gfx.drawText("!", 11, 20); // Exclamation point
			}
		}
		gfx.setFont(smallFont);
		if (bigIcon) {
			gfx.drawText(temp, lOffset, 29);
		} else {
			gfx.drawText(temp, lOffset, 12);
		}
		HomeScreen.updateIcon(bg, 1);
		
		if (options.changeAppName()) { 
			// App name is the temperature, current condition string
			String tempType = (options.metric()) ? "C, " : "\u00b0F, ";
			String appName = (loc.getLocality() + ": " + temp + tempType + condition);
			HomeScreen.setName(appName, 1);
		} else {
			HomeScreen.setName("NWSClient", 1);
		}
		
		if (!options.changeAppRolloverIcon()) {
			// If we're not changing the rollover icon make
			// sure the rollover is the same as the regular icon
			HomeScreen.setRolloverIcon(bg, 1);
		}
		
	}
	
	/* Class methods */
	
	public String getNewLocation()
	{
		// Return a copy of the string
		synchronized(this) {
			return _newLocation;
		}
	}
	
	
	public void setNewLocation(String newLocation)
	{
		synchronized(this) {
			_newLocation = newLocation;
		}
	}
	
	/**
	 * For any location--US or international--fetch the necessary url, parse the  
	 * returned XML, and return a hashtable of current_conditions.
	 * @param location A valid LocationData object
	 * @return	   A hashtable of the current condition observations
	 */
	public Hashtable getParseCurrentConditions(LocationData location) throws IOException, ParseError
	{
		Hashtable parsed = new Hashtable();
		if (location.getCountry().equals("US") && options.useNws()) {
			// USA / NWS
			
			if (location.getIcao().equals(""))
				throw new RuntimeException("Could not get nearest weather station");
			
			HttpHelper.Connection conn = null;
			try {
				conn = HttpHelper.getUrl(NWS_CURRENT_URL+location.getIcao()+".xml");
				parsed = parseUSCurrentConditions(conn.is);
			} catch (IOException ioe) {
				// Pass on the error
				throw new IOException(ioe.getMessage());
			} finally {
				// Always close our http connection
				if (conn != null)
					conn.close();
			}
			
		} else {
			// International / Google
			Hashtable gw = getGoogleWeather(location);
			if (gw != null && gw.containsKey("current_conditions")) {
				parsed = (Hashtable)gw.get("current_conditions");
			}
		}
		return parsed;
	}
	
	// menu items
	// cache menu items for reuse
	
	private MenuItem optionsMenuItem_ = new MenuItem(_resources.getString(nwsclientResource.OPTIONS), 110, 10) {
		public void run()
		{
			viewOptions();
		}
	};
	
	private MenuItem refreshMenuItem_ = new MenuItem(_resources.getString(nwsclientResource.REFRESH), 110, 10) {
		public void run()
		{
			if (_workerBusy) {
				synchronized(UiApplication.getEventLock()) {
					Dialog.alert(_resources.getString(nwsclientResource.BUSY));
				}
				return;
			}
			if (checkDataConnectionAndWarn())
				refreshWeather();
		}
	};
	
	private MenuItem _newLocationMenuItem = new MenuItem(_resources.getString(nwsclientResource.GET_FORECAST), 100, 10) {
		public void run()
		{
			if (_newLocField.getText().length() > 0) {
				if (_workerBusy) {
					synchronized(UiApplication.getEventLock()) {
						Dialog.alert(_resources.getString(nwsclientResource.BUSY));
					}
					return;
				}
				
				if (checkDataConnectionAndWarn()) {
					if (_optionsScreen.isDisplayed()) {
						_optionsScreen.storeInterfaceValues();
						_optionsScreen.close();
					}
					setNewLocation(_newLocField.getText());
					refreshWeather();
				}
			} else {
				synchronized(UiApplication.getEventLock()) {
					Dialog.alert(_resources.getString(nwsclientResource.BUSY));
				}
			}
		}
	};
	
	
	// Methods
	
	// constructor
	public NwsClient(boolean autostart)
	{	
		// Don't need to start the bitmpaProvider--it will start on demand
		_bitmapProvider = new BitmapProvider();
		
		if (autostart) {
			_foreground = false;
			// Alternate entry point
			ApplicationManager myApp = ApplicationManager.getApplicationManager();
			boolean keepGoing = true;
			while(keepGoing) {
				if (myApp.inStartup()) {
					//The BlackBerry is still starting up, sleep for 1 second.
					try {
						Thread.sleep(1000);
					} catch (Exception ex) {
						System.err.println("Error sleeping entry thread: "+ex.toString());
					}
				} else {
					keepGoing = false;
					// Start the icon updater thread
					_workerThread = new Thread(new IconUpdaterThread());
					_workerThread.start();
				}
			}
		} else {
			_foreground = true;
			requestForeground();
			// started by the user
			_mainScreen = new NwsClientScreen("NWSClient", "");
			_optionsScreen = new OptionsScreen();
			displaySplash(_mainScreen);
			pushScreen(_mainScreen);
			
			_workerThread = new Thread(new WorkerThread());
			_workerThread.start();
			
			this.workerThread_ = this.new WorkerThread();
			this.workerThread_.start();
			
			this.locationFinder_ = this.new LocationFinder();
			this.locationFinder_.start();
			
			// if no location go to the options screen
<<<<<<< HEAD:src/java/NwsClient.java
			if (options.getCurrentLocation() != null) {
				refreshWeather();
			} else {
=======
			if (options.getCurrentLocation() == null) {
>>>>>>> master:src/java/NwsClient.java
				viewOptions();
			}
		}
	}
	
	public void getLinkInBrowser(String url)
	{
		BrowserSession sess = Browser.getDefaultSession();
		sess.displayPage(url);
	}
	
	protected boolean acceptsForeground()
	{
		return _foreground;
	}
	
	/**
	 * Check to make sure we have a data connection. 
	 * If not, show a warning dialog and return false.
	 */
	private boolean checkDataConnectionAndWarn()
	{
		if (!RadioInfo.isDataServiceOperational()) {
			synchronized(UiApplication.getEventLock()) {
				_mainScreen.setStatusText(_resources.getString(nwsclientResource.WAITING_FOR_DATA));
				_mainScreen.setStatusVisible(true);
				Dialog.alert(_resources.getString(nwsclientResource.NO_DATA));
			}
			return false;
		}
		return true;
	}
	
	private void refreshWeather()
<<<<<<< HEAD:src/java/NwsClient.java
	{
		setLastUpdated(0);
	}
	
	private synchronized void setLastUpdated(long lastUpdated)
	{
		// This will effectively notify the Icon Updater thread that we've updated
		LocationData loc = options.getCurrentLocation();
		if (loc != null) {
			loc.setLastUpdated(lastUpdated);
		}
=======
	{
		this._workerThread.interrupt();
>>>>>>> master:src/java/NwsClient.java
	}
	
	
	/**
	 * View the various options for this application
	 */
	public void viewOptions() 
	{
		_optionsScreen.setInterfaceValues();
		pushScreen(_optionsScreen);
	}
	
	private boolean findNearestWeatherStation(LocationData loc)
	{
		WeatherStation tmp = new WeatherStation();
		WeatherStation weatherStation = tmp.findNearest(loc.getLat(), loc.getLon());
		loc.setIcao(weatherStation.getName());
		loc.setIcaoLat(weatherStation.getLat()); // latitude in radians
		loc.setIcaoLon(weatherStation.getLon()); // longitude in radians
		
		return true;
	}
	
	private void displayGoogleMap(final LocationData location)
	{
		/* from http://www.blackberryforums.com/developer-forum/143263-heres-how-start-google-maps-landmark.html */
		
		int mh = CodeModuleManager.getModuleHandle("GoogleMaps");
		if (mh == 0) {
			Dialog.alert("Google Maps is not installed");
			return;
		}
		
		String address = location.getLocality()+", "+location.getArea();
		if (location.getArea().equals(""))
			address = location.getLocality(); 
		if (!location.getCountry().equals("US"))
			address = location.getLocality() + ", " + location.getCountry();
		
		URLEncodedPostData uepd = new URLEncodedPostData(null, false);
		uepd.append("action","LOCN");
		uepd.append("a", "@latlon:"+location.getLat()+","+location.getLon());
		uepd.append("title", address);
		uepd.append("description", location.getIcao());
		String[] args = { "http://gmm/x?"+uepd.toString() };
		ApplicationDescriptor ad = CodeModuleManager.getApplicationDescriptors(mh)[0];
		ApplicationDescriptor ad2 = new ApplicationDescriptor(ad, args);
		try {
			ApplicationManager.getApplicationManager().runApplication(ad2, true);
		} catch (ApplicationManagerException e) {
			Dialog.alert("Error launching Google Maps: "+e.toString());
		}
	}
		
	private LocationData getLocationData(String userAddress) throws NotFoundException {
		// Encode the URL (from net.rim.blackberry.api.browser)
		URLEncodedPostData post = new URLEncodedPostData(null, true);
		post.append("q", userAddress);
		post.append("output", "xml");
		post.append("key", GOOGLE_API_KEY);
		HttpHelper.Connection conn = null; 
		// Parse the XML from the Google Maps Geocoder
		try {
			conn = HttpHelper.getUrl(GOOGLE_MAPS_URL+"?"+post.toString());
			
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document document = builder.parse(conn.is);
			
			LocationData loc = new LocationData();
			loc.setUserAddress(userAddress);
			
			loc.loadFromXml(document);
			return loc;

		} catch (IOException ioe) {
			throw new RuntimeException(ioe.toString());
		} catch (ParseError pe) {
			throw new RuntimeException(pe.toString());
		} catch (ParserConfigurationException pce) {
			throw new RuntimeException(pce.toString());
		} catch (SAXException se) {
			throw new RuntimeException(se.toString());
		} finally {
			// Always close the http connection
			if (conn != null)
				conn.close();
		}
	}
	
<<<<<<< HEAD:src/java/NwsClient.java
	private void setNewLocation(final String userAddress)
	{
		locationFinder_.find(userAddress);
	}
	
=======
>>>>>>> master:src/java/NwsClient.java
	private Calendar parseTime(String timeStr)
	{
		// I have to parse the date myself because RIM's SimpleDateFormat doesn't
		// implement the 'parse' method. Lame.
		String part;
		Calendar cal = Calendar.getInstance(); // new calendar object
		
		// Time string looks like '2008-10-12T20:00:00-04:00'
		// Year
		part = timeStr.substring(0,4); // strip off the time zone information
		cal.set(Calendar.YEAR, Integer.parseInt(part));
		// Month
		part = timeStr.substring(5,7);
		cal.set(Calendar.MONTH, Integer.parseInt(part)-1); // 0-based month
		// Day
		part = timeStr.substring(8,10);
		cal.set(Calendar.DATE, Integer.parseInt(part));
		// Hour
		part = timeStr.substring(11,13);
		cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(part)); // 0-based hour
		// Minute
		part = timeStr.substring(14,16);
		cal.set(Calendar.MINUTE, Integer.parseInt(part));
		// Get the timezone value from the string and set it
		TimeZone tz = TimeZone.getTimeZone(timeStr.substring(19,22));
		cal.setTimeZone(tz);
		// Return a date object
		return cal;
	}
	
	private Hashtable getNDFDTimeSeries(Document document) 
	{
		Hashtable times = new Hashtable();
		try {
			Element root = document.getDocumentElement();
			NodeList nl = document.getElementsByTagName("time-layout");
			for (int i=0; i < nl.getLength(); i++) {
				Node myNode = nl.item(i);
				Node keyNode = XmlHelper.getNode(myNode, "layout-key");
				if (keyNode == null) 
					continue;
				String key = XmlHelper.getNodeText(keyNode);
				if (key.equals(""))
					continue;
				// Place to save this key's list of times
				Vector fcTimes = new Vector();
				
				// Loop through children of this time-layout
				Node tmpChildNode = myNode.getFirstChild();
				while(tmpChildNode != null) {
					if (tmpChildNode.getNodeType() == Node.ELEMENT_NODE) {
						Element child = (Element)tmpChildNode;
						
						if (child.getTagName().equals("start-valid-time")) {
							TimeKey myTime = new TimeKey();
							myTime.startTime = parseTime(XmlHelper.getNodeText(child)); // Convert time string to timestamp
							myTime.periodName = null;
							// Look for the string name of this time period, e.g. "Thursday"
							if (child.hasAttribute("period-name")) {
								myTime.periodName = child.getAttribute("period-name");	
							}
							fcTimes.addElement(myTime);
						}
					}
					// next time node
					tmpChildNode = tmpChildNode.getNextSibling();
				}
				times.put(key, fcTimes);
			}
		} catch (ParseError e) {
			synchronized(UiApplication.getEventLock()) {
				Dialog.alert("Error parsing NDFD timeseries: "+e.toString());
			}
		}
		
		return times;
	}
	
	private Vector flattenNDFD(Hashtable weather) {
		// Flatten the NDFD observations into a vector of hashes just like the 
		// Google weather forecast so we can display them the same way
		
		Vector fc = new Vector();
		Vector conditions = new Vector();
		// Roll up the NDFD into a hash of days
		if (weather.containsKey("weather")) {
			Hashtable types = (Hashtable)weather.get("weather");
			if (types.containsKey("weather-conditions")) {
				conditions = (Vector)types.get("weather-conditions");
			}
		}
		
		for (int i = 0; i < conditions.size(); i++) {
			Hashtable day = new Hashtable();
			Observation myCond = (Observation)conditions.elementAt(i);
			String temp = "";
			Observation tempObs;
			if (myCond.time.startTime.get(Calendar.HOUR_OF_DAY) < 18) {
				// Day!
				tempObs = getObservationByIndex(weather, "temperature", "maximum", i/2);
			} else {
				// Night!
				tempObs = getObservationByIndex(weather, "temperature", "minimum", i/2);
			}
			
			if (tempObs != null) {
				if (options.metric() && tempObs.units.equals("Fahrenheit")) {
					temp = fahrenheitToCelsius(Integer.valueOf(tempObs.value).intValue()) + " C";
				} else {
					temp = tempObs.value+" "+tempObs.units;
				}
			}
			
			// Get probability of precipitation
			Observation popObs = getObservationByIndex(weather, "probability-of-precipitation", "12 hour", i); 
			
			// Get the icon
			Observation iconUrl = getObservationByIndex(weather, "conditions-icon", "forecast-NWS", i);
			
			day.put("day_of_week", myCond.time.periodName); // e.g. "Thursday" or "Thanksgiving Day"
			day.put("temperature", temp);
			day.put("condition", myCond.value);
			if (iconUrl != null)
				day.put("icon_url", iconUrl.value);
			if (popObs != null)
				day.put("probability-of-precipitation", popObs.value + "% chance precipitation"); 
			
			fc.addElement(day);
		}
		return fc;
	}

	/**
	 * Parse an NDFD XML response element and return the supplied observations 
	 * in a hash indexed by observation time.
	 */
	private Hashtable getNDFDObservations(Element root, Hashtable times, 
											final String[] observations) 
	{
		/*
		fc = Hashtable(	 
			'obsName' => Hashtable( 
				'obsType' => Array(
						Observation,
						Observation,
						. . .
								)
			)
		 )
		*/
		
		// build up our hash of hashes
		Hashtable fc = new Hashtable();
		
		for (int h=0; h < observations.length; h++) {
			String obsName = observations[h];
			
			NodeList obsNodes = root.getElementsByTagName(obsName);
			
			Hashtable obsTypes = new Hashtable();
			
			for (int i=0; i < obsNodes.getLength(); i++) {
				Element obs = (Element)obsNodes.item(i);
				if (obs.hasAttribute("time-layout")) {
					String timeLayout = obs.getAttribute("time-layout");
					if (!times.containsKey(timeLayout)) {
						System.err.println("Warning: malformed NDFD response, missing time-layout key '"+timeLayout+"'");
						continue;
					}
					Vector obsTimes = (Vector)times.get(timeLayout);
					
					String obsType = "";
					if (obs.hasAttribute("type")) {
						obsType = obs.getAttribute("type");
					} else if (obsName.equals("weather")) { 
						obsType = "weather-conditions";
					} else if (obsName.equals("hazards")) {
						obsType = "hazard-conditions";
					}
					
					String obsUnits = "";
					if (obs.hasAttribute("units"))
						obsUnits = obs.getAttribute("units");
						
					Vector myObservations;
					if (obsTypes.containsKey(obsType)) {
						myObservations = (Vector)obsTypes.get(obsType);
					} else {
						myObservations = new Vector();
						obsTypes.put(obsType, observations);
					}
					
					NodeList typeObsNodes = obs.getChildNodes();
					int counter = 0;
					for (int j=0; j < typeObsNodes.getLength(); j++) {
						Node tmpNode = typeObsNodes.item(j);
						
						if (tmpNode.getNodeType() == Node.ELEMENT_NODE) {
							
							Observation theObs = new Observation();
							theObs.time = (TimeKey)obsTimes.elementAt(counter);
							theObs.units = obsUnits;
							
							Element obsChild = (Element)tmpNode;
							// Watch for nil values and skip them...
							String nil = obsChild.getAttribute("xsi:nil");
							if (nil.equals("true")) {
								counter++;
								continue;
							}
							
							if (obsChild.getTagName().equals("value")) {
								if (counter >= obsTimes.size()) {
									// we've run out of times...
									System.err.println("Not enough times for "+obsType);
									continue;
								}
								theObs.value = XmlHelper.getNodeText(obsChild);
								myObservations.addElement(theObs);
								counter++;
							} else if (obsChild.getTagName().equals("icon-link")) {
								// It's an icon!
								if (counter >= obsTimes.size()) {
									// we've run out of times...
									System.err.println("Not enough times for "+obsType);
									continue;
								}
								theObs.value = XmlHelper.getNodeText(obsChild);
								myObservations.addElement(theObs);
								counter++;
							} else if (obsChild.getTagName().equals("hazard-conditions")) {
								// Hazards/warnings are a weird case
								if (counter >= obsTimes.size()) {
									// we've run out of times...
									System.err.println("Not enough times for "+obsType);
									continue;
								}
								// Actual hazards are nested even further down...
								NodeList myHazards = obsChild.getElementsByTagName("hazard");
								if (myHazards.getLength() > 0) {
									Element myHazardEl = (Element)myHazards.item(0);
									theObs.value = myHazardEl.getAttribute("phenomena");
									theObs.units = myHazardEl.getAttribute("significance");
									myObservations.addElement(theObs);
									// try to get the url
									NodeList myUrls = myHazardEl.getElementsByTagName("hazardTextURL");
									if (myUrls.getLength() > 0) {
										theObs.url = XmlHelper.getNodeText(myUrls.item(0));
									}
								}
								counter++;
							} else if (obsChild.getTagName().equals(obsType)) {
								// It's a condition -- i.e. "Partly Cloudy"
								if (counter >= obsTimes.size()) {
									// we've run out of times...
									System.err.println("Not enough times for "+obsType);
									continue;
								}
								theObs.value = obsChild.getAttribute("weather-summary");
								myObservations.addElement(theObs);
								counter++;
							} 
						}
						// Add our list of observations to the hashtable
						obsTypes.put(obsType, myObservations);
					}
				}
			}
			// Add this hash of observation types to the forecast hash
			fc.put(obsName, obsTypes);
		}
		return fc;
	}
	
	private static Hashtable parseUSCurrentConditions(InputStream is) throws ParseError
	{
		Hashtable cc = new Hashtable();
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document document = builder.parse(is);
				
			Element root = document.getDocumentElement();
			
			// weather "Partly Cloudy"
			cc.put("condition", XmlHelper.getValue(root, "weather"));
			// observation_time "Last Updated on Feb 24, 10:53 pm PST"
			cc.put("observation_time", XmlHelper.getValueIfExists(root, "observation_time"));
			// temperature "55"
			if (options.metric()) {
				cc.put("temperature", XmlHelper.getValue(root, "temp_c"));
			} else {
				cc.put("temperature", XmlHelper.getValue(root, "temp_f"));
			}
			// relative_humidity "65"
			cc.put("relative_humidity", XmlHelper.getValueIfExists(root, "relative_humidity"));
			// location "Oakland, CA"
			cc.put("location", XmlHelper.getValueIfExists(root, "location"));
			// wind_str "From the west at 12mph"
			cc.put("wind", XmlHelper.getValueIfExists(root, "wind_string")); 
			// pressure_string "29.90" (1012.4 mb)"
			cc.put("pressure", XmlHelper.getValueIfExists(root, "pressure_string"));
			// dewpoint_string "27 F (-3 C)"
			cc.put("dewpoint", XmlHelper.getValueIfExists(root, "dewpoint_string"));
			// heat_index_string "NA"
			cc.put("heat_index", XmlHelper.getValueIfExists(root, "heat_index_string"));
			// windchill_string "31 F (-1 C)"
			cc.put("windchill", XmlHelper.getValueIfExists(root, "windchill_string"));
			// "visibility" 
			cc.put("visibility", XmlHelper.getValueIfExists(root, "visibility_mi"));
			
			String iconUrl = XmlHelper.getValue(root, "icon_url_base") +
								XmlHelper.getValue(root, "icon_url_name");
			cc.put("icon_url", iconUrl);
		} catch (ParseError pe) {
			throw new ParseError(pe.toString());
		} catch (ParserConfigurationException pce) {
			throw new RuntimeException(pce.toString());
		} catch (SAXException se) {
			throw new RuntimeException(se.toString());
		} catch (IOException ioe) {
			throw new RuntimeException(ioe.toString());
		}
		return cc;
	}
	
	/**
	 * Parse an inputstream containing an NDFD XML response and return a 
	 * hashtable of observations indexed by time.
	 * @param is A valid XML InputStream
	 * @param observations An array of observation names to fetch
	 * @return A hashtable of forecast observations indexed by time
	 */
	private Hashtable parseNDFD(InputStream is, final String[] observations) 
	{
		Hashtable fc = new Hashtable();
		
		try { 
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document document = builder.parse(is);
				
			Element root = document.getDocumentElement();
			
			// Parse the times legend 
			Hashtable times = getNDFDTimeSeries(document);
			
			// Get forecast values...
			fc = getNDFDObservations(root, times, observations);
			
		} catch (ParserConfigurationException pce) {
			throw new RuntimeException(pce.toString());
		} catch (SAXException se) {
			throw new RuntimeException(se.toString());
		} catch (IOException ioe) {
			throw new RuntimeException(ioe.toString());
		}
		
		return fc;
	}
	
	private Observation getObservationByIndex(Hashtable weather, String obsName, String type, int index) 
	{
		if (weather.containsKey(obsName)) {
			Hashtable types = (Hashtable)weather.get(obsName);
			if (types.containsKey(type)) {
				Vector observations = (Vector)types.get(type);
				if (index < observations.size()) {
					Observation obs = (Observation)observations.elementAt(index);
					return obs;
				}
			}
		}
		return null;
	}
	
		
	private synchronized void clearScreen()
	{
		String statusText = _mainScreen.getStatusText();
		boolean statusVisible = _mainScreen.getStatusVisible();
		_mainScreen.deleteAll();
		_mainScreen.setStatusText(statusText);
		_mainScreen.setStatusVisible(statusVisible);
	}
	
	private void displayNWSCurrentConditions(final LocationData location, final Hashtable weather)
	{
		clearScreen();
		
		String address = location.getLocality()+", "+location.getArea();
		if (location.getArea().equals(""))
			address = location.getLocality();
		
		if (weather == null) {
			_mainScreen.add(new LabelField("Unable to fetch current conditions"));
			return;
		}
		
		String station = (String)weather.get("location");
		
		double range = WeatherStation.greatCircleDistance(
			Math.toRadians(location.getLat()), Math.toRadians(location.getLon()),
			location.getIcaoLat(), location.getIcaoLon());
		
		String bearing = WeatherStation.bearing(
			Math.toRadians(location.getLat()), Math.toRadians(location.getLon()),
			location.getIcaoLat(), location.getIcaoLon());
		
		Formatter fmt = new Formatter(null);
		String rangeBearing = fmt.formatNumber(range, 1) + "mi "+bearing+" of forecast position";
		
		String conditionsLabel = (String)weather.get("condition");
		String temp = (String)weather.get("temperature");
		String observation_time = (String)weather.get("observation_time");
		String humidity = (String)weather.get("relative_humidity") + "%";
		String condIconUrl = (String)weather.get("icon_url");
		String wind = (String)weather.get("wind");
		String pressure = (String)weather.get("pressure");
		String dewpoint = (String)weather.get("dewpoint");
		String heat_index = (String)weather.get("heat_index");
		String windchill = (String)weather.get("windchill");
		String vis = (String)weather.get("visibility");
		
		// Grab some fonts...
		FontFamily fontfam[] = FontFamily.getFontFamilies();
		Font smallFont = fontfam[0].getFont(FontFamily.SCALABLE_FONT, options.minFontSize()+1);
		Font tinyFont = smallFont.derive(Font.PLAIN, options.minFontSize());

		// Make the title label
		_mainScreen.setTitle(new LabelStatusField(address, LabelField.ELLIPSIS, "status..."));
		
		VerticalFieldManager main = new VerticalFieldManager(Manager.USE_ALL_WIDTH);
<<<<<<< HEAD:src/java/NwsClient.java
		mainScreen_.add(main);
=======
		_mainScreen.add(main);
>>>>>>> master:src/java/NwsClient.java
		
		// Current Conditions Label
		LabelField lbl = new LabelField(_resources.getString(nwsclientResource.CURRENT_CONDITIONS_AT)+
									" "+location.getIcao());
		Font fnt = lbl.getFont().derive(Font.BOLD);
		lbl.setFont(fnt);
		main.add(lbl);
		
		// The human-readable name of the station to the right
		lbl = new LabelField(station, Field.FIELD_RIGHT);
		lbl.setFont(smallFont);
		main.add(lbl);
		
		// Range/bearing to the weatherstation
		lbl = new LabelField(rangeBearing, Field.FIELD_RIGHT);
		lbl.setFont(tinyFont);
		main.add(lbl);
		
		HorizontalFieldManager topHField = new HorizontalFieldManager();
		main.add(topHField);
		
		BitmapField currentCondBitmap = new BitmapField();
		topHField.add(currentCondBitmap);
		
		if (!condIconUrl.equals("")) {
			_bitmapProvider.getBitmap(condIconUrl, currentCondBitmap, options.changeAppRolloverIcon());
		}
		
		VerticalFieldManager topRightCol = new VerticalFieldManager();
		topHField.add(topRightCol);
		
		// Show the current conditions
		topRightCol.add(new RichTextField(conditionsLabel));
		// Show the temperature
		topRightCol.add(new RichTextField("Temp: "+temp));
		// Observation time
		RichTextField obsTime = new RichTextField(observation_time);
		obsTime.setFont(tinyFont);
		topRightCol.add(obsTime);
		
		String fields[] = { 
							"Relative humidity", humidity, "Wind", wind, 
							"Barometric pressure", pressure,
							"Dewpoint", dewpoint, "Heat Index", heat_index,
							"Windchill", windchill, "Visibility", (vis + " miles")
							};
		HorizontalFieldManager row = null;
		for (int i=0; i < fields.length; i++) {
			
			// LabelFields are dangerous -- check for crazy field length...
			if (fields[i].length() > 128) {
				// truncate if too long...
				fields[i] = fields[i].substring(0,128);
			}
			
			LabelField fld;
			if (i==0 || i%2 == 0) {
				row = new HorizontalFieldManager(Manager.USE_ALL_WIDTH);
				main.add(row);
				VerticalFieldManager leftCol = new VerticalFieldManager(); 
				row.add(leftCol);
				fld = new LabelField(fields[i], Field.FIELD_LEFT | Field.FOCUSABLE);
				fld.setFont(tinyFont);
				leftCol.add(fld);
			} else {
				VerticalFieldManager rightCol = new VerticalFieldManager(Manager.USE_ALL_WIDTH);
				row.add(rightCol);
				fld = new LabelField(fields[i],	 Field.FIELD_RIGHT | Field.FOCUSABLE);
				fld.setFont(tinyFont);
				rightCol.add(fld);
			}
		}
		
		// Forecast discussion link... 
		LinkField forecastDiscussionLink = new LinkField("Forecast discussion");
		FieldChangeListener listener = new FieldChangeListener() {
			public void fieldChanged(Field field, int context) {
				WfoStation tmp = new WfoStation();
				WfoStation wfoStation = tmp.findNearest(location.getLat(), location.getLon());
				String link = wfoStation.getForecastDiscussionLink();
				getLinkInBrowser(link);
			}
		};
		forecastDiscussionLink.setChangeListener(listener);
		main.add(forecastDiscussionLink);
		
		LinkField radarLink = new LinkField("Radar");
		FieldChangeListener radarListener = new FieldChangeListener() {
			public void fieldChanged(Field field, int context) {
				RadarStation tmp = new RadarStation();
				RadarStation rdr = tmp.findNearest(location.getLat(), location.getLon());
				getLinkInBrowser(NWS_RADAR_URL+rdr.getName());
			}
		};
		radarLink.setChangeListener(radarListener);
		main.add(radarLink);
		
		//btVField.add(btRightCol);
		main.add(new SeparatorField());
		
	}
	
	private void displayGoogleCurrentConditions(LocationData location, Hashtable weather)
	{
		clearScreen();
		
		if (weather == null) {
			_mainScreen.add(new LabelField("Unable to fetch current conditions"));
			return;
		} else if (weather.containsKey("temperature") && (weather.containsKey("condition"))) {
			updateIcon(location, (String)weather.get("temperature"), 
									(String)weather.get("condition"), false);
		}
		
		//String address = (String)weather.get("city");
		String address = location.getLocality()+", "+location.getCountry();
		
		String conditionsLabel = (String)weather.get("condition");
		String temp = (String)weather.get("temperature");
		String humidity = (String)weather.get("relative_humidity");
		String station = (String)weather.get("location");
		String condIconUrl = (String)weather.get("icon_url");
		String wind = (String)weather.get("wind");
		
		// Grab some fonts...
		FontFamily fontfam[] = FontFamily.getFontFamilies();
		Font smallFont = fontfam[0].getFont(FontFamily.SCALABLE_FONT, 12);
		Font tinyFont = smallFont.derive(Font.PLAIN, options.minFontSize());

		// Make the title label
		_mainScreen.setTitle(new LabelField(address, LabelField.ELLIPSIS));
		
		VerticalFieldManager main = new VerticalFieldManager(Manager.USE_ALL_WIDTH);
		_mainScreen.add(main);
		
		// Current Conditions Label
		LabelField lbl = new LabelField(_resources.getString(nwsclientResource.CURRENT_CONDITIONS),
							LabelField.ELLIPSIS);
		Font fnt = lbl.getFont().derive(Font.BOLD);
		lbl.setFont(fnt);
		main.add(lbl);
		
		// The human-readable name of the station to the right
		lbl = new LabelField(station, Field.FIELD_RIGHT);
		lbl.setFont(smallFont);
		main.add(lbl);
		
		HorizontalFieldManager topHField = new HorizontalFieldManager();
		main.add(topHField);
		
		BitmapField currentCondBitmap = new BitmapField();
		
		if (!condIconUrl.equals("")) {
			_bitmapProvider.getBitmap(condIconUrl, currentCondBitmap, options.changeAppRolloverIcon());
		}
		
		topHField.add(currentCondBitmap);
		
		VerticalFieldManager topRightCol = new VerticalFieldManager();
		topHField.add(topRightCol);
		
		// Show max/min temps
		topRightCol.add(new RichTextField(conditionsLabel));
		topRightCol.add(new RichTextField("Temp: "+temp));
		
		HorizontalFieldManager btHField = new HorizontalFieldManager(Manager.USE_ALL_WIDTH);
		main.add(btHField);
		
		// Bottom left col
		VerticalFieldManager btLeftCol = new VerticalFieldManager();
		
		// Bottom right col
		VerticalFieldManager btRightCol = new VerticalFieldManager(Manager.USE_ALL_WIDTH);
		
		
		String fields[] = { "Relative humidity", humidity, "Wind", wind };
		for (int i=0; i < fields.length; i++) {
			
			// LabelFields are dangerous -- check for crazy field length...
			if (fields[i].length() > 128) {
				// truncate if too long...
				fields[i] = fields[i].substring(0,128);
			}
			
			LabelField fld;
			
			if (i==0 || i%2 == 0) {
				fld = new LabelField(fields[i], (Field.FIELD_LEFT | Field.FOCUSABLE));
				fld.setFont(tinyFont);
				btLeftCol.add(fld);
			} else {
				fld = new LabelField(fields[i],	 Field.FIELD_RIGHT);
				fld.setFont(tinyFont);
				btRightCol.add(fld);
			}
		}
		btHField.add(btLeftCol);
		btHField.add(btRightCol);
		main.add(new SeparatorField());
		
	}
	
	/**
	 * Take a vector of hashes, each vector item representing a day's
	 * or 12 hour period's observations.
	 * <p>
	 * Each hashtable contains a number of observationName / value 
	 * pairs. 
	 * 
	 * @param location The LocationData object for the forecast
	 * @param forecast The forecast Vector object (see above)
	 *
	 */
	private void displayForecast(final LocationData location, final Vector forecast, 
															final String credit)
	{
		for (int i=0; i < forecast.size(); i++) {
			
			Hashtable day = (Hashtable)forecast.elementAt(i);
			
			String dayOfWeek = (String)day.get("day_of_week");
			String condition = (String)day.get("condition");
			String temperature = (String)day.get("temperature");
			
			String precip = null;
			if (day.get("probability-of-precipitation") != null) {
				// Only NWS supplies probability of precipitation
				precip = (String)day.get("probability-of-precipitation");
			}
			String iconUrl = (String)day.get("icon_url");
			
			// Lay out the elements on the screen
			LabelField lbl = new LabelField(dayOfWeek);
			Font fnt = lbl.getFont().derive(Font.BOLD);
			lbl.setFont(fnt);
			_mainScreen.add(lbl);
			
			BitmapField forecastBitmap = new BitmapField();
			
			if (!iconUrl.equals("")) {
				_bitmapProvider.getBitmap(iconUrl, forecastBitmap, false);
			}
			
			HorizontalFieldManager myHField = new HorizontalFieldManager();
			_mainScreen.add(myHField);
			
			myHField.add(forecastBitmap);
			
			FlowFieldManager rightCol = new FlowFieldManager();
			myHField.add(rightCol);
			
			// Show the observations
			rightCol.add(new RichTextField(condition));
			rightCol.add(new RichTextField(temperature));
			if (precip != null)
				rightCol.add(new RichTextField(precip));
			_mainScreen.add(new SeparatorField());
		}
		
		if (forecast.size() == 0) {
			_mainScreen.add(new RichTextField("Error: No NWS Forecast information found."));
		}
		
		// Google Maps link
		LinkField googleMapsLink = new LinkField("Google Map of "+location.getLocality());
		FieldChangeListener listener = new FieldChangeListener() {
			public void fieldChanged(Field field, int context) {
				displayGoogleMap(location);
			}
		};
		googleMapsLink.setChangeListener(listener);
		_mainScreen.add(googleMapsLink);
		
		displayCredit(credit, location.getLastUpdated());
	}
	
	private void displayAlerts(final Vector alerts)
	{
		DateFormat dateFormat = new SimpleDateFormat("ha E");
		Observation lastAlert = null;
		for (int i=0; i < alerts.size(); i++) {
			Observation alert = (Observation)alerts.elementAt(i);
			boolean last = false;
			if (i == (alerts.size()-1)) {
				// this is the last alert in the list!
				last = true;
			} else {
				// it's not the last alert but the next one is different
				Observation nextAlert = (Observation)alerts.elementAt(i+1);
				last = (!nextAlert.value.equals(alert.value) || !nextAlert.units.equals(alert.units));
			}
			
			if (last) {
				// We need to print!
				if (lastAlert != null) {
					Date alertDate = lastAlert.time.startTime.getTime();
					Date endDate = alert.time.startTime.getTime();
					final String alertUrl = alert.url;
					String startTimeStr = dateFormat.format(alertDate, new StringBuffer(), null).toString();
					String endTimeStr = dateFormat.format(endDate, new StringBuffer(), null).toString();
					LinkField warningField = new LinkField(alert.value+" "+alert.units+" "+startTimeStr+" - "+endTimeStr) {
						public void paint(Graphics graphics) {
							// Warning text is red
							graphics.setColor(0xff0000);
							super.paint(graphics);
						}
					};
					FieldChangeListener warningListener = new FieldChangeListener() {
						public void fieldChanged(Field field, int context) {
							getLinkInBrowser(alertUrl);
						}
					};
					warningField.setChangeListener(warningListener);
					_mainScreen.add(warningField);
				} else {
					Date alertDate = alert.time.startTime.getTime();
					String alertTimeStr = dateFormat.format(alertDate, new StringBuffer(), null).toString();
					_mainScreen.add(new RichTextField(alert.value+" "+alert.units+" "+alertTimeStr));
				}
				lastAlert = null;
			} else if (lastAlert == null) {
				lastAlert = alert;
			}
		}
		if (alerts.size() == 0) {
			_mainScreen.add(new RichTextField("No alerts or warnings"));
		}
		_mainScreen.add(new SeparatorField());
	}
	
	private void displayCredit(final String who, final long when)
	{
		DateFormat dateFormat = new SimpleDateFormat("h:mma E, MMM d, yyyy");
		Date lastUpdate = new Date(when); 
		String formattedDate = dateFormat.format(lastUpdate, new StringBuffer(), null).toString();
		String credit = "Downloaded at "+formattedDate+
						"\nfrom "+who+".";
		RichTextField creditField = new RichTextField(credit);
		Font small = creditField.getFont().derive(Font.PLAIN, options.minFontSize());
		creditField.setFont(small);
		_mainScreen.add(creditField);
	}
	
	/**
	 * Parses an input stream of iGoogle weather data and builds a hash 
	 * containing the current conditions hash and a vector representing the
	 * forecast data for all available days. 
	 * Hashtable(
	 *  'currentConditions' => Hashtable(
	 *			    'weather' => "Partly Cloudy"
	 *			    'temperature' => "45" . . .
	 *			   )
	 *  'forecast' => Vector( Hashtable(
	 *				    'day_of_week' => 'Sat'
	 *				    'weather' => "Rain"
	 *				    'temperature' => "50"
	 *				    'conditions-icon' => "http..."
	 *				    'wind' => "Wind: W at 10mph" 
	 *			   )
	 *
	 * @param is An inputStream from google 
	 */
	private static Hashtable parseGoogleWeather(final InputStream is) throws ParseError
	{			 
		Hashtable gw = new Hashtable();
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document document = builder.parse(is);
			
			NodeList nl = document.getElementsByTagName("weather");
			if (nl.getLength() > 0) {
				Element weather = (Element)nl.item(0);
				// It has a weather element
				
				// Check for error condition:
				NodeList problemNodes = weather.getElementsByTagName("problem_cause");
				if (problemNodes.getLength() > 0) {
					Element problemEl = (Element)problemNodes.item(0);
					String problem = problemEl.getAttribute("data");
					throw new IllegalArgumentException(problem+
						"No Google weather for location. Make sure you specify "+
						"'City, Country,' i.e. \"London, GB\" for international locations.");
				}
				
				String units = XmlHelper.getElementData(weather, "unit_system");
				
				// Get the current conditions
				NodeList ccNodes = weather.getElementsByTagName("current_conditions");
				if (ccNodes.getLength() == 0) 
					throw new ParseError("No current conditions found in Google Weather response.");
				Element ccEl = (Element)ccNodes.item(0);
				Hashtable cc = new Hashtable();
				cc.put("city", XmlHelper.getElementData(weather, "city")); // Google city name...
				cc.put("condition", XmlHelper.getElementData(ccEl, "condition"));
				if (options.metric()) {
					cc.put("temperature", XmlHelper.getElementData(ccEl, "temp_c"));
				} else {
					cc.put("temperature", XmlHelper.getElementData(ccEl, "temp_f"));
				}
				cc.put("relative_humidity", XmlHelper.getElementData(ccEl, "humidity"));
				cc.put("wind", XmlHelper.getElementData(ccEl, "wind_condition"));
				cc.put("icon_url", (GOOGLE_URL + XmlHelper.getElementData(ccEl, "icon")));
				// Store the current conditions
				gw.put("current_conditions", cc);
				
				// Get the forecast data as a vector
				Vector fc = new Vector();
				NodeList forecastDays = weather.getElementsByTagName("forecast_conditions");
				for (int i=0; i < forecastDays.getLength(); i++) {
					Element dayEl = (Element)forecastDays.item(i);
					Hashtable day = new Hashtable();
					
					day.put("day_of_week", XmlHelper.getElementData(dayEl, "day_of_week")); //dayEl.getAttribute("day_of_week")
					day.put("condition", XmlHelper.getElementData(dayEl, "condition"));
					String hi = XmlHelper.getElementData(dayEl, "high");
					String lo = XmlHelper.getElementData(dayEl, "low");
					if (options.metric() && !units.equals("SI")) {
						// Convert to metric...
						hi = fahrenheitToCelsius(Integer.valueOf(hi).intValue());
						lo = fahrenheitToCelsius(Integer.valueOf(lo).intValue());
					}
					day.put("temperature", (hi + " low: " + lo));
					// Get the icon url
					day.put("icon_url", (GOOGLE_URL + XmlHelper.getElementData(dayEl, "icon")));
					
					fc.addElement(day);
				}
				gw.put("forecast", fc);
				
			} else {
				// there was a problem...
				throw new ParseError("Google weather returned no data");
			}
			
		} catch(ParseError pe) {
			throw new ParseError(pe.toString());
		} catch (ParserConfigurationException pce) {
			throw new RuntimeException(pce.toString());
		} catch (SAXException se) {
			throw new RuntimeException(se.toString());
		} catch (IOException ioe) {
			throw new RuntimeException(ioe.toString());
		}
		return gw;
	}
	
	/** 
	 * Connects to GOOGLE_WEATHER_URL and grabs the xml file for a location's 
	 * current conditions and weather information. Unlike NOAA Google returns 
	 * the current conditions and the forecast data all in one go.
	 * <p>
	 * Returns a formatted weather conditions and forecast hashtable.
	 * <p> 
	 * 
	 * @param location The LocationData representing the location for which to 
	 *		   grab the weather information.
	 * 
	 */
	public Hashtable getGoogleWeather(final LocationData location) throws IOException, ParseError
	{	
		// Get some new Google weather
		URLEncodedPostData post = new URLEncodedPostData(null, true);
		post.append("weather", location.getLocality()+", "+location.getCountry());
		post.append("hl", "en"); // English gives us imperial units
		HttpHelper.Connection conn = null;
		try {
			conn = HttpHelper.getUrl(GOOGLE_WEATHER_URL+"?"+post.toString());
			Hashtable googleWeather = parseGoogleWeather(conn.is);
			return googleWeather;
		} catch (IOException ioe) {
			// Pass the exception on
			throw new IOException(ioe.getMessage());
		} finally {
			if (conn != null)
				conn.close();
		}
	}
	
	public void getDisplayGoogleWeather(final LocationData location)
	{
		try {
			final Hashtable weather = getGoogleWeather(location);
			final Vector fc = (Vector)weather.get("forecast");
			UiApplication.getUiApplication().invokeLater(new Runnable() {
				public void run()
				{
					displayGoogleCurrentConditions(location, (Hashtable)weather.get("current_conditions"));
					displayForecast(location, fc, "Google");
				}
			});
		} catch(Exception e) {
			final String msg = e.getMessage();
			UiApplication.getUiApplication().invokeLater(new Runnable() {
				public void run()
				{
					clearScreen();
					LabelField errorLabel = new LabelField("Error loading Google Weather: "+msg);
					_mainScreen.add(errorLabel);
				}
			});
		}
	}
	
	/**
	 * When given a location this method will both fetch and display the NWS 
	 * NDFD list of current watches and warnings.
	 *
	 * @param location The LocationData object for which to display the forecast 
	 *
	 */
	private Hashtable getNWSAlerts(final LocationData location)
	{
		// The forecast observations we're interested in...
		final String[] observations = { 
			"hazards"
		};
		
		// http://www.weather.gov/forecasts/xml/SOAP_server/ndfdXMLclient.php?whichClient=NDFDgen&lat=28.812831&lon=-97.004264&product=time-series&wwa=wwa
		
		// get NWS Weather
		final URLEncodedPostData post = new URLEncodedPostData(null, true);
		post.append("whichClient", "NDFDgen");
		post.append("format", "12 hourly");
		post.append("lat", Double.toString(location.getLat()));
		post.append("lon", Double.toString(location.getLon()));
		post.append("product", "time-series");
		post.append("wwa", "wwa"); // Get watches and warnings
		
		// <hazard hazardCode="FW.W" phenomena="Red Flag" significance="Warning" hazardType="long duration">
		
		HttpHelper.Connection conn = null;
		try {
			conn = HttpHelper.getUrl(NWS_XML_URL+"?"+post.toString());
			final Hashtable alerts = parseNDFD(conn.is, observations);
			return alerts;
		} catch (Exception e) {
			throw new RuntimeException(e.getMessage());
		} finally {
			// Always close our http connection
			if (conn != null)
				conn.close();
		}
	}
	
	private Vector getAlerts(final LocationData location)
	{
		if (location.getCountry().equals("US") && options.useNws()) {
			final Hashtable alerts = getNWSAlerts(location);
			if (alerts.containsKey("hazards")) {
				Hashtable types = (Hashtable)alerts.get("hazards");
				if (types.containsKey("hazard-conditions")) {
					return (Vector)types.get("hazard-conditions");
				}
			}
		}
		return new Vector();
	}
	
	private boolean getDisplayNWSAlerts(final LocationData location)
	{
		boolean alert = false;
		synchronized(UiApplication.getEventLock()) {
			_mainScreen.setStatusText(_resources.getString(nwsclientResource.GETTING_ALERTS));
			_mainScreen.setStatusVisible(true);
		}
		try {
			final Vector alerts = getAlerts(location);
			alert = (alerts.size() > 0);
			
			UiApplication.getUiApplication().invokeLater(new Runnable() {
				public void run()
				{
					displayAlerts(alerts);
				}
			});
			
		} catch(Exception e) {
			final String msg = e.getMessage();
			UiApplication.getUiApplication().invokeLater(new Runnable() {
				public void run()
				{
					LabelField errorLabel = new LabelField("Error getting NWS alerts: "+msg);
					_mainScreen.add(errorLabel);
				}
			});
		}
		return alert;
	}
	
	/**
	 * When given a location this method will both fetch and display the NWS 
	 * NDFD by-day forecast.  
	 *
	 * @param location The LocationData object for which to display the forecast 
	 *
	 */
	private void getDisplayNWSForecast(final LocationData location)
	{
		synchronized (UiApplication.getEventLock()) {
			_mainScreen.setStatusText(_resources.getString(nwsclientResource.GETTING_FORECAST));
			_mainScreen.setStatusVisible(true);
		}
		// The forecast observations we're interested in...
		final String[] observations = { 
			"weather", 
			"temperature",
			"conditions-icon",
			"probability-of-precipitation"
		};
		
		// get NWS Weather
		final URLEncodedPostData post = new URLEncodedPostData(null, true);
		//post.append("whichClient", "NDFD");
		post.append("whichClient", "NDFDgenByDay");
		post.append("format", "12 hourly");
		post.append("lat", Double.toString(location.getLat()));
		post.append("lon", Double.toString(location.getLon()));
		post.append("numDays", "7"); // get a week
		
		HttpHelper.Connection conn = null;
		try {
			conn = HttpHelper.getUrl(NWS_DAY_URL+"?"+post.toString());
			final Vector flattened = flattenNDFD(parseNDFD(conn.is, observations));
			
			UiApplication.getUiApplication().invokeLater(new Runnable() {
				public void run()
				{
					displayForecast(location, flattened, 
					"The National Oceanic and Atmospheric Administration"
					);
					_mainScreen.setStatusVisible(false);
				}
			});
		} catch (Exception e) {
			final String msg = e.getMessage();
			UiApplication.getUiApplication().invokeLater(new Runnable() {
				public void run()
				{
					LabelField errorLabel = new LabelField("Error getting forecast: "+msg);
					_mainScreen.add(errorLabel);
				}
			});
		} finally {
			// Always close our http connection
			if (conn != null)
				conn.close();
		}
	}
	
	/**
	 * When passed a LocationData object fetches and displays the NDFD forecast
	 * data for the requested location.
	 * @param location The LocationData object for which to get the forecast
	 */
	private String[] getDisplayNWSCurrentConditions(final LocationData location)
	{
		String[] conditions = {"", ""};
		if (location.getIcao().equals("")) {
			UiApplication.getUiApplication().invokeLater(new Runnable() {
				public void run()
				{
					clearScreen();
					// Can't get current conditions
					_mainScreen.add(new RichTextField(
						"Error getting current conditions: "+
						"Could not find closest weather station for location.")
					);
				}
			});
			return conditions;
		}
		
		try {
			final Hashtable parsed = getParseCurrentConditions(location);
			if (parsed != null && parsed.containsKey("temperature")) {
				// Remember the temperature and condition to pass to the icon updater
				conditions[0] = (String)parsed.get("temperature");
				conditions[1] = (String)parsed.get("condition");
			}
			UiApplication.getUiApplication().invokeLater(new Runnable() {
				public void run()
				{
					displayNWSCurrentConditions(location, parsed);
				}
			});
		} catch (Exception ioe) {
			final String msg = ioe.getMessage();
			UiApplication.getUiApplication().invokeLater(new Runnable() {
				public void run() {
					clearScreen();
					LabelField errorLabel = new LabelField("Error getting current conditions: "+msg);
					_mainScreen.add(errorLabel);
				}
			});
		}
		return conditions;
	}
	
	/**
	 * This method does most of the work. It chooses whether to get Google 
	 * weather or National Weather Service NDFD data and displays it on the 
	 * screen. It displays the current conditions for the location at the top
	 * with the full forecast beneath it (by day for Google, by 12 hour period 
	 * for the NDFD data).
	 * @param location The Location for which to display the weather information
	 */
	private void getDisplayWeather(final LocationData location) 
	{
		// Indicate update is happening
		location.setLastUpdated(System.currentTimeMillis());
		
		synchronized(UiApplication.getEventLock()) {
			_mainScreen.setStatusText(_resources.getString(nwsclientResource.GETTING_WEATHER));
			_mainScreen.setStatusVisible(true);
		}
		
		String myCountry = location.getCountry();
		if (myCountry.equals("US") && options.useNws()) {
			// United States - Get NWS NDFD data 	
			String weather[] = getDisplayNWSCurrentConditions(location);
			
			// Alerts!
			boolean alert = getDisplayNWSAlerts(location);
			
			updateIcon(location, weather[0], weather[1], alert);
			
			// Separate call for the forecast
			getDisplayNWSForecast(location);
			
		} else {
			// International - get Google Weather
			getDisplayGoogleWeather(location);
		}
		
	}
	
	private void displayLicense()
	{
		MainScreen scrn = new AbstractScreen();
		LabelField title = new LabelField(_resources.getString(nwsclientResource.ABOUT), 
			LabelField.ELLIPSIS | LabelField.USE_ALL_WIDTH);
		scrn.setTitle(title);
		displaySplash(scrn);
		pushScreen(scrn);
	}
	
	private void displaySplash(final MainScreen scrn)
	{
		if (scrn == null)
			return;
		
		VerticalFieldManager vField = new VerticalFieldManager(Field.FIELD_VCENTER | Field.USE_ALL_WIDTH | Field.USE_ALL_HEIGHT);
		scrn.add(vField);
		String splash[] = new String[4];
		ApplicationDescriptor ad = ApplicationDescriptor.currentApplicationDescriptor();
		splash[0] = "NWSClient\n";
		splash[1] = "version " + ad.getVersion() + "\n" + 
					_resources.getString(nwsclientResource.SPLASH1)+"\n";
		splash[2] = _resources.getString(nwsclientResource.SPLASH2)+"\n";
		splash[3] = _resources.getString(nwsclientResource.SPLASH3) +"\n"+
					_resources.getString(nwsclientResource.SPLASH4);
		
		FontFamily fontfam[] = FontFamily.getFontFamilies();
		Font fnts[] = new Font[4];
		fnts[0] = fontfam[0].getFont(FontFamily.SCALABLE_FONT, options.minFontSize()+4);
		fnts[1] = fnts[0].derive(Font.PLAIN, options.minFontSize()+1);
		
		int fgColors[] = {0x00, 0x00, 0x00, 0x00, 0x00};
		int bgColors[] = {0xffffff, 0xffffff, 0xffffff, 0xffffff, 0xffffff};
		
		Bitmap bg = Bitmap.getBitmapResource("icon.png");
		BitmapField icn = new BitmapField(bg, Field.FIELD_HCENTER);
		vField.add(icn);
		byte attr[] = new byte[] {0, 1, 1, 1};
		
		int off[] = new int[] {
								0, 
								splash[0].length(),
								splash[0].length() + splash[1].length(),
								splash[0].length() + splash[1].length() + splash[2].length(),
								splash[0].length() + splash[1].length() + splash[2].length() + splash[3].length()
								};
		
		ActiveRichTextField text = new ActiveRichTextField(
			(splash[0] + splash[1] + splash[2] + splash[3]), 
			off, attr, fnts, fgColors, bgColors, RichTextField.TEXT_ALIGN_HCENTER);
		vField.add(text);
	}
	
}

