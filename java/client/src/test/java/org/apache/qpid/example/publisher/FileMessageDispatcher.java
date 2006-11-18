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
package org.apache.qpid.example.publisher;

import org.apache.log4j.Logger;
import java.util.Properties;
import java.io.File;

import org.apache.qpid.example.shared.FileUtils;
import org.apache.qpid.example.shared.Statics;

import javax.jms.JMSException;

/**
 * Class that sends message files to the Publisher to distribute
 * using files as input
 * Must set system properties for host etc or amend and use config props
 * Author: Marnie McCormack
 * Date: 20-Jul-2006
 * Time: 09:56:56
 * Copyright JPMorgan Chase 2006
 */
public class FileMessageDispatcher {

    private static final Logger _logger = Logger.getLogger(FileMessageDispatcher.class);

    private static Publisher _publisher = null;

    private static final String DEFAULT_PUB_NAME = "Publisher";

    public static void main(String[] args)
    {

        //Check command line args ok - must provide a path or file for us to run
        if (args.length == 0)
        {
            System.err.println("Usage: FileMessageDispatcher <filesToDispatch>" + "");
        }
        else
        {
            try
            {
                //publish message(s) from file(s) and send message to monitor queue
                publish(args[0]);

                //Move payload file(s) to archive location as no error
                FileUtils.moveFileToNewDir(args[0], System.getProperties().getProperty(Statics.ARCHIVE_PATH));
            }
            catch(Exception e)
            {
                System.err.println("Error trying to dispatch message: " + e);
                System.exit(1);
            }
            finally
            {
                //clean up before exiting
                if (getPublisher() != null)
                {
                    getPublisher().cleanup();
                }
            }
        }

        if (_logger.isDebugEnabled())
        {
            _logger.debug("Finished dispatching message");
        }

        System.exit(0);
    }


    //Publish files or file as message
    public static void publish(String path) throws JMSException, MessageFactoryException
    {
        File tempFile = new File(path);
        if (tempFile.isDirectory())
        {
            //while more files in dir publish them
            File[] files = tempFile.listFiles();

            if (files == null || files.length == 0)
            {
                _logger.info("FileMessageDispatcher - No files to publish in input directory: " + tempFile);
            }
            else
            {
                for (File file : files)
                {
                    //Create message factory passing in payload path
                    MessageFactory factory = new MessageFactory(getPublisher().getSession(), file.toString());

                    //Send the message generated from the payload using the _publisher
                    getPublisher().sendMessage(factory.createEventMessage());

                }
            }
        }
        else
        {
            //handle as single file
            //Create message factory passing in payload path
            MessageFactory factory = new MessageFactory(getPublisher().getSession(),tempFile.toString());

            //Send the message generated from the payload using the _publisher
            getPublisher().sendMessage(factory.createEventMessage());
        }
    }

    //cleanup publishers
    public static void cleanup()
    {
        if (getPublisher() != null)
        {
            getPublisher().cleanup();
        }
    }

    /*
     * Returns a _publisher for a queue
     * Using system properties to get connection info for now
     * Must set using -D the host, client, queue, user, pwd, virtual path, archive path
     */
    private static Publisher getPublisher()
    {
       if (_publisher != null)
       {
           return _publisher;
       }

       //Create _publisher using system properties
       Properties props = System.getProperties();

       //Create a _publisher using failover details
       _publisher = new Publisher(props.getProperty(Statics.HOST_PROPERTY),
                               props.getProperty(Statics.CLIENT_PROPERTY), props.getProperty(Statics.QUEUE_PROPERTY),
                               props.getProperty(Statics.USER_PROPERTY), props.getProperty(Statics.PWD_PROPERTY),
                               props.getProperty(Statics.VIRTUAL_PATH_PROPERTY), props.getProperty(Statics.ARCHIVE_PATH));

       _publisher.setName(DEFAULT_PUB_NAME);
       return _publisher;
    }

}
