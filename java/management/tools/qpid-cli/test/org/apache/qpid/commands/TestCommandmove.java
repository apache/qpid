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

import org.apache.qpid.ConnectionConstants;
import org.apache.qpid.Connector;
import org.apache.qpid.ConnectorFactory;
import org.apache.qpid.utils.CommandLineOptionParser;
import org.apache.qpid.utils.JMXinfo;
import org.junit.Before;
import org.junit.Test;
import org.junit.After;
import org.junit.Assert;

import javax.management.remote.JMXConnector;
import javax.management.MBeanServerConnection;

/**
 * Created by IntelliJ IDEA.
 * User: lahiru
 * Date: Aug 11, 2008
 * Time: 10:14:34 PM
 * To change this template use File | Settings | File Templates.
 */
public class TestCommandmove {
    JMXinfo info=null;
    String command = "move -o queue -n1 ping -v1 test -n2 message_queue -fmid 10 -tmid 12";
    Commandmove move = null;
    Connector conn=null;
    @Before
    public void startup()
    {
        conn = ConnectorFactory.getConnector(ConnectionConstants.BROKER_HOSTNAME, ConnectionConstants.BROKER_PORT);
        JMXConnector jmxc = conn.getConnector();
        MBeanServerConnection mbsc = conn.getMBeanServerConnection();
        CommandLineOptionParser parser = new CommandLineOptionParser(command.split(" "));
        info = new JMXinfo(jmxc,parser,mbsc);
        move = new Commandmove(info);



    }
    @Test
    public void TestSetQueryString()
    {
        move.execute();
        Assert.assertEquals(move.getObject(),"queue");
        Assert.assertEquals(move.getVirtualhost(),"test");
        Assert.assertEquals(move.getname1(),"ping");
        Assert.assertEquals(move.getname2(),"message_queue");
        Assert.assertEquals(move.getfmid(),10);
        Assert.assertEquals(move.gettmid(),12);
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
