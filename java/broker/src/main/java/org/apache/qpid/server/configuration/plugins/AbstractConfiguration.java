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
 */
package org.apache.qpid.server.configuration.plugins;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.ConversionException;
import org.apache.log4j.Logger;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

public abstract class AbstractConfiguration
{
    protected static final Logger _logger = Logger.getLogger(AbstractConfiguration.class);

    private Configuration _config;

    /**
     * The Elements that this Plugin can process.
     *
     * For a Queues plugin that would be a list containing:
     * <ul>
     * <li>queue - the queue entries
     * <li>the alerting values for defaults
     * <li>exchange - the default exchange
     * <li>durable - set the default durablity
     * </ul>
     */
    abstract public String[] getElementsProcessed();

    /** Performs configuration validation. */
    public void validateConfiguration() throws ConfigurationException
    {
        // Override in sub-classes
    }

    public Configuration getConfig()
    {
        return _config;
    }

    /**
     * Sets the configuration for this plugin
     *
     * @param path
     * @param configuration the configuration for this plugin.
     */
    public void setConfiguration(String path, Configuration configuration) throws ConfigurationException
    {
        _config = configuration;

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

            // Trim any element properties
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

        validateConfiguration();
    }

    /** Helper method to print out list of keys in a {@link Configuration}. */
    public static final void showKeys(Configuration config)
    {
        if (config.isEmpty())
        {
            _logger.info("Configuration is empty");
        }
        else
        {
            Iterator<?> keys = config.getKeys();
            while (keys.hasNext())
            {
                String key = (String) keys.next();
                _logger.info("Configuration key: " + key);
            }
        }
    }

    protected boolean hasConfiguration()
    {
        return _config != null;
    }

    /// Getters

    protected double getDoubleValue(String property)
    {
        return getDoubleValue(property, 0.0);
    }

    protected double getDoubleValue(String property, double defaultValue)
    {
        return _config.getDouble(property, defaultValue);
    }

    protected long getLongValue(String property)
    {
        return getLongValue(property, 0);
    }

    protected long getLongValue(String property, long defaultValue)
    {
        return _config.getLong(property, defaultValue);
    }

    protected int getIntValue(String property)
    {
        return getIntValue(property, 0);
    }

    protected int getIntValue(String property, int defaultValue)
    {
        return _config.getInt(property, defaultValue);
    }

    protected String getStringValue(String property)
    {
        return getStringValue(property, null);
    }

    protected String getStringValue(String property, String defaultValue)
    {
        return _config.getString(property, defaultValue);
    }

    protected boolean getBooleanValue(String property)
    {
        return getBooleanValue(property, false);
    }

    protected boolean getBooleanValue(String property, boolean defaultValue)
    {
        return _config.getBoolean(property, defaultValue);
    }

    protected List getListValue(String property)
    {
        return getListValue(property, Collections.EMPTY_LIST);
    }

    protected List getListValue(String property, List defaultValue)
    {
        return _config.getList(property, defaultValue);
    }

    /// Validation Helpers

    protected boolean contains(String property)
    {
        return _config.getProperty(property) != null;
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
                                             _config.getString(property),
                                             last);
        }
    }

    protected boolean containsLong(String property)
    {
        try
        {
            _config.getLong(property);
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
            long value = _config.getLong(property);
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
            _config.getInt(property);
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
            _config.getBoolean(property);
            return true;
        }
        catch (NoSuchElementException e)
        {
            return false;
        }
    }

    public static String escapeTagName(String name)
    {
        return name.replaceAll("\\.", "\\.\\.");
    }

    protected void setConfig(Configuration config)
    {
        _config = config;
    }
}


