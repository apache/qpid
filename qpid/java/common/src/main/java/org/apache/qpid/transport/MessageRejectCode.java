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


public enum MessageRejectCode {

    UNSPECIFIED((int) 0),
    UNROUTABLE((int) 1),
    IMMEDIATE((int) 2);

    private final int value;

    MessageRejectCode(int value)
    {
        this.value = value;
    }

    public int getValue()
    {
        return value;
    }

    public static MessageRejectCode get(int value)
    {
        switch (value)
        {
        case (int) 0: return UNSPECIFIED;
        case (int) 1: return UNROUTABLE;
        case (int) 2: return IMMEDIATE;
        default: throw new IllegalArgumentException("no such value: " + value);
        }
    }
}
