// :noTabs=false:tabSize=4:

package com.renderfast.nwsclient;

import java.util.*;
import net.rim.device.api.ui.*;
import net.rim.device.api.i18n.*;
import net.rim.device.api.ui.component.*;
import net.rim.device.api.ui.container.*;

public class DetailScreen extends AbstractScreen
{
	
	private BitmapProvider _bitmapProvider;
	
	private static final String[] obs = { "hazards", "temperature", 
		"precipitation", "wind-speed", "direction", "weather", "probability", 
		"humidity", "cloud-amount", "conditions-icon"
	};
	
    public DetailScreen(final Hashtable forecastDetail, final Calendar when)
	{
		super();
		
		_bitmapProvider = BitmapProvider.GetInstance();
		
		DateFormat dayFormat = new SimpleDateFormat("EEEE"); // day of week: "Monday"
		DateFormat timeFormat = new SimpleDateFormat("ha"); // time: "8p"
		
		int desiredDate = when.get(Calendar.DATE); // day of month
		Date now = when.getTime();
		String dayOfWeek = dayFormat.format(now, new StringBuffer(), null).toString();
		
		LabelField lbl = new LabelField(dayOfWeek);
		Font fnt = lbl.getFont().derive(Font.BOLD);
		lbl.setFont(fnt);
		add(lbl);
		
		// A running count of all the details we've found
		int foundDetails = 0;
		
		for (int i=0; i < obs.length; i++) {
			if (forecastDetail.containsKey(obs[i])) {
				String type = obs[i];
				Hashtable obsType = (Hashtable)forecastDetail.get(type);
				
				Vector subTypes = GetObservationSubTypes(type, obsType);
				
				for (int j=0; j< subTypes.size(); j++) {
					String subType = (String)subTypes.elementAt(j);
					
					if (obsType.containsKey(subType)) {
					
						NwsClient.ObservationGroup obs = (NwsClient.ObservationGroup)obsType.get(subType);
						
						Vector foundObservations = new Vector();
						// Find only observations for the desired day
						for (int k=0; k < obs.samples.size(); k++) {
							NwsClient.Observation theObs = (NwsClient.Observation)obs.samples.elementAt(k);
							if (theObs.time.startTime.get(Calendar.DATE) == desiredDate)
								foundObservations.addElement(theObs);
						}
						
						// Only print the label if there are actually observations to show
						if (foundObservations.size() > 0) {
							String label = obs.type;
							LabelField typeLabel = new LabelField(label);
							typeLabel.setFont(fnt);
							add(typeLabel);
							
							if (type.equals("hazards")) 
								foundObservations = NwsClient.rollUpObservations(foundObservations);
							
							for (int k=0; k < foundObservations.size(); k++) {
								NwsClient.Observation theObs = (NwsClient.Observation)foundObservations.elementAt(k);
								Date thisDate = theObs.time.startTime.getTime();
								String date = timeFormat.format(thisDate, new StringBuffer(), null).toString();
								if (type.equals("conditions-icon")) {
									HorizontalFieldManager row =  new HorizontalFieldManager();
									add(row);
									BitmapField condBitmap = new BitmapField();
									row.add(condBitmap);
									row.add(new RichTextField(" "+date));
									_bitmapProvider.getBitmap(theObs.value, condBitmap, false);
								} else if (type.equals("hazards")) {
									Date alertDate = theObs.time.startTime.getTime();
									DateFormat dateFormat = new SimpleDateFormat("ha E");
									
									final String theObsUrl = theObs.url;
									String startTimeStr = dateFormat.format(alertDate, new StringBuffer(), null).toString();
									String endTimeStr = "";
									if (theObs.time.endTime != null) {
										Date endDate = theObs.time.endTime.getTime();
										endTimeStr = dateFormat.format(endDate, new StringBuffer(), null).toString();
										endTimeStr = " - "+endTimeStr;
									}
									LinkField warningField = new LinkField(theObs.value+" "+theObs.units+" "+startTimeStr+endTimeStr) {
										public void paint(Graphics graphics) {
											// Warning text is red
											graphics.setColor(0xff0000);
											super.paint(graphics);
										}
									};
									FieldChangeListener warningListener = new FieldChangeListener() {
										public void fieldChanged(Field field, int context) {
											NwsClient.getLinkInBrowser(theObsUrl);
										}
									};
									warningField.setChangeListener(warningListener);
									add(warningField);
								} else {
									add(new RichTextField(date+" "+theObs.value+" "+theObs.units));
								}
							}
							add(new SeparatorField());
							// update the overall detail count
							foundDetails += foundObservations.size();
						}
					}
				}
			}
		}
		
		if (foundDetails <= 0) {
			add(new RichTextField("No forecast details found for this date."));
		} else {
			add(new RichTextField("")); // kludge so scrolling works
		}
		
	}
	
	public Vector GetKeys(final Hashtable hash) {
		Vector ret = new Vector();
		for (Enumeration e = hash.keys(); e.hasMoreElements();) {
			String type = (String)e.nextElement();
			ret.addElement(type);
		}
		return ret;
	}
	
	public Vector AppendVector(Vector first, Vector second) {
		for (int i=0; i<second.size(); i++) {
			if (!first.contains(second.elementAt(i))) 
				first.addElement(second.elementAt(i));
		}
		return first;
	}
	
	public Vector GetObservationSubTypes(String type, final Hashtable obsType) {
		Vector subTypes = new Vector();
		if (type.equals("temperature")) {
			subTypes.addElement("maximum");
			subTypes.addElement("minimum");
			subTypes.addElement("hourly");
			subTypes.addElement("apparent");
		} else if (type.equals("wind-speed")) {
			subTypes.addElement("sustained");
		} else if (type.equals("precipitation")) {
			subTypes.addElement("liquid");
			subTypes.addElement("snow");
		}
		Vector existing = GetKeys(obsType);
		// Add any other existing subTypes not listed above
		subTypes = AppendVector(subTypes, existing);
		return subTypes;
	}

}

