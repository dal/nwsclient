/**
 * A NDFD Weather data client
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
import javax.microedition.io.*;
import java.util.*;
import java.io.*;
import net.rim.device.api.util.*;
import org.w3c.dom.*;
import org.xml.sax.*; // for SAXException

/*
 * For summaries:
 * http://www.weather.gov/forecasts/xml/SOAP_server/ndfdSOAPclientByDay.php?whichClient=NDFDgenByDay&lat=37.8381770&lon=-122.2&format=12+hourly&startDate=2008-11-21&numDays=10
 *
 * For detail:
 * http://www.weather.gov/forecasts/xml/SOAP_server/ndfdXMLclient.php?%20whichClient=NDFDgen&lat=37.8381770&lon=-122.2963890&product=time-series
 */

public class NwsClient extends UiApplication
{
	// Class variables
	
	private static final long ID = 0x40360238fa3ebcc6L; // com.renderfast.nwsclient
	
	private static final String GOOGLE_MAPS_URL = "http://maps.google.com/maps/geo";
	
	private static final String GOOGLE_API_KEY = 
		"ABQIAAAAC8JsE5tvhHeNFm7ZGLaE4hRb6y4KxHYjOJR6okNA-FLzn8UPtxTXruj85ZyxdVMqDazcxknt-CapTQ";
	
	private static final String NWS_XML_URL = "http://www.weather.gov/forecasts/xml/SOAP_server/ndfdXMLclient.php";
	private static final String NWS_DAY_URL = "http://www.weather.gov/forecasts/xml/SOAP_server/ndfdSOAPclientByDay.php";
	
	private static final String NWS_CURRENT_URL = "http://www.weather.gov/xml/current_obs/";
	
	private static final String GOOGLE_WEATHER_URL = "http://www.google.com/ig/api";
	
	private static final String GOOGLE_URL = "http://www.google.com";
	
	private static final int UPDATE_INTERVAL = 3600000; // one hour in milliseconds
	
	private static PersistentObject store;
	
	private static NwsClientOptions options;

	// Instance variables
	
	private Thread workerThread_;

	private LocationFinder locationFinder_;
	
	private BitmapProvider bitmapProvider_;
	
	private NwsClientScreen mainScreen_;
	
	private OptionsScreen optionsScreen_;
	
	private EditField newLocField_;
	
	private boolean foreground_ = false;
	
	/**
	 * Initialize or reload our persistent store
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
		public String type;
		public TimeKey time;
		public String value;
		public String units;
	};
	
	/**
	 * The main application screen.
	 */
	private final class NwsClientScreen extends MainScreen
	{
		
		public LabelField locationLabel;
		
		/**
		 * Construct an NWS client screen.
		 */
		public NwsClientScreen()
		{
			setTitle(new LabelField("NWSClient", LabelField.USE_ALL_WIDTH));
		}
		
		protected void makeMenu(Menu menu, int instance)
		{
			menu.add(optionsMenuItem_);
			menu.add(refreshMenuItem_);
			menu.addSeparator();
			super.makeMenu(menu, instance);
		}
		
		public boolean onClose()
		{
			return super.onClose();
		}
		
