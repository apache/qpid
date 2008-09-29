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
package org.apache.qpid.management.configuration;

/**
 * Parser used for building access mode mappings.
 * For each access-mode-mappings/mapping element found in the configuration file, a new access mode mapping 
 * is built and injected into the configuration.
 * 
 *<mapping>
        <code>1</code>
       <value>RC</value>
   </mapping>
 * 
 * @author Andrea Gazzarini
 */
class AccessModeMappingParser implements IParser
{
    private AccessModeMapping  _mapping = new AccessModeMapping();
    private String _currentValue;
    
    /**
     * Callback : the given value is the text content of the current node.
     */
    public void setCurrrentAttributeValue (String value)
    {
        this._currentValue = value;
    }

    /**
     * Callback: each time the end of an element is reached this method is called.
     * It's here that the built mapping is injected into the configuration.
     */
    public void setCurrentAttributeName (String name)
    {
        switch (Tag.get(name))
        {
            case CODE: 
            {
                _mapping.setCode(_currentValue);
                break;
            }
            case VALUE : 
            {
                _mapping.setAccessMode(_currentValue);
                break;
            }
            case MAPPING: 
            {
                Configuration.getInstance().addAccessModeMapping(_mapping);
                _mapping = new AccessModeMapping();
                break;
            }
        }
    }
}
