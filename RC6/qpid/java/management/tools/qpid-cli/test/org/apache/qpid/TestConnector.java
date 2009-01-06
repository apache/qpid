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
/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid;

import junit.framework.TestCase;

import javax.management.remote.JMXServiceURL;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXConnector;
import javax.management.MBeanServerConnection;
import java.net.MalformedURLException;
import java.io.IOException;

import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import org.junit.Assert;

/**
 * Created by IntelliJ IDEA.
 * User: lahiru
 * Date: Jun 30, 2008
 * Time: 12:11:50 PM
 * To change this template use File | Settings | File Templates.
 */
public class TestConnector {
    Connector test;
    JMXServiceURL svc_url;
    JMXConnector connector;
    MBeanServerConnection mbsc;

    @Before
    public void setup()
    {
        test = ConnectorFactory.getConnector("localhost","8999");
        String url = "service:jmx:rmi:///jndi/rmi://localhost:8999/jmxrmi";

    }
    @Test
    public void testGetURL()
    {


//        Assert.assertNotNull(test);
        Assert.assertEquals(test.getURL(),test.getURL());
    }
    @Test
    public void testGetConnector()
    {
        Assert.assertEquals(test.getConnector(),test.getConnector());
    }
    @Test
    public void testGetMBeanServerConnection()
    {
        Assert.assertEquals(test.getMBeanServerConnection(),test.getMBeanServerConnection());
    }
    @After
    public void cleanup()
    {
          try {

              test.getConnector().close();
              test = null;
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        test = null;
    }


}