		protected boolean keyChar(char key, int status, int time)
		{
			if (key == Characters.LATIN_SMALL_LETTER_U ) {
				scroll(Manager.UPWARD);
				return true; //I've absorbed this event, so return true
			} else if (key == Characters.SPACE ) {
				scroll(Manager.DOWNWARD);
				return true; //I've absorbed this event, so return true
			} else if (key == Characters.LATIN_SMALL_LETTER_T ) {
				scroll(Manager.TOPMOST);
				return true; //I've absorbed this event, so return true
			} else if (key == Characters.LATIN_SMALL_LETTER_B ) {
				scroll(Manager.BOTTOMMOST);
				return true; //I've absorbed this event, so return true
			} else {
				return super.keyChar(key, status, time);
		}
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
		
		final class recentLocListener implements FieldChangeListener {
			public void fieldChanged(Field field, int context) {
				try {
					ObjectChoiceField ocf = (ObjectChoiceField) field;
					int idx = ocf.getSelectedIndex();
					//Dialog.alert("Location selected: " + (String)ocf.getChoice(idx));
					Vector locations = options.getLocations();
					if (idx < locations.size()) {
						final LocationData newLoc = (LocationData)locations.elementAt(idx);
						options.setCurrentLocation(newLoc);
						optionsScreen_.storeInterfaceValues();
						if (optionsScreen_.isDisplayed())
							optionsScreen_.close();
						
						// Tell the icon updater about the new location
						store.setContents(options);
						store.commit();
						if (checkDataConnectionAndWarn()) {
							refreshWeather();
						}
					}
				} catch (ClassCastException ce) {
					// ...
				}
			}
		};
		
		/**
		 * OptionsScreen constructor.
		 */
		public OptionsScreen()
		{
			super();
			LabelField title = new LabelField("NWSClient Options", 
				LabelField.ELLIPSIS | LabelField.USE_ALL_WIDTH);
			setTitle(title);
			newLocField_ = new EditField("Location: ", null, Integer.MAX_VALUE, EditField.FILTER_DEFAULT);
			add(newLocField_);
			
			//_recentLocationsLabel = new LabelField("Recent Locations:", LabelField.ELLIPSIS);
			_recentLocationsChoiceField = new ObjectChoiceField();
			_recentLocationsChoiceField.setLabel("Recent Locations:");
			setRecentLocationsChoiceField();
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
		}
		
		protected void makeMenu(Menu menu, int instance) {			
			menu.add(_newLocationMenuItem);
			super.makeMenu(menu, instance);
		}
		
		protected boolean keyChar(char key, int status, int time)
		{
			// UiApplication.getUiApplication().getActiveScreen().
			if ( getLeafFieldWithFocus() == newLocField_ && key == Characters.ENTER ) {
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
				if (!loc.getCountry().equals("US"))
					address = loc.getLocality() + ", " + loc.getCountry();
				//_recentLocationsListField.insert(i, address);
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
			
			return changed;
		}
		
		public void save()
		{
			if (storeInterfaceValues())
				store.setContents(options);
				store.commit();
				if (checkDataConnectionAndWarn()) {
					refreshWeather(); // refresh
				}
		}
		
	};
	
	class IconUpdaterThread extends Thread
	{
		private LocationData location_ = null;
		
		IconUpdaterThread()
		{
			// empty constructor
		}
		
		public synchronized void setLocation(LocationData loc)
		{
			location_ = loc;
		}
		
		public void doUpdateIcon()
		{
			//System.err.println("Setting icon for "+location_.getCountry()+" "+location_.getArea()+" "+location_.getLocality());
			// Get our current temp
			try {
				Hashtable conditions = getParseCurrentConditions(location_);
				Vector alerts = getAlerts(location_);
				boolean alert = (alerts.size() > 0);
				if (conditions != null && conditions.containsKey("temperature")) {
					String temp = (String)conditions.get("temperature");
					updateIcon(temp, alert, 1);
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
						// do nothing but sleep
						sleep(4000);
						continue;
					}
					
					// Check for a new location coming in...
					LocationData newLoc = options.getCurrentLocation();
					// If there's any change to the location (including update) re-fetch it
					if (newLoc != null && (newLoc != location_ 
						|| newLoc.getLastUpdated() != location_.getLastUpdated())) 
					{
						synchronized(this) {
							setLocation(newLoc);
						}
					}
					
					if (location_ == null) {
						sleep(4000);
						continue;
					}
					
					// Time to update?
					long now = System.currentTimeMillis();
					long thisInterval = now - location_.getLastUpdated();
					
					getOptionsFromStore();
					if (thisInterval >= UPDATE_INTERVAL) {
						doUpdateIcon();
						location_.setLastUpdated(now);
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
	
	/**
	 * This thread fires every second and decides whether we need to update 
	 * the weather display.
	 * 
	 */
	class WorkerThread extends Thread
	{
		boolean stop_ = false;
		
		WorkerThread()
		{
			// constructor does nothing
		}
		
		public void run()
		{
			for(;;) {
				
				if (stop_)
					return;
				
				final long now = System.currentTimeMillis();
				final LocationData location = options.getCurrentLocation();
				if (location != null && (now - location.getLastUpdated()) > UPDATE_INTERVAL) {
					getDisplayWeather(location);
					setLastUpdated(now);
				} else {
					try {
						sleep(1000); // sleep for a second...
					} catch (InterruptedException e) {
						System.err.println(e.toString());
						return;
					}
				}
			}
		}
		
		public void stop()
		{
			stop_ = true;
		}
		
	}
	
	class LocationFinder extends Thread
	{
		String input_ = null;
		boolean start_ = false;
		boolean stop_ = false;
		
		public void find(String userAddress)
		{
			if ( start_ ) {
				Dialog.alert("Already finding location");
				synchronized(this) {
					input_ = userAddress;
				}
			} else {
				synchronized(this) {
					if (start_) {
						Dialog.alert("Already finding location");	
					} else {
						start_ = true;
						input_ = userAddress;
					}
				}
			}
		}
		
		public void run()
		{
			for (;;) {
				while (!start_ && !stop_) {
					try {
						sleep(1000); 
					} catch (InterruptedException e) {
						System.err.println(e.toString());
						return;
					}
					
					if (stop_) {
						return;
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
	
	public static synchronized void updateIcon(String temp, boolean alert, int whichApp)
	{
		int lOffset = 22; // left offset
		if (temp.length() >= 3) // move to the left if > 3 chars long
			lOffset = 20;
		if (temp.length() > 3)
			temp = "NWS";
		Bitmap bg = Bitmap.getBitmapResource("icon.png");
		Graphics gfx = new Graphics(bg);
		FontFamily fontfam[] = FontFamily.getFontFamilies();
		Font smallFont = fontfam[0].getFont(FontFamily.SCALABLE_FONT, 12);
		if (alert) {
			gfx.setColor(0xffff33); // yellow text
			gfx.fillArc(5, 18, 16, 16, 0, 360);
			gfx.setColor(0xff3333); // red background
			gfx.drawArc(6, 19, 14, 14, 0, 360);
			Font boldFont = smallFont.derive(Font.BOLD);
			gfx.setFont(boldFont);
			gfx.drawText("!", 11, 20); // Exclamation point
		}
		gfx.setFont(smallFont);
		gfx.drawText(temp, lOffset, 12);
		HomeScreen.updateIcon(bg, 1);
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
	
	// CLASS METHODS
	
	// constructor
	public NwsClient(boolean autostart)
	{	
		if (autostart) {
			foreground_ = false;
			//System.err.println("NWSClient: In autostart");
			//alternate entry point
			ApplicationManager myApp = ApplicationManager.getApplicationManager();
			boolean keepGoing = true;
			while(keepGoing) {
				if (myApp.inStartup()) {
					//The BlackBerry is still starting up, sleep for 1 second.
					//System.err.println("autostart sleeping...");
					try {
						Thread.sleep(1000);
					} catch (Exception ex) {
						System.err.println("Error sleeping entry thread: "+ex.toString());
					}
				} else {
					keepGoing = false;
					// Start the icon updater thread
					IconUpdaterThread iup = new IconUpdaterThread();
					workerThread_ = iup;
					if (options.getCurrentLocation() != null) {
						options.getCurrentLocation().setLastUpdated(0);
						iup.setLocation(options.getCurrentLocation());
					}
					iup.start();
				}
			}
		} else {
			foreground_ = true;
			requestForeground();
			// started by the user
			mainScreen_ = new NwsClientScreen();
			pushScreen(mainScreen_);
			
			// Don't need to start the bitmpaProvider--it will start on demand
			this.bitmapProvider_ = new BitmapProvider();
			
			this.workerThread_ = this.new WorkerThread();
			this.workerThread_.start();
			
			this.locationFinder_ = this.new LocationFinder();
			this.locationFinder_.start();
			
			// if no location go to the options screen
			if (options.getCurrentLocation() != null) {
				refreshWeather();
			} else {
				viewOptions();
			}
		}
	}
	
	protected boolean acceptsForeground()
	{
		return foreground_;
	}
	
	/**
	 * Check to make sure we have a data connection. 
	 * If not, show a warning dialog and return false.
	 */
	private boolean checkDataConnectionAndWarn()
	{
		if (!RadioInfo.isDataServiceOperational()) {
			invokeLater(new Runnable() {
				public void run() {
					Dialog.alert("Data service unavailable. Try again later.");
				}
			});
			return false;
		}
		return true;
	}
	
	private void refreshWeather()
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
	}
	
	// menu items
	// cache the options menu item for reuse
	private MenuItem optionsMenuItem_ = new MenuItem("Options", 110, 10) {
		public void run()
		{
			viewOptions();
		}
	};
	
	private MenuItem refreshMenuItem_ = new MenuItem("Refresh", 110, 10) {
		public void run()
		{
			if (checkDataConnectionAndWarn())
				refreshWeather();
		}
	};
	
	private MenuItem _newLocationMenuItem = new MenuItem("Get Forecast", 100, 10) {
		public void run()
		{
			if (newLocField_.getText().length() > 0) {
				if (checkDataConnectionAndWarn()) 
					setNewLocation(newLocField_.getText());
			} else {
				Dialog.alert("Enter a valid city, State");
			}
		}
	};
	
	
	// Methods
	
	/**
	 * View the various options for this application
	 */
	public void viewOptions() 
	{
		optionsScreen_ = new OptionsScreen();
		pushScreen(optionsScreen_);
	}
	
	private boolean findNearestWeatherStation(LocationData loc)
	{
		String weatherStation = WeatherStation.findNearest(loc.getLat(), loc.getLon());
		loc.setIcao(weatherStation);
		return true;
	}
		
	private LocationData getLocationData(String userAddress) {
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
			
			try {
				loc.loadFromXml(document);
				return loc;
			} catch (NotFoundException e) {
				invokeLater(new Runnable() {
					public void run() {
						Dialog.alert("Could not find location");
					}
				});
			} catch (AmbiguousLocationException e) {
				// choose a more specific address?
				invokeLater(new Runnable() {
					public void run() {
						Dialog.alert("Choose a more specific address");
					}
				});
			}
		} catch (Exception e) {
			final String msg = e.toString();
			invokeLater(new Runnable() {
				public void run() {
					Dialog.alert("Error getting the location: '"+msg+"'");
				}
			});
		} finally {
			// Always close the http connection
			if (conn != null)
				conn.close();
		}
		return null;
	}
	
	private void setNewLocation(final String userAddress)
	{
		locationFinder_.find(userAddress);
	}
	
	private Calendar parseTime(String timeStr)
	{
		// I have to parse the date myself because RIM's SimpleDateFormat doesn't
		// implement the 'parse' method. Lame.
		String part;
		Calendar cal = Calendar.getInstance(); // new calendar object
		//			 > 0123456789012345678901234 <
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
			Dialog.alert("Error parsing NDFD timeseries: "+e.toString());
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
			
			if (tempObs != null)
				temp = tempObs.value+" "+tempObs.units;
			
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
			// temperature_string "55 F (11 C)"
			//cc.put("temperature", XmlHelper.getValue(root, "temperature_string"));
			// temperature "55"
			cc.put("temperature", XmlHelper.getValue(root, "temp_f"));
			// relative_humidity "65"
			cc.put("relative_humidity", XmlHelper.getValue(root, "relative_humidity"));
			// location "Oakland, CA"
			cc.put("location", XmlHelper.getValue(root, "location"));
			// wind_str "From the west at 12mph"
			cc.put("wind", XmlHelper.getValue(root, "wind_string")); 
			// pressure_string "29.90" (1012.4 mb)"
			cc.put("pressure", XmlHelper.getValue(root, "pressure_string"));
			// dewpoint_string "27 F (-3 C)"
			cc.put("dewpoint", XmlHelper.getValue(root, "dewpoint_string"));
			// heat_index_string "NA"
			cc.put("heat_index", XmlHelper.getValue(root, "heat_index_string"));
			// windchill_string "31 F (-1 C)"
			cc.put("windchill", XmlHelper.getValue(root, "windchill_string"));
			// "visibility" 
			cc.put("visibility", XmlHelper.getValue(root, "visibility_mi"));
			
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
		// replace the screen with a new, clean one
		popScreen(mainScreen_);
		mainScreen_ = new NwsClientScreen();
		pushScreen(mainScreen_);
	}
	
	private void displayNWSCurrentConditions(LocationData location, Hashtable weather)
	{
		String address = location.getLocality()+", "+location.getArea();
		//locationLabel = new LabelField(address);
		
		if (weather == null) {
			mainScreen_.add(new LabelField("Unable to fetch current conditions"));
			return;
		}
		
		String conditionsLabel = (String)weather.get("condition");
		String temp = (String)weather.get("temperature");
		String humidity = (String)weather.get("relative_humidity") + "%";
		String station = (String)weather.get("location");
		String condIconUrl = (String)weather.get("icon_url");
		String wind = (String)weather.get("wind");
		String pressure = (String)weather.get("pressure");
		String dewpoint = (String)weather.get("dewpoint");
		String heat_index = (String)weather.get("heat_index");
		String windchill = (String)weather.get("windchill");
		String vis = (String)weather.get("visibility");
		
		// Grab some fonts...
		FontFamily fontfam[] = FontFamily.getFontFamilies();
		Font tinyFont = fontfam[0].getFont(FontFamily.SCALABLE_FONT, 11);
		Font smallFont = fontfam[0].getFont(FontFamily.SCALABLE_FONT, 12);

		// Make the title label
		mainScreen_.setTitle(new LabelField(address, LabelField.ELLIPSIS));
		
		VerticalFieldManager main = new VerticalFieldManager(Manager.USE_ALL_WIDTH);
		mainScreen_.add(main);
		
		// Current Conditions Label
		LabelField lbl = new LabelField("Current Conditions at "+location.getIcao(), LabelField.ELLIPSIS);
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
		topHField.add(currentCondBitmap);
		
		if (!condIconUrl.equals("")) {
			bitmapProvider_.getBitmap(condIconUrl, currentCondBitmap);
		}
		
		VerticalFieldManager topRightCol = new VerticalFieldManager();
		topHField.add(topRightCol);
		
		// Show max/min temps
		topRightCol.add(new RichTextField(conditionsLabel));
		topRightCol.add(new RichTextField("Temp: "+temp));
		
		String fields[] = { 
							"Relative humidity", humidity, "Wind", wind, 
							"Barometric pressure", pressure,
							"Dewpoint", dewpoint, "Heat Index", heat_index,
							"Windchill", windchill, "Visibility", (vis + " miles")
							};
		HorizontalFieldManager row = null;
		for (int i=0; i < fields.length; i++) {
			
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
		
		//btVField.add(btRightCol);
		main.add(new SeparatorField());
		
	}
	
	private void displayGoogleCurrentConditions(LocationData location, Hashtable weather)
	{
		
		if (weather == null) {
			mainScreen_.add(new LabelField("Unable to fetch current conditions"));
			return;
		} else if (weather.containsKey("temperature")) {
			updateIcon((String)weather.get("temperature"), false, 0);
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
		Font tinyFont = fontfam[0].getFont(FontFamily.SCALABLE_FONT, 11);
		Font smallFont = fontfam[0].getFont(FontFamily.SCALABLE_FONT, 12);

		// Make the title label
		mainScreen_.setTitle(new LabelField(address, LabelField.ELLIPSIS));
		
		VerticalFieldManager main = new VerticalFieldManager(Manager.USE_ALL_WIDTH);
		mainScreen_.add(main);
		
		// Current Conditions Label
		LabelField lbl = new LabelField("Current Conditions", LabelField.ELLIPSIS);
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
			bitmapProvider_.getBitmap(condIconUrl, currentCondBitmap);
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
	private void displayForecast(LocationData location, Vector forecast, String credit)
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
			mainScreen_.add(lbl);
			
			BitmapField forecastBitmap = new BitmapField();
			
			if (!iconUrl.equals("")) {
				// DEBUG DEBUG DEBUG
				bitmapProvider_.getBitmap(iconUrl, forecastBitmap);
			}
			
			HorizontalFieldManager myHField = new HorizontalFieldManager();
			mainScreen_.add(myHField);
			
			myHField.add(forecastBitmap);
			
			FlowFieldManager rightCol = new FlowFieldManager();
			myHField.add(rightCol);
			
			// Show the observations
			rightCol.add(new RichTextField(condition));
			rightCol.add(new RichTextField(temperature));
			if (precip != null)
				rightCol.add(new RichTextField(precip));
			mainScreen_.add(new SeparatorField());
		}
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
					String startTimeStr = dateFormat.format(alertDate, new StringBuffer(), null).toString();
					String endTimeStr = dateFormat.format(endDate, new StringBuffer(), null).toString();
					RichTextField warningField = new RichTextField(alert.value+" "+alert.units+" "+startTimeStr+" - "+endTimeStr) {
						public void paint(Graphics graphics) {
							// Warning text is red
							graphics.setColor(0xff0000);
							super.paint(graphics);
						}
					};
					mainScreen_.add(warningField);
				} else {
					Date alertDate = alert.time.startTime.getTime();
					String alertTimeStr = dateFormat.format(alertDate, new StringBuffer(), null).toString();
					mainScreen_.add(new RichTextField(alert.value+" "+alert.units+" "+alertTimeStr));
				}
				lastAlert = null;
			} else if (lastAlert == null) {
				lastAlert = alert;
			}
		}
		if (alerts.size() == 0) {
			mainScreen_.add(new RichTextField("No alerts or warnings"));
		}
		mainScreen_.add(new SeparatorField());
	}
	
	private void displayCredit(final String who, final long when)
	{
		DateFormat dateFormat = new SimpleDateFormat("h:mma E, MMM d, yyyy");
		Date lastUpdate = new Date(when); 
		String formattedDate = dateFormat.format(lastUpdate, new StringBuffer(), null).toString();
		String credit = "Downloaded at "+formattedDate+
						"\nfrom "+who+".";
		RichTextField creditField = new RichTextField(credit);
		Font small = creditField.getFont().derive(Font.PLAIN, 11);
		creditField.setFont(small);
		mainScreen_.add(creditField);
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
				
				// Get the current conditions
				NodeList ccNodes = weather.getElementsByTagName("current_conditions");
				if (ccNodes.getLength() == 0) 
					throw new ParseError("No current conditions found in Google Weather response.");
				Element ccEl = (Element)ccNodes.item(0);
				Hashtable cc = new Hashtable();
				cc.put("city", XmlHelper.getElementData(weather, "city")); // Google city name...
				cc.put("condition", XmlHelper.getElementData(ccEl, "condition"));
				cc.put("temperature", XmlHelper.getElementData(ccEl, "temp_f"));
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
					day.put("temperature", (XmlHelper.getElementData(dayEl, "high") + 
						" low: " + XmlHelper.getElementData(dayEl, "low")));
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
		post.append("hl", "en");
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
					LabelField errorLabel = new LabelField("Error loading Google Weather: "+msg);
					mainScreen_.add(errorLabel);
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
					mainScreen_.add(errorLabel);
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
				}
			});
		} catch (Exception e) {
			final String msg = e.getMessage();
			UiApplication.getUiApplication().invokeLater(new Runnable() {
				public void run()
				{
					LabelField errorLabel = new LabelField("Error getting forecast: "+msg);
					mainScreen_.add(errorLabel);
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
	private String getDisplayNWSCurrentConditions(final LocationData location)
	{
		String temp = "";
		if (location.getIcao().equals("")) {
			UiApplication.getUiApplication().invokeLater(new Runnable() {
				public void run()
				{
					Dialog.alert("Could not find closest weather station for location.");
				}
			});
			return temp;
		}
		
		try {
			final Hashtable parsed = getParseCurrentConditions(location);
			if (parsed != null && parsed.containsKey("temperature")) {
				// Remember the temperature to pass to the icon updater
				temp = (String)parsed.get("temperature");
			}
			UiApplication.getUiApplication().invokeLater(new Runnable() {
				public void run()
				{
					displayNWSCurrentConditions(location, parsed);
				}
			});
		} catch (Exception ioe) {
			// choose a more specific address?
			final String msg = ioe.getMessage();
			UiApplication.getUiApplication().invokeLater(new Runnable() {
				public void run() {
					LabelField errorLabel = new LabelField("Error getting current conditions: "+msg);
					mainScreen_.add(errorLabel);
				}
			});
		}
		return temp;
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
		UiApplication.getUiApplication().invokeAndWait(new Runnable() {
			public void run() {
				clearScreen();
			}
		});
		
		// Indicate update is happening
		location.setLastUpdated(System.currentTimeMillis());
		
		final MessageScreen wait = new MessageScreen("Getting Weather...");
		invokeLater(new Runnable() {
			public void run() {
				pushScreen(wait);
			}
		});
		
		String myCountry = location.getCountry();
		if (myCountry.equals("US") && options.useNws()) {
			// United States - Get NWS NDFD data 	
			String temp = getDisplayNWSCurrentConditions(location);
			
			// Alerts!
			boolean alert = getDisplayNWSAlerts(location);
			
			updateIcon(temp, alert, 0);
			
			// Separate call for the forecast
			getDisplayNWSForecast(location);
			
		} else {
			// International - get Google Weather
			getDisplayGoogleWeather(location);
		}
		
		// Lift up the wait screen
		invokeLater(new Runnable() {
			public void run() {
				popScreen(wait);
			}
		});
	}
	
}

