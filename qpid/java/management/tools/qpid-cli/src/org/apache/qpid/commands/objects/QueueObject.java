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

package org.apache.qpid.commands.objects;

import javax.management.MBeanServerConnection;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.ObjectName;

public class QueueObject extends ObjectNames
{
    public QueueObject(MBeanServerConnection mbsc)
    {
        ObjectNames(mbsc);
    }

    public void setQueryString(String object, String name, String vhost)
    {
        if (name != null && vhost == null)
            querystring = "org.apache.qpid:type=VirtualHost.Queue,name=" + name + ",*";
        else if (name != null && vhost != null)
            querystring = "org.apache.qpid:type=VirtualHost.Queue,VirtualHost=" + vhost + ",name=" + name + ",*";
        else if (name == null && vhost != null)
            querystring = "org.apache.qpid:type=VirtualHost.Queue,VirtualHost=" + vhost + ",*";
        else
            querystring = "org.apache.qpid:type=VirtualHost.Queue,*";
    }

    public int getmessagecount(ObjectName queue)
    {
        int attr_count = 0;
        String value;
        Integer depth = null;

        try
        {
            MBeanInfo bean_info;
            bean_info = mbsc.getMBeanInfo(queue);
            MBeanAttributeInfo[] attr_info = bean_info.getAttributes();
            if (attr_info == null)
                return 0;
            else
            {
                for (MBeanAttributeInfo attr : attr_info)
                {
                    Object toWrite = null;
                    attr_count++;
                    toWrite = mbsc.getAttribute(queue, attr.getName());
                    if (attr_count == 7)
                    {
                        value = toWrite.toString();
                        depth = new Integer(value);
                    }
                }

            }

        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
        if (depth != null)
            return depth.intValue();
        else
            return -1;
    }

}
