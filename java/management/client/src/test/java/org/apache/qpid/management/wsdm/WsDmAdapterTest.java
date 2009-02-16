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
package org.apache.qpid.management.wsdm;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.URI;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.xml.namespace.QName;

import junit.extensions.TestSetup;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.muse.core.proxy.ProxyHandler;
import org.apache.muse.core.proxy.ReflectionProxyHandler;
import org.apache.muse.core.serializer.SerializerRegistry;
import org.apache.muse.ws.addressing.EndpointReference;
import org.apache.muse.ws.addressing.soap.SoapFault;
import org.apache.muse.ws.resource.remote.WsResourceClient;
import org.apache.muse.ws.resource.sg.remote.ServiceGroupClient;
import org.apache.qpid.management.Names;
import org.apache.qpid.management.Protocol;
import org.apache.qpid.management.wsdm.capabilities.Result;
import org.apache.qpid.management.wsdm.muse.serializer.DateSerializer;
import org.apache.qpid.management.wsdm.muse.serializer.InvocationResultSerializer;
import org.apache.qpid.management.wsdm.muse.serializer.MapSerializer;
import org.apache.qpid.management.wsdm.muse.serializer.ObjectSerializer;
import org.apache.qpid.management.wsdm.muse.serializer.UUIDSerializer;
import org.mortbay.component.LifeCycle;
import org.mortbay.component.LifeCycle.Listener;
import org.w3c.dom.Element;

/**
 * Test case for WS-Resource lifecycle management.
 * 
 * @author Andrea Gazzarini
 */
public class WsDmAdapterTest extends TestCase {
	
	private MBeanServer _managementServer;
	private ObjectName _resourceObjectName;
		
	private WsResourceClient _resourceClient;
	private MBeanInfo _mbeanInfo;
	
	private Map<String, ProxyHandler> _invocationHandlers = createInvocationHandlers();
	final Long retCodeOk = new Long(0);

	private static ServerThread _server;
	
	/**
	 * Test case wide set up.
	 * Provides Server startup & shutdown global procedure.
	 * 
	 * @author Andrea Gazzarini
	 */
	private static class WsDmAdapterTestSetup extends TestSetup
	{
		private Object _serverMonitor = new Object();
		
		Listener listener = new WebApplicationLifeCycleListener() 
		{
			public void lifeCycleStarted(LifeCycle event)
			{
				synchronized (_serverMonitor) 
				{
					_serverMonitor.notify();
				}
			}
		};
		
		
		/**
		 * Builds a new test setup with for the given test.
		 * 
		 * @param test the decorated test.
		 */
		public WsDmAdapterTestSetup(Test test)
		{
			super(test);
		}
		
		/**
		 * Starts up Web server.
		 * 
		 * @throws Exception when the server startup fails.
		 */
		@Override
		protected void setUp() throws Exception
		{	
			SerializerRegistry.getInstance().registerSerializer(Object.class, new ObjectSerializer());
			SerializerRegistry.getInstance().registerSerializer(Date.class, new DateSerializer());
			SerializerRegistry.getInstance().registerSerializer(Map.class, new MapSerializer());
			SerializerRegistry.getInstance().registerSerializer(HashMap.class, new MapSerializer());
			SerializerRegistry.getInstance().registerSerializer(UUID.class, new UUIDSerializer());
			SerializerRegistry.getInstance().registerSerializer(Result.class, new InvocationResultSerializer());
			
			System.setProperty(
					Names.ADAPTER_HOST_PROPERTY_NAME, 
					Protocol.DEFAULT_QMAN_HOSTNAME);

			System.setProperty(
					Names.ADAPTER_PORT_PROPERTY_NAME, 
					String.valueOf(getFreePort()));
			
			_server = new ServerThread(listener);
			_server.start();
			
			synchronized(_serverMonitor) {
				_serverMonitor.wait();
			}
		}
		
		@Override
		protected void tearDown() throws Exception
		{
			_server.shutdown();
		}
	};
	
	/**
	 * Set up fixture for this test case.
	 * 
	 * @throws Exception when the test case intialization fails.
	 */
	protected void setUp() throws Exception 
	{		
		_managementServer = ManagementFactory.getPlatformMBeanServer();
		
        ServiceGroupClient serviceGroup = getServiceGroupClient();
        WsResourceClient [] members = serviceGroup.getMembers();
        
        assertEquals(
        		"No resource has been yet created so how is " +
        			"it possible that service group children list is not empty?",
        		0,
        		members.length);

        _managementServer.invoke(
        		Names.QPID_EMULATOR_OBJECT_NAME, 
        		"createQueue", new Object[]{_resourceObjectName = createResourceName()}, 
        		new String[]{ObjectName.class.getName()});
                
        members = serviceGroup.getMembers();
        assertEquals(
        		"One resource has just been created so " +
        			"I expect to find it on service group children list...",
        		1,
        		members.length);
        
        _resourceClient = members[0];
        _mbeanInfo = _managementServer.getMBeanInfo(_resourceObjectName);
	}

