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
package org.apache.qpid.server.configuration;

import org.apache.commons.configuration.Configuration;
import org.apache.log4j.Logger;
import org.apache.qpid.configuration.Configured;
import org.apache.qpid.configuration.PropertyUtils;
import org.apache.qpid.configuration.PropertyException;
import org.apache.qpid.server.registry.ApplicationRegistry;

import java.lang.reflect.Field;

/**
 * This class contains utilities for populating classes automatically from values pulled from configuration
 * files.
 */
public class Configurator
{
    private static final Logger _logger = Logger.getLogger(Configurator.class);

    /**
     * Configure a given instance using the application configuration. Note that superclasses are <b>not</b>
     * currently configured but this could easily be added if required.
     * @param instance the instance to configure
     */
    public static void configure(Object instance)
    {
        final Configuration config = ApplicationRegistry.getInstance().getConfiguration();

        for (Field f : instance.getClass().getDeclaredFields())
        {
            Configured annotation = f.getAnnotation(Configured.class);
            if (annotation != null)
            {
                setValueInField(f, instance, config, annotation);
            }
        }
    }

    private static void setValueInField(Field f, Object instance, Configuration config, Configured annotation)
    {
        Class fieldClass = f.getType();
        String configPath = annotation.path();
        try
        {
            if (fieldClass == String.class)
            {
                String val = config.getString(configPath, annotation.defaultValue());
                val = PropertyUtils.replaceProperties(val);
                f.set(instance, val);
            }
            else if (fieldClass == int.class)
            {
                int val = config.getInt(configPath, Integer.parseInt(annotation.defaultValue()));
                f.setInt(instance, val);
            }
            else if (fieldClass == long.class)
            {
                long val = config.getLong(configPath, Long.parseLong(annotation.defaultValue()));
                f.setLong(instance, val);
            }
            else if (fieldClass == double.class)
            {
                double val = config.getDouble(configPath, Double.parseDouble(annotation.defaultValue()));
                f.setDouble(instance, val);
            }
            else if (fieldClass == boolean.class)
            {
                boolean val = config.getBoolean(configPath, Boolean.parseBoolean(annotation.defaultValue()));
                f.setBoolean(instance, val);
            }
            else
            {
                _logger.error("Unsupported field type " + fieldClass + " for " + f + " IGNORING configured value");
            }
        }
        catch (PropertyException e)
        {
            _logger.error("Unable to expand property: " + e + " INGORING field " + f, e);
        }
        catch (IllegalAccessException e)
        {
            _logger.error("Unable to access field " + f + " IGNORING configured value");
        }
    }
}