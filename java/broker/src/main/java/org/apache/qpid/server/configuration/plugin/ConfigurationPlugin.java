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
package org.apache.qpid.server.configuration.plugin;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.qpid.server.configuration.ConfigurationManager;
import org.apache.qpid.server.registry.ApplicationRegistry;
import org.apache.log4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public abstract class ConfigurationPlugin
{
    protected Logger _logger = Logger.getLogger(this.getClass());

    private Map<Class<? extends ConfigurationPlugin>, ConfigurationPlugin>
            _pluginConfiguration = new HashMap<Class<? extends ConfigurationPlugin>, ConfigurationPlugin>();

    protected Configuration _configuration;

    /**
     * The Elements that this Plugin can process.
     * i.e.
     *   For a Queues plugin that would be a list containing:
     *      queue - the queue entries
     *      the alerting values for defaults
     *      exchange - the default exchange
     *      durable - set the default durablity
     *      etc
     *
     * @return 
     */
    abstract public String[] getElementsProcessed();

    public Configuration getConfig()
    {
        return _configuration;
    }

    public <C extends ConfigurationPlugin> C getConfiguration(Class<C> plugin)
    {
        return (C) _pluginConfiguration.get(plugin);
    }

    /**
     * Sets the configuration for this plugin
     *
     * @param path
     * @param configuration the configuration for this plugin.
     */

    public void setConfiguration(String path, Configuration configuration) throws ConfigurationException
    {
        _configuration = configuration;

        // Extract a list of elements for processing
        Iterator<?> keys = configuration.getKeys();

        Set<String> elements = new HashSet<String>();
        while (keys.hasNext())
        {
            String key = (String) keys.next();

            int elementNameIndex = key.indexOf(".");

            String element = key.trim();
            if (elementNameIndex != -1)
            {
                element = key.substring(0, elementNameIndex).trim();
            }

            //Trim any element properties
            elementNameIndex = element.indexOf("[");
            if (elementNameIndex != -1)
            {
                element = element.substring(0,elementNameIndex).trim();
            }

            elements.add(element);
        }


        //Remove the items we already expect in the configuration
        for (String tag : getElementsProcessed())
        {
            elements.remove(tag);
        }

        if (_logger.isInfoEnabled())
        {
            if (!elements.isEmpty())
            {
                _logger.info("Elements to lookup:" + path);
                for (String tag : elements)
                {
                    _logger.info(tag);
                }
            }
        }

        // Process the elements in the configuration
        for (String element : elements.toArray(new String[elements.size()]))
        {
            ConfigurationManager configurationManager = ApplicationRegistry.getInstance().getConfigurationManager();

            String configurationElement = path +"."+ element;
            ConfigurationPlugin elementHandler = configurationManager.
                    getConfigurationPlugin(configurationElement,
                                           configuration.subset(element));


            if (elementHandler == null)
            {
                _logger.warn("Unused configuration element:" + configurationElement);
            }
            else
            {
                _pluginConfiguration.put(elementHandler.getClass(), elementHandler);
            }
        }
    }
}


