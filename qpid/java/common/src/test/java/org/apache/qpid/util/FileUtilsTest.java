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

import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;

public class FileUtilsTest extends TestCase
{
    public void testDelete()
    {
        File test = new File("FileUtilsTest-testDelete");
        try
        {
            test.createNewFile();
        }
        catch (IOException e)
        {
            fail(e.getMessage());
        }

        assertTrue("File does not exists", test.exists());
        assertTrue("File is not a file", test.isFile());

        try
        {
            FileUtils.deleteFile("FileUtilsTest-testDelete");
        }
        catch (IOException e)
        {
            fail(e.getMessage());
        }

        assertTrue("File exists after delete", !test.exists());
    }

    public void testRecursiveDelete()
    {
        String directoryName = "FileUtilsTest-testRecursiveDelete";
        File test = new File(directoryName);

        assertTrue("Directory exists", !test.exists());

        test.mkdir();

        createSubDir(directoryName, 2, 4);

        try
        {
            FileUtils.deleteFile("FileUtilsTest-testDelete");
        }
        catch (IOException e)
        {
            fail(e.getMessage());
        }

        assertTrue("File does not exist after file delete", test.exists());

        try
        {
            FileUtils.delete(test, false);
        }
        catch (IOException e)
        {
            fail(e.getMessage());
        }
        
        assertTrue("File does not exist after non recursive delete", test.exists());

        try
        {
            FileUtils.delete(test, true);
        }
        catch (IOException e)
        {
            fail(e.getMessage());
        }
        assertTrue("File  exist after recursive delete", !test.exists());

    }

    private void createSubDir(String path, int directories, int files)
    {
        File directory = new File(path);

        assertTrue("Directory" + path + " does not exists", directory.exists());

        for (int dir = 0; dir < directories; dir++)
        {
            String subDirName = path + File.separatorChar + "sub" + dir;
            File subDir = new File(subDirName);

            subDir.mkdir();

            createSubDir(subDirName, directories - 1, files);
        }

        for (int file = 0; file < files; file++)
        {
            String subDirName = path + File.separatorChar + "file" + file;
            File subFile = new File(subDirName);
            try
            {
                subFile.createNewFile();
            }
            catch (IOException e)
            {
                fail(e.getMessage());
            }
        }
    }

}
