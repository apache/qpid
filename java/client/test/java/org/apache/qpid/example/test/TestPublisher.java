/**
 * Class that uses an input file for message content to publish and doesn't archive it
 * Author: Marnie McCormack
 * Date: 18-Jul-2006
 * Time: 14:54:31
 * Copyright JPMorgan Chase 2006
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

