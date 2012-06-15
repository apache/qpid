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
package org.apache.qpid.test.utils;

import java.io.File;

import org.apache.qpid.util.FileUtils;

/**
 * Utility methods intended to be used in unit tests that manipulate files
 */
public class TestFileUtils
{
    private static final String SYSTEM_TMP_DIR = System.getProperty("java.io.tmpdir");

    /**
     * Create and return a temporary directory that will be deleted on exit.
     */
    public static File createTestDirectory()
    {
        String dirNameStem = TestFileUtils.class.getSimpleName() + "-testDir";
        return createTestDirectory(dirNameStem, true);
    }

    /**
     * Creates an empty directory with a name like /tmp/dirNameStem-12345678
     */
    public static File createTestDirectory(String dirNameStem, boolean deleteOnExit)
    {
        File testDir = new File(SYSTEM_TMP_DIR, dirNameStem + "-" + System.currentTimeMillis());
        if (testDir.exists())
        {
            FileUtils.delete(testDir, true);
        }

        testDir.mkdirs();

        if (deleteOnExit)
        {
            testDir.deleteOnExit();
        }

        return testDir;
    }
}
