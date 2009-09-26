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
import java.util.*;

class LinkField extends Field implements DrawStyle
{
	private String _label;
	private Vector _lines;
	
	public LinkField(String label) 
	{
		this(label, 0 );
	}
	
	public LinkField(String label, long style) 
	{
		super(style);
		_label = label;
		_lines = new Vector();
	}
	
	public String getLabel() 
	{
		return _label;
	}
	
	public boolean isFocusable() 
	{
		return true;
	}
	
	protected void drawFocus(Graphics graphics, boolean on) 
	{
		graphics.invert(1, 1, getWidth() - 2, getHeight() - 2);
	}
	
	public int getPreferredWidth() 
	{
		Font _font = getFont();
		return _font.getAdvance(_label) + 4;
	}
	
	public int getPreferredHeight() 
	{
		Font _font = getFont();
		return _font.getHeight() + 2;
	}
	
	protected void layout(int width, int height) 
	{
		Font _font = getFont();
		int _labelHeight = _font.getHeight();
		int _labelWidth = _font.getAdvance(_label);
		
		width = Math.min( width, getPreferredWidth() );
		_lines.removeAllElements();
		if (width < _labelWidth) {
			StringBuffer thisLine = new StringBuffer();
			int lastPos = 0;
			while (lastPos != -1) {
				int newPos = _label.indexOf(" ", lastPos+1);
				String part;
				if (newPos == -1) {
					part = _label.substring(lastPos);
				} else {
					part = _label.substring(lastPos, newPos);
				}
				
				thisLine.append(part);
				
				if (_font.getAdvance(thisLine.toString()) > width) {
					_lines.addElement(thisLine.toString().substring(0, thisLine.length() - part.length()));
					thisLine.delete(0,thisLine.length());
				} else if (newPos == -1) {
					_lines.addElement(thisLine.toString());
					lastPos = newPos;
				} else {
					lastPos = newPos;
				}
			}
			height = _lines.size() * _labelHeight;
		} else {
			_lines.addElement(_label);
			height = Math.min( height, getPreferredHeight() );
		}
		
		setExtent( width, height );
	}
	
	protected void paint(Graphics graphics) 
	{
		int textX, textY, textWidth;
		Font _font = getFont();
		_font = _font.derive(Font.UNDERLINED);
		setFont(_font);
		
		for (int i=0; i<_lines.size(); i++) {
			int offset = _font.getHeight() * i;
			int w = _font.getAdvance((String)_lines.elementAt(i))+4;
			graphics.drawText((String)_lines.elementAt(i), 0, offset, (int)( getStyle() | DrawStyle.LEFT | DrawStyle.HALIGN_MASK ), w);
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

