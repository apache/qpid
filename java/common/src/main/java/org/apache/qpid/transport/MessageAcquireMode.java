package org.apache.qpid.transport;
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


public enum MessageAcquireMode {

    PRE_ACQUIRED((short) 0),
    NOT_ACQUIRED((short) 1);

    private final short value;

    MessageAcquireMode(short value)
    {
        this.value = value;
    }

    public short getValue()
    {
        return value;
    }

    public static MessageAcquireMode get(short value)
    {
        switch (value)
        {
        case (short) 0: return PRE_ACQUIRED;
        case (short) 1: return NOT_ACQUIRED;
        default: throw new IllegalArgumentException("no such value: " + value);
        }
    }
}
