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

import javax.management.InstanceNotFoundException;
import javax.management.MBeanServer;
import javax.management.Notification;
import javax.management.NotificationFilterSupport;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import org.apache.muse.core.AbstractCapability;
import org.apache.muse.core.Resource;
import org.apache.muse.core.ResourceManager;
import org.apache.muse.core.serializer.SerializerRegistry;
import org.apache.muse.ws.addressing.soap.SoapFault;
import org.apache.qpid.management.Messages;
import org.apache.qpid.management.Names;
import org.apache.qpid.management.jmx.EntityLifecycleNotification;
import org.apache.qpid.management.wsdm.common.ThreadSessionManager;
import org.apache.qpid.management.wsdm.muse.serializer.ByteArraySerializer;
import org.apache.qpid.transport.util.Logger;

/**
 * MBean Server capabilty interface implementor.
 * Providers all the operations of the MBeanServer interface using the platform MBeanServer.
*/
public class QManAdapterCapability extends AbstractCapability
{	
	private MBeanServer _mxServer;
	private final static Logger LOGGER = Logger.get(QManAdapterCapability.class);
	
	private WsArtifactsFactory _artifactsFactory; 
	
	private final NotificationListener listenerForNewInstances = new NotificationListener() 
	{
		public void handleNotification(Notification notification, Object data) 
		{
			EntityLifecycleNotification lifecycleNotification = (EntityLifecycleNotification) notification;
			ObjectName eventSourceName = lifecycleNotification.getObjectName();
			ThreadSessionManager.getInstance().getSession().setObjectName(eventSourceName);
			
			try 
			{
				LOGGER.debug(Messages.QMAN_200039_DEBUG_JMX_NOTIFICATION, notification);

				ResourceManager resourceManager = getResource().getResourceManager();
				Resource resource = resourceManager.createResource(Names.QMAN_RESOURCE_NAME);
				
				WsArtifacts artifacts = _artifactsFactory.getArtifactsFor(resource,eventSourceName);
				MBeanCapability capability = _artifactsFactory.createCapability(
						artifacts.getCapabilityClass(), 
						eventSourceName);
				
				
				ThreadSessionManager.getInstance().getSession().setWsdlDocument(artifacts.getWsdl());
				ThreadSessionManager.getInstance().getSession().setResourceMetadataDescriptor(artifacts.getResourceMetadataDescriptor());
				
//				ResourceManager resourceManager = getResource().getResourceManager();
//				
//				Resource resource = resourceManager.createResource(Names.QMAN_RESOURCE_NAME);
//				resource.setWsdlPortType(Names.QMAN_RESOURCE_PORT_TYPE_NAME);
				resource.addCapability(capability);
				resource.initialize();
				resourceManager.addResource(resource.getEndpointReference(), resource);
			} catch (ArtifactsNotAvailableException exception) 
			{
				LOGGER.error(exception,Messages.QMAN_100023_BUILD_WS_ARTIFACTS_FAILURE);
			} catch (IllegalAccessException exception) 
			{
				LOGGER.error(exception,Messages.QMAN_100024_CAPABILITY_INSTANTIATION_FAILURE,eventSourceName);
			} catch (InstantiationException exception) 
			{
				LOGGER.error(exception,Messages.QMAN_100024_CAPABILITY_INSTANTIATION_FAILURE,eventSourceName);
			} catch (SoapFault exception) 
			{
				LOGGER.error(exception,Messages.QMAN_100025_WSRF_FAILURE,eventSourceName);	
			} catch (Exception exception) 
			{
				LOGGER.error(exception,Messages.QMAN_100025_WSRF_FAILURE,eventSourceName);	
			} 

		}
	};
	
	private final NotificationListener listenerForRemovedInstances = new NotificationListener() 
	{
		public void handleNotification(Notification notification, Object data) 
		{
			LOGGER.warn("TBD : Notification Listener for removed instances has not yet implemeted!");
		}
	};	
			
	@Override
	public void initialize() throws SoapFault 
	{
		super.initialize();
		
		// Workaround : it seems that is not possibile to declare a serializer for a byte array using muse descriptor...
		// What is the stringified name of the class? byte[].getClass().getName() is [B but is not working (ClassNotFound).
		// So, at the end, this is hard-coded here!
		SerializerRegistry.getInstance().registerSerializer(byte[].class, new ByteArraySerializer());
		
		try 
		{
			_mxServer = ManagementFactory.getPlatformMBeanServer();
			_artifactsFactory = new WsArtifactsFactory(getEnvironment(),_mxServer);
			
			NotificationFilterSupport filterForNewInstances = new NotificationFilterSupport();
			filterForNewInstances.enableType(EntityLifecycleNotification.INSTANCE_ADDED);
			
			NotificationFilterSupport filterForRemovedInstances = new NotificationFilterSupport();
			filterForNewInstances.enableType(EntityLifecycleNotification.INSTANCE_REMOVED);
			
			_mxServer.addNotificationListener(Names.QMAN_OBJECT_NAME, listenerForNewInstances, filterForNewInstances, null);
			_mxServer.addNotificationListener(Names.QMAN_OBJECT_NAME, listenerForRemovedInstances, filterForRemovedInstances, null);

			try {
				_mxServer.addNotificationListener(new ObjectName("A:A=1"), listenerForNewInstances, filterForNewInstances, null);
			} catch (Exception exception) {
				LOGGER.info(Messages.QMAN_000028_TEST_MODULE_NOT_FOUND);
			} 
			
		}  catch(InstanceNotFoundException exception) 
		{
			// throw new QManNotRunningFault
			throw new SoapFault(exception);	
		}
	}
			
	@SuppressWarnings("unchecked")
	public void connect(String host, int port, String username, String password, String virtualHost) throws SoapFault 
	{
		
	}
}