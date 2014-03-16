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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// QMF2 Imports
import org.apache.qpid.qmf2.common.ObjectId;
import org.apache.qpid.qmf2.common.QmfData;
import org.apache.qpid.qmf2.common.QmfEvent;
import org.apache.qpid.qmf2.common.QmfEventListener;
import org.apache.qpid.qmf2.common.QmfException;
import org.apache.qpid.qmf2.common.SchemaClass;
import org.apache.qpid.qmf2.common.SchemaClassId;
import org.apache.qpid.qmf2.common.WorkItem;
import org.apache.qpid.qmf2.console.Agent;
import org.apache.qpid.qmf2.console.AgentAddedWorkItem;
import org.apache.qpid.qmf2.console.Console;
import org.apache.qpid.qmf2.console.QmfConsoleData;
import org.apache.qpid.qmf2.util.ConnectionHelper;

/**
 * This class tests the case where getObjects() returns "a lot" of objects. The broker ManagementAgent actually
 * sends multiple messages in this case with the initial messages marked with a "partial" header. The Console
 * getObjects() method needs to be able to support receiving multiple response messages when the "partial"
 * header is set.
 *
 * @author Fraser Adams
 */
public final class PartialGetObjectsTest implements QmfEventListener
{
    private Console _console;

    public PartialGetObjectsTest(String url)
    {
        try
        {
            System.out.println("*** Starting PartialGetObjectsTest used to test schema retrieval ***");
                
            Connection connection = ConnectionHelper.createConnection(url, "{reconnect: true}");
            _console = new Console(this);
            _console.addConnection(connection);

            // First we create a large number of queues using the QMF2 create method on the broker object
            List<QmfConsoleData> brokers = _console.getObjects("org.apache.qpid.broker", "broker");
            if (brokers.isEmpty())
            {
                System.out.println("No broker QmfConsoleData returned");
                System.exit(1);
            }

            QmfConsoleData broker = brokers.get(0);
            QmfData arguments = new QmfData();
            arguments.setValue("type", "queue");

            for (int i = 0; i < 300; i++)
            {
                arguments.setValue("name", "test " + i);

                try
                {
                    broker.invokeMethod("create", arguments);
                }
                catch (QmfException e)
                { // This may be throw if we've already added the queues, we just catch and ignore for this test.
                    //System.out.println(e.getMessage());
                }
            }

            // After we've created lots of queues we attempt to list them. This list should include all the queues
            // we've added irrespective of the number.
            List<QmfConsoleData> queues = _console.getObjects("org.apache.qpid.broker", "queue");
            System.out.println("Call 1 Returned " + queues.size() + " objects");
            for (QmfConsoleData queue : queues)
            {
                String name = queue.getStringValue("name");
                System.out.println("Queue: " + name);
            }

            // We get the queue objects a second time. If getObjects() correctly handles partial responses then
            // this should return the complete list of queue objects, if not it will only return the subset that
            // corresponds to the additional partial responses. Not handling these messages is likely to be a very
            // bad thing as subsequent QMF requests will then start to receive the wrong responses.
            queues = _console.getObjects("org.apache.qpid.broker", "queue");
            System.out.println("Call 2 Returned " + queues.size() + " objects");
            for (QmfConsoleData queue : queues)
            {
                String name = queue.getStringValue("name");
                System.out.println("Queue: " + name);
            }

        }
        catch (QmfException qmfe)
        {
            System.err.println("QmfException " + qmfe.getMessage() + ": PartialGetObjectsTest failed");
            System.exit(1);
        }
    }

    public void onEvent(WorkItem wi)
    {
       /* if (wi instanceof AgentAddedWorkItem)
        {
            AgentAddedWorkItem item = (AgentAddedWorkItem)wi;
            Agent agent = item.getAgent();
            System.out.println("\nAgent " + agent.getName() + " added ");

            // Retrieve the List of SchemaClassIds from the Agent, these will be used to retrieve the Schema.
            List<SchemaClassId> schemaClassIds = _console.getClasses(agent);

            if (schemaClassIds.size() > 0)
            {
                // For each retrieved Class retrieve and display the Schema.
                for (SchemaClassId schemaClassId : schemaClassIds)
                {
                    List<SchemaClass> schema = _console.getSchema(schemaClassId, agent);
                    if (schema.size() == 1)
                    {
                        schema.get(0).listValues();
                    }
                }
            }
            else
            {
                System.out.println("No schema information is available for this Agent");
            }
        }*/

    }

    public static void main(String[] args)
    {
        //System.out.println ("Setting log level to FATAL");
        System.setProperty("amqj.logging.level", "FATAL");

        String url = (args.length == 1) ? args[0] : "localhost";
        PartialGetObjectsTest test = new PartialGetObjectsTest(url);

        BufferedReader commandLine = new BufferedReader(new InputStreamReader(System.in));
        try
        { // Blocks here until return is pressed
            System.out.println("Hit Return to exit");
            String s = commandLine.readLine();
            System.exit(0);
        }
        catch (IOException e)
        {
            System.out.println ("PartialGetObjectsTest main(): IOException: " + e.getMessage());
        }

        System.out.println("*** Ending PartialGetObjectsTest ***");
    }
}
