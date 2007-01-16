/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.    
 *
 * 
 */
package org.apache.qpid.common;

import org.apache.log4j.Logger;

import java.util.Properties;
import java.util.Map;
import java.io.IOException;
import java.io.InputStream;

public class QpidProperties
{
    private static final Logger _logger = Logger.getLogger(QpidProperties.class);
    
    public static final String VERSION_RESOURCE = "qpidversion.properties";

    public static final String PRODUCT_NAME_PROPERTY = "qpid.name";
    public static final String RELEASE_VERSION_PROPERTY = "qpid.version";
    public static final String BUILD_VERSION_PROPERTY = "qpid.svnversion";

    private static final String DEFAULT = "unknown";

    private static String productName = DEFAULT;
    private static String releaseVersion = DEFAULT;
    private static String buildVersion = DEFAULT;

    /** Loads the values from the version properties file. */
    static
    {
        Properties props = new Properties();

        try
        {
            InputStream propertyStream = QpidProperties.class.getClassLoader().getResourceAsStream(VERSION_RESOURCE);
            if (propertyStream == null)
            {
                _logger.warn("Unable to find resource " + VERSION_RESOURCE + " from classloader");
            }
            else
            {
                props.load(propertyStream);

                if (_logger.isDebugEnabled())
                {
                    _logger.debug("Dumping QpidProperties");
                    for (Map.Entry<Object,Object> entry : props.entrySet())
                    {
                        _logger.debug("Property: " + entry.getKey() + " Value: "+ entry.getValue());
                    }
                    _logger.debug("End of property dump");
                }

                productName = readPropertyValue(props, PRODUCT_NAME_PROPERTY);
                releaseVersion = readPropertyValue(props, RELEASE_VERSION_PROPERTY);
                buildVersion = readPropertyValue(props, BUILD_VERSION_PROPERTY);                
            }
        }
        catch (IOException e)
        {
            // Log a warning about this and leave the values initialized to unknown.
            _logger.error("Could not load version.properties resource: " + e, e);
        }
    }

    public static String getProductName()
    {
        return productName;
    }

    public static String getReleaseVersion()
    {
        return releaseVersion;
    }

    public static String getBuildVersion()
    {
        return buildVersion;
    }

    public static String getVersionString()
    {
        return getProductName() + " - " + getReleaseVersion() + " build: " + getBuildVersion();
    }

    private static String readPropertyValue(Properties props, String propertyName)
    {
        String retVal = (String) props.get(propertyName);
        if (retVal == null)
        {
            retVal = DEFAULT;
        }
        return retVal;
    }

    public static void main(String[] args)
    {
        System.out.println(getVersionString());
    }
}
