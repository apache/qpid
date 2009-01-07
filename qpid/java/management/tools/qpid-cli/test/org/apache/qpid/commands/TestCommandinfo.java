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
package org.apache.qpid.commands;

import org.apache.qpid.utils.JMXinfo;
import org.apache.qpid.utils.CommandLineOptionParser;
import org.apache.qpid.Connector;
import org.apache.qpid.ConnectorFactory;
import org.apache.qpid.ConnectionConstants;
import org.junit.Before;
import org.junit.Test;
import org.junit.Assert;
import org.junit.After;

import javax.management.remote.JMXConnector;
import javax.management.MBeanServerConnection;

/**
 * Created by IntelliJ IDEA.
 * User: lahiru
 * Date: Aug 16, 2008
 * Time: 6:30:24 PM
 * To change this template use File | Settings | File Templates.
 */
public class TestCommandinfo {
      JMXinfo info=null;
    String command = "info -o queue -n ping -v test";
    CommandImpl infocommand = null;
    Connector conn = null;
    @Before
    public void startup() throws Exception
    {
        conn = ConnectorFactory.getConnector(ConnectionConstants.BROKER_HOSTNAME,ConnectionConstants.BROKER_PORT, ConnectionConstants.USERNAME, ConnectionConstants.PASSWORD);
        JMXConnector jmxc = conn.getConnector();
        MBeanServerConnection mbsc = conn.getMBeanServerConnection();
        CommandLineOptionParser parser = new CommandLineOptionParser(command.split(" "));
        info = new JMXinfo(jmxc,parser,mbsc);
        infocommand = new Commandinfo(info);

    }
    @Test
    public void TestSetQueryString()
    {
        infocommand.execute();
        Assert.assertEquals(infocommand.getObject(),"queue");
        Assert.assertEquals(infocommand.getVirtualhost(),"test");
        Assert.assertEquals(infocommand.getName(),"ping");
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
