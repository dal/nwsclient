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
import net.rim.device.api.system.*;
import net.rim.device.api.ui.component.*;
import net.rim.device.api.system.*;

class LabelStatusField extends LabelField implements DrawStyle
{
	private String _status;
	private int _statusHeight;
	private int _statusWidth;
	private Font _statusFont;
	private boolean _showStatus;
	
	public LabelStatusField(String label, long style, String status) 
	{
		super(label, style);
		_status = status;
		_statusFont = getFont().derive(Font.BOLD);
		_showStatus = false;
		_setStatusSize();
	}
	
	public void setStatusVisible(boolean visible)
	{
		if (_showStatus != visible) {
			_showStatus = visible;
			invalidate();
		}
	}
	
	public boolean getStatusVisible()
	{
		return _showStatus;
	}
	
	public String getStatus() 
	{
		return _status;
	}
	
	public void setStatus(String status)
	{
		_status = status;
		_setStatusSize();
	}
	
	public void setStatusFont(Font font)
	{
		_statusFont = font;
		_setStatusSize();
	}
	
	protected void _setStatusSize()
	{
		_statusHeight = _statusFont.getHeight() + 4;
		_statusWidth = _statusFont.getAdvance(_status) + 4;
	}
	
	public int getPreferredWidth()
	{
		// We want the whole screen
		return Display.getWidth();
	}
	
	public int getPreferredHeight()
	{
		return _statusHeight + 4;
	}
	
	protected void paint(Graphics graphics) 
	{
		super.paint(graphics);
		if (_showStatus) {
			int boxX = getContentRect().width - _statusWidth;
			int boxY = 0;
			int color = graphics.getColor();
			graphics.setColor(0xccccff); // l blue
			graphics.fillRect(boxX , boxY, _statusWidth, getContentRect().height);
			graphics.setColor(0x666666);
			graphics.drawRect(boxX, boxY, _statusWidth, getContentRect().height);
			graphics.setFont(_statusFont);
			graphics.drawText(_status, boxX+2, boxY, (int)( getStyle() & DrawStyle.ELLIPSIS | DrawStyle.HALIGN_MASK ), _statusWidth );
			graphics.setColor(color);
		}
	}
	
	protected void fieldChangeNotify(int context)  
	{
		try {
			this.getChangeListener().fieldChanged(this, context);  
		} catch (Exception e) { }
	}  
	
	protected boolean navigationClick(int status, int time)
	{
		fieldChangeNotify(1);  
		return true;
	}
	
}

