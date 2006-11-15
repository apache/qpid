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
import org.apache.qpid.example.shared.Statics;

import java.util.Properties;

import org.apache.log4j.Logger;
import org.apache.log4j.BasicConfigurator;


public class TestSubscriber {

    private static final Logger _logger = Logger.getLogger(TestSubscriber.class);
    private static final String _defaultPayloadPath = "C:/Requirements/examplexml/test.xml";

    private static Subscriber subscriber;

    private static final String DEFAULT_LOG_CONFIG_FILENAME = "log4j.xml";

     /**
     * Test main for class using default of local file for message payload
     */
    public static void main(String[] args)
    {

        //switch on logging
        BasicConfigurator.configure();

        TestSubscriber testSub = new TestSubscriber();

        //create publisher and subscriber
        subscriber = new Subscriber();

        //subscribe to the topic
        testSub.subscribe(args);

        //and exit as we're all done
        //System.exit(0);

    }

    private void subscribe(String[] args)
    {
        Properties props = System.getProperties();
        subscriber.subscribe(props.getProperty(Statics.HOST_PROPERTY),
                                props.getProperty(Statics.USER_PROPERTY), props.getProperty(Statics.PWD_PROPERTY),
                                props.getProperty(Statics.VIRTUAL_PATH_PROPERTY), props.getProperty(Statics.QUEUE_PROPERTY));
    }

}

