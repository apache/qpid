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
package org.apache.qpid.example.jmsexample.common;

import java.util.Enumeration;
import java.util.Properties;

/**
 * Command-line argument processor.
 */
public class ArgProcessor
{
    /** Textual representation of program using parser. */
    private String _id;

    /** Command line arguments to parse. */
    private String[] _argv;

    /** Properties table mapping argument name to description */
    private Properties _options;

    /** Properties table mapping argument name to default value */
    private Properties _defaults;

    /** Properties table containing parsed arguments */
    private Properties _parsed;

    /**
     * Create an argument parser and parse the supplied arguments. If the arguments
     * cannot be parsed (or the argument <code>-help</code> is supplied) then
     * exit
     *
     * @param id textual representation of program identity using parser.
     * @param argv the argument array.
     * @param options list of allowable options stored in the keys.
     * @param defaults list of option default values keyed on option name.
     */
    public ArgProcessor(String id, String[] argv, Properties options, Properties defaults)
    {
        _id = id;
        _argv = argv;
        _options = options;
        _defaults = defaults;
        // Try to parse. If we can't then exit
        if (!parse())
        {
             System.exit(0);
        }
    }

    /**
     * Get the processed arguments.
     * @return Properties table mapping argument name to current value, eg, ["-foo", "MyFoo"].
     */
    public Properties getProcessedArgs()
    {
        return _parsed;
    }

    /**
     * Display the current property settings on the supplied stream.
     * Output sent to <code>System.out</code>.
     */
    public void display()
    {
        System.out.println(_id + " current settings:");
        Enumeration optionEnumeration = _options.keys();
        while (optionEnumeration.hasMoreElements())
        {
            String option = (String) optionEnumeration.nextElement();
            String description = (String) _options.get(option);
            String currentValue = (String) _parsed.get(option);
            if (currentValue != null)
            {
                System.out.println("\t" + description + " = " + currentValue);
            }
        }
        System.out.println();
    }

    /**
     * Get the value of the specified option as a String.
     * @param option the option to query.
     * @return the value of the option.
     */
    public String getStringArgument(String option)
    {
        return _parsed.getProperty(option);
    }

    /**
     * Get the value of the specified option as an integer.
     * @param option the option to query.
     * @return the value of the option.
     */
    public int getIntegerArgument(String option)
    {
        String value = _parsed.getProperty(option);
        return Integer.parseInt(value);
    }

       /**
     * Get the value of the specified option as a boolean.
     * @param option the option to query.
     * @return the value of the option.
     */
    public boolean getBooleanArgument(String option)
    {
        String value = _parsed.getProperty(option);
        return Boolean.valueOf(value);
    }

    /**
     * Parse the arguments.
     * @return true if parsed.
     */
    private boolean parse()
    {
        boolean parsed = false;
        _parsed = new Properties();
        if ((_argv.length == 1) && (_argv[0].equalsIgnoreCase("-help")))
        {
            displayHelp();
        }
        else
        {
            // Parse argv looking for options putting the results in results
            for (int i = 0; i < _argv.length; i++)
            {
                String arg = _argv[i];
                if (arg.equals("-help"))
                {
                    continue;
                }
                if (!arg.startsWith("-"))
                {
                    System.err.println(_id + ": unexpected argument: " + arg);
                }
                else
                {
                    if (_options.containsKey(arg))
                    {
                        if (i == _argv.length - 1 || _argv[i + 1].startsWith("-"))
                        {
                            System.err.println(_id + ": missing value argument for: " + arg);
                        }
                        else
                        {
                            _parsed.put(arg, _argv[++i]);
                        }
                    }
                    else
                    {
                        System.err.println(_id + ": unrecognised option: " + arg);
                    }
                }
            }

            // Now add the default values if none have been specified in aggv
            Enumeration optionsEnum = _options.keys();
            while (optionsEnum.hasMoreElements())
            {
                String option = (String) optionsEnum.nextElement();

                if (_parsed.get(option) == null)
                {
                    String defaultValue = (String) _defaults.get(option);

                    if (defaultValue != null)
                    {
                        _parsed.put(option, defaultValue);
                    }
                }
            }
            parsed = true;
        }
        return parsed;
    }

    /**
     * Display all options with descriptions and default values (if specified).
     * Output is sent to <code>System.out</code>.
     */
    private void displayHelp()
    {
        System.out.println(_id + " available options:");
        Enumeration optionEnumeration = _options.keys();
        while (optionEnumeration.hasMoreElements())
        {
            String option = (String) optionEnumeration.nextElement();
            String value = (String) _options.get(option);
            String defaultValue = (String) _defaults.get(option);
            if (defaultValue != null)
            {
                System.out.println("\t" + option + " <" + value + "> [" + defaultValue + "]");
            }
            else
            {
                System.out.println("\t" + option + " <" + value + ">");
            }
        }
    }
}
