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


public enum MessageDeliveryPriority {

    LOWEST((short) 0),
    LOWER((short) 1),
    LOW((short) 2),
    BELOW_AVERAGE((short) 3),
    MEDIUM((short) 4),
    ABOVE_AVERAGE((short) 5),
    HIGH((short) 6),
    HIGHER((short) 7),
    VERY_HIGH((short) 8),
    HIGHEST((short) 9);

    private final short value;

    MessageDeliveryPriority(short value)
    {
        this.value = value;
    }

    public short getValue()
    {
        return value;
    }

    public static MessageDeliveryPriority get(short value)
    {
        switch (value)
        {
        case (short) 0: return LOWEST;
        case (short) 1: return LOWER;
        case (short) 2: return LOW;
        case (short) 3: return BELOW_AVERAGE;
        case (short) 4: return MEDIUM;
        case (short) 5: return ABOVE_AVERAGE;
        case (short) 6: return HIGH;
        case (short) 7: return HIGHER;
        case (short) 8: return VERY_HIGH;
        case (short) 9: return HIGHEST;
        default: throw new IllegalArgumentException("no such value: " + value);
        }
    }
}