	/**
	 * Shutdown procedure for this test case.
	 * 
	 * @throws Exception when either the server or some resource fails to shutdown.
	 */
	@Override
	protected void tearDown() throws Exception
	{
        ServiceGroupClient serviceGroup = getServiceGroupClient();
        WsResourceClient [] members = serviceGroup.getMembers();

		_managementServer.invoke(
				Names.QPID_EMULATOR_OBJECT_NAME,
				"unregister",
				new Object[]{_resourceObjectName},
				new String[]{ObjectName.class.getName()});

      	members = serviceGroup.getMembers();

      	assertEquals(
      			"No resource has been yet created so how is it possible that service group children list is not empty?",
      			0,
      			members.length);
	}
	
	/**
	 * Test the WS-RP GetResourceProperty interface of the WS-DM adapter.
	 * 
	 * <br>precondition : a ws resource exists and is registered. 
	 * <br>postcondition : property values coming from WS-DM resource are the same of the JMX interface.
	 */
	public void testGetResourcePropertiesOK() throws Exception
	{
		MBeanAttributeInfo [] attributesMetadata = _mbeanInfo.getAttributes();
		for (MBeanAttributeInfo attributeMetadata : attributesMetadata)
		{
			String name = attributeMetadata.getName();
			Object propertyValues = _resourceClient.getPropertyAsObject(
					new QName(
							Names.NAMESPACE_URI,
							name,
							Names.PREFIX),
					Class.forName(attributeMetadata.getType()));
			
			int length = Array.getLength(propertyValues);
			if (length != 0)
			{
				Object propertyValue = Array.get(propertyValues, 0);
				
				assertEquals(
						"Comparison failed for property "+name,
						_managementServer.getAttribute(_resourceObjectName,name),
						propertyValue);
			} else {
				assertNull(
						String.format(
								"\"%s\" property value shouldn't be null. Its value is %s",
								name,
								_managementServer.getAttribute(_resourceObjectName,name)),
								_managementServer.getAttribute(_resourceObjectName,name));
			}
		}
	}
	
	/**
	 * Test the WS-RP SetResourceProperty interface of the WS-DM adapter.
	 * 
	 * <br>precondition : a WS-Resource exists and is registered. 
	 * <br>postcondition : property values are correctly updated on the target WS-Resource..
	 */
	public void testSetResourcePropertiesOK() throws Exception
	{
		Map<String, Object> sampleMap = new HashMap<String, Object>();
		sampleMap.put("Key1", "BLABALABLABALBAL");
		sampleMap.put("Key2", 182838484l);
		sampleMap.put("Key3", -928376362);
		sampleMap.put("Key4", 23762736276.33D);
		sampleMap.put("Key4", 2327363.2F);
		
		Map<String, Object> sampleValues = new HashMap<String, Object>();
		sampleValues.put(String.class.getName(),"SAMPLE_STRING");
		sampleValues.put(UUID.class.getName(),UUID.randomUUID());
		sampleValues.put(Boolean.class.getName(),Boolean.FALSE);
		sampleValues.put(Map.class.getName(),sampleMap);
		sampleValues.put(Long.class.getName(),283781273L);
		sampleValues.put(Integer.class.getName(),12727);
		sampleValues.put(Short.class.getName(),new Short((short)22));
		sampleValues.put(Date.class.getName(),new Date());
		
		MBeanAttributeInfo [] attributesMetadata = _mbeanInfo.getAttributes();
		boolean atLeastThereIsOneWritableProperty = false;
		
		for (MBeanAttributeInfo attributeMetadata : attributesMetadata)
		{
			String name = attributeMetadata.getName();
			
			if (attributeMetadata.isWritable())
			{	
				atLeastThereIsOneWritableProperty = true;
				Object sampleValue = sampleValues.get(attributeMetadata.getType());
				Object []values = new Object[]{sampleValue};
				
				Object result = _managementServer.getAttribute(_resourceObjectName, name);
				if (result == null)
				{
					_resourceClient.insertResourceProperty(	
							new QName(
									Names.NAMESPACE_URI,
									name,
									Names.PREFIX),
							values);
				} else 
				{
					_resourceClient.updateResourceProperty(	
							new QName(
									Names.NAMESPACE_URI,
									name,
									Names.PREFIX),
							values);					
				}
				
				Object propertyValues = _resourceClient.getPropertyAsObject(
						new QName(
								Names.NAMESPACE_URI,
								name,
								Names.PREFIX),
						Class.forName(attributeMetadata.getType()));
				int length = Array.getLength(propertyValues);
				if (length != 0)
				{
					Object propertyValue = Array.get(propertyValues, 0);
					
					assertEquals(
							"Comparison failed for property "+name,
							sampleValue,
							propertyValue);
				} else {
					assertNull(
							String.format(
									"\"%s\" property value shouldn't be null. Its value is %s",
									name,
									_managementServer.getAttribute(_resourceObjectName,name)),
									sampleValue);
				}				
			}
		}
		assertTrue(
				"It's not possibile to run successfully this test case if " +
					"the target WS-Resource has no at least one writable property",
				atLeastThereIsOneWritableProperty);
	}

