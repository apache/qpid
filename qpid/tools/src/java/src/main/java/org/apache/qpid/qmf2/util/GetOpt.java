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
package org.apache.qpid.qmf2.util;

// Misc Imports
import java.util.List;
import java.util.ArrayList;

/**
 * Basic Java port of the python getopt function.
 *
 * Takes a standard args String array plus short form and long form options lists.
 * Searches the args for options and any option arguments and stores these in optList
 * any remaining args are stored in encArgs.
 *
 * <p>
 * Example usage (paraphrased from QpidConfig):
 * <pre>
 * String[] longOpts = {"help", "durable", "cluster-durable", "bindings", "broker-addr=", "file-count=",
 *                      "file-size=", "max-queue-size=", "max-queue-count=", "limit-policy=",
 *                      "order=", "sequence", "ive", "generate-queue-events=", "force", "force-if-not-empty",
 *                      "force-if-used", "alternate-exchange=", "passive", "timeout=", "file=", "flow-stop-size=",
 *                      "flow-resume-size=", "flow-stop-count=", "flow-resume-count=", "argument="};
 *
 * try
 * {
 *     GetOpt getopt = new GetOpt(args, "ha:bf:", longOpts);
 *     List<String[]> optList = getopt.getOptList();
 *     String[] cargs = {};
 *     cargs = getopt.getEncArgs().toArray(cargs);
 *
 *     for (String[] opt : optList)
 *     {
 *         //System.out.println(opt[0] + ":" + opt[1]);
 *         if (opt[0].equals("-a") || opt[0].equals("--broker-addr"))
 *         {
 *             _host = opt[1];
 *         }
 *         // Just a sample - more parsing would follow....
 *     }
 *
 *     int nargs = cargs.length;
 *     if (nargs == 0)
 *     {
 *         overview();
 *     }
 *     else
 *     {
 *         String cmd = cargs[0];
 *         String modifier = "";
 *
 *         if (nargs > 1)
 *         {
 *             modifier = cargs[1];
 *         }
 *
 *         if (cmd.equals("exchanges"))
 *         {
 *             if (_recursive)
 *             {
 *                 exchangeListRecurse(modifier);
 *             }
 *             else
 *             {
 *                 exchangeList(modifier);
 *             }
 *         }
 *         // Just a sample - more parsing would follow....
 *     }
 * }
 * catch (IllegalArgumentException e)
 * {
 *     System.err.println(e.toString());
 *     usage();
 * }
 * </pre>
 *
 * @author Fraser Adams
 */
public final class GetOpt
{
    private List<String[]> _optList = new ArrayList<String[]>();
    private List<String> _encArgs = new ArrayList<String>();

    /**
     * Returns the options and option arguments as a list containing a String array. The first element of the array
     * contains the option and the second contains any arguments if present.
     *
     * @return the options and option arguments
     */
    public List<String[]> getOptList()
    {
        return _optList;
    }

    /**
     * Returns any remaining arguments. This is any argument from a command line that doesn't begin "--" or "-".
     *
     * @return any remaining arguments not made available by getOptList().
     */
    public List<String> getEncArgs()
    {
        return _encArgs;
    }

    /**
     * Takes a standard args String array plus short form and long form options lists.
     * Searches the args for options and any option arguments and stores these in optList
     * any remaining args are stored in encArgs.
     *
     * @param args standard arg String array
     * @param opts short form option list of the form "ab:cd:" where b and d are options with arguments.
     * @param longOpts long form option list as an array of strings. "option=" signifies an options with arguments.
     */
    public GetOpt(String[] args, String opts, String[] longOpts) throws IllegalArgumentException
    {
        int argslength = args.length;
        for (int i = 0; i < argslength; i++)
        {
            String arg = args[i];
            if (arg.startsWith("--"))
            {
                String extractedOption = arg.substring(2);
                int nargs = _optList.size();
                for (String j : longOpts)
                {
                    String k = j.substring(0, j.length() - 1);
                    if (j.equals(extractedOption) || k.equals(extractedOption))
                    { // Handle where option and value are space separated
                        String arg0 = arg;
                        String arg1 = "";
                        if (i < argslength - 1 && k.equals(extractedOption))
                        {
                            i++;
                            arg1 = args[i];
                        }
                        String[] option = {arg0, arg1};
                        _optList.add(option);
                    }
                    else
                    { // Handle where option and value are separated by '='
                        String[] split = arg.split("=", 2); // Split on the first occurrence of '='
                        String arg0 = split[0];
                        String arg1 = "";
                        if (split.length == 2)
                        {
                            j = j.substring(0, j.length() - 1);
                            arg1 = split[1];
                        }

                        if (arg0.substring(2).equals(j))
                        {
                            String[] option = {arg0, arg1};
                            _optList.add(option);
                        }
                    }
                }

                if (nargs == _optList.size())
                {
                    throw new IllegalArgumentException("Unknown Option " + arg);
                }
            }
            else if (arg.startsWith("-"))
            {
                String extractedOption = arg.substring(1);
                int index = opts.indexOf(extractedOption);
                if (index++ != -1)
                {
                    String arg1 = "";
                    if (i < argslength - 1 && index < opts.length() && opts.charAt(index) == ':')
                    {
                        i++;
                        arg1 = args[i];
                    }
                    String[] option = {arg, arg1};
                    _optList.add(option);
                }
                else
                {
                    throw new IllegalArgumentException("Unknown Option " + arg);
                }
            }
            else
            {
                _encArgs.add(arg);
            }
        }
    } 
}
