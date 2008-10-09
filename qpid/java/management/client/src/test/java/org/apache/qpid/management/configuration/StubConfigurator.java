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

import org.apache.qpid.management.domain.model.type.Str8;

public class StubConfigurator extends Configurator
{
    /**
     * Builds whole configuration.
     * 
     * @throws ConfigurationException when the build fails.
     */
    public void configure() throws ConfigurationException 
    {
        addAccessModeMapping("1", "RW");
        addAccessModeMapping("2", "RO");
        addAccessModeMapping("3", "RC");
        
        addTypeMapping("1", Str8.class.getName());
    }
    
    public void addTypeMapping(String code,String clazzName)
    {
        TypeMapping mapping = new TypeMapping();
        mapping.setCode(code);
        mapping.setType(clazzName);
        Configuration.getInstance().addTypeMapping(mapping);        
    }
    
    public void addAccessModeMapping(String code, String value)
    {
        AccessModeMapping mapping = new AccessModeMapping();
        mapping.setCode(code);;
        mapping.setAccessMode(value);
        Configuration.getInstance().addAccessModeMapping(mapping);        
    }
}