	/**
	 * Test the WS-RP SetResourceProperty interface of the WS-DM adapter when the 
	 * target property is null.
	 * According to WS-RP specs this operation is not allowed because in this case a SetResourceProperty with an "Insert"
	 * message should be sent in order to initialize the property.
	 * 
	 * <br>precondition : a ws resource exists and is registered. The value of the target property is null. 
	 * <br>postcondition : a Soap fault is received indicating the failuire.
	 */
	public void testSetResourcePropertiesKO() throws Exception
	{
		Object typePropertyValue = _managementServer.getAttribute(_resourceObjectName, "Type");
		assertNull(typePropertyValue);
		
		try 
		{		
			_resourceClient.updateResourceProperty(
					new QName(
							Names.NAMESPACE_URI,
							"Type",
							Names.PREFIX),
					new Object[]{"sampleValue"});					
			fail(
					"If the property is null on the target ws resource, according " +
					"to WS-RP specs, an update of its value is not possible.");
		} catch(SoapFault expected)
		{
			
		}
	}
	
//	public void testGetAndPutResourcePropertyDocumentOK() throws Exception
//	{	
//		Element properties = _resourceClient.getResourcePropertyDocument();
//		
//		Element mgmtPubInterval = XmlUtils.getElement(properties, new QName(Names.NAMESPACE_URI,"MgmtPubInterval",Names.PREFIX));
//		mgmtPubInterval.setTextContent(String.valueOf(Long.MAX_VALUE));
//		
//		Element durable = XmlUtils.getElement(properties, new QName(Names.NAMESPACE_URI,"Durable",Names.PREFIX));
//		durable.setTextContent(String.valueOf(Boolean.FALSE));
//		
//		Element consumerCount = XmlUtils.getElement(properties, new QName(Names.NAMESPACE_URI,"ConsumerCount",Names.PREFIX));
//		consumerCount.setTextContent(String.valueOf(13));		
//		
//		fail("PutResourcePropertyDocument not yet implemented!");
////		_resourceClient.putResourcePropertyDocument(properties);
////		
////		Element newProperties = _resourceClient.getResourcePropertyDocument();
////		
////		assertEquals(properties,newProperties);
//	}
	
	/**
	 * Test the WS-RP GetResourceProperties interface of the WS-DM adapter.
	 * 
	 * <br>precondition : a ws resource exists and is registered. 
	 * <br>postcondition : Properties are correctly returned according to WSRP interface and they (their value)
	 * 								are matching with corresponding MBean properties.
	 */
	public void testGetMultipleResourcePropertiesOK() throws Exception
	{
		MBeanAttributeInfo [] attributesMetadata = _mbeanInfo.getAttributes();
		QName[] names = new QName[attributesMetadata.length];
		
		int index = 0;
		for (MBeanAttributeInfo attributeMetadata : _mbeanInfo.getAttributes())
		{
			QName qname = new QName(Names.NAMESPACE_URI,attributeMetadata.getName(),Names.PREFIX);
			names[index++] = qname;
		}
		
		Element[] properties =_resourceClient.getMultipleResourceProperties(names);
		for (Element element : properties)
		{
			String name = element.getLocalName();
			Object value = _managementServer.getAttribute(_resourceObjectName, name);
			if ("Name".equals(name))
			{
				assertEquals(
						value,
						element.getTextContent());
			} else if ("Durable".equals(name))
			{
				assertEquals(
						value,
						Boolean.valueOf(element.getTextContent()));				
			} else if ("ExpireTime".equals(name))
			{
				assertEquals(
						value,
						new Date(Long.valueOf(element.getTextContent())));								
			} else if ("MsgTotalEnqueues".equals(name))
			{
				assertEquals(
						value,
						Long.valueOf(element.getTextContent()));								
			} else if ("ConsumerCount".equals(name))
			{
				assertEquals(
						value,
						Integer.valueOf(element.getTextContent()));								
			}else if ("VhostRef".equals(name))
			{
				assertEquals(
						value,
						UUID.fromString(element.getTextContent()));								
			}
		}
	}
	
