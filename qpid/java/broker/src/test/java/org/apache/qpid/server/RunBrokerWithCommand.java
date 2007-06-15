/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.    
 *
 * 
 */
package org.apache.qpid.server;

import org.apache.log4j.Logger;
import org.apache.log4j.Level;

import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

public class RunBrokerWithCommand
{
    public static void main(String[] args)
    {
        //Start broker

        try
        {

            String[] fudge = new String[1];
            fudge[0] = "-v";
            new Main(fudge).startup();
        }
        catch (Exception e)
        {
            System.out.println("Unable to start broker due to: " + e.getMessage());

            e.printStackTrace();
            exit(1);
        }

        Logger.getRootLogger().setLevel(Level.ERROR);

        //run command
        try
        {
            Process task = Runtime.getRuntime().exec(args[0]);
            System.out.println("Started Proccess: " + args[0]);

            InputStream inputStream = task.getInputStream();

            InputStream errorStream = task.getErrorStream();

            Thread out = new Thread(new Outputter("[OUT]", new BufferedReader(new InputStreamReader(inputStream))));
            Thread err = new Thread(new Outputter("[ERR]", new BufferedReader(new InputStreamReader(errorStream))));

            out.start();
            err.start();

            out.join();
            err.join();

            System.out.println("Waiting for process to exit: " + args[0]);
            task.waitFor();
            System.out.println("Done Proccess: " + args[0]);

        }
        catch (IOException e)
        {
            System.out.println("Proccess had problems: " + e.getMessage());
            exit(1);
        }
        catch (InterruptedException e)
        {
            System.out.println("Proccess had problems: " + e.getMessage());

            exit(1);
        }


        exit(0);
    }

    private static void exit(int i)
    {
        Logger.getRootLogger().setLevel(Level.INFO);
        System.exit(i);
    }

    static class Outputter implements Runnable
    {

        BufferedReader reader;
        String prefix;

        Outputter(String s, BufferedReader r)
        {
            prefix = s;
            reader = r;
        }

        public void run()
        {
            String line;
            try
            {
                while ((line = reader.readLine()) != null)
                {
                    System.out.println(prefix + line);
                }
            }
            catch (IOException e)
            {
                System.out.println("Error occured reading; " + e.getMessage());
            }
        }

    }

}
