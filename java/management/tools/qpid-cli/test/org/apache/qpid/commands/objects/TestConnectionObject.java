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
package org.apache.qpid.commands.objects;

import junit.framework.TestCase;

import org.apache.qpid.ConnectionConstants;
import org.apache.qpid.ConnectorFactory;
import org.apache.qpid.Connector;
import org.junit.After;
import org.junit.Test;
import org.junit.Assert;
import org.junit.Before;

import javax.management.MBeanServerConnection;

/**
 * Created by IntelliJ IDEA.
 * User: lahiru
 * Date: Jun 30, 2008
 * Time: 12:24:14 PM
 * To change this template use File | Settings | File Templates.
 */
public class TestConnectionObject {
    Connector conn;
    MBeanServerConnection mbsc;
    ConnectionObject test;
    String test1,test2,test3;

    @Before
    public void startup() throws Exception
    {
        conn = ConnectorFactory.getConnector(ConnectionConstants.BROKER_HOSTNAME,ConnectionConstants.BROKER_PORT, ConnectionConstants.USERNAME, ConnectionConstants.PASSWORD);
        mbsc = conn.getMBeanServerConnection();
        test = new ConnectionObject(mbsc);
        test1 = "ping";
        test2 = "test";
        test3 = "object";


    }
    @Test
    public void TestSetQueryString()
    {
        test.setQueryString(test3,test1,null);
        Assert.assertEquals(test.querystring,"org.apache.qpid:type=Connection,name=ping,*");
        test.querystring = null;
        test.setQueryString(test3,null,test2);
        Assert.assertEquals(test.querystring,"org.apache.qpid:type=Connection,VirtualHost=test,*");
        test.querystring = null;
        test.setQueryString(test3,test1,test2);
        Assert.assertEquals(test.querystring,"org.apache.qpid:type=Connection,VirtualHost=test,name=ping,*");
        test.querystring = null;
        test.setQueryString(test3,null,null);
        Assert.assertEquals(test.querystring,"org.apache.qpid:type=Connection,*");



    }

    @After
    public void cleanup()
    {
        try{
            conn.getConnector().close();
        }catch(Exception ex)
        {
            ex.printStackTrace();
        }

    }
}
