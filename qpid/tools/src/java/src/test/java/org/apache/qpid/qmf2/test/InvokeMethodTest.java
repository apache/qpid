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
import org.apache.qpid.qmf2.console.MethodResult;
import org.apache.qpid.qmf2.console.QmfConsoleData;
import org.apache.qpid.qmf2.util.ConnectionHelper;

/**
 * This class tests invoking a method lots of times.
 *
 * @author Fraser Adams
 */
public final class InvokeMethodTest implements QmfEventListener
{
    private Console _console;

    public InvokeMethodTest(String url)
    {
        try
        {
            System.out.println("*** Starting InvokeMethodTest ctrl^C to exit ***");
            System.out.println("This is intended to soak test QMF2 invokeMethod, it doesn't do anything obvious");
            System.out.println("but attaching jconsole shows memory consumption. It *looks* like it's leaking");
            System.out.println("memory, but it turns out to be due to the use of a SoftReference in Qpid's");
            System.out.println("setReplyTo() method on JMSMessage. Consumption *eventually* flattens out...");
                
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

            while (true)
            {
                try
                {
                    MethodResult results = broker.invokeMethod("getLogLevel", arguments);
//System.out.println(results.getStringValue("level"));
                }
                catch (QmfException e)
                { // This may be throw if we've already added the queues, we just catch and ignore for this test.
                    //System.out.println(e.getMessage());
                }
            }
        }
        catch (QmfException qmfe)
        {
            System.err.println("QmfException " + qmfe.getMessage() + ": InvokeMethodTest failed");
            System.exit(1);
        }
    }

    public void onEvent(WorkItem wi)
    {
    }

    public static void main(String[] args)
    {
        System.setProperty("amqj.logging.level", "FATAL");

        String url = (args.length == 1) ? args[0] : "localhost";
        InvokeMethodTest test = new InvokeMethodTest(url);
    }
}
