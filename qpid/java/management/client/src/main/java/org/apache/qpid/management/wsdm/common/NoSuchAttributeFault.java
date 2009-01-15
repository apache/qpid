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
package org.apache.qpid.management.wsdm.common;

import javax.management.ObjectName;
import javax.xml.namespace.QName;

import org.apache.muse.ws.addressing.EndpointReference;
import org.apache.qpid.management.Names;

/**
 * This is the exception encapsulating the fault that will be thrown in case of 
 * method invocation failure.
 * 
 * @author Andrea Gazzarini
 */
public class NoSuchAttributeFault extends QManFault 
{
	private static final long serialVersionUID = 5977379710882983474L;

	/**
	 * Builds a new exception with the given endpoint reference and method invocation exception.
	 * This constructor will be used when the invocation thrown the MethodInvocationException.
	 * 
	 * @param endpointReference the endpoint reference.
	 * @param exception the method invocation exception containing failure details.
	 */
	public NoSuchAttributeFault(EndpointReference endpointReference, ObjectName name, String attributeName) 
	{
		super(
				endpointReference,
				new QName(
						Names.NAMESPACE_URI,
						"NoSuchAttributeFault",
						Names.PREFIX),
				createMessage(name, attributeName));
	}
	
	/**
	 * Creates the reason message for this fault.
	 * 
	 * @param name the object name of the managed entity. 
	 * @param attributeName the name of the attribute that wasn't found.
	 * @return the reason message for this fault.
	 */
	private static String createMessage(ObjectName name, String attributeName)
	{
		return String.format("Attribute %s was not found on entity %s",attributeName,name);
	}
}