	/**
	 * Test operation invocation on WS-Resource.
	 * This method tests the exchange of simple types between requestor and service provider.
	 * With simple types we mean :
	 * 
	 * <ul>
	 * 	<li>java.lang.Long / long (xsd:long)
	 * 	<li>java.lang.Integer / int (xsd:int / xsd:integer)
	 * 	<li>java.lang.Double/ double (xsd:double)
	 * 	<li>java.lang.Float / float (xsd:float)
	 * 	<li>java.lang.Short / short (xsd:short)
	 * 	<li>java.lang.Boolean / boolean (xsd:boolean)
	 * 	<li>java.lang.String (xsd:string)
	 * 	<li>java.net.URI (xsd:anyURI)
	 * 	<li>java.util.Date(xsd:dateTime)
	 * </ul>
	 * 
	 * <br>precondition : a ws resource exists and is registered and the requested operation is available on that. 
	 * <br>postcondition : invocations are executed successfully, no exception is thrown and parameters are correctly returned.
	 */
	@SuppressWarnings("unchecked")
	public void testOperationInvocationOK_withSimpleTypes() throws Exception
	{
		Long expectedLongResult = new Long(1373);
		Boolean expectedBooleanResult = Boolean.TRUE;
		Double expectedDoubleResult = new Double(12763.44);
		Float expectedFloatResult = new Float(2727.233f);
		Integer expectedIntegerResult = new Integer(28292);
		Short expectedShortResult = new Short((short)227);
		String expectedStringResult = "expectedStringResult";
		URI expectedUriResult = URI.create("http://qpid.apache.org/");
		Date expectedDateResult = new Date();
		
		Object result = _resourceClient.invoke(
				_invocationHandlers.get("echoWithSimpleTypes"), 
				new Object[]{
					expectedLongResult,
					expectedBooleanResult,
					expectedDoubleResult,
					expectedFloatResult,
					expectedIntegerResult,
					expectedShortResult,
					expectedStringResult,
					expectedUriResult,
					expectedDateResult});

		Method getStatusCode = result.getClass().getMethod("getStatusCode");
		Method getOutputParameters = result.getClass().getMethod("getOutputParameters");
		assertEquals(retCodeOk,getStatusCode.invoke(result));
		Map<String,Object> out = (Map<String, Object>) getOutputParameters.invoke(result);
		
		assertEquals("Output parameters must be 9.",9,out.size());
		assertTrue("Long output parameter not found on result object.",out.containsValue(expectedLongResult));
		assertTrue("Boolean output parameter not found on result object.",out.containsValue(expectedBooleanResult));
		assertTrue("Double output parameter not found on result object.",out.containsValue(expectedDoubleResult));
		assertTrue("Float output parameter not found on result object.",out.containsValue(expectedFloatResult));
		assertTrue("Integer output parameter not found on result object.",out.containsValue(expectedIntegerResult));
		assertTrue("Short output parameter not found on result object.",out.containsValue(expectedShortResult));
		assertTrue("String output parameter not found on result object.",out.containsValue(expectedStringResult));
		assertTrue("URI output parameter not found on result object.",out.containsValue(expectedUriResult));
		assertTrue("Date output parameter not found on result object.",out.containsValue(expectedDateResult));		
	}
	
	/**
	 * Test operation invocation on WS-Resource.
	 * This method tests the exchange of arrays between requestor and service provider.
	 * For this test exchanged arrays contain :
	 * 
	 * <ul>
	 * 	<li>java.lang.Long  (xsd:long)
	 * 	<li>java.lang.Integer (xsd:int / xsd:integer)
	 * 	<li>java.lang.Double (xsd:double)
	 * 	<li>java.lang.Float (xsd:float)
	 * 	<li>java.lang.Short (xsd:short)
	 * 	<li>java.lang.Boolean (xsd:boolean)
	 * 	<li>java.lang.String (xsd:string)
	 * 	<li>java.net.URI (xsd:anyURI)
	 * 	<li>java.util.Date(xsd:dateTime)
	 * </ul>
	 * 
	 * <br>precondition : a ws resource exists and is registered and the requested operation is available on that. 
	 * <br>postcondition : invocations are executed successfully, no exception is thrown and parameters are correctly returned.
	 */
	@SuppressWarnings("unchecked")
	public void testOperationInvocationOK_withWrapperArrays() throws Exception
	{
		Long [] expectedLongResult = {new Long(2),new Long(1),new Long(3),new Long(4)};
		Boolean [] expectedBooleanResult = { Boolean.TRUE,Boolean.FALSE,Boolean.FALSE};
		Double [] expectedDoubleResult = {12763.44d,2832.33d,2292.33d,22293.22d};
		Float [] expectedFloatResult = {2727.233f,1f,2f,4f,5.4f,33.2f};
		Integer [] expectedIntegerResult = {1,2,3,4,55,66,77,88,99};
		Short [] expectedShortResult = {(short)227,(short)23,(short)9};
		String [] expectedStringResult = {"s1","s2","s333","s4"};
		URI [] expectedUriResult = {
				URI.create("http://qpid.apache.org/"),
				URI.create("http://www.apache.org"),
				URI.create("http://projects.apache.org")};
		
		Date [] expectedDateResult = {
				new Date(), 
				new Date(38211897),
				new Date(903820382)};
		
		Object result = _resourceClient.invoke(
				_invocationHandlers.get("echoWithArrays"), 
				new Object[]{
					expectedLongResult,
					expectedBooleanResult,
					expectedDoubleResult,
					expectedFloatResult,
					expectedIntegerResult,
					expectedShortResult,
					expectedStringResult,
					expectedUriResult,
					expectedDateResult});

		Method getStatusCode = result.getClass().getMethod("getStatusCode");
		Method getOutputParameters = result.getClass().getMethod("getOutputParameters");
		assertEquals(retCodeOk,getStatusCode.invoke(result));
		Map<String,Object> out = (Map<String, Object>) getOutputParameters.invoke(result);
		
		assertEquals("Output parameters must be 9.",9,out.size());
		assertTrue("Long array doesn't match.",Arrays.equals(expectedLongResult, (Long[])out.get(Long.class.getName())));
		assertTrue("Boolean array doesn't match.",Arrays.equals(expectedBooleanResult, (Boolean[])out.get(Boolean.class.getName())));
		assertTrue("Double array doesn't match.",Arrays.equals(expectedDoubleResult, (Double[])out.get(Double.class.getName())));
		assertTrue("Float array doesn't match.",Arrays.equals(expectedFloatResult, (Float[])out.get(Float.class.getName())));
		assertTrue("Integer array doesn't match.", Arrays.equals(expectedIntegerResult, (Integer[])out.get(Integer.class.getName())));
		assertTrue("Short array doesn't match.",Arrays.equals(expectedShortResult, (Short[])out.get(Short.class.getName())));
		assertTrue("String array doesn't match.",Arrays.equals(expectedStringResult, (String[])out.get(String.class.getName())));
		assertTrue("URI array doesn't match.",Arrays.equals(expectedUriResult, (URI[])out.get(URI.class.getName())));
		assertTrue("Date array doesn't match.",Arrays.equals(expectedDateResult, (Date[])out.get(Date.class.getName())));
	}	

