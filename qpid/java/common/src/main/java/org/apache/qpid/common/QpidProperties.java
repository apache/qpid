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

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * QpidProperties captures the project name, version number, and source code repository revision number from a properties
 * file which is generated as part of the build process. Normally, the name and version number are pulled from the module
 * name and version number of the Maven build POM, but could come from other sources if the build system is changed. The
 * idea behind this, is that every build has these values incorporated directly into its jar file, so that code in the
 * wild can be identified, should its origination be forgotten.
 *
 * <p>To get the build version of any Qpid code call the {@link #main} method. This version string is usually also
 * printed to the console on broker start up.
 */
public class QpidProperties
{
    /** The name of the version properties file to load from the class path. */
    public static final String VERSION_RESOURCE = "qpidversion.properties";

    /** Defines the name of the product property. */
    public static final String PRODUCT_NAME_PROPERTY = "qpid.name";

    /** Defines the name of the version property. */
    public static final String RELEASE_VERSION_PROPERTY = "qpid.version";

    /** Defines the name of the version suffix property. */
    public static final String RELEASE_VERSION_SUFFIX = "qpid.version.suffix";

    /** Defines the name of the source code revision property. */
    public static final String BUILD_VERSION_PROPERTY = "qpid.svnversion";

    /** Defines the default value for all properties that cannot be loaded. */
    private static final String DEFAULT = "unknown";

    /** Holds the product name. */
    private static final String productName;

    /** Holds the product version. */
    private static final String releaseVersion;

    /** Holds the source code revision. */
    private static final String buildVersion;

    private static final Properties properties = new Properties();

    // Loads the values from the version properties file.
    static
    {

        try(InputStream propertyStream = QpidProperties.class.getClassLoader().getResourceAsStream(VERSION_RESOURCE))
        {
            if (propertyStream != null)
            {
                properties.load(propertyStream);
            }
        }
        catch (IOException e)
        {
            // Ignore, most likely running within an IDE, values will have the DEFAULT text
        }

        String versionSuffix = properties.getProperty(RELEASE_VERSION_SUFFIX);
        String version = properties.getProperty(RELEASE_VERSION_PROPERTY, DEFAULT);

        productName = properties.getProperty(PRODUCT_NAME_PROPERTY, DEFAULT);
        releaseVersion = versionSuffix == null || "".equals(versionSuffix) ? version : version + ";" + versionSuffix;
        buildVersion =  properties.getProperty(BUILD_VERSION_PROPERTY, DEFAULT);
    }

    public static Properties asProperties()
    {
        return new Properties(properties);
    }

    /**
     * Gets the product name.
     *
     * @return The product name.
     */
    public static String getProductName()
    {
        return productName;
    }

    /**
     * Gets the product version.
     *
     * @return The product version.
     */
    public static String getReleaseVersion()
    {
        return releaseVersion;
    }

    /**
     * Gets the source code revision.
     *
     * @return The source code revision.
     */
    public static String getBuildVersion()
    {
        return buildVersion;
    }

    /**
     * Extracts all of the version information as a printable string.
     *
     * @return All of the version information as a printable string.
     */
    public static String getVersionString()
    {
        return getProductName() + " - " + getReleaseVersion() + " build: " + getBuildVersion();
    }

    /**
     * Prints the versioning information to the console. This is extremely usefull for identifying Qpid code in the
     * wild, where the origination of the code has been forgotten.
     *
     * @param args Does not require any arguments.
     */
    public static void main(String[] args)
    {
        System.out.println(getVersionString());
    }
}
