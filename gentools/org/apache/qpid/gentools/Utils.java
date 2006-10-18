/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.gentools;

import org.w3c.dom.Attr;
//import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class Utils
{
	public final static String fileSeparator = System.getProperty("file.separator");
	public final static String lineSeparator = System.getProperty("line.separator");
	
	public final static String ATTRIBUTE_NAME = "name";
	public final static String ATTRIBUTE_MAJOR = "major";
	public final static String ATTRIBUTE_MINOR = "minor";
	public final static String ATTRIBUTE_INDEX = "index";
	public final static String ATTRIBUTE_LABEL = "label";
	public final static String ATTRIBUTE_SYNCHRONOUS = "synchronous";
	public final static String ATTRIBUTE_CONTENT = "content";
	public final static String ATTRIBUTE_HANDLER = "handler";
	public final static String ATTRIBUTE_DOMAIN = "domain";
	public final static String ATTRIBUTE_TYPE = "type"; // For compatibility with AMQP 8.0
	
	public final static String ELEMENT_AMQP = "amqp";
	public final static String ELEMENT_CLASS = "class";
	public final static String ELEMENT_DOMAIN = "domain";
	public final static String ELEMENT_METHOD = "method";
	public final static String ELEMENT_FIELD = "field";
	public final static String ELEMENT_VERSION = "version";

	// Version functions
	
//	public static String createVersionKey(int major, int minor)
//	{
//		return major + "-" + minor;
//	}
	
	// Attribute functions
	
	public static String getNamedAttribute(Node n, String attrName) throws AmqpParseException
	{
		NamedNodeMap nnm = n.getAttributes();
		if (nnm == null)
			throw new AmqpParseException("Node \"" + n.getNodeName() + "\" has no attributes.");
		Attr a = (Attr)nnm.getNamedItem(attrName);
		if (a == null)
			throw new AmqpParseException("Node \"" + n.getNodeName() + "\" has no attribute \"" + attrName + "\".");
		return a.getNodeValue();
	}
	
	public static int getNamedIntegerAttribute(Node n, String attrName) throws AmqpParseException
	{
		return Integer.parseInt(getNamedAttribute(n, attrName));
	}

//	public static boolean containsAttribute(Node n, String attrName)
//	{
//		try { getNamedAttribute(n, attrName); }
//		catch (AmqpParseException e) { return false; }
//		return true;
//	}
//
//	public static boolean containsAttributeValue(Node n, String attrName, String attrValue)
//	{
//		try { return getNamedAttribute(n, attrName).compareTo(attrValue) == 0; }
//		catch (AmqpParseException e) { return false; }
//	}
//
//	public static boolean containsAttributeValue(Node n, String attrName, int attrValue)
//	{
//		try { return Integer.parseInt(getNamedAttribute(n, attrName)) == attrValue; }
//		catch (AmqpParseException e) { return false; }
//	}
//	
//	public static void createNamedAttribute(Document doc, NamedNodeMap nnm, String attrName, String attrValue)
//	{
//		Attr a = doc.createAttribute(attrName);
//		a.setNodeValue(attrValue);
//		nnm.setNamedItem(a);
//	}
//	
//	public static void createNamedAttribute(Document doc, NamedNodeMap nnm, String attrName, int attrValue)
//	{
//		createNamedAttribute(doc, nnm, attrName, Integer.toString(attrValue));
//	}
//	
//	public static void createNamedAttribute(Node n, String attrName, String attrValue)
//	{
//		createNamedAttribute(n.getOwnerDocument(), n.getAttributes(), attrName, attrValue);
//	}
//	
//	public static void createNamedAttribute(Node n, String attrName, int attrValue)
//	{
//		createNamedAttribute(n, attrName, Integer.toString(attrValue));
//	}
	
	// Element functions
	
	public static Node findChild(Node n, String eltName) throws AmqpParseException
	{
		NodeList nl = n.getChildNodes();
		for (int i=0; i<nl.getLength(); i++)
		{
			Node cn = nl.item(i);
			if (cn.getNodeName().compareTo(eltName) == 0)
				return cn;
		}
		throw new AmqpParseException("Node \"" + n.getNodeName() +
				"\" does not contain child element \"" + eltName + "\".");
	}
	
//	public static boolean containsChild(Node n, String eltName)
//	{
//		try { findChild(n, eltName); }
//		catch(AmqpParseException e) { return false; }
//		return true;
//	}
//	
//	public static Node findNamedChild(Node n, String eltName, String nameAttrVal) throws AmqpParseException
//	{
//		NodeList nl = n.getChildNodes();
//		for (int i=0; i<nl.getLength(); i++)
//		{
//			Node cn = nl.item(i);
//			if (cn.getNodeName().compareTo(eltName) == 0)
//				if (Utils.getNamedAttribute(cn, "name").compareTo(nameAttrVal) == 0)
//					return cn;
//		}
//		throw new AmqpParseException("Node \"" + n.getNodeName() +
//				"\" does not contain child element \"" + eltName + "\".");
//	}
//	
//	public static boolean containsNamedChild(Node n, String eltName, String nameAttrVal)
//	{
//		try { findNamedChild(n, eltName, nameAttrVal); }
//		catch(AmqpParseException e) { return false; }
//		return true;
//	}
	
	// Map functions

	
//	protected static Vector<AmqpVersion> buildVersionMap(Node n)throws AmqpParseException
//	{
//		Vector<AmqpVersion> versionList = new Vector<AmqpVersion>();
//		NodeList nl = n.getChildNodes();
//		for (int i=0; i<nl.getLength(); i++)
//		{
//			Node cn = nl.item(i);
//			if (cn.getNodeName().compareTo(AmqpXmlParser.ELEMENT_VERSION) == 0)
//			{
//				AmqpVersion ver = new AmqpVersion();
//				ver.major = Utils.getNamedIntegerAttribute(cn, "major");
//				ver.minor = Utils.getNamedIntegerAttribute(cn, "minor");
//				versionList.add(ver);
//			}
//		}
//		return versionList;
//	}
//	
//	protected static Vector<AmqpField> buildFieldMap(Node n)throws AmqpParseException
//	{
//		Vector<AmqpField> fieldList = new Vector<AmqpField>();
//		NodeList nl = n.getChildNodes();
//		for (int i=0; i<nl.getLength(); i++)
//		{
//			Node c = nl.item(i);
//			if (c.getNodeName().compareTo(AmqpXmlParser.ELEMENT_FIELD) == 0)
//				fieldList.add(new AmqpField(c));
//		}
//		return fieldList;
//	}
	
	// String functions
	
	public static String firstUpper(String str)
	{
		if (!Character.isLowerCase(str.charAt(0)))
			return str;
		StringBuffer sb = new StringBuffer(str);
		sb.setCharAt(0, Character.toUpperCase(str.charAt(0)));
		return sb.toString();
	}
	
	public static String createSpaces(int cnt)
	{
		StringBuffer sb = new StringBuffer();
		for (int i=0; i<cnt; i++)
			sb.append(' ');
		return sb.toString();
	}
}
