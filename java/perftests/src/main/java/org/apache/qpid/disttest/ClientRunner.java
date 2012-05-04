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
 *
 */
package org.apache.qpid.disttest;

import javax.naming.Context;
import javax.naming.NamingException;

import org.apache.qpid.disttest.client.Client;
import org.apache.qpid.disttest.jms.ClientJmsDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientRunner extends AbstractRunner
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientRunner.class);

    public static final String NUM_OF_CLIENTS_PROP = "number-of-clients";

    public static final String NUM_OF_CLIENTS_DEFAULT = "1";

    public ClientRunner()
    {
        getCliOptions().put(NUM_OF_CLIENTS_PROP, NUM_OF_CLIENTS_DEFAULT);
    }

    public static void main(String[] args)
    {
        ClientRunner runner = new ClientRunner();
        runner.parseArgumentsIntoConfig(args);
        runner.runClients();
    }

    public void setJndiPropertiesFileLocation(String jndiConfigFileLocation)
    {
        getCliOptions().put(JNDI_CONFIG_PROP, jndiConfigFileLocation);
    }

    /**
     * Run the clients in the background
     */
    public void runClients()
    {
        int numClients = Integer.parseInt(getCliOptions().get(NUM_OF_CLIENTS_PROP));

        Context context = getContext();

        for(int i = 1; i <= numClients; i++)
        {
            createBackgroundClient(context);
        }
    }

    private void createBackgroundClient(Context context)
    {
        try
        {
            final Client client = new Client(new ClientJmsDelegate(context));

            final Thread clientThread = new Thread(new Runnable()
            {
                @Override
                public void run()
                {
                    LOGGER.info("Starting client " + client.getClientName());
                    client.start();
                    client.waitUntilStopped();
                    LOGGER.info("Stopped client " + client.getClientName());
                }
            });
            clientThread.start();
        }
        catch (NamingException e)
        {
            throw new DistributedTestException("Exception while creating client instance", e);
        }
    }
}
