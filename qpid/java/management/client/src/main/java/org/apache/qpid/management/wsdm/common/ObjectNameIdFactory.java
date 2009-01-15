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

import javax.xml.namespace.QName;

import org.apache.muse.core.routing.ResourceIdFactory;
import org.apache.muse.ws.addressing.WsaConstants;

/**
 * ResourceIdFactory implementation that is using an objectName as 
 * resource identifier.
 * 
 * @author Andrea Gazzarini
 */
public class ObjectNameIdFactory implements ResourceIdFactory 
{
	public QName getIdentifierName() 
	{
		return WsaConstants.DEFAULT_RESOURCE_ID_QNAME;
    }

	/**
	 * Returns the object name used as a resource identifier.
	 * Developer note : this factory is highly coupled with ThreadSessionManager stuff because 
	 * the object name that will be used as identifier is supposed to be in the thread session.
	 * 
	 * @return the object name used as a resource identifier.
	 */
	public String getNextIdentifier() 
	{
		return ThreadSessionManager.getInstance().getSession().getObjectName().getCanonicalName();
	}
}