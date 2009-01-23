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
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanServer;
import javax.management.Notification;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.xml.namespace.QName;

import org.apache.muse.core.AbstractCapability;
import org.apache.muse.core.Resource;
import org.apache.muse.core.ResourceManager;
import org.apache.muse.core.routing.MessageHandler;
import org.apache.muse.core.serializer.SerializerRegistry;
import org.apache.muse.ws.addressing.EndpointReference;
import org.apache.muse.ws.addressing.soap.SoapFault;
import org.apache.qpid.management.Messages;
import org.apache.qpid.management.Names;
import org.apache.qpid.management.jmx.EntityLifecycleNotification;
import org.apache.qpid.management.wsdm.common.ThreadSessionManager;
import org.apache.qpid.management.wsdm.muse.engine.WSDMAdapterEnvironment;
import org.apache.qpid.management.wsdm.muse.serializer.ByteArraySerializer;
import org.apache.qpid.transport.util.Logger;

/**
 * QMan Adapter capability.
 * Basically it acts as a lifecycle manager of all ws resource that correspond to entities on JMX side.
 * 
 * @author Andrea Gazzarini
*/
public class QManAdapterCapability extends AbstractCapability
{	
	private final static Logger LOGGER = Logger.get(QManAdapterCapability.class);

	private MBeanServer _mxServer;
	private WsArtifactsFactory _artifactsFactory; 
	private URI _resourceURI;
	
	/**
	 * This listener handles "create" mbean events and therefore provides procedure to create and initialize
	 * corresponding ws resources.
	 */
	private final NotificationListener listenerForNewInstances = new NotificationListener() 
	{
		/**
		 * Handles JMX "create" notification type.
		 * 
		 * @param notification the entity lifecycle notification.
		 * @param data user data associated with the incoming notifiication : it is not used at the moment.
		 */
		public void handleNotification(Notification notification, Object data) 
		{
			ObjectName eventSourceName = null;
			try 
			{				
				EntityLifecycleNotification lifecycleNotification = (EntityLifecycleNotification) notification;
				eventSourceName = lifecycleNotification.getObjectName();
				ThreadSessionManager.getInstance().getSession().setObjectName(eventSourceName);
			
				LOGGER.debug(Messages.QMAN_200039_DEBUG_JMX_NOTIFICATION, notification);

				ResourceManager resourceManager = getResource().getResourceManager();
				Resource resource = resourceManager.createResource(Names.QMAN_RESOURCE_NAME);
				
				WsArtifacts artifacts = _artifactsFactory.getArtifactsFor(resource,eventSourceName);
				MBeanCapability capability = _artifactsFactory.createCapability(
						artifacts.getCapabilityClass(), 
						eventSourceName);
				
				ThreadSessionManager.getInstance().getSession().setWsdlDocument(artifacts.getWsdl());
				ThreadSessionManager.getInstance().getSession().setResourceMetadataDescriptor(artifacts.getResourceMetadataDescriptor());
				
				resource.setWsdlPortType(Names.QMAN_RESOURCE_PORT_TYPE_NAME);
				capability.setCapabilityURI(Names.NAMESPACE_URI+"/"+capability.getClass().getSimpleName());
				capability.setMessageHandlers(createMessageHandlers(capability));
				
				resource.addCapability(capability);
				resource.initialize();
				resourceManager.addResource(resource.getEndpointReference(), resource);
				
				LOGGER.info(
						Messages.QMAN_000030_RESOURCE_HAS_BEEN_CREATED,
						eventSourceName);
			} catch (ArtifactsNotAvailableException exception) 
			{
				LOGGER.error(
						exception,
						Messages.QMAN_100023_BUILD_WS_ARTIFACTS_FAILURE);
			} catch (IllegalAccessException exception) 
			{
				LOGGER.error(
						exception,
						Messages.QMAN_100024_CAPABILITY_INSTANTIATION_FAILURE,
						eventSourceName);
			} catch (InstantiationException exception) 
			{
				LOGGER.error(
						exception,
						Messages.QMAN_100024_CAPABILITY_INSTANTIATION_FAILURE,
						eventSourceName);
			} catch (SoapFault exception) 
			{
				LOGGER.error(
						exception,Messages.QMAN_100025_WSRF_FAILURE,
						eventSourceName);	
			} catch (Exception exception) 
			{
				LOGGER.error(
						exception,
						Messages.QMAN_100025_WSRF_FAILURE,
						eventSourceName);	
			} 
		}
	};
	
