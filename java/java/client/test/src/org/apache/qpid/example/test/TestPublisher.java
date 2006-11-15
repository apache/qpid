/*
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
 */
package org.apache.qpid.example.test;

import org.apache.qpid.example.publisher.FileMessageDispatcher;

import java.net.InetAddress;

import org.apache.log4j.Logger;
import org.apache.log4j.BasicConfigurator;


public class TestPublisher {

    private static final Logger _logger = Logger.getLogger(TestAMSPubSub.class);
    private static final String _defaultPayloadPath = "C:/Requirements/examplexml/test.xml";

       private static final String DEFAULT_LOG_CONFIG_FILENAME = "log4j.xml";

     /**
     * Test main for class using default of local file for message payload
     */
    public static void main(String[] args)
    {

        //switch on logging
        BasicConfigurator.configure();

        InetAddress _address;
        TestPublisher testPub = new TestPublisher();

        //publish a message
        if (args.length == 1)
        {
            testPub.publish(args[0]);
        }
        else
        {
            testPub.publish(null);
        }

        //Should be able to see message publication and receipt in logs now

        //Disconnect and end test run
        FileMessageDispatcher.cleanup();

        //and exit as we're all done
        System.exit(0);

    }

    private void publish(String payloadPath)
    {

        try
        {
            if (payloadPath == null|| payloadPath.length() == 0)
            {
                payloadPath = _defaultPayloadPath;
            }

            FileMessageDispatcher.publish(payloadPath);

        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}