	/**
	 * Test operation invocation on WS-Resource.
	 * This method tests the exchange of primitive type arrays between requestor and service provider.
	 * NOte that even the sent array contain primtiive type QMan deals only with objects so in the result 
	 * object you will find the corresponding wrapper types.
	 * 
	 * For this test exchanged arrays contain :
	 * 
	 * <ul>
	 * 	<li>java.lang.Long / long (xsd:long)
	 * 	<li>java.lang.Integer / int (xsd:int / xsd:integer)
	 * 	<li>java.lang.Double/ double (xsd:double)
	 * 	<li>java.lang.Float / float (xsd:float)
	 * 	<li>java.lang.Short / short (xsd:short)
	 * 	<li>java.lang.Boolean / boolean (xsd:boolean)
	 * </ul>
	 * 
	 * <br>precondition : a ws resource exists and is registered and the requested operation is available on that. 
	 * <br>postcondition : invocations are executed successfully, no exception is thrown and parameters are correctly returned.
	 */
	@SuppressWarnings("unchecked")
	public void testOperationInvocationOK_withPrimitiveArrays() throws Exception
	{
		long [] expectedLongResult = {1L,2L,3L,4L};
		boolean [] expectedBooleanResult = { true,false,false};
		double [] expectedDoubleResult = {12763.44d,2832.33d,2292.33d,22293.22d};
		float [] expectedFloatResult = {2727.233f,1f,2f,4f,5.4f,33.2f};
		int [] expectedIntegerResult = {1,2,3,4,55,66,77,88,99};
		short [] expectedShortResult = {(short)227,(short)23,(short)9};
		
		Object result = _resourceClient.invoke(
				_invocationHandlers.get("echoWithSimpleTypeArrays"), 
				new Object[]{
					expectedLongResult,
					expectedBooleanResult,
					expectedDoubleResult,
					expectedFloatResult,
					expectedIntegerResult,
					expectedShortResult});

		Method getStatusCode = result.getClass().getMethod("getStatusCode");
		Method getOutputParameters = result.getClass().getMethod("getOutputParameters");
		assertEquals(retCodeOk,getStatusCode.invoke(result));
		Map<String,Object> out = (Map<String, Object>) getOutputParameters.invoke(result);
		
		assertEquals("Output parameters must be 6.",6,out.size());
		assertArrayEquals(expectedLongResult, out.get(long.class.getName()));
		assertArrayEquals(expectedBooleanResult, out.get(boolean.class.getName()));
		assertArrayEquals(expectedDoubleResult, out.get(double.class.getName()));
		assertArrayEquals(expectedFloatResult, out.get(float.class.getName()));
		assertArrayEquals(expectedIntegerResult, out.get(int.class.getName()));
		assertArrayEquals(expectedShortResult, out.get(short.class.getName()));
	}	

	/**
	 * Test operation invocation on WS-Resource.
	 * This method tests the exchange of a byte type array between requestor and service provider.
	 * 
	 * <br>precondition : a WS-Resource exists and is registered and the requested operation is available on that. 
	 * <br>postcondition : invocations are executed successfully, no exception is thrown and byte array are correctly returned.
	 */
	@SuppressWarnings("unchecked")
	public void testOperationInvocationOK_withByteArray() throws Exception
	{
		byte [] expectedByteResult = {1,3,4,2,2,44,22,3,3,55,66};

		Object result = _resourceClient.invoke(
				_invocationHandlers.get("echoWithByteArray"), 
				new Object[]{expectedByteResult});

		Method getStatusCode = result.getClass().getMethod("getStatusCode");
		Method getOutputParameters = result.getClass().getMethod("getOutputParameters");
		
		assertEquals(retCodeOk,getStatusCode.invoke(result));
		Map<String,Object> out = (Map<String, Object>) getOutputParameters.invoke(result);
		
		assertEquals("Output parameters must be 1.",1,out.size());
		assertArrayEquals(expectedByteResult, out.get(byte[].class.getName()));
	}		
		
	/**
	 * Test a simple operation invocation on a WS-Resource.
	 * This method tests a simple operation without any input and output parameters.
	 * 
	 * <br>precondition : a ws resource exists and is registered and the requested operation is available on that. 
	 * <br>postcondition : invocations are executed successfully an no exception is thrown.
	 */
	@SuppressWarnings("unchecked")
	public void testSimpleOperationInvocationOK() throws Exception
	{
		Object result = _resourceClient.invoke(
				_invocationHandlers.get("voidWithoutArguments"), 
				null);

		Method getStatusCode = result.getClass().getMethod("getStatusCode");
		assertEquals(
				"Something was wrong...expected return code is "+retCodeOk,
				retCodeOk,
				getStatusCode.invoke(result));
	}

