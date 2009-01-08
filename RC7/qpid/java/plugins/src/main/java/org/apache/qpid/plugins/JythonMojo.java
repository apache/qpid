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
package org.apache.qpid.plugins;

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import org.python.util.jython;


/**
 * JythonMojo
 *
 * @author Rafael H. Schloming
 *
 * @goal jython
 */

public class JythonMojo extends AbstractMojo
{

    /**
     * Arguments to jython.
     *
     * @parameter
     */
    private String[] params = new String[0];

    /**
     * Source file.
     *
     * @parameter
     */
    private File[] sources;

    /**
     * Optional timestamp.
     *
     * @parameter
     */
    private File timestamp;

    public void execute() throws MojoExecutionException
    {
        boolean stale = true;

        if (sources != null && sources.length > 0 && timestamp != null)
        {
            stale = false;
            for (File source : sources)
            {
                if (source.lastModified() > timestamp.lastModified())
                {
                    stale = true;
                    break;
                }
            }
        }

        if (stale)
        {
            jython.main(params);

            if (timestamp != null)
            {
                try
                {
                    timestamp.createNewFile();
                }
                catch (IOException e)
                {
                    throw new MojoExecutionException("cannot create timestamp", e);
                }
                timestamp.setLastModified(System.currentTimeMillis());
            }
        }
    }

}
