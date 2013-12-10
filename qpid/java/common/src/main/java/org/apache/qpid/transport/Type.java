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




public enum Type
{

    BIN8((byte) 0x00, 1, true),
    INT8((byte) 0x01, 1, true),
    UINT8((byte) 0x02, 1, true),
    CHAR((byte) 0x04, 1, true),
    BOOLEAN((byte) 0x08, 1, true),
    BIN16((byte) 0x10, 2, true),
    INT16((byte) 0x11, 2, true),
    UINT16((byte) 0x12, 2, true),
    BIN32((byte) 0x20, 4, true),
    INT32((byte) 0x21, 4, true),
    UINT32((byte) 0x22, 4, true),
    FLOAT((byte) 0x23, 4, true),
    CHAR_UTF32((byte) 0x27, 4, true),
    BIN64((byte) 0x30, 8, true),
    INT64((byte) 0x31, 8, true),
    UINT64((byte) 0x32, 8, true),
    DOUBLE((byte) 0x33, 8, true),
    DATETIME((byte) 0x38, 8, true),
    BIN128((byte) 0x40, 16, true),
    UUID((byte) 0x48, 16, true),
    BIN256((byte) 0x50, 32, true),
    BIN512((byte) 0x60, 64, true),
    BIN1024((byte) 0x70, 128, true),
    VBIN8((byte) 0x80, 1, false),
    STR8_LATIN((byte) 0x84, 1, false),
    STR8((byte) 0x85, 1, false),
    STR8_UTF16((byte) 0x86, 1, false),
    VBIN16((byte) 0x90, 2, false),
    STR16_LATIN((byte) 0x94, 2, false),
    STR16((byte) 0x95, 2, false),
    STR16_UTF16((byte) 0x96, 2, false),
    VBIN32((byte) 0xa0, 4, false),
    MAP((byte) 0xa8, 4, false),
    LIST((byte) 0xa9, 4, false),
    ARRAY((byte) 0xaa, 4, false),
    STRUCT32((byte) 0xab, 4, false),
    BIN40((byte) 0xc0, 5, true),
    DEC32((byte) 0xc8, 5, true),
    BIN72((byte) 0xd0, 9, true),
    DEC64((byte) 0xd8, 9, true),
    VOID((byte) 0xf0, 0, true),
    BIT((byte) 0xf1, 0, true);

    private final byte code;
    private final int width;
    private final boolean fixed;

    Type(byte code, int width, boolean fixed)
    {
        this.code = code;
        this.width = width;
        this.fixed = fixed;
    }

    public byte getCode()
    {
        return code;
    }

    public int getWidth()
    {
        return width;
    }

    public boolean isFixed()
    {
        return fixed;
    }

    public static Type get(byte code)
    {
        switch (code)
        {
        case (byte) 0x00: return BIN8;
        case (byte) 0x01: return INT8;
        case (byte) 0x02: return UINT8;
        case (byte) 0x04: return CHAR;
        case (byte) 0x08: return BOOLEAN;
        case (byte) 0x10: return BIN16;
        case (byte) 0x11: return INT16;
        case (byte) 0x12: return UINT16;
        case (byte) 0x20: return BIN32;
        case (byte) 0x21: return INT32;
        case (byte) 0x22: return UINT32;
        case (byte) 0x23: return FLOAT;
        case (byte) 0x27: return CHAR_UTF32;
        case (byte) 0x30: return BIN64;
        case (byte) 0x31: return INT64;
        case (byte) 0x32: return UINT64;
        case (byte) 0x33: return DOUBLE;
        case (byte) 0x38: return DATETIME;
        case (byte) 0x40: return BIN128;
        case (byte) 0x48: return UUID;
        case (byte) 0x50: return BIN256;
        case (byte) 0x60: return BIN512;
        case (byte) 0x70: return BIN1024;
        case (byte) 0x80: return VBIN8;
        case (byte) 0x84: return STR8_LATIN;
        case (byte) 0x85: return STR8;
        case (byte) 0x86: return STR8_UTF16;
        case (byte) 0x90: return VBIN16;
        case (byte) 0x94: return STR16_LATIN;
        case (byte) 0x95: return STR16;
        case (byte) 0x96: return STR16_UTF16;
        case (byte) 0xa0: return VBIN32;
        case (byte) 0xa8: return MAP;
        case (byte) 0xa9: return LIST;
        case (byte) 0xaa: return ARRAY;
        case (byte) 0xab: return STRUCT32;
        case (byte) 0xc0: return BIN40;
        case (byte) 0xc8: return DEC32;
        case (byte) 0xd0: return BIN72;
        case (byte) 0xd8: return DEC64;
        case (byte) 0xf0: return VOID;
        case (byte) 0xf1: return BIT;

        default: return null;
        }
    }
}
