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
import org.apache.qpid.url.URLSyntaxException;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Session;
import javax.jms.Topic;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameAlreadyBoundException;
import javax.naming.NamingException;
import javax.naming.NoInitialContextException;
import java.io.File;
import java.util.Hashtable;

import junit.framework.TestCase;

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
class Bind extends TestCase
{
    public static final String DEFAULT_PROVIDER_FILE_PATH = System.getProperty("java.io.tmpdir") + "/JNDITest" + System.currentTimeMillis();
    public static final String DEFAULT_PROVIDER_URL = "file://" + DEFAULT_PROVIDER_FILE_PATH;
    public String PROVIDER_URL = DEFAULT_PROVIDER_URL;

    String _connectionFactoryString = "";

    String _connectionString = "amqp://guest:guest@clientid/testpath?brokerlist='vm://:1'";
    Topic _topic = null;

    boolean _bound = false;

    public Bind() throws NameAlreadyBoundException, NoInitialContextException
    {
        this(false, DEFAULT_PROVIDER_URL);
    }

    public Bind(boolean output) throws NameAlreadyBoundException, NoInitialContextException
    {
        this(output, DEFAULT_PROVIDER_URL);
    }

    public Bind(boolean output, String providerURL) throws NameAlreadyBoundException, NoInitialContextException
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

        Connection connection = null;
        try
        {
            // Create the initial context
            Context ctx = new InitialContext(env);

            // Create the connection factory to be bound
            ConnectionFactory connectionFactory = null;
            // Create the Connection to be bound


            try
            {
                connectionFactory = new AMQConnectionFactory(_connectionString);
                connection = connectionFactory.createConnection();

                _connectionFactoryString = ((AMQConnectionFactory) connectionFactory).getConnectionURL().getURL();
            }
            catch (JMSException jmsqe)
            {
                fail("Unable to create Connection:" + jmsqe);
            }
            catch (URLSyntaxException urlse)
            {
                fail("Unable to create Connection:" + urlse);
            }

            try
            {
                Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

                _topic = session.createTopic("Fruity");
            }
            catch (JMSException jmse)
            {

            }
            // Perform the binds
            ctx.bind("ConnectionFactory", connectionFactory);
            if (output)
            {
                System.out.println("Bound factory\n" + ((AMQConnectionFactory) connectionFactory).getConnectionURL());
            }
            ctx.bind("Connection", connection);
            if (output)
            {
                System.out.println("Bound Connection\n" + ((AMQConnection) connection).toURL());
            }
            ctx.bind("Topic", _topic);
            if (output)
            {
                System.out.println("Bound Topic:\n" + ((AMQTopic) _topic).toURL());
            }
            _bound = true;

            // Check that it is bound
            //Object obj = ctx.lookup("Connection");
            //System.out.println(((AMQConnection)obj).toURL());

            // Close the context when we're done
            ctx.close();
        }
        catch (NamingException e)
        {
            System.out.println("Operation failed: " + e);
            if (e instanceof NameAlreadyBoundException)
            {
                throw(NameAlreadyBoundException) e;
            }

            if (e instanceof NoInitialContextException)
            {
                throw(NoInitialContextException) e;
            }
        }
        finally
        {
            try
            {
                if (connection != null)
                {
                    connection.close();
                }
            }
            catch (JMSException e)
            {
                //ignore just want it closed
            }
        }
    }

    public String connectionFactoryValue()
    {
        if (_connectionFactoryString != null)
        {
            return _connectionFactoryString;
        }
        else
        {
            return "";
        }
    }

    public String connectionValue()
    {
        if (_connectionString != null)
        {
            return _connectionString;
        }
        else
        {
            return "";
        }
    }

    public String topicValue()
    {
        if (_topic != null)
        {
            return ((AMQTopic) _topic).toURL();
        }
        else
        {
            return "";
        }

    }

    public boolean bound()
    {
        return _bound;
    }

    public String getProviderURL()
    {
        return PROVIDER_URL;
    }

    public static void main(String[] args) throws NameAlreadyBoundException, NoInitialContextException
    {
        new Bind(true);
    }
}
