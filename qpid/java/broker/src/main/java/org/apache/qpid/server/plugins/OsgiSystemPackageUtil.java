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
package org.apache.qpid.server.plugins;

import org.osgi.framework.Version;

import java.util.Iterator;
import java.util.Map;

/**
 * Utility class to convert a map of package name to version numbers into the string
 * with the format expected of a OSGi system package declaration:
 * 
 * <code>
 * org.xyz; version=1.0.0, org.xyz.xyz; version=1.0.0,...
 * </code>
 * 
 * Additionally, if the caller has provided a qpidPackageReleaseNumber and the package
 * begins org.apache.qpid, this release number will be used, in preference to the one
 * found in the Map.
 * 
 * @see org.osgi.framework.Constants#FRAMEWORK_SYSTEMPACKAGES
 * 
 */
public class OsgiSystemPackageUtil
{
    private static final String APACHE_QPID_PKG_PREFIX = "org.apache.qpid";

    private final Map<String, String> _packageNameVersionMap;
    private final Version _qpidPackageReleaseNumber;

    public OsgiSystemPackageUtil(final Version qpidPackageReleaseNumber, final Map<String, String> packageNameVersionMap)
    {
        _qpidPackageReleaseNumber = qpidPackageReleaseNumber;
        _packageNameVersionMap = packageNameVersionMap;
    }

    public String getFormattedSystemPackageString()
    {
        if (_packageNameVersionMap == null || _packageNameVersionMap.size() == 0)
        {
            return null;
        }

        final StringBuilder packages = new StringBuilder();

        for(Iterator<String> itr = _packageNameVersionMap.keySet().iterator(); itr.hasNext();)
        {
            final String packageName = itr.next();
            final String packageVersion;
            
            if (_qpidPackageReleaseNumber != null && packageName.startsWith(APACHE_QPID_PKG_PREFIX))
            {
                packageVersion = _qpidPackageReleaseNumber.toString();
            }
            else 
            {
                packageVersion = _packageNameVersionMap.get(packageName);
            }
            
            packages.append(packageName);
            packages.append("; ");
            packages.append("version=");
            packages.append(packageVersion);
            
            if (itr.hasNext())
            {
                packages.append(", ");
            }
        }

        return packages.toString();
    }

}
