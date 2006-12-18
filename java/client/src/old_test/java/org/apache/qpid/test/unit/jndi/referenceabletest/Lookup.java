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
package org.apache.qpid.test.unit.jndi.referenceabletest;

import org.apache.qpid.client.AMQConnection;
import org.apache.qpid.client.AMQConnectionFactory;
import org.apache.qpid.client.AMQTopic;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.jms.JMSException;
import java.io.File;
import java.util.Hashtable;


/**
 * Usage: To run these you need to have the sun JNDI SPI for the FileSystem.
 * This can be downloaded from sun here:
 * http://java.sun.com/products/jndi/downloads/index.html
 * Click : Download JNDI 1.2.1 & More button
 * Download: File System Service Provider, 1.2 Beta 3
 * and add the two jars in the lib dir to your class path.
 * <p/>
 * Also you need to create the directory /temp/qpid-jndi-test
 */
class Lookup
{
    public static final String DEFAULT_PROVIDER_FILE_PATH = System.getProperty("java.io.tmpdir") + "/JNDITest";
    public static final String DEFAULT_PROVIDER_URL = "file://" + DEFAULT_PROVIDER_FILE_PATH;
    public String PROVIDER_URL = DEFAULT_PROVIDER_URL;

    AMQTopic _topic = null;
    AMQConnection _connection = null;
    AMQConnectionFactory _connectionFactory = null;
    private String _connectionURL;


    public Lookup()
    {
        this(DEFAULT_PROVIDER_URL);
    }

    public Lookup(String providerURL)
    {

        PROVIDER_URL = providerURL;

        // Set up the environment for creating the initial context
        Hashtable env = new Hashtable(11);
        env.put(Context.INITIAL_CONTEXT_FACTORY,
                "com.sun.jndi.fscontext.RefFSContextFactory");

        env.put(Context.PROVIDER_URL, PROVIDER_URL);

        File file = new File(PROVIDER_URL.substring(PROVIDER_URL.indexOf("://") + 3));

        if (file.exists() && !file.isDirectory())
        {
            System.out.println("Couldn't make directory file already exists");
            return;
        }
        else
        {
            if (!file.exists())
            {
                if (!file.mkdirs())
                {
                    System.out.println("Couldn't make directory");
                    return;
                }
            }
        }

        try
        {
            // Create the initial context
            Context ctx = new InitialContext(env);

            _topic = (AMQTopic) ctx.lookup("Topic");

            _connection = (AMQConnection) ctx.lookup("Connection");

            _connectionURL = _connection.toURL();

            _connectionFactory = (AMQConnectionFactory) ctx.lookup("ConnectionFactory");
            //System.out.println(topic);

            // Close the context when we're done
            ctx.close();
        }
        catch (NamingException e)
        {
            System.out.println("Operation failed: " + e);
        }
        finally
        {
            try
            {
                if (_connection != null)
                {
                    _connection.close();
                }
            }
            catch (JMSException e)
            {
                //ignore just need to close
            }
        }
    }

    public String connectionFactoryValue()
    {
        if (_connectionFactory != null)
        {
            return _connectionFactory.getConnectionURL().toString();
        }
        return "";
    }

    public String connectionValue()
    {
        if (_connectionURL != null)
        {
            return _connectionURL;
        }
        return "";
    }

    public String topicValue()
    {
        if (_topic != null)
        {
            return _topic.toURL();
        }
        return "";
    }

    public String getProviderURL()
    {
        return PROVIDER_URL;
    }

    public static void main(String[] args)
    {
        new Lookup();
    }
}

