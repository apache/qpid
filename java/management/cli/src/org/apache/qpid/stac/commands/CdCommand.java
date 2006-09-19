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
package org.apache.qpid.stac.commands;

import org.apache.qpid.stac.jmx.MBeanServerConnectionContext;
import org.apache.qpid.stac.jmx.CurrentMBean;
import org.apache.qpid.AMQException;

public class CdCommand
{
    public static void execute(MBeanServerConnectionContext context, String destination)
            throws AMQException
    {
        // check if it is an absolute path and if so change to the root first
        if (destination.startsWith("/"))
        {
            context.changeBean("/");
            destination = destination.substring(1);
        }
        if (destination.length() == 0)
        {
            return;
        }
        String[] destinations = destination.split("/");
        for (String item : destinations)
        {
            if ("..".equals(item))
            {
                item = CurrentMBean.PARENT_ATTRIBUTE;
            }
            context.changeBean(item);
        }
    }

}
