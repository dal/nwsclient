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

class LinkField extends Field implements DrawStyle
{
	private String _label;
	private Font _font;
	private int _labelHeight;
	private int _labelWidth;
	
	public LinkField(String label) 
	{
		this(label, 0 );
	}
	
	public LinkField(String label, long style) 
	{
		super(style);
		_label = label;
		_font = getFont();
		_font = _font.derive(Font.UNDERLINED);
		setFont(_font);
		_labelHeight = _font.getHeight();
		_labelWidth = _font.getAdvance(_label);
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
		return _labelWidth + 4;
	}
	
	public int getPreferredHeight() 
	{
		return _labelHeight + 2;
	}
	
	protected void layout(int width, int height) 
	{
		_font = getFont();
		_labelHeight = _font.getHeight();
		_labelWidth = _font.getAdvance(_label);
		
		width = Math.min( width, getPreferredWidth() );
		height = Math.min( height, getPreferredHeight() );
		
		setExtent( width, height );
	}
	
	protected void paint(Graphics graphics) 
	{
		int textX, textY, textWidth;
		int w = getWidth();
		graphics.drawText(_label, 0, 0, (int)( getStyle() & DrawStyle.ELLIPSIS | DrawStyle.HALIGN_MASK ), w);
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

