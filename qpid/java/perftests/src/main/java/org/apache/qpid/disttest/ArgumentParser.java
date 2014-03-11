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
package org.apache.qpid.disttest;

import java.util.Map;

public class ArgumentParser
{
    public void parseArgumentsIntoConfig(Map<String, String> initialValues, String[] args)
    {
        for(String arg: args)
        {
            String[] splitArg = arg.split("=");
            if (splitArg.length == 1 && splitArg[0].equals("-h"))
            {
                initialValues.put("-h", null);
                continue;
            }

            if(splitArg.length != 2)
            {
                throw new IllegalArgumentException("arguments must have format <name>=<value>: " + arg);
            }


            String argumentKey = splitArg[0];
            String argumentValue = splitArg[1];
            if(!initialValues.containsKey(argumentKey))
            {
                throw new IllegalArgumentException("not a valid configuration property: " + arg);
            }
            initialValues.put(argumentKey, argumentValue);
        }

    }
}
