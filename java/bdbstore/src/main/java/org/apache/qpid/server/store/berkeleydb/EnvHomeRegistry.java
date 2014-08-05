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

package org.apache.qpid.server.store.berkeleydb;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.apache.qpid.server.store.StoreException;

/**
 * JE permits the same environment to opened for write many times from within the same JVM.
 * The Java Broker needs to disallow this as the stores of many VHNs or many VH
 */
public class EnvHomeRegistry
{
    private static final EnvHomeRegistry _instance = new EnvHomeRegistry();
    private final Set<String> _canonicalNames = new HashSet<>();

    public static final EnvHomeRegistry getInstance()
    {
        return _instance;
    }

    // default for unit testing
    EnvHomeRegistry()
    {
        super();
    }

    public synchronized void registerHome(final File home) throws StoreException
    {
        if (home == null)
        {
            throw new IllegalArgumentException("home parameter cannot be null");
        }

        String canonicalForm = getCanonicalForm(home);
        if (_canonicalNames.contains(canonicalForm))
        {
            throw new IllegalArgumentException("JE Home " + home + " is already in use");
        }
        _canonicalNames.add(canonicalForm);
    }


    public synchronized void deregisterHome(final File home) throws StoreException
    {
        if (home == null)
        {
            throw new IllegalArgumentException("home parameter cannot be null");
        }

        String canonicalForm = getCanonicalForm(home);
        _canonicalNames.remove(canonicalForm);
    }

    private String getCanonicalForm(final File home)
    {
        try
        {
            return home.getCanonicalPath();
        }
        catch (IOException e)
        {
            throw new StoreException("Failed to resolve " + home + " into canonical form", e);
        }
    }

}
