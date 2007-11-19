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

import java.io.*;

/**
 * FileUtils provides some simple helper methods for working with files. It follows the convention of wrapping all
 * checked exceptions as runtimes, so code using these methods is free of try-catch blocks but does not expect to
 * recover from errors.
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Read a text file as a string.
 * <tr><td> Open a file or default resource as an input stream.
 * </table>
 */
public class FileUtils
{
    /**
     * Reads a text file as a string.
     *
     * @param filename The name of the file.
     *
     * @return The contents of the file.
     */
    public static String readFileAsString(String filename)
    {
        BufferedInputStream is = null;

        try
        {
            is = new BufferedInputStream(new FileInputStream(filename));
        }
        catch (FileNotFoundException e)
        {
            throw new RuntimeException(e);
        }

        return readStreamAsString(is);
    }

    /**
     * Reads a text file as a string.
     *
     * @param file The file.
     *
     * @return The contents of the file.
     */
    public static String readFileAsString(File file)
    {
        BufferedInputStream is = null;

        try
        {
            is = new BufferedInputStream(new FileInputStream(file));
        }
        catch (FileNotFoundException e)
        {
            throw new RuntimeException(e);
        }

        return readStreamAsString(is);
    }

    /**
     * Reads the contents of a reader, one line at a time until the end of stream is encountered, and returns all
     * together as a string.
     *
     * @param is The reader.
     *
     * @return The contents of the reader.
     */
    private static String readStreamAsString(BufferedInputStream is)
    {
        try
        {
            byte[] data = new byte[4096];

            StringBuffer inBuffer = new StringBuffer();

            String line;
            int read;

            while ((read = is.read(data)) != -1)
            {
                String s = new String(data, 0, read);
                inBuffer.append(s);
            }

            return inBuffer.toString();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * Either opens the specified filename as an input stream, or uses the default resource loaded using the
     * specified class loader, if opening the file fails or no file name is specified.
     *
     * @param filename        The name of the file to open.
     * @param defaultResource The name of the default resource on the classpath if the file cannot be opened.
     * @param cl              The classloader to load the default resource with.
     *
     * @return An input stream for the file or resource, or null if one could not be opened.
     */
    public static InputStream openFileOrDefaultResource(String filename, String defaultResource, ClassLoader cl)
    {
        InputStream is = null;

        // Flag to indicate whether the default resource should be used. By default this is true, so that the default
        // is used when opening the file fails.
        boolean useDefault = true;

        // Try to open the file if one was specified.
        if (filename != null)
        {
            try
            {
                is = new BufferedInputStream(new FileInputStream(new File(filename)));

                // Clear the default flag because the file was succesfully opened.
                useDefault = false;
            }
            catch (FileNotFoundException e)
            {
                // Ignore this exception, the default will be used instead.
            }
        }

        // Load the default resource if a file was not specified, or if opening the file failed.
        if (useDefault)
        {
            is = cl.getResourceAsStream(defaultResource);
        }

        return is;
    }

    /**
     * Copies the specified source file to the specified destintaion file. If the destinationst file does not exist,
     * it is created.
     *
     * @param src The source file name.
     * @param dst The destination file name.
     */
    public static void copy(File src, File dst)
    {
        try
        {
            InputStream in = new FileInputStream(src);
            if (!dst.exists())
            {
                dst.createNewFile();
            }

            OutputStream out = new FileOutputStream(dst);

            // Transfer bytes from in to out
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0)
            {
                out.write(buf, 0, len);
            }

            in.close();
            out.close();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }
}
