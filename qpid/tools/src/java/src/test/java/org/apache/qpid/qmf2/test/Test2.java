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
import org.apache.qpid.qmf2.common.QmfQuery;
import org.apache.qpid.qmf2.common.QmfQueryTarget;
import org.apache.qpid.qmf2.common.WorkItem;
import org.apache.qpid.qmf2.console.Agent;
import org.apache.qpid.qmf2.console.Console;
import org.apache.qpid.qmf2.console.QmfConsoleData;
import org.apache.qpid.qmf2.util.ConnectionHelper;
import static org.apache.qpid.qmf2.common.WorkItem.WorkItemType.*;

/**
 * A class used to test a non-blocking query for all Agents using the Notifier/WorkItem Event API. As most of
 * the tests and samples use the alternative QmfEventListener API (because the author prefers that) rather than
 * the official QMF2 WorkQueue API it seems sensible to include at least one test illustrating that.
 *
 * Note that in practice the WorkQueue API can also be used without an explicit notifier as one of the
 * overloaded Console getNextWorkitem() methods is a blocking call to the WorkQueue that blocks until a WorkItem
 * is available. The approach used in this class is for API illustration purposes and the author is unlikely to
 * use it for anything useful.
 *
 * @author Fraser Adams
 */

public final class Test2
{
    private Console _console;

    public Test2(String url)
    {
        try
        {
            System.out.println("*** Starting Test2 asynchronous Agent discovery using WorkQueue API ***");
                
            BlockingNotifier notifier = new BlockingNotifier();

            Connection connection = ConnectionHelper.createConnection(url, "{reconnect: true}");
            _console = new Console(notifier);
//console.disableAgentDiscovery(); // To miss all notifications this needs done before addConnection()
            _console.addConnection(connection);

            int count = 0;
            while (true)
            {
                notifier.waitForWorkItem();
                System.out.println("WorkItem available, WorkItem count = " + _console.getWorkitemCount());

                WorkItem wi;
                while ((wi = _console.getNextWorkitem(0)) != null)
                {
                    System.out.println("WorkItem type: " + wi.getType());
                    if (wi.getType() == AGENT_HEARTBEAT || wi.getType() == AGENT_ADDED || 
                        wi.getType() == AGENT_DELETED || wi.getType() == AGENT_RESTARTED || 
                        wi.getType() == EVENT_RECEIVED)
                    {
                        Map<String, Object> p = wi.<Map<String, Object>>getParams();
                        Agent agent = (Agent)p.get("agent");
                        System.out.println(agent.getName());
                    }
                }

                count++;
                if (count == 10)
                {
                    System.out.println("Applying Agent Discovery Query ['eq', '_product', ['quote', 'gizmo']]");
                    System.out.println("This should disable broker Agent events but allow gizmo Agent events");
                    System.out.println("Run AgentTest to prove this");
                    QmfQuery query = new QmfQuery(QmfQueryTarget.OBJECT, "['eq', '_product', ['quote', 'gizmo']]");
                    _console.enableAgentDiscovery(query);
                }
            }
        }
        catch (QmfException qmfe)
        {
            System.err.println("QmfException " + qmfe.getMessage() + " caught: Test2 failed");
        }
    }

    public static void main(String[] args)
    {
        //System.out.println ("Setting log level to FATAL");
        System.setProperty("amqj.logging.level", "FATAL");

        String url = (args.length == 1) ? args[0] : "localhost";
        Test2 test2 = new Test2(url);
    }
}
