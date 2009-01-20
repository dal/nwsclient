/**
 * A NDFD Weather data client
 */
 
package com.renderfast.nwsclient;

import net.rim.device.api.system.Bitmap;
import net.rim.device.api.ui.Field;
import net.rim.device.api.ui.component.BitmapField;
import net.rim.device.api.ui.component.RichTextField;
import net.rim.device.api.ui.container.HorizontalFieldManager;
import net.rim.device.api.ui.container.PopupScreen;

public class MessageScreen extends PopupScreen
{
	private String message;
	
	public MessageScreen (String message)
	{
		super( new HorizontalFieldManager(), Field.NON_FOCUSABLE);
		this.message = message;
		final BitmapField logo = new BitmapField(Bitmap.getBitmapResource("website_blue.png"));
		logo.setSpace( 5, 5 );
		add(logo);
	
		RichTextField rtf = new RichTextField(message, Field.FIELD_VCENTER | Field.NON_FOCUSABLE | Field.FIELD_HCENTER);
		rtf.setEditable( false );
		
		add(rtf);
	}
}


