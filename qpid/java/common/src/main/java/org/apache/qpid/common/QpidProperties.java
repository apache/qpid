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

import java.util.Properties;
import java.io.IOException;

public class QpidProperties
{
    public static final String VERSION_RESOURCE = "version.properties";

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
            props.load(QpidProperties.class.getClassLoader().getResourceAsStream(VERSION_RESOURCE));

            productName = props.getProperty(PRODUCT_NAME_PROPERTY);
            releaseVersion = props.getProperty(RELEASE_VERSION_PROPERTY);
            buildVersion = props.getProperty(BUILD_VERSION_PROPERTY);
        }
        catch (IOException e)
        {
            // Log a warning about this and leave the values initialized to unknown.
            System.err.println("Could not load version.properties resource.");
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

    public static void main(String[] args)
    {
        System.out.println(getVersionString());
    }
}
