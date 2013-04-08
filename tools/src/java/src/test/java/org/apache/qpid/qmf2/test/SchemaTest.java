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
 * This class retrieves schema information when it detects that Agents have been added.
 *
 * @author Fraser Adams
 */
public final class SchemaTest implements QmfEventListener
{
    private Console _console;

    public SchemaTest(String url)
    {
        try
        {
            System.out.println("*** Starting SchemaTest used to test schema retrieval ***");
                
            Connection connection = ConnectionHelper.createConnection(url, "{reconnect: true}");
            _console = new Console(this);
            _console.addConnection(connection);

        }
        catch (QmfException qmfe)
        {
            System.err.println("QmfException " + qmfe.getMessage() + ": SchemaTest failed");
            System.exit(1);
        }
    }

    public void onEvent(WorkItem wi)
    {
        if (wi instanceof AgentAddedWorkItem)
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
        }

    }

    public static void main(String[] args)
    {
        //System.out.println ("Setting log level to FATAL");
        System.setProperty("amqj.logging.level", "FATAL");

        String url = (args.length == 1) ? args[0] : "localhost";
        SchemaTest test = new SchemaTest(url);

        BufferedReader commandLine = new BufferedReader(new InputStreamReader(System.in));
        try
        { // Blocks here until return is pressed
            System.out.println("Hit Return to exit");
            String s = commandLine.readLine();
            System.exit(0);
        }
        catch (IOException e)
        {
            System.out.println ("SchemaTest main(): IOException: " + e.getMessage());
        }

        System.out.println("*** Ending SchemaTest ***");
    }
}
