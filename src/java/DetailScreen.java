// :noTabs=false:tabSize=4:

package com.renderfast.nwsclient;

import java.util.*;
import net.rim.device.api.ui.*;
import net.rim.device.api.i18n.*;
import net.rim.device.api.ui.component.*;
import net.rim.device.api.ui.container.*;

public class DetailScreen extends MainScreen
{
	
	private static final String[] obs = { "temperature", "precipitation", 
		"wind-speed", "direction", "weather", "probability", "humidity", 
		"cloud-amount"
	};
	
    public DetailScreen(final Hashtable forecastDetail, final Calendar when)
	{
		super();
		
		DateFormat dayFormat = new SimpleDateFormat("EEEE"); // day of week
		DateFormat timeFormat = new SimpleDateFormat("ha"); // 8pm
		
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
				Hashtable obsType = (Hashtable)forecastDetail.get(obs[i]);
				
				Vector subTypes = GetObservationSubTypes(obs[i], obsType);
				
				for (int j=0; j< subTypes.size(); j++) {
					String type = (String)subTypes.elementAt(j);
					
					if (obsType.containsKey(type)) {
					
						NwsClient.ObservationGroup obs = (NwsClient.ObservationGroup)obsType.get(type);
						
						// Print the subType's observations first, then insert the label later
						int startIndex = getFieldCount();
						int foundObs = 0;
						
						for (int k=0; k < obs.samples.size(); k++) {
							NwsClient.Observation theObs = (NwsClient.Observation)obs.samples.elementAt(k);
							if (theObs.time.startTime.get(Calendar.DATE) == desiredDate) {
								Date thisDate = theObs.time.startTime.getTime();
								String date = timeFormat.format(thisDate, new StringBuffer(), null).toString();
								add(new RichTextField(date+" "+theObs.value+" "+theObs.units));
								foundObs++;
							}
						}
						
						// Only print the label if there are actually observations to show
						if (foundObs > 0) {
							String label = obs.type;
							LabelField typeLabel = new LabelField(label);
							typeLabel.setFont(fnt);
							insert(typeLabel, startIndex);
							
							add(new SeparatorField());
							
							// update the overall detail count
							foundDetails += foundObs;
						}
					}
				}
			}
		}
		
		if (foundDetails <= 0) {
			add(new RichTextField("No forecast details found for this date."));
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

