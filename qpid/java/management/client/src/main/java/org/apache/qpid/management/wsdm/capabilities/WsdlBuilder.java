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
import javax.management.MBeanParameterInfo;
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
			schema.appendChild(defineSchemaFor(attributeMetadata.getType(), attributeMetadata.getName()));
			
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
	
	private Element defineSchemaFor(String type, String attributeName) throws Exception
	{
		if (type.equals("java.util.Map")) 
		{
			Element prop = XmlUtils.createElement(_document, new QName("http://www.w3.org/2001/XMLSchema","element","xsd"));
			prop.setAttribute("name",attributeName);
			
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
						return prop;
		} else if ("java.util.UUID".equals(type)) 
		{
			Element prop = XmlUtils.createElement(_document, new QName("http://www.w3.org/2001/XMLSchema","element","xsd"));
			prop.setAttribute("name", attributeName);
				Element complexType = XmlUtils.createElement(_document, new QName("http://www.w3.org/2001/XMLSchema","complexType","xsd"));
					Element sequence = XmlUtils.createElement(_document, new QName("http://www.w3.org/2001/XMLSchema","sequence","xsd"));
						Element uuid = XmlUtils.createElement(_document, new QName("http://www.w3.org/2001/XMLSchema","element","xsd"));
						uuid.setAttribute("name", "uuid");
						uuid.setAttribute("type", "xsd:string");						
			sequence.appendChild(uuid);
			complexType.appendChild(sequence);
			prop.appendChild(complexType);
			return prop;
		} else {			
			Element propertyDeclaration = XmlUtils.createElement(_document, new QName("http://www.w3.org/2001/XMLSchema","element","xsd"));
			propertyDeclaration.setAttribute("name",attributeName);
			propertyDeclaration.setAttribute("type", serializer.getXmlType(Class.forName(type)));
			return propertyDeclaration;
		}			
	}
	
	public void begin(ObjectName objectName) throws BuilderException
	{
		this._objectName = objectName;
		try 
		{
			Attr location = (Attr) XPathAPI.selectSingleNode(
					_document, 
					"/wsdl:definitions/wsdl:service/wsdl:port/wsdl-soap:address/@location");
					
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
			LOGGER.error(
					exception,
					Messages.QMAN_100026_SOAP_ADDRESS_REPLACEMENT_FAILURE);
			throw new BuilderException(exception);
		}
		
		try 
		{
			schema = (Element) XPathAPI.selectSingleNode(
					_document.getDocumentElement(),
				"/wsdl:definitions/wsdl:types/xsd:schema[@targetNamespace='http://amqp.apache.org/qpid/management/qman']");
		} catch(Exception exception)
		{
			LOGGER.error(
					exception,
					Messages.QMAN_100034_WSDL_SCHEMA_SECTION_NOT_FOUND);
			throw new BuilderException(exception);
		}
/*
		<xs:complexType name='InvocationResult'>
			<xs:sequence>
				<xs:element name='statusCode' type="xsd:long" />
				<xs:element name='statusText' type="xsd:string" />
				<xs:element name='outputParameters'>
		        	<xs:complexType>
		         		<xs:sequence>
		         		
		          			<xs:element maxOccurs='unbounded' minOccurs='0' name='entry'>
		          			
		           				<xs:complexType>
		            				<xs:sequence>
		             					<xs:element minOccurs='0' name="name' type='xs:string'/>
		             					<xs:element minOccurs='0' name="value" type='xs:anyType'/>
		            				</xs:sequence>
		           				</xs:complexType>
		          			</xs:element>
		         		</xs:sequence>
		        	</xs:complexType>
		       </xs:element>
		</xs:sequence>
	</xs:complexType>
*/
		Element complexTypeResult = _document.createElement("xsd:complexType");
		complexTypeResult.setAttribute("name", "result");
		Element sequence = _document.createElement("xsd:sequence");
		complexTypeResult.appendChild(sequence);
		
		Element statusCode = _document.createElement("xsd:element");
		statusCode.setAttribute("name", "statusCode");
		statusCode.setAttribute("type", "xsd:long");

		Element statusText = _document.createElement("xsd:element");
		statusCode.setAttribute("name", "statusText");
		statusCode.setAttribute("type", "xsd:string");
		
		sequence.appendChild(statusCode);
		sequence.appendChild(statusText);
		
		Element outputParams = _document.createElement("xsd:complexType");
		outputParams.setAttribute("name", "outputParameters");
		
		Element complexTypeOutput = _document.createElement("xsd:complexType");
		Element outputSequence = _document.createElement("xsd:sequence");
		
		outputParams.appendChild(complexTypeOutput);
		complexTypeOutput.appendChild(outputSequence);
		
		Element entry = _document.createElement("xsd:element");
		entry.setAttribute("maxOccurs", "unbounded");
		entry.setAttribute("minOccurs", "0");
		entry.setAttribute("name", "entry");
		
		outputSequence.appendChild(entry);
		
		Element entryComplexType = _document.createElement("xsd:complexType");
		Element entrySequence = _document.createElement("xsd:sequence");
		entryComplexType.appendChild(entrySequence);
		
		Element name = _document.createElement("xsd:name");
		name.setAttribute("name", "name");
		name.setAttribute("type", "xsd:string");
		
		Element value = _document.createElement("xsd:element");
		value.setAttribute("name", "value");
		value.setAttribute("type", "xsd:anyType");
		
		entrySequence.appendChild(name);
		entrySequence.appendChild(value);		
		
		schema.appendChild(complexTypeResult);
	}
	
	public void onOperation(MBeanOperationInfo operationMetadata) throws BuilderException
	{
		// SCHEMA SECTION
		/*
			<xs:element name='purgeRequest' type='qman:purgeRequest' />
			
			<xs:element name='purgeResponse' type='qman:purgeResponse' />
			
			<xs:complexType name='purgeRequest'>
				<xs:sequence>
					<xs:element name='arg0' type='xs:int' />
					<xs:element minOccurs='0' name='arg1' type='qman:hashMap' />
				</xs:sequence>
			</xs:complexType>
			
			<xs:element name='hashMap'>
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
			
			<xs:complexType name='purgeResponse'>
				<xs:sequence />
			</xs:complexType>
		 */
		try 
		{
			// <xs:element name='purgeRequest' type='qman:purgeRequest' />
			// <xsd:element xmlns="" name="purgeRequest" type="qman:purgeRequest"/>
			
			Element methodRequestElement= _document.createElement("xsd:element");		
			String methodNameRequest = operationMetadata.getName()+"Request";
			methodRequestElement.setAttribute("name", methodNameRequest);
			methodRequestElement.setAttribute("type", "qman:"+methodNameRequest);
			
			// <xs:element name='purgeResponse' type='qman:purgeResponse' />
			Element methodResponseElement= _document.createElement("xsd:element");		
			String methodNameResponse= operationMetadata.getName()+"Response";
			methodResponseElement.setAttribute("name", methodNameResponse);
			methodResponseElement.setAttribute("type", "qman:"+methodNameResponse);
			
			schema.appendChild(methodRequestElement);
			schema.appendChild(methodResponseElement);
	
			/*
				<xs:complexType name='purgeRequest'>
					<xs:sequence>
						<xs:element name='arg0' type='xs:int' />
						<xs:element minOccurs='0' name='arg1' type='qman:hashMap' />
					</xs:sequence>
				</xs:complexType>
	
			 */
			
			Element methodNameRequestComplexType =  _document.createElement("xsd:complexType");
			methodNameRequestComplexType.setAttribute("name", methodNameRequest);
			Element methodNameRequestComplexTypeSequence = _document.createElement("xsd:sequence");
			for(MBeanParameterInfo parameter : operationMetadata.getSignature())
			{
				methodNameRequestComplexTypeSequence.appendChild(defineSchemaFor(parameter.getType(), parameter.getName()));
			}
						
			methodNameRequestComplexType.appendChild(methodNameRequestComplexTypeSequence);
			schema.appendChild(methodNameRequestComplexType);
			
			Element methodNameResponseComplexType =  _document.createElement("xsd:complexType");
			methodNameResponseComplexType.setAttribute("name", methodNameResponse);
			
			Element methodNameResponseSequence = _document.createElement("xsd:sequence");
			methodNameResponseComplexType.appendChild(methodNameResponseSequence);
			
			Element result = _document.createElement("xsd:element");
			result.setAttribute("name", "result");
			result.setAttribute("type", "qman:invocationResult");
			methodNameResponseSequence.appendChild(result);
			
			schema.appendChild(methodNameResponseComplexType);
			
			/*
		<message name="purgeResponseMessage">
			<part element='qman:purgeResponse' name='purgeResponse'></part>
		</message>
		
		<message name='purgeRequestMessage'>
			<part element="qman:purgeRequest" name='purgeRequest'></part>
		</message>
			 */	
			Element definitions = (Element) XPathAPI.selectSingleNode(_document, "/wsdl:definitions");
			
			String requestMessageName = methodNameRequest+"Message";
			String responseMessageName = methodNameResponse+"Message";
			
			Element requestMessage = _document.createElement("message");
			requestMessage.setAttribute("name", requestMessageName);
			Element requestPart = _document.createElement("wsdl:part");
			requestPart.setAttribute("element", "qman:"+methodNameRequest);
			requestPart.setAttribute("name", methodNameRequest);
			requestMessage.appendChild(requestPart);
			
			Element responseMessage = _document.createElement("wsdl:message");
			responseMessage.setAttribute("name", responseMessageName);
			Element responsePart = _document.createElement("wsdl:part");
			responsePart.setAttribute("element", "qman:"+methodNameResponse);
			responsePart.setAttribute("name", methodNameResponse);
			responseMessage.appendChild(responsePart);
			
			definitions.appendChild(requestMessage);
			definitions.appendChild(responseMessage);
			
			
			/*
	<operation name='purge'>
			<input message="qman:purgeRequestMessage">
			</input>
			<output message='qman:purgeResponseMessage'>
			</output>
		</operation>		 
			 */
			Element portType = (Element) XPathAPI.selectSingleNode(_document, "/wsdl:definitions/wsdl:portType");
			Element operation = _document.createElement("wsdl:operation");
			operation.setAttribute("name", operationMetadata.getName());
			
			Element input = _document.createElement("wsdl:input");
			input.setAttribute("message", "qman:"+requestMessageName);
			input.setAttribute("name", methodNameRequest);
			input.setAttribute("wsa:action", Names.NAMESPACE_URI+"/"+operationMetadata.getName());
			
			//name="SetResourcePropertiesRequest" wsa:Action="http://docs.oasis-open.org/wsrf/rpw-2/SetResourceProperties/SetResourcePropertiesRequest"/>
			
			operation.appendChild(input);
			
			Element output = _document.createElement("wsdl:output");
			output.setAttribute("message", "qman:"+responseMessageName);
			output.setAttribute("name", methodNameResponse);
			output.setAttribute("wsa:action", Names.NAMESPACE_URI+"/"+methodNameResponse);
			
			operation.appendChild(output);		
			
			portType.appendChild(operation);
			
			/*
			<operation name='purge'>
				<soap:operation soapAction='purge' />
				<input>
					<soap:body use='literal' />
				</input>
				<output>
					<soap:body use='literal' />
				</output>
			</operation>
			 */
			Element binding = (Element) XPathAPI.selectSingleNode(_document, "/wsdl:definitions/wsdl:binding");
			Element bindingOperation = _document.createElement("wsdl:operation");
			bindingOperation.setAttribute("name", operationMetadata.getName());
			
			Element soapOperation = _document.createElement("wsdl-soap:operation");
			soapOperation.setAttribute("soapAction", Names.NAMESPACE_URI+"/"+operationMetadata.getName());
			
			Element bindingInput = _document.createElement("wsdl:input");
			Element bodyIn = _document.createElement("wsdl-soap:body");
			bodyIn.setAttribute("use", "literal");
			
			Element bindingOutput = _document.createElement("wsdl:output");
			Element bodyOut = _document.createElement("wsdl-soap:body");
			bodyOut.setAttribute("use", "literal");
			
			bindingOutput.appendChild(bodyOut);
			bindingInput.appendChild(bodyIn);
			
			bindingOperation.appendChild(soapOperation);
			bindingOperation.appendChild(bindingInput);
			bindingOperation.appendChild(bindingOutput);
			
			binding.appendChild(bindingOperation);
		} catch(Exception exception) 
		{
			throw new BuilderException(exception);
		}
	}

	public void endAttributes() 
	{
		// N.A.
	}

	public void endOperations() 
	{

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
	}
}
