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
		"wind-speed", "direction", "cloud-amount", "humidity", "probability",
		"weather"
	};
	
    public DetailScreen(final Hashtable forecastDetail, final Calendar when)
	{
		super();
		
		DateFormat dayFormat = new SimpleDateFormat("E"); // day of week
		DateFormat timeFormat = new SimpleDateFormat("ha"); // 8pm
		
		int desiredDate = when.get(Calendar.DATE); // day of month
		Date now = when.getTime();
		String dayOfWeek = dayFormat.format(now, new StringBuffer(), null).toString();
		
		LabelField lbl = new LabelField(dayOfWeek);
		Font fnt = lbl.getFont().derive(Font.BOLD);
		lbl.setFont(fnt);
		add(lbl);
		
		for (int i=0; i < obs.length; i++) {
			if (forecastDetail.containsKey(obs[i])) {
				Hashtable obsType = (Hashtable)forecastDetail.get(obs[i]);
				
				for (Enumeration e = obsType.keys(); e.hasMoreElements();) {
					String type = (String)e.nextElement();
					NwsClient.ObservationGroup obs = (NwsClient.ObservationGroup)obsType.get(type);
					String label = obs.type;
					add(new LabelField(label));
					
					for (int j=0; j < obs.samples.size(); j++) {
						NwsClient.Observation theObs = (NwsClient.Observation)obs.samples.elementAt(j);
						if (theObs.time.startTime.get(Calendar.DATE) == desiredDate) {
							Date thisDate = theObs.time.startTime.getTime();
							String date = timeFormat.format(thisDate, new StringBuffer(), null).toString();
							add(new RichTextField(date+" "+theObs.value+" "+theObs.units));
						}
					}
					add(new SeparatorField());
				}

			}
		}
	}

}

