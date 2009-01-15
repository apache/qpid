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
package org.apache.qpid.management.wsdm.capabilities;

import java.net.InetAddress;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanOperationInfo;
import javax.management.ObjectName;
import javax.xml.namespace.QName;

import org.apache.muse.core.Environment;
import org.apache.muse.util.xml.XmlUtils;
import org.apache.muse.ws.wsdl.WsdlUtils;
import org.apache.qpid.management.Messages;
import org.apache.qpid.management.Names;
import org.apache.qpid.management.wsdm.muse.serializer.ObjectSerializer;
import org.apache.qpid.qman.debug.XmlDebugger;
import org.apache.qpid.transport.util.Logger;
import org.apache.xpath.XPathAPI;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

class WsdlBuilder implements IArtifactBuilder {

	private final static Logger LOGGER = Logger.get(WsdlBuilder.class);
	
	private Environment _environment;
	private Document _document;
	private Element schema;
	
	private ObjectSerializer serializer = new ObjectSerializer();

	private ObjectName _objectName;
	
	public void onAttribute(MBeanAttributeInfo attributeMetadata) throws BuilderException  
	{
		try 
		{
			if (schema == null) 
			{
				
				schema = (Element) XPathAPI.selectSingleNode(
						_document.getDocumentElement(),
					"/wsdl:definitions/wsdl:types/xsd:schema[@targetNamespace='http://amqp.apache.org/qpid/management/qman']");
			
			}
	/*
			<xs:element name='accountAttributes'>
	        <xs:complexType>
	         <xs:sequence>
	          <xs:element maxOccurs='unbounded' minOccurs='0' name='entry'>
	           <xs:complexType>
	            <xs:sequence>
	             <xs:element minOccurs='0' name='key' type='xs:string'/>
	             <xs:element minOccurs='0' name='value' type='xs:anyType'/>
	            </xs:sequence>
	           </xs:complexType>
	          </xs:element>
	         </xs:sequence>
	        </xs:complexType>
	       </xs:element>
*/			
			if (attributeMetadata.getType().equals("java.util.Map")) 
			{
				Element prop = XmlUtils.createElement(_document, new QName("http://www.w3.org/2001/XMLSchema","element","xsd"));
				prop.setAttribute("name",attributeMetadata.getName());
				
					Element complexType = XmlUtils.createElement(_document, new QName("http://www.w3.org/2001/XMLSchema","complexType","xsd"));
				
						Element sequence = XmlUtils.createElement(_document, new QName("http://www.w3.org/2001/XMLSchema","sequence","xsd"));

						Element entry = XmlUtils.createElement(_document, new QName("http://www.w3.org/2001/XMLSchema","element","xsd"));
						entry.setAttribute("name", "entry");
						entry.setAttribute("minOccurs", "0");
						entry.setAttribute("maxOccurs", "unbounded");

						Element complexType2 = XmlUtils.createElement(_document, new QName("http://www.w3.org/2001/XMLSchema","complexType","xsd"));
						Element sequence2 = XmlUtils.createElement(_document, new QName("http://www.w3.org/2001/XMLSchema","sequence","xsd"));

							Element key = XmlUtils.createElement(_document, new QName("http://www.w3.org/2001/XMLSchema","element","xsd"));
							key.setAttribute("name", "key");
							key.setAttribute("type", "xsd:string");

							Element value = XmlUtils.createElement(_document, new QName("http://www.w3.org/2001/XMLSchema","element","xsd"));
							value.setAttribute("name", "value");
							value.setAttribute("type", "xsd:anyType");
			
							sequence2.appendChild(key);
							sequence2.appendChild(value);
							complexType2.appendChild(sequence2);
							entry.appendChild(complexType2);
							sequence.appendChild(entry);
							complexType.appendChild(sequence);
							prop.appendChild(complexType);
							schema.appendChild(prop);
			} else if ("java.util.UUID".equals(attributeMetadata.getType())) 
			{
				Element prop = XmlUtils.createElement(_document, new QName("http://www.w3.org/2001/XMLSchema","element","xsd"));
				prop.setAttribute("name", attributeMetadata.getName());
					Element complexType = XmlUtils.createElement(_document, new QName("http://www.w3.org/2001/XMLSchema","complexType","xsd"));
						Element sequence = XmlUtils.createElement(_document, new QName("http://www.w3.org/2001/XMLSchema","sequence","xsd"));
							Element uuid = XmlUtils.createElement(_document, new QName("http://www.w3.org/2001/XMLSchema","element","xsd"));
							uuid.setAttribute("name", "uuid");
							uuid.setAttribute("type", "xsd:string");						
				sequence.appendChild(uuid);
				complexType.appendChild(sequence);
				prop.appendChild(complexType);
				schema.appendChild(prop);
			} else {			
				Element propertyDeclaration = XmlUtils.createElement(_document, new QName("http://www.w3.org/2001/XMLSchema","element","xsd"));
				propertyDeclaration.setAttribute("name",attributeMetadata.getName());
				propertyDeclaration.setAttribute("type", serializer.getXmlType(Class.forName(attributeMetadata.getType())));
				schema.appendChild(propertyDeclaration);
			}			
			
			Element wsrpProperties = (Element) XPathAPI.selectSingleNode(
					_document, 
					"/wsdl:definitions/wsdl:types/xsd:schema[@targetNamespace='http://amqp.apache.org/qpid/management/qman']/xsd:element[@name='QManWsResourceProperties']/xsd:complexType/xsd:sequence");

			Element propertyRef= XmlUtils.createElement(_document, new QName("http://www.w3.org/2001/XMLSchema","element","xsd"));
			propertyRef.setAttribute("ref", "qman:"+attributeMetadata.getName());
			
			wsrpProperties.appendChild(propertyRef);
			
		} catch(Exception exception)
		{
			throw new BuilderException(exception);
		}
	}
	
	public void begin(ObjectName objectName) 
	{
		this._objectName = objectName;
	}
	
	public void onOperation(MBeanOperationInfo operation) 
	{
		// TODO
	}

	public void endAttributes() 
	{
		// TODO
	}

	public void endOperations() 
	{
		// TODO
	}

	public Document getWsdl() 
	{
		XmlDebugger.debug(_objectName,_document);
		return _document;
	}

	public void setEnvironment(Environment environment) 
	{
		this._environment = environment;
	}
	
	public void setWsdlPath(String wsdlPath)
	{
		_document = WsdlUtils.createWSDL(_environment, wsdlPath, true);
		try 
		{
			Attr location = (Attr) XPathAPI.selectSingleNode(_document, "/wsdl:definitions/wsdl:service/wsdl:port/wsdl-soap:address/@location");
					
			// TODO : come faccio a recuperare l'URL sul quale gira l'applicazione?
			StringBuilder builder = new StringBuilder("http://")
				.append(InetAddress.getLocalHost().getHostName())
				.append(':')
				.append(System.getProperty(Names.ADAPTER_PORT,"8080"))
				.append('/')
				.append("qman")
				.append('/')
				.append("services/QManWsResource");
			location.setValue(builder.toString());
		} catch(Exception exception)
		{
			LOGGER.error(exception,Messages.QMAN_100026_SOAP_ADDRESS_REPLACEMENT_FAILURE);
		}
	}
}
