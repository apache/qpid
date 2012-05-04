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
package org.apache.qpid.amqp_1_0.client;

import java.lang.reflect.InvocationTargetException;

public class Command
{
    public static void main(String[] args) throws
                                           ClassNotFoundException,
                                           NoSuchMethodException,
                                           InvocationTargetException,
                                           IllegalAccessException,
                                           InstantiationException
    {
        String name = args[0];
        String[] cmdArgs = new String[args.length-1];
        System.arraycopy(args,1,cmdArgs,0,args.length-1);
        name = "org.apache.qpid.amqp_1_0.client." + String.valueOf(name.charAt(0)).toUpperCase() + name.substring(1).toLowerCase();
        Class<Util> clazz = (Class<Util>) Class.forName(name);
        Util util = clazz.getDeclaredConstructor(String[].class).newInstance((Object)cmdArgs);
        util.run();

    }
}
