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

import org.apache.qpid.management.domain.model.type.Type;

/**
 * Type Mapping used for associating a code with a management type.
 * 
 * @author Andrea Gazzarini
 */
class TypeMapping
{
    private final int _code;
    private final Type _type;
    private final String _validatorClass;
    
    /**
     * Builds a new type mapping with the given parameters and no validator.
     * 
     * @param code the code.
     * @param type the management type.
     */
    TypeMapping(int code, Type type)
    {
    	this(code,type,null);
    }
    
    /**
     * Builds a new type mapping with the given parameters.
     * 
     * @param code the code.
     * @param type the management type.
     * @param validatorClassName the class name of the validator to be used.
     */
    TypeMapping(int code, Type type, String validatorClassName)
    {
    	this._code = code;
    	this._type = type;
    	this._validatorClass = validatorClassName;
    }
    
    /**
     * Returns the code of this mapping.
     * 
     * @return the code of this mapping.
     */
    int getCode ()
    {
        return _code;
    }

    /**
     * Returns the type for this mapping.
     * 
     * @return the type for this mapping.
     */
    Type getType ()
    {
        return _type;
    }

    /**
     * Returns the validator class of this mapping.
     * 
     * @return the validator class (as a string) of this mapping.
     */
    public String getValidatorClassName() 
    {
        return _validatorClass;
    }
}
