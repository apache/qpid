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
package org.apache.qpid.management.domain.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.management.MBeanAttributeInfo;

import org.apache.qpid.management.TestConstants;
import org.apache.qpid.management.configuration.ConfigurationException;
import org.apache.qpid.management.configuration.StubConfigurator;
import org.apache.qpid.management.domain.model.QpidClass.QpidManagedObject;

public class QpidClassTest extends BaseDomainModelTestCase
{
    private QpidClass _qpidClass;
    
    @Override
    protected void setUp () throws Exception
    {
        QpidPackage qpidPackage = new QpidPackage(TestConstants.QPID_PACKAGE_NAME,TestConstants.DOMAIN_MODEL);
        _qpidClass = new QpidClass(TestConstants.EXCHANGE_CLASS_NAME,TestConstants.HASH,qpidPackage);
    }
        
    /**
     * Tests the execution of the getObjectInstance() method.
     * Basically it tests the addition of a new object instance.
     * 
     * <br>precondition: class has no object instances.
     * <br>precondition : class contains the added object instance.
     */
    public void testGetObjectInstance() 
    {            
        assertFalse (
                "Nobody set instance #"+TestConstants.OBJECT_ID+" into this class so why is it there?", 
        		_qpidClass._objectInstances.containsKey(TestConstants.OBJECT_ID));

        _qpidClass.getObjectInstance(TestConstants.OBJECT_ID, false);
        
        assertTrue (
                "Now the instance #"+TestConstants.OBJECT_ID+" should be there...",
                _qpidClass._objectInstances.containsKey(TestConstants.OBJECT_ID));
    }
       
    /**
     * Tests the injection of instrumentation and configuration data (related to a specific object instance) before the 
     * schema is installed.
     * 
     * <br>precondition : the schema hasn't yet installed on this class.
     * <br>postcondition : incoming configuration & instrumentation data is stored into the corresponding object instance.
     */
    public void testAddInstrumentationAndConfigurationDataBeforeSchemaInstallation() 
    {
        _qpidClass._state = _qpidClass._schemaRequestedButNotYetInjected;
        QpidManagedObject objectInstance = _qpidClass.getObjectInstance(TestConstants.OBJECT_ID,false);
        
        assertTrue(
                "This object instance is a new one so how is it possible that it has already instrumentation data? ",
                objectInstance._rawInstrumentationData.isEmpty());
        assertTrue(
                "This object instance is a new one so how is it possible that it has already configuration data? ",
                objectInstance._rawConfigurationData.isEmpty());
        
        byte [] dummyConfigurationData = {1,2,3,4,5,6,7,8};
        byte [] dummyInstrumentationData = {11,21,31,41,51,61,71,81};
        
        _qpidClass.addConfigurationData(TestConstants.OBJECT_ID, dummyConfigurationData);
        _qpidClass.addInstrumentationData(TestConstants.OBJECT_ID, dummyInstrumentationData);
        
        assertEquals("Now configuration data should be there...",1,objectInstance._rawConfigurationData.size());
        assertEquals("Now instrumentation data should be there...",1,objectInstance._rawInstrumentationData.size());
        
        assertTrue(
                "Object instance configuration data should be the previously set...",
                Arrays.equals(objectInstance._rawConfigurationData.get(0), 
                dummyConfigurationData));
        
        assertTrue(
                "Object instance instrumentation data should be the previously set...",
                Arrays.equals(objectInstance._rawInstrumentationData.get(0), 
                dummyInstrumentationData));        
    }
    
    public void testBuildAttributesOK() throws UnableToBuildFeatureException, ConfigurationException
    {
        StubConfigurator configurator = new StubConfigurator();
        configurator.configure();

        List<Map<String,Object>> properties = new ArrayList<Map<String, Object>>();
        List<Map<String,Object>> statistics = new ArrayList<Map<String, Object>>();
        
        Map <String,Object> age = new HashMap<String, Object>();
        
        age.put("name","age");
        age.put("access", new Integer(1));
        age.put("unit","years");
        age.put("min", new Integer(0));
        age.put("max",new Integer(120));
        age.put("desc", "The age of a person.");
        age.put("type", new Integer(1));
        age.put("optional",0);
        age.put("index", new Integer(1));

        Map <String,Object> surname = new HashMap<String, Object>();
        surname.put("name","surname");
        surname.put("access", new Integer(1));
        surname.put("desc", "The surname of a person.");
        surname.put("type", new Integer(1));
        surname.put("optional",1);
        surname.put("index", new Integer(1));
        properties.add(age);
        properties.add(surname);
        
        MBeanAttributeInfo [] info = new MBeanAttributeInfo[properties.size()+statistics.size()];
        _qpidClass.buildAttributes(properties, statistics, info);
        
        assertEquals(2,_qpidClass._properties.size());
        
        QpidProperty property = _qpidClass._properties.get("age");
        
        assertEquals("age",property.getName());
        assertEquals(AccessMode.RW,property.getAccessMode());
        assertEquals("years",property.getUnit());
        assertEquals(0,property.getMinValue());
        assertEquals(120,property.getMaxValue());
        assertEquals(Integer.MIN_VALUE,property.getMaxLength());
        assertEquals("The age of a person.",property.getDescription());
        assertEquals(String.class,property.getJavaType());
        assertFalse(property.isOptional());
        
        property = _qpidClass._properties.get("surname");
        
        assertEquals("surname",property.getName());
        assertEquals(AccessMode.RW,property.getAccessMode());
        assertNull(property.getUnit());
        assertEquals(Integer.MIN_VALUE,property.getMinValue());
        assertEquals(Integer.MIN_VALUE,property.getMaxValue());
        assertEquals(Integer.MIN_VALUE,property.getMaxLength());        
        assertEquals("The surname of a person.",property.getDescription());
        assertEquals(String.class,property.getJavaType());
        assertTrue(property.isOptional());
  }
}