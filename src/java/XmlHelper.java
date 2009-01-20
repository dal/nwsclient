/**
 * A class that encapsulates some helpful XML DOM tree functions for the NwsClient
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
	
	public static Node getNode(Node root, String tagName) throws ParseError
	{
		if (root.getNodeType() != Node.ELEMENT_NODE)
			return null;
		
		// Cast to an element
		Element rootEl = (Element)root;
		
		NodeList nl = rootEl.getElementsByTagName(tagName);
		if (nl.getLength() > 0) {
			return nl.item(0);
		} else {
			throw new ParseError("No "+tagName+" node in XML");
		}
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
		return new String("");
	}
	
	public static String getElementData(Node root, String tagName) throws ParseError
	{
		Node myNode = getNode(root, tagName);
		if (myNode.getNodeType() == Node.ELEMENT_NODE) {
			Element el = (Element)myNode;
			return el.getAttribute("data");
		}
		return new String("");
	}
	
}


