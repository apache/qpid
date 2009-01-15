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

import java.lang.management.ManagementFactory;
import java.lang.reflect.Array;

import javax.management.Attribute;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.xml.namespace.QName;

import org.apache.muse.core.serializer.Serializer;
import org.apache.muse.core.serializer.SerializerRegistry;
import org.apache.muse.util.ReflectUtils;
import org.apache.muse.util.xml.XmlUtils;
import org.apache.muse.ws.addressing.soap.SoapFault;
import org.apache.muse.ws.resource.basefaults.BaseFault;
import org.apache.muse.ws.resource.basefaults.WsbfUtils;
import org.apache.muse.ws.resource.impl.AbstractWsResourceCapability;
import org.apache.muse.ws.resource.properties.ResourcePropertyCollection;
import org.apache.qpid.management.wsdm.common.EntityInstanceNotFoundFault;
import org.apache.qpid.management.wsdm.common.NoSuchAttributeFault;
import org.apache.qpid.management.wsdm.common.QManFault;
import org.apache.qpid.management.wsdm.muse.serializer.ByteArraySerializer;
import org.w3c.dom.Element;

/**
 * Abstract capability used for centralize common behaviour of the QMan
 * resource(s) related capabilities.
 * 
 * @author Andrea Gazzarini
 */
public abstract class MBeanCapability extends AbstractWsResourceCapability 
{
	private static final Element[] _NO_VALUES = new Element[0];

	protected final MBeanServer _mxServer;
	protected ObjectName _objectName;

	/**
	 * Builds a new capability related to the given object name.
	 * 
	 * @param objectName the name of the target object of this capability.
	 */
	public MBeanCapability() 
	{
		_mxServer = ManagementFactory.getPlatformMBeanServer();
	}

	/**
	 * Injects on this capability the object name of the target mbean.
	 * 
	 * @param objectName the object name of the target mbean.
	 */
	public void setResourceObjectName(ObjectName objectName) 
	{
		this._objectName = objectName;
	}

	/**
	 * Returns the attribute value of a QMan managed object instance.
	 * 
	 * @param attributeName the name of the attribute to be requested.
	 * @return the value for the requested attribute.
	 * @throws NoSuchAttributeFault when the requested attribute cannot be found 
	 * 			on the given entity instance.
	 * @throws EntityInstanceNotFoundFault when the requested entity instance cannot 
	 * 			be found.
	 * @throws QManFault in case of internal system failure.
	 */
	protected Object getAttribute(String attributeName) throws QManFault 
	{
		try 
		{
			return _mxServer.getAttribute(_objectName, attributeName);
		} catch (AttributeNotFoundException exception) 
		{
			throw new NoSuchAttributeFault(
					getWsResource().getEndpointReference(), 
					_objectName, 
					attributeName);
		} catch (InstanceNotFoundException exception) 
		{
			throw new EntityInstanceNotFoundFault(
					getWsResource().getEndpointReference(), 
					_objectName);
		} catch (Exception exception) 
		{
			throw new QManFault(
					getWsResource().getEndpointReference(),
					exception);
		}
	}

	/**
	 * Sets the value for the given attribute on this MBean (proxy).
	 * 
	 * @param objectName
	 *            the object name of the target instance (excluding the domain
	 *            name).
	 * @param attributeName
	 *            the name of the attribute to be requested.
	 * @param value
	 *            the value for the requested attribute.
	 * @throws NoSuchAttributeFault
	 *             when the requested attribute cannot be found on the given
	 *             entity instance.
	 * @throws EntityInstanceNotFoundFault
	 *             when the requested entity instance cannot be found.
	 * @throws QManFault
	 *             in case of internal system failure.
	 */
	protected void setAttribute(String attributeName, Object value) throws QManFault 
	{
		try 
		{
			_mxServer.setAttribute(_objectName, new Attribute(attributeName,
					value));
		} catch (AttributeNotFoundException exception) 
		{
			throw new NoSuchAttributeFault(
					getWsResource().getEndpointReference(), 
					_objectName, 
					attributeName);
		} catch (InstanceNotFoundException exception) 
		{
			throw new EntityInstanceNotFoundFault(
					getWsResource().getEndpointReference(), 
					_objectName);
		} catch (Exception exception) 
		{
			throw new QManFault(
					getWsResource().getEndpointReference(),
					exception);
		}
	}

	/**
	 * 
	 * @param name
	 * @param value
	 * TODO TODO TODO!!! Vedi che poi fà co 'sto metodo che è un pò una monnezza!!!
	 * @return The XML representation of the resource property value(s).
	 * 
	 */
	@SuppressWarnings("unchecked")
	protected Element[] getPropertyElements(QName name, Object value)
			throws BaseFault {
		//
		// in this case, we have to determine if there IS a property
		// and it's null, or there is no property
		//
		if (value == null) {
			ResourcePropertyCollection props = getWsResource().getPropertyCollection();

			//
			// property is nillable - we say it exists. not 100% accurate,
			// but as close as we're going to get
			//
			if (props.getSchema().isNillable(name))
				return new Element[] { XmlUtils.createElement(name) };

			//
			// not nillable - must not exist
			//
			return _NO_VALUES;
		}

		//
		// in all other cases, we determine the type of the property
		// values and use that to serialize into XML
		//
		Object valuesArray = null;
		Class type = null;

		if (value.getClass().isArray()) {
			// TODO : 'sta porcheria non va bene. L'ho inserita perché se il
			// value è un array non vivne
			// utilizzato il mio ByteArraySerializer ma array serializer che fa
			// il serial degli elementi uno a uno : quindi
			// cercava un serializer per "byte"
			if (value.getClass() == byte[].class) {
				Serializer serializer = new ByteArraySerializer();
				try {
					return new Element[] { serializer.toXML(value, name) };
				} catch (SoapFault e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

			valuesArray = value;
			type = ReflectUtils.getClassFromArrayClass(value.getClass());
		}

		else {
			valuesArray = new Object[] { value };
			type = value.getClass();
		}

		int length = Array.getLength(valuesArray);
		Element[] properties = new Element[length];

		SerializerRegistry registry = SerializerRegistry.getInstance();
		Serializer ser = registry.getSerializer(type);

		for (int n = 0; n < length; ++n)
			properties[n] = serializeValue(ser, Array.get(valuesArray, n), name);

		return properties;
	}

	private Element serializeValue(Serializer ser, Object value, QName name) throws BaseFault 
	{
		try {
			return ser.toXML(value, name);
		}

		catch (SoapFault error) {
			throw WsbfUtils.convertToFault(error);
		}
	}
}