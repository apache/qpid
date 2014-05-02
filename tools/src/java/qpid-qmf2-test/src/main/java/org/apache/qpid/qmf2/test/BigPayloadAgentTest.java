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
import org.apache.qpid.qmf2.agent.Agent;
import org.apache.qpid.qmf2.agent.MethodCallParams;
import org.apache.qpid.qmf2.agent.MethodCallWorkItem;
import org.apache.qpid.qmf2.agent.QmfAgentData;
import org.apache.qpid.qmf2.common.ObjectId;
import org.apache.qpid.qmf2.common.QmfData;
import org.apache.qpid.qmf2.common.QmfEvent;
import org.apache.qpid.qmf2.common.QmfEventListener;
import org.apache.qpid.qmf2.common.QmfException;
import org.apache.qpid.qmf2.common.QmfType;
import org.apache.qpid.qmf2.common.SchemaEventClass;
import org.apache.qpid.qmf2.common.SchemaMethod;
import org.apache.qpid.qmf2.common.SchemaObjectClass;
import org.apache.qpid.qmf2.common.SchemaProperty;
import org.apache.qpid.qmf2.common.WorkItem;
import org.apache.qpid.qmf2.util.ConnectionHelper;
import static org.apache.qpid.qmf2.common.WorkItem.WorkItemType.*;

/**
 * A class used to test the Agent API functionality. This Agent specifies an explicit queue name and a larger than
 * default queue size so that it can receive large payloads on its methods.
 *
 * @author Fraser Adams
 */
public final class BigPayloadAgentTest implements QmfEventListener
{
    private Agent _agent;
    private QmfAgentData _control;
    private SchemaObjectClass _controlSchema;

    public BigPayloadAgentTest(String url)
    {
        try
        {
            System.out.println("** Starting BigPayloadAgentTest a test of basic Agent class functions **");
                
            Connection connection = ConnectionHelper.createConnection(url, "{reconnect: true}");
            _agent = new Agent(this);
            _agent.setVendor("test.com");
            _agent.setProduct("big-payload-agent");

            System.out.println("Agent name: " + _agent.getName());

            setupSchema();
            populateData();
            _agent.setConnection(connection, " ; {link: {name:'big-payload-agent', x-declare: {arguments: {'qpid.policy_type': ring, 'qpid.max_size': 500000000}}}}");

        }
        catch (QmfException qmfe)
        {
            System.err.println("QmfException " + qmfe.getMessage() + " caught: BigPayloadAgentTest failed");
        }
    }

    public void onEvent(WorkItem wi)
    {
        System.out.println("WorkItem type: " + wi.getType());

        if (wi.getType() == METHOD_CALL)
        {
            MethodCallWorkItem item = (MethodCallWorkItem)wi;
            MethodCallParams methodCallParams = item.getMethodCallParams();
            String methodName = methodCallParams.getName();
            ObjectId objectId = methodCallParams.getObjectId();

            QmfData inArgs = methodCallParams.getArgs();
            ObjectId controlAddress = _control.getObjectId();

            if (objectId.equals(controlAddress))
            {
                if (methodName.equals("processPayload"))
                {
                    System.out.println("Invoked processPayload method");

                    byte[] parameter = inArgs.getValue("parameter");
                    System.out.println("payload size = " + parameter.length);

                    QmfData outArgs = new QmfData();
                    outArgs.setValue("return", parameter);
                    _agent.methodResponse(methodName, item.getHandle(), outArgs, null);
                }
            }
        }
    }

    public void setupSchema() throws QmfException
    {
        System.out.println("*** BigPayloadAgentTest initialising the various Schema classes ***");
                
        // Create and register schema for this agent.
        String packageName = "com.test.bigagent";

        // Declare a control object to test methods against.
        _controlSchema = new SchemaObjectClass(packageName, "control");
        _controlSchema.addProperty(new SchemaProperty("name", QmfType.TYPE_STRING));
        _controlSchema.setIdNames("name");

        SchemaMethod createMethod = new SchemaMethod("processPayload", "Process a large payload");
        createMethod.addArgument(new SchemaProperty("parameter", QmfType.TYPE_STRING, "{dir:IN}"));
        createMethod.addArgument(new SchemaProperty("return", QmfType.TYPE_STRING, "{dir:OUT}"));
        _controlSchema.addMethod(createMethod);
 
        System.out.println("BigPayloadAgentTest Schema classes initialised OK");

        _agent.registerObjectClass(_controlSchema);

        System.out.println("BigPayloadAgentTest Schema classes registered OK");
    }

    public void populateData() throws QmfException
    {
        System.out.println("*** BigPayloadAgentTest creating a control object ***");

        _control = new QmfAgentData(_controlSchema);
        _control.setValue("name", "controller");
        _agent.addObject(_control);
        System.out.println("BigPayloadAgentTest Schema control object added OK");
    }


    public static void main(String[] args)
    {
        //System.out.println("Setting log level to FATAL");
        System.setProperty("amqj.logging.level", "FATAL");

        String url = (args.length == 1) ? args[0] : "localhost";
        BigPayloadAgentTest test1 = new BigPayloadAgentTest(url);

        BufferedReader commandLine = new BufferedReader(new InputStreamReader(System.in));
        try
        { // Blocks here until return is pressed
            System.out.println("Hit Return to exit");
            String s = commandLine.readLine();
            System.exit(0);
        }
        catch (IOException e)
        {
            System.out.println ("ConnectionAudit main(): IOException: " + e.getMessage());
        }

        System.out.println("*** Ending BigPayloadAgentTest ***");
    }
}
