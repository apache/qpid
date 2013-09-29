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


public enum ExecutionErrorCode {

    UNAUTHORIZED_ACCESS((int) 403),
    NOT_FOUND((int) 404),
    RESOURCE_LOCKED((int) 405),
    PRECONDITION_FAILED((int) 406),
    RESOURCE_DELETED((int) 408),
    ILLEGAL_STATE((int) 409),
    COMMAND_INVALID((int) 503),
    RESOURCE_LIMIT_EXCEEDED((int) 506),
    NOT_ALLOWED((int) 530),
    ILLEGAL_ARGUMENT((int) 531),
    NOT_IMPLEMENTED((int) 540),
    INTERNAL_ERROR((int) 541),
    INVALID_ARGUMENT((int) 542);

    private final int value;

    ExecutionErrorCode(int value)
    {
        this.value = value;
    }

    public int getValue()
    {
        return value;
    }

    public static ExecutionErrorCode get(int value)
    {
        switch (value)
        {
        case (int) 403: return UNAUTHORIZED_ACCESS;
        case (int) 404: return NOT_FOUND;
        case (int) 405: return RESOURCE_LOCKED;
        case (int) 406: return PRECONDITION_FAILED;
        case (int) 408: return RESOURCE_DELETED;
        case (int) 409: return ILLEGAL_STATE;
        case (int) 503: return COMMAND_INVALID;
        case (int) 506: return RESOURCE_LIMIT_EXCEEDED;
        case (int) 530: return NOT_ALLOWED;
        case (int) 531: return ILLEGAL_ARGUMENT;
        case (int) 540: return NOT_IMPLEMENTED;
        case (int) 541: return INTERNAL_ERROR;
        case (int) 542: return INVALID_ARGUMENT;
        default: throw new IllegalArgumentException("no such value: " + value);
        }
    }
}
