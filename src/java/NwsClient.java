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
import net.rim.device.api.system.RuntimeStore.*;
import net.rim.blackberry.api.homescreen.*; // for updating the application icon
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
	private static final long ID = 0x40360238fa3ebcc6L; // com.renderfast.nwsclient 
	private static final String GOOGLE_MAPS_URL = "http://maps.google.com/maps/geo";
	private static final String GOOGLE_API_KEY = 
		"ABQIAAAAC8JsE5tvhHeNFm7ZGLaE4hRb6y4KxHYjOJR6okNA-FLzn8UPtxTXruj85ZyxdVMqDazcxknt-CapTQ";
	
	//private static String NWS_URL = "http://www.weather.gov/forecasts/xml/SOAP_server/ndfdXMLclient.php";
	private static final String NWS_URL = "http://www.weather.gov/forecasts/xml/SOAP_server/ndfdSOAPclientByDay.php";
	
	private static final String NWS_CURRENT_URL = "http://www.weather.gov/xml/current_obs/";
	
	// This URL will reverse encode lat/lon to the nearest ICAO station identifier
	//private static final String GEONAMES_URL = "http://ws.geonames.org/findNearByWeatherXML";
	
	private static final String GOOGLE_WEATHER_URL = "http://www.google.com/ig/api";
	
	private static final String GOOGLE_URL = "http://www.google.com";
	
	private static final int _updateInterval = 3600000; // one hour in milliseconds
	
	// Last time the weather information was updated
	private long lastUpdated = 0;
	
	private static Hashtable _googleWeather;
	
	private static IconUpdater _iconUpdater;
		
	private static PersistentObject _store;
	private static NwsClientOptions _options;
	private BitmapProvider _bitmapProvider;
	private NwsClientScreen mainScreen;
	
	private OptionsScreen optionsScreen;
	private EditField newLocField; 
	
	/**
	 * Initialize or reload our persistent store
	 */
	
	static {
		_store = PersistentStore.getPersistentObject(ID);
		if(_store.getContents()==null) {
			_store.setContents( new NwsClientOptions() );
		}
		
		_options = (NwsClientOptions)_store.getContents();
	}
	
	public static String getCalendarDayOfWeek(Calendar day)
	{
		int dow = day.get(Calendar.DAY_OF_WEEK);
		String ret;
		switch(dow) {
			case Calendar.SUNDAY: ret = "Sunday"; break;
			case Calendar.MONDAY: ret = "Monday"; break;
			case Calendar.TUESDAY: ret = "Tuesday"; break;
			case Calendar.WEDNESDAY: ret = "Wednesday"; break;
			case Calendar.THURSDAY: ret = "Thursday"; break;
			case Calendar.FRIDAY: ret = "Friday"; break;
			case Calendar.SATURDAY: ret = "Saturday"; break;
			default: ret = "ERROR"; break;
		}
		return ret;
	}
	
	private class TimeKey
	{
		public Calendar startTime;
		public String periodName;
	};
	
	private class Observation
	{
		public String type;
		public TimeKey time;
		public String value;
		public String units;
	};
	
	// Inner Classes
	// The main application screen
	private final class NwsClientScreen extends MainScreen
	{
		
		public LabelField locationLabel;
		
		public NwsClientScreen()
		{
			//currentCondBitmap = new BitMapField(new Bitmap(54, 58));
		}
		
		protected void makeMenu(Menu menu, int instance)
		{
			menu.add(_optionsMenuItem);
			menu.add(_refreshMenuItem);
			menu.addSeparator();
			super.makeMenu(menu, instance);
		}
		
		public boolean onClose()
		{
			return super.onClose();
		}
		
	};
	
	private class OptionsScreen extends MainScreen
	{
		private ObjectChoiceField _recentLocationsChoiceField;
		private CheckboxField _useNwsCheckBox;
		
		final class recentLocListener implements FieldChangeListener {
			public void fieldChanged(Field field, int context) {
				try {
					ObjectChoiceField ocf = (ObjectChoiceField) field;
					int idx = ocf.getSelectedIndex();
					//Dialog.alert("Location selected: " + (String)ocf.getChoice(idx));
					Vector locations = _options.getLocations();
					if (idx < locations.size()) {
						LocationData newLoc = (LocationData)locations.elementAt(idx);
						_options.setCurrentLocation(newLoc);
						//optionsScreen.save();
						optionsScreen.storeInterfaceValues();
						if (optionsScreen.isDisplayed())
							optionsScreen.close();
						
						// Tell the icon updater about the new location
						storeLocation(newLoc);
						getWeather(newLoc);
					}
				} catch (ClassCastException ce) {
					// ...
				}
			}
		};
		
		public OptionsScreen()
		{
			super();
			LabelField title = new LabelField("Weather Options", 
				LabelField.ELLIPSIS | LabelField.USE_ALL_WIDTH);
			setTitle(title);
			newLocField = new EditField("Location: ", null, Integer.MAX_VALUE, EditField.FILTER_DEFAULT);
			add(newLocField);
			
			//_recentLocationsLabel = new LabelField("Recent Locations:", LabelField.ELLIPSIS);
			_recentLocationsChoiceField = new ObjectChoiceField();
			_recentLocationsChoiceField.setLabel("Recent Locations:");
			setRecentLocationsChoiceField();
			_recentLocationsChoiceField.setChangeListener(new recentLocListener());
			add(_recentLocationsChoiceField);
			String helpText = "\nValid locations include City, State (e.g. \"New "+
				"York, NY\"); Airport Codes (e.g. \"JFK\"); zip code; "+
				"or International City, Country (e.g. \"London, GB\" or "+
				"\"Moscow\").\n National Weather Service forecast data is only "+
				"available for the United States. For all other locations "+
				"iGoogle weather data will be used.";
			LabelField _helpLabel = new LabelField(helpText);
			Font small = _helpLabel.getFont().derive(Font.PLAIN, 11);
			_helpLabel.setFont(small);
			add(_helpLabel);
			
			_useNwsCheckBox = new CheckboxField("Use NWS data if available", _options.useNws());
			add(_useNwsCheckBox);
		}
		
		private MenuItem _cancel = new MenuItem("Cancel", 110, 10) {
			public void run()
			{
				OptionsScreen.this.close();
			}
		};
		
		protected void makeMenu(Menu menu, int instance) {			
			menu.add(_newLocationMenuItem);
			super.makeMenu(menu, instance);
		}
		
		protected boolean keyChar(char key, int status, int time)
		{
			// UiApplication.getUiApplication().getActiveScreen().
            if ( getLeafFieldWithFocus() == newLocField && key == Characters.ENTER ) {
				storeInterfaceValues();
                _newLocationMenuItem.run();
                return true; //I've absorbed this event, so return true
            } else {
                return super.keyChar(key, status, time);
            }
		}
		
		protected void setRecentLocationsChoiceField()
		{
			Vector locs = _options.getLocations();
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
			boolean useNws = _options.useNws();
			if (useNws != _useNwsCheckBox.getChecked()) {
				changed = true;
				_options.setUseNws(_useNwsCheckBox.getChecked());
			}
			return changed;
		}
		
		public void save()
		{
			if (storeInterfaceValues())
				refreshWeather(true); // refresh with force
		}
		
	};
	
	// STATIC METHODS
	
	 /**
	 * Instantiate the new application object and enter the event loop
	 * @param args unsupported. No args are supported for this application
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
	
	public static InputStream getUrl(String url)
	{
		//System.err.println("Fetching url "+url);
		StreamConnection s = null;
		InputStream is = null;
		int rc;
		
		try {
			
			s = (StreamConnection)Connector.open(url);
			HttpConnection httpConn = (HttpConnection)s;
			
			int status = httpConn.getResponseCode();
			
			if (status == HttpConnection.HTTP_OK) {
				is = httpConn.openInputStream();
				
				// pass the stream on to the supplied handler
				//handler.handleInputStream(is);
				return is;
			} else {
				System.err.println("Error fetching url. Status: "+status);
			}
			
			//if (is != null)
			//	is.close();
			if (s != null)
				s.close();
			
		} catch (Exception e) {
			System.err.println(e.toString());
		}
		
		return is;
		
	}
	
	public static Hashtable getParseCurrentConditions(LocationData location) throws IOException, ParseError
	{
		Hashtable parsed = null;
		if (location.getCountry().equals("US") && _options.useNws()) {
			
			if (location.getIcao().equals(""))
				return null;
			
			InputStream is = getUrl(NWS_CURRENT_URL+location.getIcao()+".xml");
			
			if (is != null) {
				try {
					parsed = parseUSCurrentConditions(is);
					is.close();
				} catch (IOException ioe) {
					// choose a more specific address?
					//System.err.println("Error getting weather informtion: "+ioe.toString());
					throw new IOException(ioe.toString());
				}
			}
		} else {
			Hashtable gw = getGoogleWeather(location);
			parsed = (Hashtable)gw.get("current_conditions");
		}
		return parsed;
	}
	
	// CLASS METHODS
	
	// constructor
	public NwsClient(boolean autostart)
	{
		if (autostart) {
			//System.err.println("NWSClient: In autostart");
			//alternate entry point
			invokeLater(new Runnable() {
				public void run() {
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
							startIconUpdater();
						}
					}
				}
			} );
		} else {
			// started by the user
			mainScreen = new NwsClientScreen();
			mainScreen.setTitle(new LabelField("NWS Weather", LabelField.USE_ALL_WIDTH));
			
			pushScreen(mainScreen);
			
			// Debug...
			/*
			LocationData testLoc = new LocationData();
			testLoc.setLat(37.838177);
			testLoc.setLon(-122.296389);
			testLoc.setLocality("Emeryville");
			testLoc.setArea("CA");
			testLoc.setIcao("KOAK");
			testLoc.setCountry("US");
			_options.setCurrentLocation(testLoc);
			*/
			// ..end debug
			
			invokeLater(new Runnable() {
				public void run() {
					refreshWeather(false);
				}
			}, 4000, true);
			
			// if no location go to the options screen
			if (_options.getCurrentLocation() == null) {
				viewOptions();
			} else {
				getWeather(_options.getCurrentLocation());
			}
		}
	}
	
	private synchronized void refreshWeather(boolean force)
	{
		LocationData location = _options.getCurrentLocation();
		long now = new Date().getTime();
		if (location != null && (force || (now - lastUpdated) > _updateInterval))
			getWeather(location);
	}
	
	private void startIconUpdater()
	{
		// If we have a location then get the temperature
		if (_options.getCurrentLocation() != null) {
			storeLocation(_options.getCurrentLocation());
		}
		// The icon updater will periodically update the application icon
		_iconUpdater = new IconUpdater();
		_iconUpdater.start();
	}
	
	private void resetThreads()
	{
		if (_bitmapProvider != null && _bitmapProvider.isAlive()) {
			// We're going to stop the bitmapProvider and create a new fresh one
			//_bitmapProvider.interrupt();
			_bitmapProvider.stop();
			try {
				_bitmapProvider.join();
			} catch (InterruptedException ie) {
				System.err.println("BitmapProvider already stopped...");
			}
		}
		_bitmapProvider = null;
		_bitmapProvider = new BitmapProvider(); // new bitmapprovider thread
	}
	
	// menu items
	//cache the options menu item for reuse
	private MenuItem _optionsMenuItem = new MenuItem("Options", 110, 10) {
		public void run()
		{
			NwsClient.this.viewOptions();
		}
	};
	
	private MenuItem _refreshMenuItem = new MenuItem("Refresh", 110, 10) {
		public void run()
		{
			NwsClient.this.refreshWeather(true);
		}
	};
	
	private MenuItem _newLocationMenuItem = new MenuItem("Get Forecast", 100, 10) {
		public void run()
		{
			if (newLocField.getText().length() > 0) {
				setNewLocation(newLocField.getText());
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
		optionsScreen = new OptionsScreen();
		pushScreen(optionsScreen);
	}
	
	private boolean findNearestWeatherStation(LocationData loc)
	{
		String weatherStation = WeatherStation.findNearest(loc.getLat(), loc.getLon());
		loc.setIcao(weatherStation);
		return true;
	}
	
	/*
	private boolean getIcao(LocationData loc)
	{
		URLEncodedPostData post = new URLEncodedPostData(null, true);
		post.append("lat", Double.toString(loc.getLat()));
		post.append("lng", Double.toString(loc.getLon()));
		InputStream is = getUrl(GEONAMES_URL+"?"+post.toString());
		if (is != null) {
			try {
				DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
				DocumentBuilder builder = factory.newDocumentBuilder();
				Document document = builder.parse(is);
				is.close();
				
				try {
					loc.getIcaoFromXml(document);
				} catch (ParseError e) {
					Dialog.alert("Error getting weather station: "+e.getMessage());
					System.err.println(e.toString());
					return false;
				}
				
			} catch (IOException e) {
				Dialog.alert("Error parsing response from the geocoder: '"+e.toString()+"'");
				System.err.println(e.toString());
				return false;
			} catch (SAXException se) {
				System.err.println(se.toString());
				return false;
			} catch (ParserConfigurationException pe) {
				System.err.println(pe.toString());
				return false;
			}
			return true;
		} 
		return false;
	}
	*/
	
	private LocationData getLocationData(String userAddress) {
		// Encode the URL (from net.rim.blackberry.api.browser)
		URLEncodedPostData post = new URLEncodedPostData(null, true);
		post.append("q", userAddress);
		post.append("output", "xml");
		post.append("key", GOOGLE_API_KEY);
		InputStream is = getUrl(GOOGLE_MAPS_URL+"?"+post.toString());
		if (is != null) {
			// Parse the XML from the Google Maps Geocoder
			try {
				DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
				DocumentBuilder builder = factory.newDocumentBuilder();
				Document document = builder.parse(is);
				is.close();
				
				LocationData loc = new LocationData();
				loc.setUserAddress(userAddress);
				
				try {
					loc.loadFromXml(document);
					return loc;
				} catch (NotFoundException e) {
					Dialog.alert("Could not find location");
				} catch (AmbiguousLocationException e) {
					// choose a more specific address?
					Dialog.alert("Choose a more specific address");
				} catch (Exception e) {
					Dialog.alert("Error getting location data: "+e.getMessage());
					System.err.println(e.toString());
				}
				
			} catch (Exception e) {
				Dialog.alert("Error parsing response from the geocoder: '"+e.toString()+"'");
			}
		}
		return null;
	}
	
	private void setNewLocation(final String userAddress)
	{
		MessageScreen msgScreen = new MessageScreen("Getting location...");
		pushScreen(msgScreen);
		invokeLater(new Runnable() {
			public void run() {
				LocationData newLoc = getLocationData(userAddress);
				lastUpdated = 0;
				if (newLoc.getCountry().equals("US")) {
					// Get the ID of the weather station...
					findNearestWeatherStation(newLoc);
				}
				
				// Lost the getting location message...
				UiApplication.getUiApplication().popScreen(UiApplication.getUiApplication().getActiveScreen());
				if (newLoc != null) {
					_options.setCurrentLocation(newLoc);
					if (optionsScreen.isDisplayed())
						optionsScreen.close();
					storeLocation(newLoc);
					getWeather(newLoc);
				}
			}
		});
	}
	
	private Calendar parseTime(String timeStr)
	{
		// I have to parse the date myself because RIM's SimpleDateFormat doesn't
		// implement the 'parse' method. Lame.
		String part;
		Calendar cal = Calendar.getInstance(); // new calendar object
		//                       > 0123456789012345678901234 <
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
		
		// DEBUG
		/*
		System.err.println("Showing keys:");
		for (Enumeration e = times.keys() ; e.hasMoreElements() ;) {
			String key = (String)e.nextElement();
			System.err.println(" "+key);
			Vector myTimes = (Vector)times.get(key);
			for (int i=0; i<myTimes.size(); i++) {
				Calendar thisCal = (Calendar)myTimes.elementAt(i);
				System.err.println("   "+thisCal.getTime().toString());
			}
		}
		*/
		
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

		
	private Hashtable getNDFDObservations(Element root, Hashtable times) {
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
		
		// built up our hash of hashes
		Hashtable fc = new Hashtable();
		
		/*
		String[] observations = { "temperature", "precipitation", 
			"wind-speed", "direction", "cloud-amount", "humidity",
			"conditions-icon", "hazards"
		};
		*/
		
		String[] observations = { 
			"weather", 
			"temperature",
			"conditions-icon",
			"probability-of-precipitation"
		};
		
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
					if (obs.hasAttribute("type"))
						obsType = obs.getAttribute("type");
					else if (obsName.equals("weather")) 
						obsType = "weather-conditions";
					
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
	
	private static Hashtable parseUSCurrentConditions(InputStream is)
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
			System.err.println(pe.toString());
			return null;
		} catch (ParserConfigurationException pce) {
			throw new RuntimeException(pce.toString());
		} catch (SAXException se) {
			throw new RuntimeException(se.toString());
		} catch (IOException ioe) {
			throw new RuntimeException(ioe.toString());
		}
		return cc;
	}
	
	private Hashtable parseNDFD(InputStream is) 
	{
		Hashtable fc = new Hashtable();
		
		try { 
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document document = builder.parse(is);
				
			Element root = document.getDocumentElement();
			
			// Parse the times legend and get the 
			Hashtable times = getNDFDTimeSeries(document);
			
			// Get forecast values...
			fc = getNDFDObservations(root, times);
			
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
	
	private Observation getObservationByTime(Hashtable weather, String obsName, String type, Calendar start, Calendar end) 
	{
		if (weather.containsKey(obsName)) {
			Hashtable types = (Hashtable)weather.get(obsName);
			if (types.containsKey(type)) {
				Vector observations = (Vector)types.get(type);
				for (int i=0; i < observations.size(); i++) {
					Observation obs = (Observation)observations.elementAt(i);
					if (obs.time.startTime.after(start) && obs.time.startTime.before(end)) {
						// Return the string value of this overservation
						// System.err.println("Found observation");
						return obs;
					}
				}
			}
		}
		return null;
	}
	
	private void clearScreen()
	{
		// delete everyone from this manager
		popScreen(mainScreen);
		mainScreen = new NwsClientScreen();
		mainScreen.setTitle(new LabelField("NWS Weather", LabelField.USE_ALL_WIDTH));
		pushScreen(mainScreen);
	}
	
	private void displayNWSCurrentConditions(LocationData location, Hashtable weather)
	{
		String address = location.getLocality()+", "+location.getArea();
		//locationLabel = new LabelField(address);
		
		if (weather == null) {
			mainScreen.add(new LabelField("Unable to fetch current conditions"));
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
		String vis = (String)weather.get("visiblity");
		
		// Grab some fonts...
		FontFamily fontfam[] = FontFamily.getFontFamilies();
		Font tinyFont = fontfam[0].getFont(FontFamily.SCALABLE_FONT, 11);
		Font smallFont = fontfam[0].getFont(FontFamily.SCALABLE_FONT, 12);

		// Make the title label
		mainScreen.setTitle(new LabelField(address, LabelField.ELLIPSIS));
		
		VerticalFieldManager main = new VerticalFieldManager(Manager.USE_ALL_WIDTH);
		mainScreen.add(main);
		
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
			//System.err.println("Getting "+condIconUrl);
			_bitmapProvider.getBitmap(condIconUrl, currentCondBitmap);
		} else {
			System.err.println("Condition icon url was empty!");
		}
		
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
		
		
		String fields[] = { "Relative humidity", humidity, "Wind", wind, "Barometric pressure", pressure };
		for (int i=0; i < fields.length; i++) {
			LabelField fld;
			
			if (i==0 || i%2 == 0) {
				fld = new LabelField(fields[i], (Field.FIELD_LEFT | Field.FOCUSABLE));
				fld.setFont(tinyFont);
				btLeftCol.add(fld);
			} else {
				fld = new LabelField(fields[i],  Field.FIELD_RIGHT);
				fld.setFont(tinyFont);
				btRightCol.add(fld);
			}
		}
		btHField.add(btLeftCol);
		btHField.add(btRightCol);
		main.add(new SeparatorField());
		
	}
	
	private void displayGoogleCurrentConditions(LocationData location, Hashtable weather)
	{
		
		if (weather == null) {
			mainScreen.add(new LabelField("Unable to fetch current conditions"));
			return;
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
		mainScreen.setTitle(new LabelField(address, LabelField.ELLIPSIS));
		
		VerticalFieldManager main = new VerticalFieldManager(Manager.USE_ALL_WIDTH);
		mainScreen.add(main);
		
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
		topHField.add(currentCondBitmap);
		
		if (!condIconUrl.equals("")) {
			//System.err.println("Getting "+condIconUrl);
			_bitmapProvider.getBitmap(condIconUrl, currentCondBitmap);
		} else {
			System.err.println("Condition icon url was empty!");
		}
		
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
				fld = new LabelField(fields[i],  Field.FIELD_RIGHT);
				fld.setFont(tinyFont);
				btRightCol.add(fld);
			}
		}
		btHField.add(btLeftCol);
		btHField.add(btRightCol);
		main.add(new SeparatorField());
		
	}
	
	private void displayForecast(LocationData location, Vector forecast)
	{
		for (int i=0; i < forecast.size(); i++) {
			
			Hashtable day = (Hashtable)forecast.elementAt(i);
			
			String dayOfWeek = (String)day.get("day_of_week");
			String condition = (String)day.get("condition");
			String temperature = (String)day.get("temperature");
			String pop = null;
			if (day.get("probability-of-precipitation") != null) {
				pop = (String)day.get("probability-of-precipitation");
			}
			String iconUrl = (String)day.get("icon_url");
			
			// Layout the elements on the screen
			LabelField lbl = new LabelField(dayOfWeek);
			Font fnt = lbl.getFont().derive(Font.BOLD);
			lbl.setFont(fnt);
			mainScreen.add(lbl);
			
			BitmapField currentCondBitmap = new BitmapField();
			
			if (!iconUrl.equals("")) {
				//System.err.println("Getting "+iconUrl);
				_bitmapProvider.getBitmap(iconUrl, currentCondBitmap);
			} else {
				System.err.println("Condition icon url was empty!");
			}
			HorizontalFieldManager myHField = new HorizontalFieldManager();
			mainScreen.add(myHField);
			
			myHField.add(currentCondBitmap);
			
			FlowFieldManager rightCol = new FlowFieldManager();
			myHField.add(rightCol);
			
			// Show the observations
			rightCol.add(new RichTextField(condition));
			rightCol.add(new RichTextField(temperature));
			if (pop != null)
				rightCol.add(new RichTextField(pop));
			mainScreen.add(new SeparatorField());
		}
	}
	
	/*
	private void displayNDFDByDay(LocationData location, Hashtable weather)
	{
		Vector conditions = new Vector();
		
		if (weather.containsKey("weather")) {
			Hashtable types = (Hashtable)weather.get("weather");
			if (types.containsKey("weather-conditions")) {
				conditions = (Vector)types.get("weather-conditions");
			} else {
				// bail
				Dialog.alert("ERROR: Couldn't find \"weather-conditions\" tag in forecast results.");
			}
		} else {
			Dialog.alert("ERROR: Couldn't find \"weather\" tag in forecast results.");
		}
		
		for (int i = 0; i < conditions.size(); i++) {
			Observation myCond = (Observation)conditions.elementAt(i);
			String dayOfWeek = myCond.time.periodName; // e.g. "Thursday" or "Thanksgiving Day"
			//String dayOfWeek = myCond.time.startTime.getTime().toString();
			String conditionsLabel = myCond.value;
			Observation condIconUrl = getObservationByIndex(weather, "conditions-icon", "forecast-NWS", i);
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
				temp = tempLabel + tempObs.value+" "+tempObs.units;
			
			
			// Layout the elements on the screen
			LabelField lbl = new LabelField(dayOfWeek);
			Font fnt = lbl.getFont().derive(Font.BOLD);
			lbl.setFont(fnt);
			mainScreen.add(lbl);
			
			BitmapField currentCondBitmap = new BitmapField();
			
			if (!condIconUrl.value.equals("")) {
				System.err.println("Getting "+condIconUrl.value);
				_bitmapProvider.getBitmap(condIconUrl.value, currentCondBitmap);
			} else {
				System.err.println("Condition icon url was empty!");
			}
			HorizontalFieldManager myHField = new HorizontalFieldManager();
			mainScreen.add(myHField);
			
			myHField.add(currentCondBitmap);
			
			FlowFieldManager rightCol = new FlowFieldManager();
			myHField.add(rightCol);
			
			// Show max/min temps
			rightCol.add(new RichTextField(conditionsLabel));
			rightCol.add(new RichTextField(temp));
			mainScreen.add(new SeparatorField());
			
		}
		
	}
	
	private void displayNDFD(LocationData location, Hashtable weather)
	{
		resetThreads(); // reset the bitmapProvider
		clearScreen();
		
		String address = location.getLocality()+", "+location.getArea();
		//locationLabel = new LabelField(address);
		
		mainScreen.setTitle(new LabelField(address, LabelField.USE_ALL_WIDTH));
		
		long day = 86400000; // milliseconds in one day
		
		// Grab two dates for each 24-hour interval
		Calendar start = Calendar.getInstance();
		Calendar end = Calendar.getInstance(); // we'll reset this later
		if (start.get(Calendar.HOUR_OF_DAY) > 19) {
			// If it's after 7pm set the day to tomorrow
			end.setTime(new Date(end.getTime().getTime() + day));
		}
		// set 'end' to one second before midnight yesterday
		end.set(Calendar.AM_PM, Calendar.AM);
		end.set(Calendar.HOUR_OF_DAY, 0);
		end.set(Calendar.MINUTE, 0);
		end.set(Calendar.SECOND, 0);
		// Show seven days
		for (int i = 0; i < 7; i++) {
			// start is the previous end
			start.setTime(new Date(end.getTime().getTime()));
			end.setTime(new Date(end.getTime().getTime() + day));
			String dayOfWeek = getCalendarDayOfWeek(start);
			
			// Get our data
			// Temperature
			Observation maxTemp = getObservationByTime(weather, "temperature", "maximum", start, end);
			//start.set(Calendar.HOUR_OF_DAY, 12);
			Observation minTemp = getObservationByTime(weather, "temperature", "minimum", start, end);
			// Get the condition icon
			Observation condIconUrl = getObservationByTime(weather, "conditions-icon", "forecast-NWS", start, end);
			
			mainScreen.add(new LabelField(dayOfWeek));
			
			//System.err.println(start.getTime().toString()+"->"+end.getTime().toString());
			
			BitmapField currentCondBitmap = new BitmapField();
			
			if (!condIconUrl.value.equals("")) {
				System.err.println("Getting "+condIconUrl.value);
				_bitmapProvider.getBitmap(condIconUrl.value, currentCondBitmap);
			} else {
				System.err.println("Condition icon url was empty!");
			}
			HorizontalFieldManager myHField = new HorizontalFieldManager();
			mainScreen.add(myHField);
			
			myHField.add(currentCondBitmap);
			
			FlowFieldManager rightCol = new FlowFieldManager();
			myHField.add(rightCol);
			
			// Show max/min temps
			rightCol.add(new RichTextField("Max: "+maxTemp.value));
			rightCol.add(new RichTextField("Another item here!"));
			mainScreen.add(new SeparatorField());
		}
	}
	*/
	
	private static Hashtable parseGoogleWeather(final InputStream is) throws ParseError
	{
		/* Build a hash of hashes and vectors:
		 * Hashtable(
		 *  'currentConditions' => Hashtable(
		 *                          'weather' => "Partly Cloudy"
		 *                          'temperature' => "45" . . .
		 *                         )
		 *  'forecast' => Vector( Hashtable(
		 *                                  'day_of_week' => 'Sat'
		 *                                  'weather' => "Rain"
		 *                                  'temperature' => "50"
		 *                                  'conditions-icon' => "http..."
		 *                                  'wind' => "Wind: W at 10mph" 
		 *                         )
		 */                     
		 
		Hashtable gw = new Hashtable();
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document document = builder.parse(is);
				
			Element root = document.getDocumentElement();
			
			NodeList nl = document.getElementsByTagName("weather");
			if (nl.getLength() > 0) {
				Element weather = (Element)nl.item(0);
				// It has a weather element
				
				// Check for error condition:
				NodeList problemNodes = weather.getElementsByTagName("problem_cause");
				if (problemNodes.getLength() > 0) {
					Element problemEl = (Element)problemNodes.item(0);
					String problem = problemEl.getAttribute("data");
					throw new ParseError(problem+"Make sure you specify "+
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
	
	/* 
	 *  This will get cached 
	 */
	public static Hashtable getGoogleWeather(final LocationData location) throws ParseError
	{
		Date now = new Date();
		if (_googleWeather != null && _googleWeather.containsKey("lastUpdate")) {
			Date updated = (Date)_googleWeather.get("lastUpdate");
			if (updated.getTime() > (now.getTime() - _updateInterval)) {
				return _googleWeather;
			}
		}
		
		// Get some new Google weather
		URLEncodedPostData post = new URLEncodedPostData(null, true);
		//post.append("weather", location.getUserAddress());
		post.append("weather", location.getLocality()+", "+location.getCountry());
		post.append("hl", "en");
		InputStream is = getUrl(GOOGLE_WEATHER_URL+"?"+post.toString());
		try {
			_googleWeather = parseGoogleWeather(is);
			is.close();
			_googleWeather.put("lastUpdate", now);
		} catch (IOException ioe) {
			// choose a more specific address?
			throw new RuntimeException(ioe.toString());
		}
		return _googleWeather;
	}
	
	private void getUSForecast(final LocationData location)
	{
		// get NWS Weather
		final URLEncodedPostData post = new URLEncodedPostData(null, true);
		//post.append("whichClient", "NDFD");
		post.append("whichClient", "NDFDgenByDay");
		post.append("format", "12 hourly");
		post.append("lat", Double.toString(location.getLat()));
		post.append("lon", Double.toString(location.getLon()));
		post.append("numDays", "7"); // get a week
		
		invokeLater(new Runnable() {
			public void run() {
				InputStream is = getUrl(NWS_URL+"?"+post.toString());
				System.err.println("Weather url: "+NWS_URL+"?"+post.toString());
			
				if (is != null) {
					try {
						Vector flattened = flattenNDFD(parseNDFD(is));
						is.close();
						displayForecast(location, flattened);
						//displayNDFDByDay(location, parsed);
					} catch (IOException ioe) {
						// choose a more specific address?
						Dialog.alert("Error getting weather informtion: "+ioe.toString());
					}
				}
			}
		});
	}
	
	private void getNWSCurrentConditions(final LocationData location)
	{
		if (location.getIcao().equals("")) {
			Dialog.alert("Could not find closest weather station for location.");
			return;
		}
		
		Hashtable parsed;
		try {
			parsed = getParseCurrentConditions(location);
			//updateIcon((String)parsed.get("temp_f"));
			displayNWSCurrentConditions(location, parsed);
		} catch (IOException ioe) {
			// choose a more specific address?
			LabelField errorLabel = new LabelField("Error getting current conditions: "+ioe.getMessage());
			mainScreen.add(errorLabel);
		} catch (ParseError pe) {
			LabelField errorLabel = new LabelField("Error getting current conditions: "+pe.getMessage());
			mainScreen.add(errorLabel);
		}
	}
	
	private synchronized void getWeather(final LocationData location) 
	{
		resetThreads(); // reset the bitmapProvider
		clearScreen();
		lastUpdated = new Date().getTime();
		
		String myCountry = location.getCountry();
		if (myCountry.equals("US") && _options.useNws()) {
			// United States - Get NWS NDFD data 
			final MessageScreen wait = new MessageScreen("Getting Weather...");
			pushScreen(wait);
			
			getNWSCurrentConditions(location);
			
			invokeLater(new Runnable() {
				public void run() {
					popScreen(wait);
					getUSForecast(location);
				}
			});
			
		} else {
			// International - get Google Weather
			try {
				Hashtable weather = getGoogleWeather(location);
				Vector fc = (Vector)weather.get("forecast");
				displayGoogleCurrentConditions(location, (Hashtable)weather.get("current_conditions"));
				displayForecast(location, fc);
			} catch(ParseError re) {
				LabelField errorLabel = new LabelField("Error loading Google Weather: "+re.getMessage());
				mainScreen.add(errorLabel);
			}
		}
	}
	
	private void storeLocation(LocationData location)
	{
		_googleWeather = null; // clear out cached google weather...
		RuntimeStore.getRuntimeStore().replace(ID, location);
	}
	
}

