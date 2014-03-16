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
package org.apache.qpid.qmf2.test;

import javax.jms.Connection;

// Misc Imports
import java.io.*;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

// QMF2 Imports
import org.apache.qpid.qmf2.common.BlockingNotifier;
import org.apache.qpid.qmf2.common.Notifier;
import org.apache.qpid.qmf2.common.QmfEventListener;
import org.apache.qpid.qmf2.common.QmfException;
import org.apache.qpid.qmf2.common.WorkItem;
import org.apache.qpid.qmf2.console.Agent;
import org.apache.qpid.qmf2.console.Console;
import org.apache.qpid.qmf2.console.QmfConsoleData;
import org.apache.qpid.qmf2.util.ConnectionHelper;

/**
 * A class used to test the basic Console Agent discovery behaviour
 *
 * @author Fraser Adams
 */

public final class Test1
{
    private Console _console;

    public Test1(String url)
    {
        try
        {
            System.out.println("*** Starting Test1 synchronous Agent discovery ***");
            Connection connection = ConnectionHelper.createConnection(url, "{reconnect: true}");
            _console = new Console();
            _console.addConnection(connection);

            System.out.println("*** Test1 testing _console.getAgents(): ***");
            List<Agent> agents = _console.getAgents();

            if (agents.size() == 0)
            {
                System.out.println("*** Test1 failed, _console.getAgents() should return at least Broker Agent ***");
                System.exit(1);
            }

            for (Agent agent : agents)
            {
                agent.listValues();
            }

            System.out.println("*** Test1 testing _console.getAgent(\"broker\"): ***");
            Agent agent = _console.getAgent("broker");
            agent.listValues();

            System.out.println("*** Test1 testing _console.findAgent(\"broker\"): ***");
            agent = _console.findAgent("broker");
            if (agent == null)
            {
                System.out.println("*** Test1 _console.findAgent(\"broker\") returned null : Test1 failed ***");
                System.exit(1);
            }
            else
            {
                agent.listValues();
            }


            System.out.println("*** Test1 testing _console.findAgent(\"monkey\"): ***");
            agent = _console.findAgent("monkey");
            if (agent == null)
            {
                System.out.println("*** Test1 _console.findAgent(\"monkey\") correctly returned null ***");
            }
            else
            {
                agent.listValues();
            }


            System.out.println("*** Test1 testing _console.getObjects(\"broker\"): ***");
            List<QmfConsoleData> brokers = _console.getObjects("broker");
            if (brokers.size() == 0)
            {
                System.out.println("*** Test1 _console.getObjects(\"broker\") should return at least one object : Test1 failed ***");
                System.exit(1);
            }
            else
            {
                for (QmfConsoleData broker : brokers)
                {
                    broker.listValues();
                }
            }

            System.out.println("*** Ending Test1 ***");
        }
        catch (QmfException qmfe)
        {
            System.err.println("QmfException " + qmfe.getMessage() + " caught: Test1 failed");
        }
    }

    public static void main(String[] args)
    {
        //System.out.println ("Setting log level to FATAL");
        System.setProperty("amqj.logging.level", "FATAL");

        // As of Qpid 0.16 the Session Dispatcher Thread is non-Daemon so the JVM gets prevented from exiting.
        // Setting the following property to true makes it a Daemon Thread.
        System.setProperty("qpid.jms.daemon.dispatcher", "true");

        String url = (args.length == 1) ? args[0] : "localhost";
        Test1 test1 = new Test1(url);
    }
}
