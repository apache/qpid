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
package org.apache.qpid.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;

import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Logger;
import org.apache.log4j.SimpleLayout;
import org.apache.qpid.server.store.DerbyMessageStore;
import org.apache.qpid.test.utils.QpidTestCase;

public class AlertingTest extends QpidTestCase
{
    private String VIRTUALHOST = "test";
    private Session _session;
    private Connection _connection;
    private Queue _destination;
    private MessageConsumer _consumer; // Never read, but does need to be here to create the destination.
    private File _logfile;
    private XMLConfiguration _configuration;
    private int _numMessages;
    
    public void setUp() throws Exception
    {
        // First we munge the config file and, if we're in a VM, set up an additional logfile
        
        _configuration = new XMLConfiguration(_configFile); 
        _configuration.setProperty("management.enabled", "false");
        Class storeClass = DerbyMessageStore.class;
        try {
            Class bdb = Class.forName("org.apache.qpid.store.berkleydb.BDBMessageStore");
        }
        catch (ClassNotFoundException e)
        {
            // No BDB store, we'll use Derby instead. 
        }
        
        _configuration.setProperty("virtualhosts.virtualhost." + VIRTUALHOST + ".store.class", storeClass.getName());
        _numMessages = 5000;
        
        File tmpFile = File.createTempFile("configFile", "test");
        tmpFile.deleteOnExit();
        _configuration.save(tmpFile);
        _configFile = tmpFile;
        

        if (_outputFile != null)  
        {
            _logfile = _outputFile;
        } 
        else 
        {
            // This is mostly for running the test outside of the ant setup
            _logfile = File.createTempFile("logFile", "test"); 
            FileAppender appender = new FileAppender(new SimpleLayout(), _logfile.getAbsolutePath());
            appender.setFile(_logfile.getAbsolutePath());
            appender.setImmediateFlush(true);
            Logger.getRootLogger().addAppender(appender);
            //_logfile.deleteOnExit();
        }

        // Then we do the normal setup stuff like starting the broker, getting a connection etc.
        
        super.setUp();
        
        _connection = getConnection();
        _session = _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        _destination = _session.createQueue("testQueue");
        
        // Consumer is only used to actually create the destination
        _consumer = _session.createConsumer(_destination);
    }

    private boolean wasAlertFired() throws Exception
    {
        // Loop throught alerts until we're done or 5 seconds have passed, 
        // just in case the logfile takes a while to flush. 
        BufferedReader reader = new BufferedReader(new FileReader(_logfile));
        boolean found = false;
        int lineCount = 0;
        long endtime = System.currentTimeMillis()+5000; 
        while (!found && System.currentTimeMillis() < endtime)
        {
            while (reader.ready())
            {
                String line = reader.readLine();
                lineCount++;
                if (line.contains("MESSAGE_COUNT_ALERT"))
                {
                    found = true;
                }
            }
        }
        return found;
    }
    
    public void testAlertingReallyWorks() throws Exception
    {
        // Send 5 messages, make sure that the alert was fired properly. 
        sendMessage(_session, _destination, _numMessages + 1);
        boolean found = wasAlertFired();
        assertTrue("no alert generated in "+_logfile.getAbsolutePath(), found);
    }

    public void testAlertingReallyWorksWithRestart() throws Exception
    {
        sendMessage(_session, _destination, _numMessages + 1);
        stopBroker();
        (new FileOutputStream(_logfile)).getChannel().truncate(0);
        startBroker();
        boolean found = wasAlertFired();
        assertTrue("no alert generated in "+_logfile.getAbsolutePath(), found);
    }
    
    public void testAlertingReallyWorksWithChanges() throws Exception
    {
        // send some messages and nuke the logs
        sendMessage(_session, _destination, 2);
        stopBroker();
        (new FileOutputStream(_logfile)).getChannel().truncate(0);
        
        // Change max message count to 5, start broker and make sure that that's triggered at the right time
        _configuration.setProperty("virtualhosts.virtualhost." + VIRTUALHOST + ".queues.maximumMessageCount", 5);
        startBroker();
        _connection = getConnection();
        _session = _connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        
        // Trigger the new value
        sendMessage(_session, _destination, 3);
        boolean found = wasAlertFired();
        assertTrue("no alert generated in "+_logfile.getAbsolutePath(), found);
    }
}
