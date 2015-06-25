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
import org.apache.qpid.qmf2.common.QmfQuery;
import org.apache.qpid.qmf2.common.QmfQueryTarget;
import org.apache.qpid.qmf2.common.WorkItem;
import org.apache.qpid.qmf2.console.Agent;
import org.apache.qpid.qmf2.console.AgentAddedWorkItem;
import org.apache.qpid.qmf2.console.AgentHeartbeatWorkItem;
import org.apache.qpid.qmf2.console.Console;
import org.apache.qpid.qmf2.console.EventReceivedWorkItem;
import org.apache.qpid.qmf2.console.MethodResult;
import org.apache.qpid.qmf2.console.MethodResponseWorkItem;
import org.apache.qpid.qmf2.console.ObjectUpdateWorkItem;
import org.apache.qpid.qmf2.console.QmfConsoleData;
import org.apache.qpid.qmf2.console.SubscribeIndication;
import org.apache.qpid.qmf2.console.SubscribeParams;
import org.apache.qpid.qmf2.console.SubscribeResponseWorkItem;
import org.apache.qpid.qmf2.console.SubscriptionIndicationWorkItem;
import org.apache.qpid.qmf2.util.ConnectionHelper;
import static org.apache.qpid.qmf2.common.WorkItem.WorkItemType.*;

/**
 * This class provides a test of broker subscription behaviour.
 * <p>
 * N.B. That the 0.12 C++ broker does not *actually* support subscriptions however it does periodically "push" 
 * QmfConsoleData Object updates as _data indications. The Console class uses these to provide client side
 * emulation of broker subscriptions. One slightly subtle thing to bear in mind however is that the broker
 * ManagementAgent separates "properties" and "statistics" so for example a subscription query on say a queue's
 * name property will only evaluate true for a properties push and not a statistics push. The most useful usage
 * pattern is likely to be a getObjects() call to return all queues, followed by a search for the queue of interest
 * then a subscription with a query using the ObjectId of the wanted queue.
 *
 * @author Fraser Adams
 */
public final class BrokerSubscriptionTestConsole implements QmfEventListener
{
    private Console _console;
    private Agent _broker;
    private ObjectId _objectId; // Used to test ObjectId Query

    public BrokerSubscriptionTestConsole(String url)
    {
        try
        {
            System.out.println("** Starting BrokerSubscriptionTestConsole used to test subscription behaviour **");
                
            Connection connection = ConnectionHelper.createConnection(url, "{reconnect: true}");
            _console = new Console(this);
            _console.addConnection(connection);

            // Wait until the broker Agent has been discovered
            _broker = _console.findAgent("broker");
            if (_broker == null)
            {
                System.out.println("broker Agent not found");
                System.exit(1);
            }

            System.out.println("Creating Query for objects whose name property has a value that starts with 'a'");

            SubscribeParams params;
            QmfQuery query = new QmfQuery(QmfQueryTarget.OBJECT, "['re_match', 'name', ['quote', '^a']]");

            // Create a subscription, response returned synchronously
            params = _console.createSubscription(_broker, query, "consoleHandle1", "{publishInterval:5}");
            System.out.println("duration = " + params.getLifetime());
            System.out.println("interval = " + params.getPublishInterval());
            System.out.println("subscriptionId = " + params.getSubscriptionId());
            System.out.println("consoleHandle = " + params.getConsoleHandle());

            // Sleep a while, getting query result as they become available
            try
            {
                Thread.sleep(20000);
            }
            catch (InterruptedException ie)
            {
            }

            // Refresh the subscription getting results asynchronously, just for variety
            System.out.println("Calling refreshSubscription on " + params.getSubscriptionId());
            _console.refreshSubscription(params.getSubscriptionId(), "{replyHandle:ignoredReplyHandle}");


            // Create a subscription for _class_name = queue
            System.out.println("Creating Query for all queue objects");
            query = new QmfQuery(QmfQueryTarget.OBJECT, "['eq', '_class_name', ['quote', 'queue']]");
            params = _console.createSubscription(_broker, query, "queues");

            while (_objectId == null)
            {
                System.out.println("Waiting for ObjectId to be set");
                try
                {
                    Thread.sleep(1000);
                }
                catch (InterruptedException ie)
                {
                }
            }

            // Cancel the query for all queue objects
            System.out.println("Cancelling Query for all queue objects");
            _console.cancelSubscription(params.getSubscriptionId());

            // Create a subscription for _object_id
            System.out.println("Creating Query for _object_id = " + _objectId);
            query = new QmfQuery(QmfQueryTarget.OBJECT, _objectId);
            params = _console.createSubscription(_broker, query, "queues");

        }
        catch (QmfException qmfe)
        {
            System.err.println("QmfException " + qmfe.getMessage() + ": BrokerSubscriptionTestConsole failed");
            System.exit(1);
        }
    }

