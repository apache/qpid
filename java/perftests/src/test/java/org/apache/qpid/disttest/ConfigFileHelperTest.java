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
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.test.utils.TestFileUtils;

public class ConfigFileHelperTest extends QpidTestCase
{
    private File _testDir;
    private ConfigFileHelper _configFileHelper = new ConfigFileHelper();

    @Override
    protected void setUp() throws Exception
    {
        _testDir = TestFileUtils.createTestDirectory();
    }

    public void testGetTestConfigFilesForDirectory() throws Exception
    {
        String jsFile = createFile("file1.js");
        String jsonFile = createFile("file2.json");
        createFile("file.txt");
        createDir("dir.js");

        String testConfigPath = _testDir.getAbsolutePath();

        List<String> configFiles = _configFileHelper.getTestConfigFiles(testConfigPath);

        Set<String> expectedFiles = new HashSet<String>(Arrays.asList(jsFile, jsonFile));
        Set<String> actualFiles = new HashSet<String>(configFiles);

        assertEquals(expectedFiles, actualFiles);
    }

    private void createDir(String dirName)
    {
        File dir = new File(_testDir, dirName);
        dir.mkdir();
    }

    private String createFile(String fileName) throws IOException
    {
        File file = new File(_testDir, fileName);
        file.createNewFile();
        return file.getAbsolutePath();
    }
}
