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
package org.apache.qpid.management.domain.model;

import java.util.LinkedList;
import java.util.List;

import org.apache.qpid.transport.codec.ManagementEncoder;

/**
 * Qpid method definition.
 * An entity describing an invocation that can be made on a managed object instance.
 * 
 * @author Andrea Gazzarini
 */
class QpidMethod extends QpidFeature
{
    /** Argument list */
    List<QpidArgument> arguments = new LinkedList<QpidArgument>();
    
    /**
     * Builds a new qpid method definition with the given name and description.
     * 
     * @param name the method name.
     * @param description the method description.
     */
    QpidMethod(String name, String description)
    {
        this._name = name;
        this._description = description;
    }
    
    /**
     * Adds an argument to this method.
     * 
     * @param argument the new argument to be added.
     */
    void addArgument(QpidArgument argument) 
    {
        arguments.add(argument);
    }
    
    /**
     * Returns a string representation of this method. 
     * The result format is <method name>(argType1 argName1 (Direction), argType2 argName2 (Direction), etc...)
     * 
     * @return a string representation of this method.
     */
    @Override
    public String toString ()
    {
        StringBuilder builder = new StringBuilder()
            .append(_name)
            .append('(');
        
        for (QpidArgument argument : arguments)
        {
            builder.append(argument).append(',');
        }
        
        builder.append(')');
        return builder.toString();
    }

    /**
     * Encodes the given parameter values according to this method arguments definitions.
     * Also provide a validation of the given values according to the invariants defined for each argument.
     * Note that only Input/Output and Output parameters are encoded.
     * 
     * @param parameters the parameters values.
     * @param encoder the encoder used for encoding.
     * @throws ValidationException when one of the given values is violating an argument invariant.
     */
    void encodeParameters (Object[] parameters, ManagementEncoder encoder) throws ValidationException
    {
        int index = 0;
        for (QpidArgument argument : arguments)
        {
            if (argument.getDirection() != Direction.O)
            {
                argument.validateAndEncode(parameters[index++],encoder);
            }
        }
    }
}