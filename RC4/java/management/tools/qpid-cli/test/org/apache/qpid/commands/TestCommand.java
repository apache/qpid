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

import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.framework.JUnit4TestAdapter;
import org.junit.Before;
import org.junit.Test;
import org.junit.After;
import org.junit.Assert;
import org.apache.qpid.Connector;
import org.apache.qpid.ConnectorFactory;
import org.apache.qpid.ConnectionConstants;
import org.apache.qpid.utils.JMXinfo;
import org.apache.qpid.utils.CommandLineOptionParser;

import javax.management.MBeanServerConnection;

/**
 * Created by IntelliJ IDEA.
 * User: lahiru
 * Date: Jun 30, 2008
 * Time: 12:21:32 PM
 * To change this template use File | Settings | File Templates.
 */
public class TestCommand{
    String command = "list -o queue";
    String [] list;
    Connector test;
    MBeanServerConnection mbsc;
    JMXinfo info;
    CommandLineOptionParser parser;
    Command cmd;
    @Before
    public void setup()
    {
        list = command.split(" ");
        parser = new CommandLineOptionParser(list);
        test = ConnectorFactory.getConnector(ConnectionConstants.BROKER_HOSTNAME,ConnectionConstants.BROKER_PORT);
        info = new JMXinfo(test.getConnector(),parser,test.getMBeanServerConnection());
        cmd = new Command(info,"list");

    }
    @Test
    public void TestOptionChecker()
    {
        Assert.assertEquals(cmd.optionchecker("o"),"queue");
    }
    @Test
    public void TestCheckOptionSetting()
    {
         Assert.assertEquals(cmd.checkoptionsetting("o"),true);
        Assert.assertEquals(cmd.checkoptionsetting("p"),false);
    }
    @After
    public void cleanup()
    {
        parser = null;
        test = null;
        info = null;
        cmd = null;
    }
}
