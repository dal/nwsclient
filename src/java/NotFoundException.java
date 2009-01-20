package com.renderfast.nwsclient;

public class NotFoundException extends Exception
{
	public NotFoundException()
	{
		super("Unknown location");
	}
}

