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
package org.apache.qpid.configuration;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.common.QpidProperties;

/**
 * Centralised record of Qpid common properties.
 *
 * @see ClientProperties
 */
public class CommonProperties
{
    private static final Logger LOGGER = LoggerFactory.getLogger(CommonProperties.class);

    /**
     * The timeout used by the IO layer for timeouts such as send timeout in IoSender, and the close timeout for IoSender and IoReceiver
     */
    public static final String IO_NETWORK_TRANSPORT_TIMEOUT_PROP_NAME = "qpid.io_network_transport_timeout";
    public static final int IO_NETWORK_TRANSPORT_TIMEOUT_DEFAULT = 60000;

    public static final String HANDSHAKE_TIMEOUT_PROP_NAME = "qpid.handshake_timeout";
    public static final int HANDSHAKE_TIMEOUT_DEFAULT = 2;

    static
    {

        Properties props = new Properties(QpidProperties.asProperties());
        String initialProperties = System.getProperty("qpid.common_properties_file");
        URL initialPropertiesLocation = null;
        try
        {
            if (initialProperties == null)
            {
                initialPropertiesLocation = CommonProperties.class.getClassLoader().getResource("qpid-common.properties");
            }
            else
            {
                initialPropertiesLocation = (new File(initialProperties)).toURI().toURL();
            }

            if (initialPropertiesLocation != null)
            {
                props.load(initialPropertiesLocation.openStream());
            }
        }
        catch (MalformedURLException e)
        {
            LOGGER.warn("Could not open common properties file '"+initialProperties+"'.", e);
        }
        catch (IOException e)
        {
            LOGGER.warn("Could not open common properties file '" + initialPropertiesLocation + "'.", e);
        }

        Set<String> propertyNames = new HashSet<>(props.stringPropertyNames());
        propertyNames.removeAll(System.getProperties().stringPropertyNames());
        for (String propName : propertyNames)
        {
            System.setProperty(propName, props.getProperty(propName));
        }

    }

    private CommonProperties()
    {
        //no instances
    }
}