	/**
	 * Test a the invocation on a WS-Resource with a method that throws an exception..
	 * 
	 * <br>precondition : a ws resource exists and is registered and the requested operation is available on that. 
	 * <br>postcondition : an exception is thrown by the requested method.
	 */
	@SuppressWarnings("unchecked")
	public void testInvocationException_OK() throws Exception
	{
		try 
		{
		 	_resourceClient.invoke(
					_invocationHandlers.get("throwsException"), 
					null);
		 	fail("The requested operation has thrown an exception so a Soap Fault is expected...");
		} catch(SoapFault expected)
		{
		}
	}	
	
	/**
	 * Test operation invocation on WS-Resource.
	 * This method tests the exchange of UUID type between requestor and service provider.
	 * 
	 * <br>precondition : a WS-Resource exists and is registered and the requested operation is available on that. 
	 * <br>postcondition : invocations are executed successfully, no exception is thrown and parameters are correctly returned.
	 */
	@SuppressWarnings("unchecked")
	public void testOperationInvocationOK_withUUID() throws Exception
	{
		UUID expectedUuid = UUID.randomUUID();

		Object result = _resourceClient.invoke(
				_invocationHandlers.get("echoWithUUID"), 
				new Object[]{expectedUuid});

		Method getStatusCode = result.getClass().getMethod("getStatusCode");
		Method getOutputParameters = result.getClass().getMethod("getOutputParameters");
		
		assertEquals(retCodeOk,getStatusCode.invoke(result));
		Map<String,Object> out = (Map<String, Object>) getOutputParameters.invoke(result);
		
		assertEquals("Output parameters must be 1.",1,out.size());
		assertEquals(expectedUuid, out.get("uuid"));
	}		

	/**
	 * Test operation invocation on WS-Resource.
	 * This method tests the exchange of Map type between requestor and service provider.
	 * For this test exchanged arrays contain :
	 * 
	 * <br>precondition : a ws resource exists and is registered and the requested operation is available on that. 
	 * <br>postcondition : invocations are executed successfully, no exception is thrown and parameters are correctly returned.
	 */
	@SuppressWarnings("unchecked")
	public void testOperationInvocationOK_withMap() throws Exception
	{
		Map<String,Object> expectedMap = new HashMap<String, Object>();
		expectedMap.put("p1", new Long(1));
		expectedMap.put("p2", Boolean.TRUE);
		expectedMap.put("p3", 1234d);
		expectedMap.put("p4", 11.2f);
		expectedMap.put("p5", 1272);
		expectedMap.put("p6", (short)12);
		expectedMap.put("p7", "aString");
		expectedMap.put("p8", "http://qpid.apache.org");
		expectedMap.put("p9", new Date(12383137128L));
		expectedMap.put("p10", new byte[]{1,2,2,3,3,4});
		
		Object result = _resourceClient.invoke(
				_invocationHandlers.get("echoWithMap"), 
				new Object[]{expectedMap});

		Method getStatusCode = result.getClass().getMethod("getStatusCode");
		Method getOutputParameters = result.getClass().getMethod("getOutputParameters");
		
		assertEquals(retCodeOk,getStatusCode.invoke(result));
		Map<String,Object> out = (Map<String, Object>) ((Map<String, Object>) getOutputParameters.invoke(result)).get("map");
		
		assertEquals("Output parameters must be 10.",10,out.size());
			assertEquals(expectedMap.get("p1"),out.get("p1"));
			assertEquals(expectedMap.get("p2"),out.get("p2"));
			assertEquals(expectedMap.get("p3"),out.get("p3"));
			assertEquals(expectedMap.get("p4"),out.get("p4"));
			assertEquals(expectedMap.get("p5"),out.get("p5"));
			assertEquals(expectedMap.get("p6"),out.get("p6"));
			assertEquals(expectedMap.get("p7"),out.get("p7"));
			assertEquals(expectedMap.get("p8"),out.get("p8"));
			assertEquals(expectedMap.get("p9"),out.get("p9"));
			assertTrue( Arrays.equals((byte[])expectedMap.get("p10"),(byte[])out.get("p10")));
	}			
	
	/**
	 * Main entry point for running this test case.
	 * 
	 * @return the decorated test case.
	 */
	public static Test suite() {
		TestSuite suite = new TestSuite("Test Suite for WS-DM Adapter");
		suite.addTestSuite(WsDmAdapterTest.class);
		return new WsDmAdapterTestSetup(suite);
	}
	
	/**
	 * Creates a service group client reference.
	 * 
	 * @return a service group client reference.
	 */
	private ServiceGroupClient getServiceGroupClient()
	{
		URI address = URI.create(
				Protocol.DEFAULT_ENDPOINT_URI.replaceFirst("8080",System.getProperty(Names.ADAPTER_PORT_PROPERTY_NAME)));
		System.out.println(address);
		return new ServiceGroupClient(new EndpointReference(address));
	}
	
