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


public enum DtxXaStatus {

    XA_OK((int) 0),
    XA_RBROLLBACK((int) 1),
    XA_RBTIMEOUT((int) 2),
    XA_HEURHAZ((int) 3),
    XA_HEURCOM((int) 4),
    XA_HEURRB((int) 5),
    XA_HEURMIX((int) 6),
    XA_RDONLY((int) 7);

    private final int value;

    DtxXaStatus(int value)
    {
        this.value = value;
    }

    public int getValue()
    {
        return value;
    }

    public static DtxXaStatus get(int value)
    {
        switch (value)
        {
        case (int) 0: return XA_OK;
        case (int) 1: return XA_RBROLLBACK;
        case (int) 2: return XA_RBTIMEOUT;
        case (int) 3: return XA_HEURHAZ;
        case (int) 4: return XA_HEURCOM;
        case (int) 5: return XA_HEURRB;
        case (int) 6: return XA_HEURMIX;
        case (int) 7: return XA_RDONLY;
        default: throw new IllegalArgumentException("no such value: " + value);
        }
    }
}
