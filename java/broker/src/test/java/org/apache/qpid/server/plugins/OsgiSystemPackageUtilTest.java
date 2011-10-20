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
package org.apache.qpid.server.plugins;

import java.util.Map;
import java.util.TreeMap;

import org.apache.qpid.test.utils.QpidTestCase;
import org.osgi.framework.Version;

/**
 *
 */
public class OsgiSystemPackageUtilTest extends QpidTestCase
{
    private OsgiSystemPackageUtil _util = null; // Object under test

    private Map<String, String> _map = new TreeMap<String, String>(); // Use a TreeMap for unit test in order for determinstic results.

    public void testWithOnePackage() throws Exception
    {
        _map.put("org.xyz", "1.0.0");

        _util = new OsgiSystemPackageUtil(null, _map);

        final String systemPackageString = _util.getFormattedSystemPackageString();

        assertEquals("org.xyz; version=1.0.0", systemPackageString);   
    }

    public void testWithTwoPackages() throws Exception
    {
        _map.put("org.xyz", "1.0.0");
        _map.put("org.abc", "1.2.3");
        
        _util = new OsgiSystemPackageUtil(null, _map);
        
        final String systemPackageString = _util.getFormattedSystemPackageString();

        assertEquals("org.abc; version=1.2.3, org.xyz; version=1.0.0", systemPackageString);   
    }

    public void testWithNoPackages() throws Exception
    {
        _util = new OsgiSystemPackageUtil(null, _map);
 
        final String systemPackageString = _util.getFormattedSystemPackageString();

        assertNull(systemPackageString);   
    }

    public void testWithQpidPackageWithQpidReleaseNumberSet() throws Exception
    {
        _map.put("org.apache.qpid.xyz", "1.0.0");
        _map.put("org.abc", "1.2.3");

        _util = new OsgiSystemPackageUtil(new Version("0.13"), _map);

        final String systemPackageString = _util.getFormattedSystemPackageString();

        assertEquals("org.abc; version=1.2.3, org.apache.qpid.xyz; version=0.13.0", systemPackageString);   
    }

    public void testWithQpidPackageWithoutQpidReleaseNumberSet() throws Exception
    {
        _map.put("org.apache.qpid.xyz", "1.0.0");
        _map.put("org.abc", "1.2.3");

        _util = new OsgiSystemPackageUtil(null, _map);
        
        final String systemPackageString = _util.getFormattedSystemPackageString();
        
        assertEquals("org.abc; version=1.2.3, org.apache.qpid.xyz; version=1.0.0", systemPackageString);   
    }
}
