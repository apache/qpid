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
package org.apache.qpid.typedmessage;

public interface TypedBytesCodes
{
    static final byte BOOLEAN_TYPE = (byte) 1;

    static final byte BYTE_TYPE = (byte) 2;

    static final byte BYTEARRAY_TYPE = (byte) 3;

    static final byte SHORT_TYPE = (byte) 4;

    static final byte CHAR_TYPE = (byte) 5;

    static final byte INT_TYPE = (byte) 6;

    static final byte LONG_TYPE = (byte) 7;

    static final byte FLOAT_TYPE = (byte) 8;

    static final byte DOUBLE_TYPE = (byte) 9;

    static final byte STRING_TYPE = (byte) 10;

    static final byte NULL_STRING_TYPE = (byte) 11;
}
