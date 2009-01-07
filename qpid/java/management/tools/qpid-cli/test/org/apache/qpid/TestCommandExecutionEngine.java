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
import org.apache.qpid.utils.JMXinfo;
import org.apache.qpid.utils.CommandLineOptionParser;
import org.junit.Before;
import org.junit.Test;
import org.junit.Assert;
import org.junit.After;

import java.io.IOException;

/**
 * Created by IntelliJ IDEA.
 * User: lahiru
 * Date: Jun 30, 2008
 * Time: 12:06:25 PM
 * To change this template use File | Settings | File Templates.
 */
public class TestCommandExecutionEngine {
    String line;
    String [] command;
    CommandLineOptionParser commandlineoptionparser;
    JMXinfo info;
    CommandExecutionEngine engine;
    Connector connector;
    @Before
    public void setup(){

        connector = ConnectorFactory.getConnector("localhost","8999");


    }
    @Test
    public void TestCommandSelector() throws Exception
    {
        line = "list -o queue";
        command = line.split(" ");
        commandlineoptionparser = new CommandLineOptionParser(command);
        info = new JMXinfo(connector.getConnector(), commandlineoptionparser,connector.getMBeanServerConnection());
        engine = new CommandExecutionEngine(info);
        Assert.assertEquals(engine.CommandSelector(),true);
    }
    @After
    public void cleanup()
    {
        try {
            connector.getConnector().close();
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }
}
