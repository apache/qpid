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
package org.apache.qpid.management.ui.jmx;

import java.util.HashMap;
import java.util.Map;

import javax.management.ObjectName;

import org.apache.qpid.management.ui.ManagedBean;


public class JMXManagedObject extends ManagedBean
{
    private ObjectName _objName;
    
    public JMXManagedObject(ObjectName objName)
    {
        super();
        this._objName = objName;
        setUniqueName(_objName.toString());
        setDomain(_objName.getDomain());

        HashMap<String,String> props = new HashMap<String,String>(_objName.getKeyPropertyList());

        for(Map.Entry<String,String> entry : props.entrySet())
        {
            String value = entry.getValue();

            if(value != null)
            {
                try
                {
                    //if the name is quoted in the ObjectName, unquote it
                    value = ObjectName.unquote(value);
                    entry.setValue(value);
                }
                catch(IllegalArgumentException e)
                {
                    //ignore, this just means the name is not quoted
                    //and can be left unchanged
                }
            }
        }

        super.setProperties(props);
    }
    
    public ObjectName getObjectName()
    {
        return _objName;
    }
}
