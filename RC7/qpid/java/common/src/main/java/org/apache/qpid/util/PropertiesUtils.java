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
package org.apache.qpid.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Iterator;
import java.util.Properties;

/**
 * PropertiesHelper defines some static methods which are useful when working with properties
 * files.
 *
 * <p><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Read properties from an input stream
 * <tr><td> Read properties from a file
 * <tr><td> Read properties from a URL
 * <tr><td> Read properties given a path to a file
 * <tr><td> Trim any whitespace from property values
 * </table>
 */
public class PropertiesUtils
{
    /** Used for logging. */
    private static final Logger log = LoggerFactory.getLogger(PropertiesUtils.class);

    /**
     * Get properties from an input stream.
     *
     * @param is The input stream.
     *
     * @return The properties loaded from the input stream.
     *
     * @throws IOException If the is an I/O error reading from the stream.
     */
    public static Properties getProperties(InputStream is) throws IOException
    {
        log.debug("getProperties(InputStream): called");

        // Create properties object laoded from input stream
        Properties properties = new Properties();

        properties.load(is);

        return properties;
    }

    /**
     * Get properties from a file.
     *
     * @param file The file.
     *
     * @return The properties loaded from the file.
     *
     * @throws IOException If there is an I/O error reading from the file.
     */
    public static Properties getProperties(File file) throws IOException
    {
        log.debug("getProperties(File): called");

        // Open the file as an input stream
        InputStream is = new FileInputStream(file);

        // Create properties object loaded from the stream
        Properties properties = getProperties(is);

        // Close the file
        is.close();

        return properties;
    }

    /**
     * Get properties from a url.
     *
     * @param url The URL.
     *
     * @return The properties loaded from the url.
     *
     * @throws IOException If there is an I/O error reading from the URL.
     */
    public static Properties getProperties(URL url) throws IOException
    {
        log.debug("getProperties(URL): called");

        // Open the URL as an input stream
        InputStream is = url.openStream();

        // Create properties object loaded from the stream
        Properties properties = getProperties(is);

        // Close the url
        is.close();

        return properties;
    }

    /**
     * Get properties from a path name. The path name may refer to either a file or a URL.
     *
     * @param pathname The path name.
     *
     * @return The properties loaded from the file or URL.
     *
     * @throws IOException If there is an I/O error reading from the URL or file named by the path.
     */
    public static Properties getProperties(String pathname) throws IOException
    {
        log.debug("getProperties(String): called");

        // Check that the path is not null
        if (pathname == null)
        {
            return null;
        }

        // Check if the path is a URL
        if (isURL(pathname))
        {
            // The path is a URL
            return getProperties(new URL(pathname));
        }
        else
        {
            // Assume the path is a file name
            return getProperties(new File(pathname));
        }
    }

    /**
     * Trims whitespace from property values. This method returns a new set of properties
     * the same as the properties specified as an argument but with any white space removed by
     * the {@link java.lang.String#trim} method.
     *
     * @param properties The properties to trim whitespace from.
     *
     * @return The white space trimmed properties.
     */
    public static Properties trim(Properties properties)
    {
        Properties trimmedProperties = new Properties();

        // Loop over all the properties
        for (Iterator i = properties.keySet().iterator(); i.hasNext();)
        {
            String next = (String) i.next();
            String nextValue = properties.getProperty(next);

            // Trim the value if it is not null
            if (nextValue != null)
            {
                nextValue.trim();
            }

            // Store the trimmed value in the trimmed properties
            trimmedProperties.setProperty(next, nextValue);
        }

        return trimmedProperties;
    }

    /**
     * Helper method. Guesses whether a string is a URL or not. A String is considered to be a url if it begins with
     * http:, ftp:, or uucp:.
     *
     * @param name The string to test for being a URL.
     *
     * @return True if the string is a URL and false if not.
     */
    private static boolean isURL(String name)
    {
        return (name.toLowerCase().startsWith("http:") || name.toLowerCase().startsWith("ftp:")
                || name.toLowerCase().startsWith("uucp:"));
    }
}
