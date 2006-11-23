/**
 * Class that uses an input file for message content and doesn't archive it up after passing
 * to the AMS publisher
 * Author: Marnie McCormack
 * Date: 18-Jul-2006
 * Time: 14:54:31
 * Copyright JPMorgan Chase 2006
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

