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
package org.apache.qpid.disttest;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;

public class ConfigFileHelper
{
    /**
     * Returns absolute paths to the config file(s).
     * <p>
     * If testConfigPath is a directory, its .js and .json files are returned.
     * Otherwise, the returned list just contains testConfigPath.
     */
    public List<String> getTestConfigFiles(String testConfigPath)
    {
        final List<String> testConfigFile = new ArrayList<String>();
        final File configFileOrDirectory = new File(testConfigPath);

        if (configFileOrDirectory.isDirectory())
        {
            final String[] configFiles = configFileOrDirectory.list(new FilenameFilter()
            {
                @Override
                public boolean accept(File dir, String name)
                {
                    boolean suffixOk = name.endsWith(".json") || name.endsWith(".js");
                    return new File(dir, name).isFile() && suffixOk;
                }
            });

            for (String configFile : configFiles)
            {
                testConfigFile.add(new File(configFileOrDirectory, configFile).getAbsolutePath());
            }
        }
        else
        {
            testConfigFile.add(configFileOrDirectory.getAbsolutePath());
        }

        return testConfigFile;
    }
}
