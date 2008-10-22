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
package org.apache.qpid.management.domain.services;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Map.Entry;

import org.apache.log4j.xml.DOMConfigurator;
import org.apache.qpid.management.configuration.BrokerConnectionData;
import org.apache.qpid.management.configuration.Configuration;
import org.apache.qpid.management.configuration.ConfigurationException;
import org.apache.qpid.management.configuration.Configurator;
import org.apache.qpid.transport.util.Logger;

/**
 * Main entry point for starting Q-Man application.
 * 
 * @author Andrea Gazzarini
 */
public class QMan
{
    private final static Logger LOGGER = Logger.get(QMan.class);
    private final List<ManagementClient> managementClients = new ArrayList<ManagementClient>();

    /**
     * Starts QMan.
     * @throws StartupFailureException when it's not possible to proceed with startup.
     */
    void start() throws StartupFailureException
    {
        LOGGER.info("<QMAN-000001> : Starting Q-Man...");
        LOGGER.info("<QMAN-000002> : Reading Q-Man configuration...");

    	addShutDownHook();

        Configurator configurator = new Configurator();
        try
        {
            configurator.configure();            
            LOGGER.info("<QMAN-000003> : Creating management client(s)...");
            for (Entry<UUID, BrokerConnectionData> entry : Configuration.getInstance().getConnectionInfos())
            {
                UUID brokerId = entry.getKey();
                BrokerConnectionData data = entry.getValue();
                try 
                {
                    ManagementClient client = new ManagementClient(brokerId,data);
                    managementClients.add(client);
                    client.estabilishFirstConnectionWithBroker();
                    
                    LOGGER.info("<QMAN-000004> : Management client for broker %s successfully connected.",brokerId);
                } catch(StartupFailureException exception) {
                    LOGGER.error(exception, "<QMAN-100001>: Cannot connect to broker %s on %s",brokerId,data);
                }
            }
            LOGGER.info("<QMAN-000004> : Q-Man open for e-business.");

            // TODO : console enhancement (i.e. : connect another broker)
            System.out.println("Type \"q\" to quit.");
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            while ( !"q".equals(reader.readLine()) ){
            }
       } catch(ConfigurationException exception) {
            LOGGER.error(
                    exception, 
                    "<QMAN-100002> : Q-Man was unable to startup correctly : a configuration error occurred.");
            System.exit(1);
        } 
       catch(IOException exception) 
        {
        	throw new StartupFailureException(exception);
        }
    }

    /**
     * Compose method used for adding a "graceful" shutdown hook.
     */
    private void addShutDownHook()
    {
        // SHUTDOWN HOOK
        Runtime.getRuntime().addShutdownHook(new Thread(){
            @Override
            public void run ()
            {
                LOGGER.info("<QMAN-000006> : Shutting down Q-Man...");
                try 
                {
                    for (ManagementClient client : managementClients)
                    {   
                        client.shutdown();  
                    }
                } catch(Exception exception)
                {
                    
                }
                LOGGER.info("<QMAN-000007> : Q-Man shut down.");                
            }
        });    	
    }
    
    /**
     * Main method used for starting Q-Man.
     * 
     * @param args the command line arguments.
     */
    public static void main (String[] args)
    {  
    	if (args.length == 1) 
    	{
    		String logFileName = args[0];
    		DOMConfigurator.configureAndWatch(logFileName,5000);
    	}
    	
    	new Thread()
    	{
    		public void run()
    		{
        		QMan qman = new QMan();
    			try 
    			{
					qman.start();
				} catch (StartupFailureException exception) {
					exception.printStackTrace();
				}    			
    		}
    	}.start();
    }
}
