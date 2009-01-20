
package com.renderfast.nwsclient;

import net.rim.device.api.util.*;
import org.w3c.dom.*;

/*

accuracy < 2?
Way too imprecise, spit out an error notificiation

accuracy >= 4?
	yes -> then locality should be set...
	no -> then no locality, weather information is going to be very sketchy
	
is there a SubAdministrativeArea element?
	yes -> use the SubAdministrative Area in the area
	no -> use AdministrativeArea as our area field
	
	Country: countryNameCode
	Area: SubAdministrativeAreaName ? AdministrativeAreaName ? ""
	Locality: LocalityName ? AddressLine ? address

*/

public class LocationData implements Persistable
{
	private static int _minimumAccuracy = 3;
	
	// The name supplied by the user
	private String _userAddress;
	
	// The CountryNameCode supplied by the geocoder
	private String _country;
	// AdministrativeArea/SubAdministrativeArea supplied by the geocoder
	private String _area;
	// Locality 
	private String _locality;
	
	// Lattitude
	private double _lat;
	// Longitude
	private double _lon;
	
	// Nearest weather station id
	private String _icao;
	
	public LocationData()
	{
		_icao = "";
		_userAddress = "";
	}
	
	public boolean equals(LocationData that)
	{
		return (
				that.getCountry().equals(_country) && 
				that.getArea().equals(_area) && 
				that.getLocality().equals(_locality)
				);
	}
	
	public String getUserAddress()
	{
		return _userAddress;
	}
	
	public String getCountry()
	{
		return _country;
	}
	
	public String getArea()
	{
		return _area;
	}
	
	public String getLocality()
	{
		return _locality;
	}
	
	public double getLat()
	{
		return _lat;
	}
	
	public double getLon()
	{
		return _lon;
	}
	
	public String getIcao()
	{
		return _icao;
	}
	
	public void setUserAddress(String ua)
	{
		_userAddress = ua;
	}

	public void setCountry(String country)
	{
		_country = country;
	}
	
	public void setArea(String area)
	{
		_area = area;
	}
	
	public void setLocality(String locality)
	{
		_locality = locality;
	}
	
	public void setLat(double lat)
	{
		_lat = lat;
	}
	
	public void setLon(double lon)
	{
		_lon = lon;
	}
	
	public void setIcao(String icao)
	{
		_icao = icao;
	}
	
	public void loadFromXml(Document document) throws AmbiguousLocationException, NotFoundException, ParseError
	{
		Element root = document.getDocumentElement();
		
		Element status = (Element)XmlHelper.getNode(root, "Status");
		if (status != null) {
			// 200 = OK, 602 = BAD
			String code = XmlHelper.getValue(status, "code");
			if (!code.equals("200")) 
				throw new NotFoundException();
		}
		
		// If we get here google said we were successful...
		Element addressDetails = (Element)XmlHelper.getNode(root, "AddressDetails");
		
		int accuracy = 0;
		try {
			accuracy = Integer.parseInt(addressDetails.getAttribute("Accuracy"));
		} catch (NumberFormatException e) {
			System.err.println("Could not get accuracy information"); 
		}
		
		if (accuracy < _minimumAccuracy)
			throw new AmbiguousLocationException();
		
		// Get the country... 
		setCountry( XmlHelper.getValue(addressDetails, "CountryNameCode") );
		
		// Get the area
		NodeList subAdminList = addressDetails.getElementsByTagName("SubAdministrativeAreaName");
		NodeList adminList = addressDetails.getElementsByTagName("AdministrativeAreaName");
		if (subAdminList.getLength() > 0) {
			setArea( XmlHelper.getNodeText(subAdminList.item(0)) );
		} else if (adminList.getLength() > 0) {
			setArea( XmlHelper.getValue(addressDetails, "AdministrativeAreaName") );
		} else {
			setArea( "" );
		}
		
		NodeList localityNameList = addressDetails.getElementsByTagName("LocalityName");
		if (localityNameList.getLength() > 0) {
			setLocality( XmlHelper.getValue(addressDetails, "LocalityName") );
		} else {
			NodeList addressLineList = addressDetails.getElementsByTagName("AddressLine");
			if (addressLineList.getLength() > 0) {
				setLocality( XmlHelper.getValue(addressDetails, "AddressLine") );
			} else {
				setLocality( XmlHelper.getValue(root, "address") );
			}
		}
		
		// Now get lat/lon
		String coordinates = XmlHelper.getValue(root, "coordinates");
		int firstCommaPos = coordinates.indexOf(',');
		System.err.println("Coordinate string is: "+coordinates);
		// longitude is listed first
		if (firstCommaPos != -1) {
			// Google puts a second comma in the coordinate string...
			int secondCommaPos = coordinates.indexOf(',', firstCommaPos+1);
			if (secondCommaPos == -1)
				secondCommaPos = coordinates.length();
			
			//System.err.println("First  comma pos: "+Integer.toString(firstCommaPos));
			//System.err.println("Second comma pos: "+Integer.toString(secondCommaPos));
			
			try {
				String tmpLon = coordinates.substring(0, firstCommaPos);
				System.err.println("Lon string: "+tmpLon);
				_lon = Double.parseDouble(tmpLon);
				String tmpLat = coordinates.substring(firstCommaPos+1, secondCommaPos);
				System.err.println("Lat string: "+tmpLat);
				_lat = Double.parseDouble(tmpLat);
			} catch (NumberFormatException e) {
				System.err.println("Could not get lat/lon information");
				throw new ParseError("Could not understand lat/lon data");
			}
		} else {
			throw new ParseError("Could not understand lat/lon data");
		}
	}
	
	public void getIcaoFromXml(Document document) throws ParseError
	{
		Element root = document.getDocumentElement();
		String icao = XmlHelper.getValue(root, "ICAO");
		System.err.println("Icao: "+icao);
		_icao = icao;
	}
}
