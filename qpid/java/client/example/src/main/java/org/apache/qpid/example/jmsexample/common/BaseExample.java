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
package org.apache.qpid.example.jmsexample.common;

import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import java.util.Hashtable;
import java.util.Properties;

import org.apache.qpid.example.jmsexample.common.ArgProcessor;

/**
 * Abstract base class for providing common argument parsing features.
 * <p/>
 * <p>Classes that extend BaseExample support the following command-line arguments:</p>
 * <table>
 * <tr><td>-factoryName</td>       <td>ConnectionFactory name</td></tr>
 * <tr><td>-delMode</td>              <td>Delivery mode [persistent | non-persistent]</td></tr>
 * <tr><td>-numMessages</td>    <td>Number of messages to process</td></tr>
 * </table>
 */

abstract public class BaseExample
{
    /* The AMQP INITIAL_CONTEXT_FACTORY */
    private static final String INITIAL_CONTEXT_FACTORY_NAME=
            "org.apache.qpid.jndi.PropertiesFileInitialContextFactory";

    /* Default connection factory name. */
    private static final String DEFAULT_CONNECTION_FACTORY_NAME="ConnectionFactory";

    /* Default number of messages to process. */
    private static final int DEFAULT_NUMBER_MESSAGES=10;

    /* JNDI provider URL. */
    private String _providerURL;

    /* Number of messages to process. */
    private int _numberMessages;

    /* The delivery Mode */
    private int _deliveryMode;

    /* The argument processor */
    protected ArgProcessor _argProcessor;

    /* The supported properties */
    protected static Properties _options=new Properties();

    /* The properties default values */
    protected static Properties _defaults=new Properties();

    /* The broker communication objects */
    private InitialContext _initialContext;
    private ConnectionFactory _connectionFactory;

    /**
     * Protected constructor to create a example client.
     *
     * @param Id   Identity string used in log messages, for example, the name of the example program.
     * @param args String array of arguments.
     */
    protected BaseExample(String Id, String[] args)
    {
        _options.put("-factoryName", "ConnectionFactory name");
        _defaults.put("-factoryName", DEFAULT_CONNECTION_FACTORY_NAME);
        _options.put("-providerURL", "JNDI Provider URL");
        _options.put("-deliveryMode", "Delivery mode [persistent | non-persistent]");
        _defaults.put("-deliveryMode", "non-persistent");
        _options.put("-numMessages", "Number of messages to process");
        _defaults.put("-numMessages", String.valueOf(DEFAULT_NUMBER_MESSAGES));

        _argProcessor=new ArgProcessor(Id, args, _options, _defaults);
        _argProcessor.display();
        //Set the initial context factory
        _providerURL=_argProcessor.getStringArgument("-providerURL");
        // Set the number of messages
        _numberMessages=_argProcessor.getIntegerArgument("-numMessages");
        // Set the delivery mode
        _deliveryMode=_argProcessor.getStringArgument("-deliveryMode")
                .equals("persistent") ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT;
    }

    /**
     * Get the DeliveryMode to use when publishing messages.
     *
     * @return The delivery mode, either javax.jms.DeliveryMode.NON_PERSISTENT
     *         or javax.jms.DeliveryMode.PERSISTENT.
     */
    protected int getDeliveryMode()
    {
        return _deliveryMode;
    }

    /**
     * Get the number of messages to be used.
     *
     * @return the number of messages to be used.
     */
    protected int getNumberMessages()
    {
        return _numberMessages;
    }


    /**
     * Get the JNDI provider URL.
     *
     * @return the JNDI provider URL.
     */
    private String getProviderURL()
    {
        return _providerURL;
    }

    /**
     * we assume that the environment is correctly set
     * i.e. -Djava.naming.provider.url="..//example.properties"
     *
     * @return An initial context
     * @throws Exception if there is an error getting the context
     */
    public InitialContext getInitialContext() throws Exception
    {
        if (_initialContext == null)
        {
            Hashtable<String, String> jndiEnvironment=new Hashtable<String, String>();
            jndiEnvironment.put(Context.INITIAL_CONTEXT_FACTORY, INITIAL_CONTEXT_FACTORY_NAME);            
            if (getProviderURL() != null)
            {
                jndiEnvironment.put(Context.PROVIDER_URL, getProviderURL());
            }
            else
            {
                jndiEnvironment.put("connectionfactory.ConnectionFactory",
                        "qpid:password=guest;username=guest;client_id=clientid;virtualhost=test@tcp:127.0.0.1:5672");
            }
            _initialContext=new InitialContext(jndiEnvironment);
        }
        return _initialContext;
    }

    /**
     * Get a connection factory for the currently used broker
     *
     * @return A conection factory
     * @throws Exception if there is an error getting the tactory
     */
    public ConnectionFactory getConnectionFactory() throws Exception
    {
        if (_connectionFactory == null)
        {
            _connectionFactory=(ConnectionFactory) getInitialContext().lookup(DEFAULT_CONNECTION_FACTORY_NAME);
        }
        return _connectionFactory;
    }

    /**
     * Get a connection (remote or in-VM)
     *
     * @return a newly created connection
     * @throws Exception if there is an error getting the connection
     */
    public Connection getConnection() throws Exception
    {
        return getConnectionFactory().createConnection("guest", "guest");
    }
}
