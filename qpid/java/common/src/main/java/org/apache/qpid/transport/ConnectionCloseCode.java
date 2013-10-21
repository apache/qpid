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


public enum ConnectionCloseCode {

    NORMAL((int) 200),
    CONNECTION_FORCED((int) 320),
    INVALID_PATH((int) 402),
    FRAMING_ERROR((int) 501);

    private final int value;

    ConnectionCloseCode(int value)
    {
        this.value = value;
    }

    public int getValue()
    {
        return value;
    }

    public static ConnectionCloseCode get(int value)
    {
        switch (value)
        {
        case (int) 200: return NORMAL;
        case (int) 320: return CONNECTION_FORCED;
        case (int) 402: return INVALID_PATH;
        case (int) 501: return FRAMING_ERROR;
        default: throw new IllegalArgumentException("no such value: " + value);
        }
    }
}
