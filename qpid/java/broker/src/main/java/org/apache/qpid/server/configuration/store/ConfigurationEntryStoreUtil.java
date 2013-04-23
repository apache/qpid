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
package org.apache.qpid.server.configuration.store;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.util.FileUtils;

public class ConfigurationEntryStoreUtil
{
    public void copyInitialConfigFile(String initialConfigLocation, File destinationFile)
    {
        URL initialStoreURL = toURL(initialConfigLocation);
        InputStream in =  null;
        try
        {
            in = initialStoreURL.openStream();
            FileUtils.copy(in, destinationFile);
        }
        catch (IOException e)
        {
            throw new IllegalConfigurationException("Cannot create file " + destinationFile + " by copying initial config from " + initialConfigLocation , e);
        }
        finally
        {
            if (in != null)
            {
                try
                {
                    in.close();
                }
                catch (IOException e)
                {
                    throw new IllegalConfigurationException("Cannot close initial config input stream: " + initialConfigLocation , e);
                }
            }
        }
    }

    public URL toURL(String location)
    {
        URL url = null;
        try
        {
            url = new URL(location);
        }
        catch (MalformedURLException e)
        {
            File locationFile = new File(location);
            url = fileToURL(locationFile);
        }
        return url;
    }

    protected URL fileToURL(File storeFile)
    {
        URL storeURL = null;
        try
        {
            storeURL = storeFile.toURI().toURL();
        }
        catch (MalformedURLException e)
        {
            throw new IllegalConfigurationException("Cannot create URL for file " + storeFile, e);
        }
        return storeURL;
    }
}
