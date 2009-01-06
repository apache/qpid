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
package org.apache.qpid.management.online;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import junit.framework.TestCase;

/**
 * Layer supertype for all online QMan test cases.
 * Note that QMan must be running and up in order to run concrete subclasses (test cases).
 * 
 * @author AGazzarini
 */
public abstract class BaseOnlineTestCase extends TestCase
{
    protected MBeanServerConnection connection;
    
    /**
     * Setup fixture for this test case.
     * Basically it estabilishes a connection to QMan using RMI JMX connector.
     */
    @Override
    protected void setUp () 
    {
        try 
        {
            JMXServiceURL  url = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://localhost:1099/jmxrmi");
            JMXConnector  jmxc = JMXConnectorFactory.connect(url);
            connection = jmxc.getMBeanServerConnection();
        } catch(Exception exception) 
        {
            fail("QMan must be running and up in order to run this test!");
            exception.printStackTrace();
        }
    }
}
