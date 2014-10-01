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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.io.FileOutputStream;

import junit.framework.TestCase;

/**
 * Utility methods intended to be used in tests that manipulate files
 */
public class TestFileUtils
{
    private static final String SYSTEM_TMP_DIR = System.getProperty("java.io.tmpdir");
    private static final String SUFFIX = "tmp";

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
            delete(testDir, true);
        }

        testDir.mkdirs();

        if (deleteOnExit)
        {
            testDir.deleteOnExit();
        }

        return testDir;
    }

    public static File createTempFile(TestCase testcase)
    {
        return createTempFile(testcase, SUFFIX);
    }

    public static File createTempFile(TestCase testcase, String suffix)
    {
        String prefix = testcase.getClass().getSimpleName() + "-" + testcase.getName();

        File tmpFile;
        try
        {
            tmpFile = File.createTempFile(prefix, suffix);
            tmpFile.deleteOnExit();
        }
        catch (IOException e)
        {
            throw new RuntimeException("Cannot create temporary file with prefix " + prefix + " and suffix " + SUFFIX, e);
        }

        return tmpFile;
    }

    /**
     * Creates a temporary file from the resource name given, using the resource name as the file suffix.
     *
     * This is required because the tests use the jar files as their class path.
     */
    public static File createTempFileFromResource(TestCase testCase, String resourceName)
    {
        File dst = createTempFile(testCase, resourceName);
        InputStream in = testCase.getClass().getResourceAsStream(resourceName);
        try
        {
            copy(in, dst);
        }
        catch (Exception e)
        {
            throw new RuntimeException("Cannot copy resource " + resourceName +
                                       " to temp file " + dst.getAbsolutePath(), e);
        }
        dst.deleteOnExit();
        return dst;
    }

    /**
     * Creates a temporary file for given test with given suffix in file name.
     * The given content is stored in the file using UTF-8 encoding.
     */
    public static File createTempFile(TestCase testcase, String suffix, String content)
    {
        File file = createTempFile(testcase, suffix);
        if (content != null)
        {
            saveTextContentInFile(content, file);
        }
        return file;
    }

    public static void saveTextContentInFile(String content, File file)
    {
        FileOutputStream fos =  null;
        try
        {
            fos = new FileOutputStream(file);
            fos.write(content.getBytes("UTF-8"));
            fos.flush();
        }
        catch (Exception e)
        {
            throw new RuntimeException("Cannot add the content into temp file " + file.getAbsolutePath(), e);
        }
        finally
        {
            if (fos != null)
            {
                try
                {
                    fos.close();
                }
                catch (IOException e)
                {
                    throw new RuntimeException("Cannot close output stream into temp file " + file.getAbsolutePath(), e);
                }
            }
        }
    }

    /**
     * Delete a given file/directory,
     * A directory will always require the recursive flag to be set.
     * if a directory is specified and recursive set then delete the whole tree
     *
     * @param file      the File object to start at
     * @param recursive boolean to recurse if a directory is specified.
     *
     * @return <code>true</code> if and only if the file or directory is
     *         successfully deleted; <code>false</code> otherwise
     */
    public static boolean delete(File file, boolean recursive)
    {
        boolean success = true;

        if (file.isDirectory())
        {
            if (recursive)
            {
                File[] files = file.listFiles();

                // This can occur if the file is deleted outside the JVM
                if (files == null)
                {
                    return false;
                }

                for (int i = 0; i < files.length; i++)
                {
                    success = delete(files[i], true) && success;
                }

                return success && file.delete();
            }

            return false;
        }

        return file.delete();
    }

    /**
     * Copies the specified InputStream to the specified destination file. If the destination file does not exist,
     * it is created.
     *
     * @param in The InputStream
     * @param dst The destination file name.
     * @throws IOException
     */
    public static void copy(InputStream in, File dst) throws IOException
    {
        if(in == null)
        {
            throw new IllegalArgumentException("Provided InputStream must not be null");
        }

        try
        {
            if (!dst.exists())
            {
                dst.createNewFile();
            }

            OutputStream out = new FileOutputStream(dst);
            
            try
            {
                // Transfer bytes from in to out
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0)
                {
                    out.write(buf, 0, len);
                }
            }
            finally
            {
                out.close();
            }
        }
        finally
        {
            in.close();
        }
    }
}
