/* Licensed to the Apache Software Foundation (ASF) under one
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
package org.apache.qpidity.naming;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.qpidity.jms.ConnectionFactoryImpl;
import org.apache.qpidity.jms.DestinationImpl;
import org.apache.qpidity.jms.QueueImpl;
import org.apache.qpidity.jms.TopicImpl;
import org.apache.qpidity.url.BindingURLImpl;
import org.apache.qpidity.url.URLSyntaxException;
import org.apache.qpidity.url.BindingURL;
import org.apache.qpidity.QpidException;

import javax.naming.spi.InitialContextFactory;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.Queue;
import javax.jms.Topic;
import java.util.Hashtable;
import java.util.Map;
import java.util.Properties;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;

/**
 * This is an implementation of InitialContextFactory that uses a default jndi.properties file.
 * 
 */
public class PropertiesFileInitialContextFactory implements InitialContextFactory
{
    protected final Logger _logger = LoggerFactory.getLogger(PropertiesFileInitialContextFactory.class);

    private String CONNECTION_FACTORY_PREFIX = "connectionfactory.";
    private String DESTINATION_PREFIX = "destination.";
    private String QUEUE_PREFIX = "queue.";
    private String TOPIC_PREFIX = "topic.";

    public Context getInitialContext(Hashtable environment) throws NamingException
    {
        Map data = new ConcurrentHashMap();
        try
        {
            String file;
            if (environment.containsKey(Context.PROVIDER_URL))
            {
                file = (String) environment.get(Context.PROVIDER_URL);
            }
            else
            {
                file = System.getProperty(Context.PROVIDER_URL);
            }
            if (file != null)
            {
                _logger.info("Loading Properties from:" + file);
                // Load the properties specified
                Properties p = new Properties();
                p.load(new BufferedInputStream(new FileInputStream(file)));
                environment.putAll(p);
                _logger.info("Loaded Context Properties:" + environment.toString());
            }
            else
            {
                _logger.info("No Provider URL specified.");
            }
        }
        catch (IOException ioe)
        {
            _logger.warn(
                    "Unable to load property file specified in Provider_URL:" + environment.get(Context.PROVIDER_URL));
        }
        createConnectionFactories(data, environment);
        createDestinations(data, environment);
        createQueues(data, environment);
        createTopics(data, environment);
        return createContext(data, environment);
    }

    // Implementation methods
    // -------------------------------------------------------------------------
    protected ReadOnlyContext createContext(Map data, Hashtable environment)
    {
        return new ReadOnlyContext(environment, data);
    }

    protected void createConnectionFactories(Map data, Hashtable environment)
    {
        for (Iterator iter = environment.entrySet().iterator(); iter.hasNext();)
        {
            Map.Entry entry = (Map.Entry) iter.next();
            String key = entry.getKey().toString();
            if (key.startsWith(CONNECTION_FACTORY_PREFIX))
            {
                String jndiName = key.substring(CONNECTION_FACTORY_PREFIX.length());
                ConnectionFactory cf = createFactory(entry.getValue().toString());
                if (cf != null)
                {
                    data.put(jndiName, cf);
                }
            }
        }
    }

    protected void createDestinations(Map data, Hashtable environment)
    {
        for (Iterator iter = environment.entrySet().iterator(); iter.hasNext();)
        {
            Map.Entry entry = (Map.Entry) iter.next();
            String key = entry.getKey().toString();
            if (key.startsWith(DESTINATION_PREFIX))
            {
                String jndiName = key.substring(DESTINATION_PREFIX.length());
                Destination dest = createDestination(entry.getValue().toString());
                if (dest != null)
                {
                    data.put(jndiName, dest);
                }
            }
        }
    }

    protected void createQueues(Map data, Hashtable environment)
    {
        for (Iterator iter = environment.entrySet().iterator(); iter.hasNext();)
        {
            Map.Entry entry = (Map.Entry) iter.next();
            String key = entry.getKey().toString();
            if (key.startsWith(QUEUE_PREFIX))
            {
                String jndiName = key.substring(QUEUE_PREFIX.length());
                Queue q = createQueue(entry.getValue().toString());
                if (q != null)
                {
                    data.put(jndiName, q);
                }
            }
        }
    }

    protected void createTopics(Map data, Hashtable environment)
    {
        for (Iterator iter = environment.entrySet().iterator(); iter.hasNext();)
        {
            Map.Entry entry = (Map.Entry) iter.next();
            String key = entry.getKey().toString();
            if (key.startsWith(TOPIC_PREFIX))
            {
                String jndiName = key.substring(TOPIC_PREFIX.length());
                Topic t = createTopic(entry.getValue().toString());
                if (t != null)
                {
                    data.put(jndiName, t);
                }
            }
        }
    }

    /**
     * Factory method to create new Connection Factory instances
     */
    protected ConnectionFactory createFactory(String url)
    {
        try
        {
            return new ConnectionFactoryImpl(url);
        }
        catch (MalformedURLException urlse)
        {
            _logger.warn("Unable to createFactories:" + urlse);
        }
        return null;
    }

    /**
     * Factory method to create new Destination instances from an AMQP BindingURL
     */
    protected Destination createDestination(String bindingURL)
    {
        BindingURL binding;
        try
        {
            binding = new BindingURLImpl(bindingURL);
        }
        catch (URLSyntaxException urlse)
        {
            _logger.warn("Unable to destination:" + urlse);
            return null;
        }
        try
        {
            return new DestinationImpl(binding);
        }
        catch (QpidException iaw)
        {
            _logger.warn("Binding: '" + binding + "' not supported");
            return null;
        }
    }

    /**
     * Factory method to create new Queue instances
     */
    protected Queue createQueue(Object value)
    {
        Queue result = null;
        try
        {
            if (value instanceof String)
            {
                result = new QueueImpl((String) value);
            }
            else if (value instanceof BindingURL)
            {
                result = new QueueImpl((BindingURL) value);
            }
        }
        catch (QpidException e)
        {
            _logger.warn("Binding: '" + value + "' not supported");
        }
        return result;
    }

    /**
     * Factory method to create new Topic instances
     */
    protected Topic createTopic(Object value)
    {
        Topic result = null;
        try
        {
            if (value instanceof String)
             {
                 return new TopicImpl((String) value);
             }
             else if (value instanceof BindingURL)
             {
                 return new TopicImpl((BindingURL) value);
             }
        }
        catch (QpidException e)
        {
            _logger.warn("Binding: '" + value + "' not supported");
        }
        return result;
    }
}