	/**
	 * This listener handles "remove" mbean events and therefore provides procedure to shutdown and remove
	 * corresponding ws resources.
	 */
	private final NotificationListener listenerForRemovedInstances = new NotificationListener() 
	{
		/**
		 * Handles JMX "remove" notification type.
		 * 
		 * @param notification the entity lifecycle notification.
		 * @param data user data associated with the incoming notifiication : it is not used at the moment.
		 */
		public void handleNotification(Notification notification, Object data) 
		{
			EntityLifecycleNotification lifecycleNotification = (EntityLifecycleNotification) notification;
			ObjectName eventSourceName = lifecycleNotification.getObjectName();

			LOGGER.debug(Messages.QMAN_200042_REMOVING_RESOURCE, eventSourceName);

			EndpointReference endpointPointReference = new EndpointReference(_resourceURI);			
			endpointPointReference.addParameter(
					Names.RESOURCE_ID_QNAME, 
					eventSourceName.getCanonicalName());
			
			ResourceManager resourceManager = getResource().getResourceManager();
			try 
			{
				Resource resource = resourceManager.getResource(endpointPointReference);
				resource.shutdown();
				
				LOGGER.info(
						Messages.QMAN_000031_RESOURCE_HAS_BEEN_REMOVED, 
						eventSourceName);
			}
			catch(Exception exception) 
			{
				LOGGER.error(
						exception, 
						Messages.QMAN_100027_RESOURCE_SHUTDOWN_FAILURE, 
						eventSourceName);
			}
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
		WSDMAdapterEnvironment environment = (WSDMAdapterEnvironment) getEnvironment();
		String resourceURI = environment.getDefaultURIPrefix()+Names.QMAN_RESOURCE_NAME;
		try 
		{
			_resourceURI = URI.create(resourceURI);
			
			_mxServer = ManagementFactory.getPlatformMBeanServer();
			_artifactsFactory = new WsArtifactsFactory(getEnvironment(),_mxServer);
			
			/**
			 * NotificationFilter for "create" only events.
			 */
			NotificationFilter filterForNewInstances = new NotificationFilter(){

				private static final long serialVersionUID = 1733325390964454595L;

				public boolean isNotificationEnabled(Notification notification)
				{
					return EntityLifecycleNotification.INSTANCE_ADDED.equals(notification.getType());
				}
				
			};

			/**
			 * NotificationFilter for "remove" only events.
			 */
			NotificationFilter filterForRemovedInstances = new NotificationFilter(){

				private static final long serialVersionUID = 1733325390964454595L;

				public boolean isNotificationEnabled(Notification notification)
				{
					return EntityLifecycleNotification.INSTANCE_REMOVED.equals(notification.getType());
				}
				
			};
			
			_mxServer.addNotificationListener(
					Names.QMAN_OBJECT_NAME, 
					listenerForNewInstances, 
					filterForNewInstances, 
					null);
			
			_mxServer.addNotificationListener(
					Names.QMAN_OBJECT_NAME, 
					listenerForRemovedInstances, 
					filterForRemovedInstances, 
					null);

			try {
				_mxServer.addNotificationListener(
						Names.QPID_EMULATOR_OBJECT_NAME, 
						listenerForNewInstances, 
						filterForNewInstances, null);

				_mxServer.addNotificationListener(
						Names.QPID_EMULATOR_OBJECT_NAME, 
						listenerForRemovedInstances, 
						filterForRemovedInstances, null);

			} catch(IllegalArgumentException exception)
			{
				LOGGER.info(exception,Messages.QMAN_000029_DEFAULT_URI,resourceURI);				
			}
			catch (Exception exception) {
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

	/**
	 * Creates the message handlers for the given capability.
	 * 
	 * @param capability the QMan capability.
	 * @return a collection with message handlers for the given capability.
	 */
	protected Collection<MessageHandler> createMessageHandlers(MBeanCapability capability)
	{
        Collection<MessageHandler> handlers = new ArrayList<MessageHandler>();
        
        for (Method method :  capability.getClass().getDeclaredMethods())
        {
        	String name = method.getName();
        	
        	QName requestName = new QName(Names.NAMESPACE_URI,name,Names.PREFIX);
        	QName returnValueName = new QName(Names.NAMESPACE_URI,name+"Response",Names.PREFIX);
        	
        	String actionURI = Names.NAMESPACE_URI+"/"+name;
            MessageHandler handler = new QManMessageHandler(actionURI, requestName, returnValueName);
            handler.setMethod(method);
            handlers.add(handler);
        }
        return handlers;	
    }  
}