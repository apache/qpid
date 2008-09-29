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
    private int _code;
    private Type _type;
    private String _validatorClass;
    
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
     * Sets the code for this mapping.
     * Note that the given string must be a valid number (integer).
     * 
     * @param codeAsString the code as a string.
     * @throws NumberFormatException when the given string is not a valid number.
     */
    void setCode (String codeAsString)
    {
      this._code = Integer.parseInt(codeAsString);
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
     * Sets the type of this mapping. 
     * 
     * @param typeClass the type class as a string.
     * @throw IllegalArgumentException when it's not possible to load the given class.
     */
    void setType (String typeClass)
    {
        try
        {
            this._type = (Type) Class.forName(typeClass).newInstance();
        } catch (Exception exception)
        {
            throw new IllegalArgumentException(exception);
        } 
    }

    /**
     * Sets the validator class that will be used for validation.
     * 
     * @param className the fully qualified name of the validation class. 
     */
    public void setValidatorClassName (String className)
    {
        this._validatorClass = className;
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