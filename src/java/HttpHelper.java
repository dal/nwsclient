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

import java.io.*;
import net.rim.device.api.system.*;
import javax.microedition.io.*;

public class HttpHelper
{

	public static class Connection
	{
		public InputStream is;
		public StreamConnection s;
		
		public Connection(StreamConnection str, InputStream istr)
		{
			s = str;
			is = istr;
		}
		
		public void close() 
		{
			try {
				if (is != null)
					is.close();
			} catch (IOException ioe) {
				System.err.println("Error closing input stream: "+ioe.getMessage());
			}
			
			try {
				if (s != null)
					s.close();
			} catch (IOException ioe) {
				System.err.println("Error closing http connection: "+ioe.getMessage());
			}
		}
		
	}

	/**
	 * Convenience method takes a string url and fetches an Connection object.
	 * <p>
	 * Returns null if there's a problem fetching the url.
	 * @param url The web url to fetch.
	 * @return    An inputStream of the requested URL's contents
	 */
	public static Connection getUrl(String url) throws IOException
	{
		System.err.println("Fetching url "+url);
		StreamConnection s = null;
		InputStream is = null;
		
		Connection ret = new Connection(s, is);
		
		int rc;
		
		try {
			
			ret.s = (StreamConnection)Connector.open(url);
			HttpConnection httpConn = (HttpConnection)ret.s;
			
			int status = httpConn.getResponseCode();
			
			if (status == HttpConnection.HTTP_OK) {
				ret.is = httpConn.openInputStream();
				return ret;
			// Redirect?
			} else if (status == HttpConnection.HTTP_MOVED_PERM 
				|| status == HttpConnection.HTTP_MOVED_TEMP) 
			{
				// Allow one redirect...
				String newUrl = httpConn.getHeaderField("Location");
				if (newUrl != null) {
					ret.s = (StreamConnection)Connector.open(newUrl);
					httpConn = (HttpConnection)ret.s;
					status = httpConn.getResponseCode();
					if (status == HttpConnection.HTTP_OK) {
						ret.is = httpConn.openInputStream();
						return ret;
					} else {
						System.err.println("Error fetching redirected url. Status: "+status);
					}
				}
			}
			
			System.err.println("Error fetching url. Status: "+status);
			
			// We only get here if there's been an error
			throw new IOException("Error fetching url "+url+". Status: "+status);
			
		} catch (IOException e) {
			System.err.println("Error fetching url "+url+". "+e.toString());
			throw new IOException(e.getMessage());
		}
	}
}