    public void onEvent(WorkItem wi)
    {
        System.out.println("WorkItem type: " + wi.getType());

        if (wi.getType() == AGENT_HEARTBEAT)
        {
            AgentHeartbeatWorkItem item = (AgentHeartbeatWorkItem)wi;
            Agent agent = item.getAgent();
            System.out.println(agent.getName());
        }

        if (wi.getType() == EVENT_RECEIVED)
        {
            EventReceivedWorkItem item = (EventReceivedWorkItem)wi;
            Agent agent = item.getAgent();
            QmfEvent event = item.getEvent();

            String className = event.getSchemaClassId().getClassName();
            System.out.println("Event: " + className);
//event.listValues();    
        }

        if (wi.getType() == METHOD_RESPONSE)
        {
            MethodResponseWorkItem item = (MethodResponseWorkItem)wi;
            MethodResult result = item.getMethodResult();
            String correlationId = item.getHandle().getCorrelationId();
            System.out.println("correlationId = " + correlationId);
            System.out.println(result.getStringValue("message"));
        }

        if (wi.getType() == OBJECT_UPDATE)
        {
            ObjectUpdateWorkItem item = (ObjectUpdateWorkItem)wi;
            QmfConsoleData object = item.getQmfConsoleData();
            ObjectId objectId = object.getObjectId();
            String correlationId = item.getHandle().getCorrelationId();
            System.out.println("correlationId = " + correlationId);
            System.out.println("objectId = " + objectId);
            System.out.println("MethodCount = " + object.getLongValue("methodCount"));
        }

        if (wi.getType() == SUBSCRIBE_RESPONSE)
        {
            SubscribeResponseWorkItem item = (SubscribeResponseWorkItem)wi;
            SubscribeParams params = item.getSubscribeParams();
            System.out.println("duration = " + params.getLifetime());
            System.out.println("interval = " + params.getPublishInterval());
            System.out.println("subscriptionId = " + params.getSubscriptionId());
            System.out.println("consoleHandle = " + params.getConsoleHandle());
            String correlationId = item.getHandle().getCorrelationId();
            System.out.println("correlationId = " + correlationId);
        }

        if (wi.getType() == SUBSCRIPTION_INDICATION)
        {
            SubscriptionIndicationWorkItem item = (SubscriptionIndicationWorkItem)wi;
            SubscribeIndication indication = item.getSubscribeIndication();
            String correlationId = indication.getConsoleHandle();
            System.out.println("correlationId = " + correlationId);

            List<QmfConsoleData> objects = indication.getData();
            for (QmfConsoleData object : objects)
            {
                if (object.isDeleted())
                {
                    System.out.println("object has been deleted");
                }
                String className = object.getSchemaClassId().getClassName();
                System.out.println("object class = " + className);
                if (className.equals("queue") || className.equals("exchange"))
                {
                    if (object.hasValue("name"))
                    {
                        System.out.println("property update, name = " + object.getStringValue("name"));
                    }
                    else
                    {
                        _objectId = object.getObjectId();
                        System.out.println("statistic update, oid = " + _objectId); 
                    }
                }
            }
        }
    }

    public static void main(String[] args)
    {
        //System.out.println ("Setting log level to FATAL");
        System.setProperty("amqj.logging.level", "FATAL");

        String url = (args.length == 1) ? args[0] : "localhost";
        BrokerSubscriptionTestConsole test = new BrokerSubscriptionTestConsole(url);

        BufferedReader commandLine = new BufferedReader(new InputStreamReader(System.in));
        // Blocks here until return is pressed
        try
        { // Blocks here until return is pressed
            System.out.println("Hit Return to exit");
            String s = commandLine.readLine();
            System.exit(0);
        }
        catch (IOException e)
        {
            System.out.println ("BrokerSubscriptionTestConsole main(): IOException: " + e.getMessage());
        }

        System.out.println("*** Ending BrokerSubscriptionTestConsole ***");
    }
}
