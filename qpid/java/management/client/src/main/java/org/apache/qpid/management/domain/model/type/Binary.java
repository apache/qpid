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
package org.apache.qpid.management.domain.model.type;

import java.io.Serializable;
import java.util.Arrays;
import java.util.UUID;

import org.apache.qpid.management.messages.AmqpCoDec;

/**
 * It is a simple wrapper for a byte array (for example a 128bin).
 * It is used to let QMan deal with an object instead of an array.
 * 
 * @author Andrea Gazzarini
 */
public final class Binary implements Serializable
{
    private static final long serialVersionUID = -6865585077320637567L;

    // instance identifider.
    private final UUID uuid;
    
    // Marker internal (empty) interface 
    private interface State extends Serializable{}
    
    /**
     * Internal state of this object used to denote the situation when the hashcode() method has never been called.
     * After the hashcode has been computed this class switches the state of the outer object to the next state. 
     */
    State hashCodeNotYetComputed = new State()
    {
        private static final long serialVersionUID = 221632033761266959L;

    @Override
       public int hashCode ()
       {
           hashCode = Arrays.hashCode(bytes);
           state = hashCodeAlreadyComputed;
           return hashCode;
       } 
    };
    
    /**
     * Internal state of this object used to denote the situation where the hashcode() method has already been computed.
     * Simply it returns the just computed value for the hashcode.
     */
    State hashCodeAlreadyComputed = new State() 
    {
        private static final long serialVersionUID = 221632033761266959L;
        
        @Override
        public int hashCode ()
        {
            return hashCode;
        }
    };

    private final byte [] bytes;
    private int hashCode;
    
    /** Current state (hashcode computation). */
    State state = hashCodeNotYetComputed;
    
    /**
     * Builds a new binary with the given byte array.
     * 
     * @param bytes the wrapped data.
     */
    public Binary(byte [] bytes)
    {
        this.bytes = bytes;
        uuid = UUID.randomUUID();
    }
    
    @Override
    public int hashCode ()
    {
        return state.hashCode();
    }
    
    @Override
    public boolean equals (Object obj)
    {
        try
        {
            Binary binary = (Binary)obj;
            return Arrays.equals(bytes, binary.bytes);
        } catch (Exception exception)
        {
            return false;
        }
    }
    
    /**
     * Encodes the content (wrapped byte array) of this instance using the given encoder.
     * 
     * @param encoder the encoder used to encode instance content.
     */
    public void encode(AmqpCoDec encoder)
    {
      encoder.pack(bytes);  
    }
    
    @Override
    public String toString ()
    {
        return uuid.toString();
    }
}
