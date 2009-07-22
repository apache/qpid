package org.apache.qpid.server.logging;/*
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

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;

import java.io.File;
import java.io.FileWriter;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.ResourceBundle;

public class GenerateLogMessages
{
    private static String _tmplDir;
    private String _outputDir;

    public static void main(String[] args)
    {
        GenerateLogMessages generator = null;
        try
        {
            generator = new GenerateLogMessages(args);
        }
        catch (IllegalAccessException iae)
        {
            System.exit(-1);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            System.exit(-1);
        }

        try
        {
            generator.run();
        }
        catch (InvalidTypeException e)
        {
            System.err.println(e.getMessage());
            System.exit(-1);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    GenerateLogMessages(String[] args) throws Exception
    {
        processArgs(args);

        if (_tmplDir == null||_outputDir == null)
        {
            showUsage();
            throw new IllegalAccessException();
        }


        /* first, we init the runtime engine.  Defaults are fine. */
        Properties props = new Properties();
        props.setProperty("file.resource.loader.path", _tmplDir);

        Velocity.init(props);
    }

    private void showUsage()
    {
        System.out.println("Broker LogMessageGenerator v.0.0");
        System.out.println("Usage: GenerateLogMessages: -t tmplDir");
        System.out.println("       where -t tmplDir: Find templates in tmplDir.");
        System.out.println("             -o outDir:  Use outDir as the output dir.");
    }

    public void run() throws InvalidTypeException, Exception
    {
        /* lets make a Context and put data into it */
        createMessageClass("Broker", "BRK");
        createMessageClass("ManagementConsole", "MNG");
        createMessageClass("VirtualHost", "VHT");
        createMessageClass("MessageStore", "MST");
        createMessageClass("Connection", "CON");
        createMessageClass("Channel", "CHN");
        createMessageClass("Queue", "QUE");
        createMessageClass("Exchange", "EXH");
        createMessageClass("Binding", "BND");
        createMessageClass("Subscription", "SUB");
    }

    /**
     * Process the args for a -t value for the template location
     *
     * @param args
     */
    private void processArgs(String[] args)
    {
        // Crude but simple...
        for (int i = 0; i < args.length; i++)
        {
            String arg = args[i];
            if (arg.charAt(0) == '-')
            {
                switch (arg.charAt(1))
                {
                    case 'o':
                    case 'O':
                        if (++i < args.length)
                        {
                            _outputDir = args[i];
                        }
                        break;
                    case 't':
                    case 'T':
                        if (++i < args.length)
                        {
                            _tmplDir = args[i];
                        }
                        break;
                }
            }
        }
    }

    private void createMessageClass(String className, String typeIdentifier)
            throws InvalidTypeException, Exception
    {
        VelocityContext context = new VelocityContext();

        HashMap<String, Object> typeData = prepareType(className, typeIdentifier);

        context.put("type", typeData);

        /* lets render a template */
        FileWriter output = new FileWriter(_outputDir + File.separator + className + "Messages.java");

        Velocity.mergeTemplate("LogMessages.vm", context, output);

        output.flush();
        output.close();
    }

    private HashMap<String, Object> prepareType(String messsageName, String messageKey) throws InvalidTypeException
    {
        ResourceBundle _messages = ResourceBundle.getBundle("org.apache.qpid.server.logging.messages.LogMessages");

        Enumeration<String> messageKeys = _messages.getKeys();

        HashMap<String, Object> typeData = new HashMap<String, Object>();
        typeData.put("name", messsageName);

        List<HashMap> messageList = new LinkedList<HashMap>();
        typeData.put("list", messageList);

        while (messageKeys.hasMoreElements())
        {
            HashMap<String, Object> item = new HashMap<String, Object>();

            //Add MessageName to amp
            String message = messageKeys.nextElement();

            if (message.startsWith(messageKey))
            {
                item.put("methodName", message.replace('-','_'));
                item.put("name", message);
                
                item.put("format", _messages.getString(message));

                String[] parametersString = _messages.getString(message).split("\\{");

                // Add P
                List<HashMap<String, String>> parameters = new LinkedList<HashMap<String, String>>();
                // Skip 0 as that will not be the first entry
                //  Text {n[,type]}
                if (parametersString.length > 1)
                {
                    for (int index = 1; index < parametersString.length; index++)
                    {
                        HashMap<String, String> parameter = new HashMap<String, String>();

                        int typeIndex = parametersString[index].indexOf(",");

                        String type;
                        if (typeIndex == -1)
                        {
                            type = "String";
                        }
                        else
                        {
                            int typeIndexEnd = parametersString[index].indexOf("}", typeIndex);
                            String typeString = parametersString[index].substring(typeIndex + 1, typeIndexEnd);
                            if (typeString.equalsIgnoreCase("number"))
                            {
                                type = "Integer";
                            }
                            else
                            {
                                throw new InvalidTypeException("Invalid type(" + typeString + ") index (" + parameter.size() + ") in message:" + _messages.getString(message));
                            }

                        }

                        parameter.put("type", type);
                        parameter.put("name", "param" + index);

                        parameters.add(parameter);
                    }
                }

                item.put("parameters", parameters);
                messageList.add(item);
            }
        }

        return typeData;
    }

    private class InvalidTypeException extends Throwable
    {
        public InvalidTypeException(String message)
        {
            super(message);
        }
    }
}