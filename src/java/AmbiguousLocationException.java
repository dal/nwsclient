package com.renderfast.nwsclient;

public class AmbiguousLocationException extends IllegalArgumentException
{
	public AmbiguousLocationException()
	{
		super("Ambiguous location");
	}
}

