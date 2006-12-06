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
package org.apache.qpid.management.ui.model;

public class ParameterData
{
    private String name;
    private String description;
    private String type;
    private Object value;
    
    ParameterData(String value)
    {
        this.name = value;
    }
    
    public String getDescription()
    {
        return description;
    }
    public void setDescription(String description)
    {
        this.description = description;
    }
    
    public String getName()
    {
        return name;
    }

    public String getType()
    {
        return type;
    }
    public void setType(String type)
    {
        this.type = type;
    }

    public Object getValue()
    {
        return value;
    }
    
    public void setValueFromString(String strValue)
    {
        if ("int".equals(type))
            value = Integer.parseInt(strValue);
        else if ("boolean".equals(type))
            value = Boolean.valueOf(strValue);
        else if ("long".equals(type))
            value = Long.parseLong(strValue);
        else
            value = strValue; 
    }
    
    public void setValue(Object value)
    {
        this.value = value;
    }
}
