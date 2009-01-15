/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.qpid.management.wsdm.muse.serializer;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;

import org.apache.muse.core.serializer.Serializer;
import org.apache.muse.util.xml.XmlUtils;
import org.apache.muse.ws.addressing.soap.SoapFault;
import org.w3c.dom.Element;

import sun.misc.BASE64Decoder;
import sun.misc.BASE64Encoder;

/**
 * Implementation of Muse Serializer for byte array type.
 *  
 * @author Andrea Gazzarini
 */
public class ByteArraySerializer implements Serializer {
	
	/**
	 * Return a byte array  representation of the given xml element.
	 * 
	 * @param xml the element to unmarshal.
	 * @throws SoapFault when the unmarshalling fails.
	 */
	public Object fromXML(Element xml) throws SoapFault 
	{
		try 
		{
			return new BASE64Decoder().decodeBuffer(xml.getTextContent());
		} catch (Exception exception) 
		{
			throw new SoapFault(exception);
		}
	}

	/**
	 * Returns the java type associated to this class.
	 * 
	 * @return the java type associated to this class.
	 */
	public Class<?> getSerializableType() 
	{
		return byte[].class;
	}

	/**
	 * Return an xml representation of the given byte array with the given name.
	 * 
	 * @param object the byte array to marshal.
	 * @param qname the qualified (xml) name of the object to use in xml representation.
	 * @return the xml representation of the byte array.
	 * @throws SoapFault when the marshalling fails.
	 */
	public Element toXML(Object object, QName qname) throws SoapFault 
	{
		Element element = XmlUtils.createElement(
				qname, 
				new BASE64Encoder().encode((byte[]) object));
		element.setAttribute("xmlns:xsi", XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI);
		element.setAttribute("xsi:type","xsd:base64Binary");
		return element;
	}
}