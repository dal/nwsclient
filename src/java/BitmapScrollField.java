
package com.renderfast.nwsclient;

import java.lang.Math;
import net.rim.device.api.system.Display;
import net.rim.device.api.system.Bitmap;
import net.rim.device.api.ui.Field;
import net.rim.device.api.ui.XYRect;
import net.rim.device.api.ui.Graphics;
import net.rim.device.api.util.MathUtilities;

public class BitmapScrollField extends Field
{
	private Bitmap _bitmap;
	private int _xPos;
	private int _yPos;
	
	public BitmapScrollField(Bitmap bitmap)
	{
		this(bitmap, Field.FOCUSABLE);
	}
	
	public BitmapScrollField(Bitmap bitmap, long style)
	{
		super(style);
		_xPos = 0;
		_yPos = 0;
		_bitmap = bitmap;
	}
	
	public void centerView()
	{
		int w = getWidth();
		int h = getHeight();
		int bw = _bitmap.getWidth();
		int bh = _bitmap.getHeight();
		if (bw > w) 
			_xPos = (bw - w) / 2;
		if (bh > h) 
			_yPos = (bh - h) / 2;
		invalidate();
	}
	
	public boolean isFocusable()
	{
		return true;
	}
	
	public int getPreferredWidth()
	{
		int width = _bitmap.getWidth(); 
		int displayWidth = Display.getWidth();
		return Math.min(width, displayWidth);
	}
	
	public int getPreferredHeight()
	{
		int height = _bitmap.getHeight();
		int displayHeight = Display.getHeight();
		return Math.min(height, displayHeight);
	}
	
	protected void layout(int width, int height) 
	{
		width = Math.min( width, getPreferredWidth() );
		height = Math.min( height, getPreferredHeight() );
		setExtent( width, height );
	}
	
	protected void paint(Graphics graphics)
	{
		XYRect clip = new XYRect();
		graphics.getAbsoluteClippingRect(clip);
		graphics.drawBitmap(0, 0, clip.width, clip.height, _bitmap, _xPos, _yPos);
	}
	
	protected boolean navigationMovement(int dx, int dy, int status, int time)
	{
		int width = getWidth();
		int height = getHeight();
		int newXPos = _xPos;
		int newYPos = _yPos;
		if (width < _bitmap.getWidth()) {
			newXPos = MathUtilities.clamp(0, _xPos + dx, _bitmap.getWidth() - width);
		}
		if (height < _bitmap.getHeight()) {
			newYPos = MathUtilities.clamp(0, _yPos + dy, _bitmap.getWidth() - height);
		}
		
		if (_xPos != newXPos || _yPos != newYPos) {
			_xPos = newXPos;
			_yPos = newYPos;
			invalidate();
			return true;
		}
		return false;
	}

}

