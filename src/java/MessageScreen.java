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

import net.rim.device.api.system.Bitmap;
import net.rim.device.api.ui.Field;
import net.rim.device.api.ui.component.BitmapField;
import net.rim.device.api.ui.component.RichTextField;
import net.rim.device.api.ui.container.HorizontalFieldManager;
import net.rim.device.api.ui.container.PopupScreen;

public class MessageScreen extends PopupScreen
{
	private String _message;
	
	public MessageScreen (String message)
	{
		super( new HorizontalFieldManager(), Field.NON_FOCUSABLE);
		_message = message;
		final BitmapField logo = new BitmapField(Bitmap.getBitmapResource("website_blue.png"));
		logo.setSpace( 5, 5 );
		add(logo);
	
		RichTextField rtf = new RichTextField(_message, 
			Field.FIELD_VCENTER | Field.NON_FOCUSABLE | Field.FIELD_HCENTER);
		rtf.setEditable( false );
		
		add(rtf);
	}
}


