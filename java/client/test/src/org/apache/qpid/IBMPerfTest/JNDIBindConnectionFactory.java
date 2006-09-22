/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.IBMPerfTest;

import org.apache.qpid.client.AMQConnectionFactory;
import org.apache.log4j.Logger;
import org.apache.log4j.Level;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.jms.ConnectionFactory;
import java.util.Hashtable;
import java.io.File;
import java.net.MalformedURLException;

public class JNDIBindConnectionFactory
{

    public static final String CONNECTION_FACTORY_BINDING = "amq.ConnectionFactory";
    public static final String DEFAULT_PROVIDER_FILE_PATH = System.getProperty("java.io.tmpdir") + "/IBMPerfTestsJNDI";
    public static final String PROVIDER_URL = "file://" + DEFAULT_PROVIDER_FILE_PATH;
    public static final String FSCONTEXT_FACTORY = "com.sun.jndi.fscontext.RefFSContextFactory";

    public static void main(String[] args)
    {
        Logger.getRootLogger().setLevel(Level.OFF);

        String connectionFactoryBinding = CONNECTION_FACTORY_BINDING;
        String provider = PROVIDER_URL;
        String contextFactory = FSCONTEXT_FACTORY;

        if (args.length > 0)
        {
            connectionFactoryBinding = args[0];

            if (args.length > 1)
            {
                provider = args[1];

                if (args.length > 2)
                {
                    contextFactory = args[2];
                }
            }
            else
            {
                System.out.println("Using default File System Context Factory");
                System.out.println("Using default Connection Factory Binding:" + connectionFactoryBinding);
            }
        }
        else
        {
            System.out.println("Using default values: Usage:java JNDIBindConnectionFactory [<Connection Factory Binding>] [<Provider URL>] [<JNDI Context Factory>]");
        }
        System.out.println("File System Context Factory\n" +
                           "Connection Factory Binding:" + connectionFactoryBinding + "\n" +
                           "JNDI Provider URL:" + provider);

        if (provider.startsWith("file"))
        {
            File file = new File(provider.substring(provider.indexOf("://") + 3));

            if (file.exists() && !file.isDirectory())
            {
                System.out.println("Couldn't make directory file already exists");
                System.exit(1);
            }
            else
            {
                if (!file.exists())
                {
                    if (!file.mkdirs())
                    {
                        System.out.println("Couldn't make directory");
                        System.exit(1);
                    }
                }
            }
        }

        new JNDIBindConnectionFactory(provider, connectionFactoryBinding, contextFactory);

    }

    public JNDIBindConnectionFactory(String provider, String binding, String contextFactory)
    {
        // Set up the environment for creating the initial context
        Hashtable env = new Hashtable(11);
        env.put(Context.INITIAL_CONTEXT_FACTORY, contextFactory);

        env.put(Context.PROVIDER_URL, provider);

        try
        {
            // Create the initial context
            Context ctx = new InitialContext(env);

            // Create the object to be bound
            ConnectionFactory factory = null;

            try
            {
                factory = new AMQConnectionFactory("amqp://guest:guest@clientid/testpath?brokerlist='tcp://localhost:5672'");


                try
                {
                    Object obj = ctx.lookup(binding);

                    if (obj != null)
                    {
                        System.out.println("Un-binding previous Connection Factory");
                        ctx.unbind(binding);
                    }
                }
                catch (NamingException e)
                {

                }

                // Perform the bind
                ctx.bind(binding, factory);
                System.out.println("Bound Connection Factory:" + binding);

                // Check that it is bound
                Object obj = ctx.lookup(binding);
                System.out.println("Connection URL:" + ((AMQConnectionFactory) obj).getConnectionURL());

                System.out.println("JNDI FS Context:" + provider);
            }
            catch (Exception amqe)
            {

            }

        }
        catch (NamingException e)
        {
            System.out.println("Operation failed: " + e);
        }
    }
}
