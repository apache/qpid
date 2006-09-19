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

import org.apache.qpid.management.jmx.AMQConsole;
import org.apache.qpid.stac.jmx.MBeanServerConnectionContext;

public class ConnectionTest
{
    public static void main(String[] args) throws Exception
    {

        AMQConsole console = new AMQConsole("localhost", 5672, "guest", "guest", "/test");
        console.initialise();
        console.registerAllMBeans();
        MBeanServerConnectionContext connectionContext = new MBeanServerConnectionContext();
        connectionContext.connect();
        connectionContext.ls();
    }
}
