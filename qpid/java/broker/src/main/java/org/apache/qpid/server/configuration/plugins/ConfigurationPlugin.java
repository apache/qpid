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
package org.apache.qpid.server.configuration.plugins;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.ConversionException;
import org.apache.log4j.Logger;
import org.apache.qpid.server.configuration.ConfigurationManager;
import org.apache.qpid.server.registry.ApplicationRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
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
     * For a Queues plugin that would be a list containing:
     * queue - the queue entries
     * the alerting values for defaults
     * exchange - the default exchange
     * durable - set the default durablity
     * etc
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
            if (elementNameIndex > 0)
            {
                element = element.substring(0, elementNameIndex).trim();
            }

            elements.add(element);
        }

        //Remove the items we already expect in the configuration
        for (String tag : getElementsProcessed())
        {

            // Work round the issue with Commons configuration.
            // With an XMLConfiguration the key will be [@property]
            // but with a CompositeConfiguration it will be @property].
            // Hide this issue from our users so when/if we change the
            // configuration they don't have to. 
            int bracketIndex = tag.indexOf("[");
            if (bracketIndex != -1)
            {
                tag = tag.substring(bracketIndex + 1, tag.length());
            }

            elements.remove(tag);
        }

        if (_logger.isInfoEnabled())
        {
            if (!elements.isEmpty())
            {
                _logger.info("Elements to lookup:" + path);
                for (String tag : elements)
                {
                    _logger.info("Tag:'" + tag + "'");
                }
            }
        }

        // Process the elements in the configuration
        for (String element : elements.toArray(new String[elements.size()]))
        {
            ConfigurationManager configurationManager = ApplicationRegistry.getInstance().getConfigurationManager();

            String configurationElement = path + "." + element;
            ConfigurationPlugin elementHandler = configurationManager.
                    getConfigurationPlugin(configurationElement,
                                           configuration.subset(element));

            if (elementHandler == null)
            {
                _logger.warn("Unused configuration element: '" + configurationElement + "'");
            }
            else
            {
                _pluginConfiguration.put(elementHandler.getClass(), elementHandler);
            }
        }

        validateConfiguration();
    }

    abstract public void validateConfiguration() throws ConfigurationException;

    protected boolean hasConfiguration()
    {
        return _configuration != null;
    }
    
    /// Getters

    protected double getDoubleValue(String property)
    {
        return getDoubleValue(property, 0.0);
    }

    protected double getDoubleValue(String property, double defaultValue)
    {
        return _configuration.getDouble(property, defaultValue);
    }


    protected long getLongValue(String property)
    {
        return getLongValue(property, 0);
    }

    protected long getLongValue(String property, long defaultValue)
    {
        return _configuration.getLong(property, defaultValue);
    }

    protected int getIntValue(String property)
    {
        return getIntValue(property, 0);
    }

    protected int getIntValue(String property, int defaultValue)
    {
        return _configuration.getInt(property, defaultValue);
    }

    protected String getStringValue(String property)
    {
        return getStringValue(property, null);
    }

    protected String getStringValue(String property, String defaultValue)
    {
        return _configuration.getString(property, defaultValue);
    }

    protected boolean getBooleanValue(String property)
    {
        return getBooleanValue(property, false);
    }

    protected boolean getBooleanValue(String property, boolean defaultValue)
    {
        return _configuration.getBoolean(property, defaultValue);
    }

    protected List getListValue(String property)
    {
        return getListValue(property, Collections.EMPTY_LIST);
    }

    protected List getListValue(String property, List defaultValue)
    {
        return _configuration.getList(property, defaultValue);
    }



    /// Validation Helpers

    protected boolean contains(String property)
    {
        return _configuration.getProperty(property) != null;
    }


    /**
     * Provide mechanism to validate Configuration contains a Postiive Long Value
     *
     * @param property
     *
     * @throws ConfigurationException
     */
    protected void validatePositiveLong(String property) throws ConfigurationException
    {
        try
        {
            if (!containsPositiveLong(property))
            {
                throw new ConfigurationException(this.getClass().getSimpleName()
                                                 + ": '" + property +
                                                 "' must be a Positive Long value.");
            }
        }
        catch (Exception e)
        {
            Throwable last = e;

            // Find the first cause
            if (e instanceof ConversionException)
            {
                Throwable t = e.getCause();
                while (t != null)
                {
                    last = t;
                    t = last.getCause();
                }
            }

            throw new ConfigurationException(this.getClass().getSimpleName() +
                                             ": unable to configure invalid " +
                                             property + ":" +
                                             _configuration.getString(property),
                                             last);
        }
    }

    protected boolean containsLong(String property)
    {
        try
        {
            _configuration.getLong(property);
            return true;
        }
        catch (NoSuchElementException e)
        {
            return false;
        }
    }

    protected boolean containsPositiveLong(String property)
    {
        try
        {
            long value = _configuration.getLong(property);
            return value > 0;
        }
        catch (NoSuchElementException e)
        {
            return false;
        }

    }

    protected boolean containsInt(String property)
    {
        try
        {
            _configuration.getInt(property);
            return true;
        }
        catch (NoSuchElementException e)
        {
            return false;
        }
    }

    protected boolean containsBoolean(String property)
    {
        try
        {
            _configuration.getBoolean(property);
            return true;
        }
        catch (NoSuchElementException e)
        {
            return false;
        }
    }


}


