/**
 * XmlHelper - A class that encapsulates some helpful XML DOM tree functions for
 *             the NwsClient Blackberry weather application.
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

import net.rim.device.api.xml.parsers.*;

import org.w3c.dom.*;

public class XmlHelper
{
	private XmlHelper()
	{	
	}
	
	public static String getValue(Node root, String tagName) throws ParseError
	{
		Node myNode = getNode(root, tagName);
		return getNodeText(myNode);
	}
	
	public static String getValueIfExists(Node root, String tagName)
	{
		try {
			return getValue(root, tagName);
		} catch (ParseError pe) {
			return "";
		}
	}
	
	public static Node getNode(Node root, String tagName) throws ParseError
	{
		if (root.getNodeType() != Node.ELEMENT_NODE)
			return null;
		
		// Cast to an element
		Element rootEl = (Element)root;
		
		NodeList nl = rootEl.getElementsByTagName(tagName);
		if (nl.getLength() <= 0) {
			throw new ParseError("No "+tagName+" node in XML");
		}
		// Return the first element matching tagName
		return nl.item(0);
	}
	
	public static String getNodeText(Node node)
	{
		if (node.getNodeType() == Node.ELEMENT_NODE) {
			NodeList childNodes = node.getChildNodes();
			StringBuffer buffer = new StringBuffer();
			for (int i=0; i < childNodes.getLength(); i++) {
				if (childNodes.item(i).getNodeType() == Node.TEXT_NODE) {
					Node myNode = childNodes.item(i);
					buffer.append(myNode.getNodeValue());
				}
			}
			return buffer.toString();
		}
		return "";
	}
	
	public static String getElementData(Node root, String tagName) throws ParseError
	{
		Node myNode = getNode(root, tagName);
		// getNode may return null if root node is invalid...
		if (myNode != null) {
			if (myNode.getNodeType() == Node.ELEMENT_NODE) {
				Element el = (Element)myNode;
				return el.getAttribute("data");
			}
		}
		return "";
	}
	
}


