/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.qpid.jndi.referenceabletest;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import java.io.File;
import java.util.Hashtable;

/**
 * Usage: To run these you need to have the sun JNDI SPI for the FileSystem.
 * This can be downloaded from sun here:
 * http://java.sun.com/products/jndi/downloads/index.html
 * Click : Download JNDI 1.2.1 & More button
 * Download: File System Service Provider, 1.2 Beta 3
 * and add the two jars in the lib dir to your class path.
 * <p/>
 * Also you need to create the directory /temp/qpid-jndi-test
 */
class Unbind
{
    public static final String DEFAULT_PROVIDER_FILE_PATH = System.getProperty("java.io.tmpdir") + "/JNDITest";
    public static final String PROVIDER_URL = "file://" + DEFAULT_PROVIDER_FILE_PATH;

    boolean _unbound = false;

    public Unbind()
    {
        this(false);
    }

    public Unbind(boolean output)
    {

        // Set up the environment for creating the initial context
        Hashtable env = new Hashtable(11);
        env.put(Context.INITIAL_CONTEXT_FACTORY,
                "com.sun.jndi.fscontext.RefFSContextFactory");
        env.put(Context.PROVIDER_URL, PROVIDER_URL);

        File file = new File(PROVIDER_URL.substring(PROVIDER_URL.indexOf("://") + 3));

        if (file.exists() && !file.isDirectory())
        {
            System.out.println("Couldn't make directory file already exists");
            return;
        }
        else
        {
            if (!file.exists())
            {
                if (!file.mkdirs())
                {
                    System.out.println("Couldn't make directory");
                    return;
                }
            }
        }

        try
        {
            // Create the initial context
            Context ctx = new InitialContext(env);

            // Remove the binding
            ctx.unbind("ConnectionFactory");
            ctx.unbind("Connection");
            ctx.unbind("Topic");

            // Check that it is gone
            Object obj = null;
            try
            {
                obj = ctx.lookup("ConnectionFactory");
            }
            catch (NameNotFoundException ne)
            {
                if (output)
                {
                    System.out.println("unbind ConnectionFactory successful");
                }
                try
                {
                    obj = ctx.lookup("Connection");
                }
                catch (NameNotFoundException ne2)
                {
                    if (output)
                    {
                        System.out.println("unbind Connection successful");
                    }

                    try
                    {
                        obj = ctx.lookup("Topic");
                    }
                    catch (NameNotFoundException ne3)
                    {
                        if (output)
                        {
                            System.out.println("unbind Topic successful");
                        }
                        _unbound = true;
                    }
                }
            }

            //System.out.println("unbind failed; object still there: " + obj);

            // Close the context when we're done
            ctx.close();
        }
        catch (NamingException e)
        {
            System.out.println("Operation failed: " + e);
        }
    }

    public boolean unbound()
    {
        return _unbound;
    }

    public static void main(String[] args)
    {

        new Unbind(true);
    }
}

