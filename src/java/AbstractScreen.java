/**
 * AbstractScreen - Base class for NWSClient screens
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

import java.util.*;
import net.rim.device.api.ui.container.MainScreen;
import net.rim.device.api.ui.component.LabelField;
import net.rim.device.api.ui.Manager;
import net.rim.device.api.system.*;

public class AbstractScreen extends MainScreen
{
	
	private LabelStatusField _titleField;
	
	public AbstractScreen()
	{
		this("NWSClient", "");
	}
	
	public AbstractScreen(String title, String status)
	{
		this(title, status, 0);
	}
	
	public AbstractScreen(String title, String status, long style)
	{
		super(style);
		setTitle(new LabelStatusField(title, LabelField.USE_ALL_WIDTH, status));
	}
	 
	public void setTitle(LabelStatusField title)
	{
		super.setTitle(title);
		_titleField = title;
	}
	
	public String getTitleText()
	{
		return _titleField.getText();
	}
	
	public void setTitleText(String title)
	{
		_titleField.setText(title);
	}
	
	public void setStatusText(String status)
	{
		_titleField.setStatus(status);
	}
	
	public void setStatusVisible(boolean visible)
	{
		// Show/hide the status indicator
		_titleField.setStatusVisible(visible);
	}
	
	public boolean getStatusVisible()
	{
		return _titleField.getStatusVisible();
	}
	
	public String getStatusText()
	{
		return _titleField.getStatus();
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
	
}


