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

import org.apache.qpid.management.domain.model.AccessMode;

/**
 * Class used to encapsulate a mapping between an access mode and a code.
 * 
 * @author Andrea Gazzarini
 */
class AccessModeMapping
{
    private final int _code;
    private final AccessMode _accessMode;
    
    /**
     * Builds a new access mode mapping with the given parameters.
     * 
     * @param code the access code. 
     * @param accessMode the access mode.
     */
    AccessModeMapping(int code, AccessMode accessMode)
    {
    	this._code = code;
    	this._accessMode = accessMode;
    }
    
    /**
     * Returns the access mode of this mapping.
     * 
     * @return the access mode of this mapping.
     */
    AccessMode getAccessMode ()
    {
        return _accessMode;
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
     * Returns a string representation of this mapping.
     * The returned string is indicating the code and the corresponding access mode.
     * 
     * @return a string representation of this mapping.
     */
    @Override
    public String toString ()
    {
        return new StringBuilder()
            .append("AccessMode mapping (")
            .append(_code)
            .append(',')
            .append(_accessMode)
            .append(')').toString();
    }
}
