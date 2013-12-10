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
package org.apache.qpid.restapi.httpserver;

// Simple Logging Facade 4 Java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

import com.sun.net.httpserver.BasicAuthenticator;

/**
 * This class implements a simple com.sun.net.httpserver.BasicAuthenticator. Clearly it's not very secure being a
 * BasicAuthenticator that takes a plain (well Base64 encoded) username/password.
 * TODO Clearly something more secure needs to be implemented.....
 *
 * This class creates a Timer used to schedule regular checks on the account.properties file and if it has changed
 * it reloads the cache used for looking up the credentials. The poll period is every 10 seconds which should be OK
 * for checking account updates. The class only updates the cache if the account.properties file has actually changed.
 * Polling isn't ideal, but for this application it's probably no big deal. With Java 7 there is a Watch Service API
 * https://blogs.oracle.com/thejavatutorials/entry/watching_a_directory_for_changes that allows asynchronous
 * notification of changes, but it's an unnecessary dependency on Java 7 for this application.
 *
 * @author Fraser Adams
 */
public class Authenticator extends BasicAuthenticator
{
    private static final int CHECK_PERIOD = 10000; // Check every 10 seconds if the account properties have been changed.
    private static final String ACCOUNT_FILENAME = "account.properties";
    private static final Logger _log = LoggerFactory.getLogger(Authenticator.class);

    private File _file;
    private Properties _accountCache;
    private long _accountFileLastModified = 0; // Used to check for updates to the account properties.

    /**
     * Create a Timer used to schedule regular checks on the account.properties file.
     */
    private Timer _timer = new Timer(true);

    /**
     * This private inner class is a fairly trivial TimerTask whose run() method simply calls checkAccountFile()
     * in the main Authenticator class to check for account changes.
     */
    private final class CacheUpdater extends TimerTask
    {
        public void run()
        {
            checkAccountFile();
        }
    }

    /**
     * Construct the Authenticator. This fires up the CacheUpdater TimerTask to periodically check for changes.
     * @param realm the authentication realm to use.
     * @param path the path of the directory holding the account properties file.
     */
    public Authenticator(final String realm, final String path)
    {
        super(realm);

        String accountPathname = path + "/" + ACCOUNT_FILENAME;
        _file = new File(accountPathname);

        if (_file.exists())
        {
            CacheUpdater updater = new CacheUpdater();
            _timer.schedule(updater, 0, CHECK_PERIOD);
        }
        else
        {
            System.out.println("Authentication file " + accountPathname + " is missing.\nCannot continue - exiting.");
            System.exit(1);
        }
    }

    /**
     * Checks if the account properties file has been updated, if it has it calls loadCache() to reload the cache.
     */
    public void checkAccountFile()
    {
        long mtime = _file.lastModified();
        if (mtime != _accountFileLastModified)
        {
            _accountFileLastModified = mtime;
            loadCache();
        }
    }

    /**
     * Load the account properties file into the account cache.
     */
    private void loadCache()
    {
    	try
        {
            Properties properties = new Properties();
    	    properties.load(new FileInputStream(_file));

            // Set the cache to be the newly loaded one.
            _accountCache = properties;
    	}
        catch (IOException ex)
        {
            _log.info("loadCache failed with {}.", ex.getMessage());
        }
    }

    @Override
    public boolean checkCredentials(final String username, final String password)
    {
        //System.out.println("username = " + username);
        //System.out.println("password = " + password);

        // The original version of this forgot to check check for a null username Property. Thanks to 
        // Bruno Matos for picking that up and supplying the fix below.
        if (_accountCache.getProperty(username) != null &&
            _accountCache.getProperty(username).equals(password))
        {
            return true;
        }
        else
        {
            return false;
        }
    }
}
