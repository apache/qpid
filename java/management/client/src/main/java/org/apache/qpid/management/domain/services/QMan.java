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
import org.apache.qpid.management.Messages;
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
        LOGGER.info(Messages.QMAN_000001_STARTING_QMAN);
        LOGGER.info(Messages.QMAN_000002_READING_CONFIGURATION);

    	addShutDownHook();

        Configurator configurator = new Configurator();
        try
        {
            configurator.configure();            
            LOGGER.info(Messages.QMAN_000003_CREATING_MANAGEMENT_CLIENTS);
            for (Entry<UUID, BrokerConnectionData> entry : Configuration.getInstance().getConnectionInfos())
            {
                UUID brokerId = entry.getKey();
                BrokerConnectionData data = entry.getValue();
                try 
                {
                    ManagementClient client = new ManagementClient(brokerId,data);
                    managementClients.add(client);
                    client.estabilishFirstConnectionWithBroker();
                    
                    LOGGER.info(Messages.QMAN_000004_MANAGEMENT_CLIENT_CONNECTED,brokerId);
                } catch(StartupFailureException exception) {
                    LOGGER.error(exception, Messages.QMAN_100017_UNABLE_TO_CONNECT,brokerId,data);
                }
            }
            LOGGER.info(Messages.QMAN_000019_QMAN_STARTED);
       } catch(ConfigurationException exception) {
            LOGGER.error(exception,Messages.UNABLE_TO_STARTUP_CORRECTLY);
            throw new StartupFailureException(exception);
        } 
    }

    /**
     * Compose method used for adding a "graceful" shutdown hook.
     */
    private void addShutDownHook()
    {
        // SHUTDOWN HOOK
        Runtime.getRuntime().addShutdownHook(new Thread()
        {
            @Override
            public void run ()
            {
                LOGGER.info(Messages.QMAN_000020_SHUTTING_DOWN_QMAN);
                try 
                {
                    for (ManagementClient client : managementClients)
                    {   
                        client.shutdown();  
                    }
                } catch(Exception exception)
                {
                }
                LOGGER.info(Messages.QMAN_000021_SHUT_DOWN);                
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
    	
		QMan qman = new QMan();
		try 
		{
			qman.start();
		    
			// TODO : console enhancement (i.e. : connect another broker)
            System.out.println("Type \"q\" to quit.");
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            while ( !"q".equals(reader.readLine()) )
            {
            	
            }
			System.exit(-1);
		} catch (StartupFailureException exception) 
		{
			exception.printStackTrace();
			System.exit(-1);
		} catch (IOException exception)
		{
			System.exit(-1);					
		}
    }
}
