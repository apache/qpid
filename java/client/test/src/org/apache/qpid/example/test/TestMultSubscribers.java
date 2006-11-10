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

import org.apache.qpid.example.subscriber.Subscriber;
import org.apache.qpid.example.publisher.FileMessageDispatcher;
import org.apache.qpid.example.shared.Statics;

import java.net.InetAddress;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.apache.log4j.BasicConfigurator;


public class TestMultSubscribers {

    private static final Logger _logger = Logger.getLogger(TestMultSubscribers.class);
    private static final String _defaultPayloadPath = "C:/Requirements/examplexml/test.xml";

    private static Subscriber subscriber1;
    private static Subscriber subscriber2;

    private static final String DEFAULT_LOG_CONFIG_FILENAME = "log4j.xml";

     /**
     * Test main for class using default of local file for message payload
     */
    public static void main(String[] args)
    {

        //switch on logging
        BasicConfigurator.configure();

        InetAddress _address;
        TestMultSubscribers testMultSub = new TestMultSubscribers();

        //create publisher and subscriber
        subscriber1 = new Subscriber();
        subscriber2 = new Subscriber();

        //subscribe to the topic
        testMultSub.subscribe(args);

        //publish a message
        if (args.length == 1)
        {
            testMultSub.publish(args[0]);
        }
        else
        {
            testMultSub.publish(null);
        }

        //Should be able to see message publication and receipt in logs now

        //Disconnect and end test run
        FileMessageDispatcher.cleanup();

        //and exit as we're all done
        System.exit(0);

    }

    /*
    * Point both of our subscribers at one queue
    */
    private void subscribe(String[] args)
    {
        Properties props = System.getProperties();
        subscriber1.subscribe(props.getProperty(Statics.HOST_PROPERTY),
                                props.getProperty(Statics.USER_PROPERTY), props.getProperty(Statics.PWD_PROPERTY),
                                props.getProperty(Statics.VIRTUAL_PATH_PROPERTY),props.getProperty(Statics.QUEUE_PROPERTY));
        subscriber2.subscribe(props.getProperty(Statics.HOST_PROPERTY), 
                                props.getProperty(Statics.USER_PROPERTY), props.getProperty(Statics.PWD_PROPERTY),
                                props.getProperty(Statics.VIRTUAL_PATH_PROPERTY),props.getProperty(Statics.QUEUE_PROPERTY));

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

