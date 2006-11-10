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
package org.apache.qpid.stac;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InputStream;

public class Stac
{
    public static void main(String[] args)
    {
        BufferedReader terminal = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("\nInitializing the Scripting Tool for AMQ Console (STAC) ...");
        String var = System.getProperty("python.verbose");
        if (var != null)
        {
            System.setProperty("python.verbose", var);
        }
        else
        {
            System.setProperty("python.verbose", "warning");
        }
        StacInterpreter interp = new StacInterpreter();
        InputStream is = Stac.class.getResourceAsStream("/python/stac.py");
        if (is == null)
        {
            System.err.println("Unable to load STAC Python library. Terminating.");
            System.exit(1);
        }
        interp.execfile(is);

        boolean running = true;

        while (running)
        {
            interp.write(interp.get("commandPrompt").toString());

            String line = null;
            try
            {
                line = terminal.readLine();
                if (line != null)
                {
                    if (line.equalsIgnoreCase("quit") || line.equalsIgnoreCase("exit"))
                    {
                        running = false;
                        line = "quit()";
                    }
                    while (interp.runsource(line))
                    {
                        interp.write("...");
                        try
                        {
                            String s = terminal.readLine();
                            line = line + "\n" + s;
                        }
                        catch (IOException e)
                        {
                            e.printStackTrace();
                        }
                    }
                }
                else
                {
                    System.out.println();
                    running = false;
                }
            }
            catch (IOException ie)
            {
                System.err.println("An error occurred: " + ie);
                ie.printStackTrace(System.err);
            }
        }
        System.exit(0);
    }
}