	/**
	 * In order to test the behaviour of the WS-DM adapter, at 
	 * least one resource must be created. This is the method that 
	 * returns the name (ObjectName on JMX side, Resource-ID on WSDM side)
	 * of that resource
	 * 
	 * @return the name of the MBean instance that will be created.
	 * @throws Exception when the name if malformed. Practically never.
	 */
	private ObjectName createResourceName() throws Exception
	{
		return new ObjectName(
				"Q-MAN:objectId="+UUID.randomUUID()+
				", brokerID="+UUID.randomUUID()+
				",class=queue"+
				",package=org.apache.qpid"+
				",name="+System.currentTimeMillis());
	}
	
	private Map<String,ProxyHandler> createInvocationHandlers() 
	{
		Map<String, ProxyHandler> handlers = new HashMap<String, ProxyHandler>();
		
		ProxyHandler handler = new ReflectionProxyHandler();
        handler.setAction(Names.NAMESPACE_URI+"/"+"voidWithoutArguments");
        handler.setRequestName(
        		new QName(
        				Names.NAMESPACE_URI, 
        				"voidWithoutArgumentsRequest", 
        				Names.PREFIX));
        handler.setRequestParameterNames(new QName[]{});       
        handler.setResponseName(
        		new QName(
        				Names.NAMESPACE_URI, 
        				"voidWithoutArgumentsResponse",  
        				Names.PREFIX));
        handler.setReturnType(Result.class); 
        
        ProxyHandler exceptionHandler = new ReflectionProxyHandler();
        exceptionHandler.setAction(Names.NAMESPACE_URI+"/"+"throwsException");
        exceptionHandler.setRequestName(
        		new QName(
        				Names.NAMESPACE_URI, 
        				"throwsExceptionRequest", 
        				Names.PREFIX));
        
        exceptionHandler.setRequestParameterNames(new QName[]{});        
        exceptionHandler.setResponseName(
        		new QName(
        				Names.NAMESPACE_URI, 
        				"throwsExceptionResponse",  
        				Names.PREFIX));
        
        exceptionHandler.setReturnType(Result.class); 
        
        ProxyHandler echoWithWrapperTypesHandler = new ReflectionProxyHandler();
        echoWithWrapperTypesHandler.setAction(Names.NAMESPACE_URI+"/"+"echoWithSimpleTypes");
        echoWithWrapperTypesHandler.setRequestName(
        		new QName(
        				Names.NAMESPACE_URI, 
        				"echoWithSimpleTypesRequest", 
        				Names.PREFIX));
        
        echoWithWrapperTypesHandler.setRequestParameterNames(new QName[]{
        		new QName(Names.NAMESPACE_URI,"p1",Names.PREFIX),
        		new QName(Names.NAMESPACE_URI,"p2",Names.PREFIX),
        		new QName(Names.NAMESPACE_URI,"p3",Names.PREFIX),
        		new QName(Names.NAMESPACE_URI,"p4",Names.PREFIX),
        		new QName(Names.NAMESPACE_URI,"p5",Names.PREFIX),
        		new QName(Names.NAMESPACE_URI,"p6",Names.PREFIX),
        		new QName(Names.NAMESPACE_URI,"p7",Names.PREFIX),
        		new QName(Names.NAMESPACE_URI,"p8",Names.PREFIX),
        		new QName(Names.NAMESPACE_URI,"p9",Names.PREFIX),
        });        
        
        echoWithWrapperTypesHandler.setResponseName(
        		new QName(
        				Names.NAMESPACE_URI, 
        				"echoWithSimpleTypesResponse", 
        				Names.PREFIX));
        
        echoWithWrapperTypesHandler.setReturnType(Result.class);
        
        ProxyHandler echoWithArrayOfWrapperTypes = new ReflectionProxyHandler();
        echoWithArrayOfWrapperTypes.setAction(Names.NAMESPACE_URI+"/"+"echoWithArrays");
        echoWithArrayOfWrapperTypes.setRequestName(
        		new QName(
        				Names.NAMESPACE_URI, 
        				"echoWithArraysRequest",  
        				Names.PREFIX));
        
        echoWithArrayOfWrapperTypes.setRequestParameterNames(new QName[]{
        		new QName(Names.NAMESPACE_URI,"p1",Names.PREFIX),
        		new QName(Names.NAMESPACE_URI,"p2",Names.PREFIX),
        		new QName(Names.NAMESPACE_URI,"p3",Names.PREFIX),
        		new QName(Names.NAMESPACE_URI,"p4",Names.PREFIX),
        		new QName(Names.NAMESPACE_URI,"p5",Names.PREFIX),
        		new QName(Names.NAMESPACE_URI,"p6",Names.PREFIX),
        		new QName(Names.NAMESPACE_URI,"p7",Names.PREFIX),
        		new QName(Names.NAMESPACE_URI,"p8",Names.PREFIX),
        		new QName(Names.NAMESPACE_URI,"p9",Names.PREFIX),
        });        
        
        echoWithArrayOfWrapperTypes.setResponseName(
        		new QName(
        				Names.NAMESPACE_URI, 
        				"echoWithArraysResponse", 
        				Names.PREFIX));
        
        echoWithArrayOfWrapperTypes.setReturnType(Result.class);
        
        ProxyHandler echoWithArrayOfPrimitiveTypes = new ReflectionProxyHandler();
        echoWithArrayOfPrimitiveTypes.setAction(Names.NAMESPACE_URI+"/"+"echoWithSimpleTypeArrays");
        echoWithArrayOfPrimitiveTypes.setRequestName(
        		new QName(
        				Names.NAMESPACE_URI, 
        				"echoWithSimpleTypeArraysRequest",  
        				Names.PREFIX));
        
        echoWithArrayOfPrimitiveTypes.setRequestParameterNames(new QName[]{
        		new QName(Names.NAMESPACE_URI,"p1",Names.PREFIX),
        		new QName(Names.NAMESPACE_URI,"p2",Names.PREFIX),
        		new QName(Names.NAMESPACE_URI,"p3",Names.PREFIX),
        		new QName(Names.NAMESPACE_URI,"p4",Names.PREFIX),
        		new QName(Names.NAMESPACE_URI,"p5",Names.PREFIX),
        		new QName(Names.NAMESPACE_URI,"p6",Names.PREFIX)});        
        
        echoWithArrayOfPrimitiveTypes.setResponseName(
        		new QName(
        				Names.NAMESPACE_URI, 
        				"echoWithSimpleTypeArraysResponse", 
        				Names.PREFIX));
        
        echoWithArrayOfPrimitiveTypes.setReturnType(Result.class);
        
        ProxyHandler echoWithByteArray = new EnhancedReflectionProxyHandler();
        echoWithByteArray.setAction(Names.NAMESPACE_URI+"/"+"echoWithByteArray");
        echoWithByteArray.setRequestName(
        		new QName(
        				Names.NAMESPACE_URI, 
        				"echoWithByteArrayRequest",  
        				Names.PREFIX));
        
        echoWithByteArray.setRequestParameterNames(
        		new QName[]{
        				new QName(Names.NAMESPACE_URI,"p1",Names.PREFIX)});        
        
        echoWithByteArray.setResponseName(
        		new QName(
        				Names.NAMESPACE_URI, 
        				"echoWithByteArrayResponse", 
        				Names.PREFIX));
        
        echoWithByteArray.setReturnType(Result.class);
        
        ProxyHandler echoWithUUID = new EnhancedReflectionProxyHandler();
        echoWithUUID.setAction(Names.NAMESPACE_URI+"/"+"echoWithUUID");
        echoWithUUID.setRequestName(
        		new QName(
        				Names.NAMESPACE_URI, 
        				"echoWithUUIDRequest",  
        				Names.PREFIX));
        
        echoWithUUID.setRequestParameterNames(
        		new QName[]{
        				new QName(Names.NAMESPACE_URI,"p1",Names.PREFIX)});        
        
        echoWithUUID.setResponseName(
        		new QName(
        				Names.NAMESPACE_URI, 
        				"echoWithUUIDResponse", 
        				Names.PREFIX));
        
        echoWithUUID.setReturnType(Result.class);
        
        ProxyHandler echoWithMap = new EnhancedReflectionProxyHandler();
        echoWithMap.setAction(Names.NAMESPACE_URI+"/"+"echoWithMap");
        echoWithMap.setRequestName(
        		new QName(
        				Names.NAMESPACE_URI, 
        				"echoWithMapRequest",  
        				Names.PREFIX));
        
        echoWithMap.setRequestParameterNames(
        		new QName[]{
        				new QName(Names.NAMESPACE_URI,"p1",Names.PREFIX)});        
        
        echoWithMap.setResponseName(
        		new QName(
        				Names.NAMESPACE_URI, 
        				"echoWithMapResponse", 
        				Names.PREFIX));
        
        echoWithMap.setReturnType(Result.class);
        
        handlers.put("voidWithoutArguments",handler);
        handlers.put("echoWithSimpleTypes",echoWithWrapperTypesHandler);
        handlers.put("echoWithArrays",echoWithArrayOfWrapperTypes);
        handlers.put("echoWithSimpleTypeArrays", echoWithArrayOfPrimitiveTypes);
        handlers.put("echoWithByteArray", echoWithByteArray);
        handlers.put("echoWithUUID", echoWithUUID);
        handlers.put("echoWithMap", echoWithMap);
        handlers.put("throwsException",exceptionHandler);
        return handlers;
	}
	
	/**
	 * Internal method used for array comparison using reflection.
	 * 
	 * @param expectedArray the expected array.
	 * @param resultArray the array that must match the expected one.
	 */
	private void assertArrayEquals(Object expectedArray, Object resultArray) 
	{
		int expectedArrayLength = Array.getLength(expectedArray);
		int resultArrayLength = Array.getLength(resultArray);
		
		assertEquals(expectedArrayLength,resultArrayLength);
		
		for (int index = 0; index < expectedArrayLength; index++)
		{
			Object expected = Array.get(expectedArray, index);
			Object result = Array.get(resultArray, index);
			
			assertEquals(expected,result);
		}
	}
	
	public static int getFreePort() throws IOException {
		ServerSocket server = null;
		try 
		{
			server = new ServerSocket(0);
			return server.getLocalPort();
		} finally 
		{
			server.close();			
		}
	}
}