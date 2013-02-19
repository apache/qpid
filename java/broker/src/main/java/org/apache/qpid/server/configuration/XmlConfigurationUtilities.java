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
package org.apache.qpid.server.configuration;

import java.io.File;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.ConfigurationFactory;
import org.apache.commons.configuration.SystemConfiguration;
import org.apache.commons.configuration.XMLConfiguration;

public class XmlConfigurationUtilities
{

    // Our configuration class needs to make the interpolate method
    // public so it can be called below from the config method.
    public static class MyConfiguration extends CompositeConfiguration
    {
        public String interpolate(String obj)
        {
            return super.interpolate(obj);
        }
    }

    public static Configuration parseConfig(File file, Map<String, String> envVarMap) throws ConfigurationException
    {
        ConfigurationFactory factory = new ConfigurationFactory();
        factory.setConfigurationFileName(file.getAbsolutePath());
        Configuration conf = factory.getConfiguration();

        Iterator<?> keys = conf.getKeys();
        if (!keys.hasNext())
        {
            keys = null;
            conf = flatConfig(file);
        }

        XmlConfigurationUtilities.substituteEnvironmentVariables(conf, envVarMap);
        return conf;
    }

    public final static Configuration flatConfig(File file) throws ConfigurationException
    {
        // We have to override the interpolate methods so that
        // interpolation takes place across the entirety of the
        // composite configuration. Without doing this each
        // configuration object only interpolates variables defined
        // inside itself.
        final MyConfiguration conf = new MyConfiguration();
        conf.addConfiguration(new SystemConfiguration()
        {
            protected String interpolate(String o)
            {
                return conf.interpolate(o);
            }
        });
        conf.addConfiguration(new XMLConfiguration(file)
        {
            protected String interpolate(String o)
            {
                return conf.interpolate(o);
            }
        });
        return conf;
    }

    static void substituteEnvironmentVariables(Configuration conf, Map<String, String> envVarMap)
    {
        if (envVarMap == null || envVarMap.isEmpty())
        {
            return;
        }
        for (Entry<String, String> var : envVarMap.entrySet())
        {
            String val = System.getenv(var.getKey());
            if (val != null)
            {
                conf.setProperty(var.getValue(), val);
            }
        }
    }


    public static String escapeTagName(String name)
    {
        return name.replaceAll("\\.", "\\.\\.");
    }
}
