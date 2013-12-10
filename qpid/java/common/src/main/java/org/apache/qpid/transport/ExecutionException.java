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


import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.qpid.transport.codec.Decoder;
import org.apache.qpid.transport.codec.Encoder;

import org.apache.qpid.transport.network.Frame;


public final class ExecutionException extends Method {

    public static final int TYPE = 771;

    public final int getStructType() {
        return TYPE;
    }

    public final int getSizeWidth() {
        return 0;
    }

    public final int getPackWidth() {
        return 2;
    }

    public final boolean hasPayload() {
        return false;
    }

    public final byte getEncodedTrack() {
        return Frame.L4;
    }

    public final boolean isConnectionControl()
    {
        return false;
    }

    private short packing_flags = 0;
    private ExecutionErrorCode errorCode;
    private int commandId;
    private short classCode;
    private short commandCode;
    private short fieldIndex;
    private String description;
    private Map<String,Object> errorInfo;


    public ExecutionException() {}


    public ExecutionException(ExecutionErrorCode errorCode, int commandId, short classCode, short commandCode, short fieldIndex, String description, Map<String,Object> errorInfo, Option ... _options) {
        if(errorCode != null) {
            setErrorCode(errorCode);
        }
        setCommandId(commandId);
        setClassCode(classCode);
        setCommandCode(commandCode);
        setFieldIndex(fieldIndex);
        if(description != null) {
            setDescription(description);
        }
        if(errorInfo != null) {
            setErrorInfo(errorInfo);
        }

        for (int i=0; i < _options.length; i++) {
            switch (_options[i]) {
            case SYNC: this.setSync(true); break;
            case BATCH: this.setBatch(true); break;
            case UNRELIABLE: this.setUnreliable(true); break;
            case NONE: break;
            default: throw new IllegalArgumentException("invalid option: " + _options[i]);
            }
        }

    }

    public <C> void dispatch(C context, MethodDelegate<C> delegate) {
        delegate.executionException(context, this);
    }


    public final boolean hasErrorCode() {
        return (packing_flags & 256) != 0;
    }

    public final ExecutionException clearErrorCode() {
        packing_flags &= ~256;
        this.errorCode = null;
        setDirty(true);
        return this;
    }

    public final ExecutionErrorCode getErrorCode() {
        return errorCode;
    }

    public final ExecutionException setErrorCode(ExecutionErrorCode value) {
        this.errorCode = value;
        packing_flags |= 256;
        setDirty(true);
        return this;
    }

    public final ExecutionException errorCode(ExecutionErrorCode value) {
        return setErrorCode(value);
    }

    public final boolean hasCommandId() {
        return (packing_flags & 512) != 0;
    }

    public final ExecutionException clearCommandId() {
        packing_flags &= ~512;
        this.commandId = 0;
        setDirty(true);
        return this;
    }

    public final int getCommandId() {
        return commandId;
    }

    public final ExecutionException setCommandId(int value) {
        this.commandId = value;
        packing_flags |= 512;
        setDirty(true);
        return this;
    }

    public final ExecutionException commandId(int value) {
        return setCommandId(value);
    }

    public final boolean hasClassCode() {
        return (packing_flags & 1024) != 0;
    }

    public final ExecutionException clearClassCode() {
        packing_flags &= ~1024;
        this.classCode = 0;
        setDirty(true);
        return this;
    }

    public final short getClassCode() {
        return classCode;
    }

    public final ExecutionException setClassCode(short value) {
        this.classCode = value;
        packing_flags |= 1024;
        setDirty(true);
        return this;
    }

    public final ExecutionException classCode(short value) {
        return setClassCode(value);
    }

    public final boolean hasCommandCode() {
        return (packing_flags & 2048) != 0;
    }

    public final ExecutionException clearCommandCode() {
        packing_flags &= ~2048;
        this.commandCode = 0;
        setDirty(true);
        return this;
    }

    public final short getCommandCode() {
        return commandCode;
    }

    public final ExecutionException setCommandCode(short value) {
        this.commandCode = value;
        packing_flags |= 2048;
        setDirty(true);
        return this;
    }

    public final ExecutionException commandCode(short value) {
        return setCommandCode(value);
    }

    public final boolean hasFieldIndex() {
        return (packing_flags & 4096) != 0;
    }

    public final ExecutionException clearFieldIndex() {
        packing_flags &= ~4096;
        this.fieldIndex = 0;
        setDirty(true);
        return this;
    }

    public final short getFieldIndex() {
        return fieldIndex;
    }

    public final ExecutionException setFieldIndex(short value) {
        this.fieldIndex = value;
        packing_flags |= 4096;
        setDirty(true);
        return this;
    }

    public final ExecutionException fieldIndex(short value) {
        return setFieldIndex(value);
    }

    public final boolean hasDescription() {
        return (packing_flags & 8192) != 0;
    }

    public final ExecutionException clearDescription() {
        packing_flags &= ~8192;
        this.description = null;
        setDirty(true);
        return this;
    }

    public final String getDescription() {
        return description;
    }

    public final ExecutionException setDescription(String value) {
        this.description = value;
        packing_flags |= 8192;
        setDirty(true);
        return this;
    }

    public final ExecutionException description(String value) {
        return setDescription(value);
    }

    public final boolean hasErrorInfo() {
        return (packing_flags & 16384) != 0;
    }

    public final ExecutionException clearErrorInfo() {
        packing_flags &= ~16384;
        this.errorInfo = null;
        setDirty(true);
        return this;
    }

    public final Map<String,Object> getErrorInfo() {
        return errorInfo;
    }

    public final ExecutionException setErrorInfo(Map<String,Object> value) {
        this.errorInfo = value;
        packing_flags |= 16384;
        setDirty(true);
        return this;
    }

    public final ExecutionException errorInfo(Map<String,Object> value) {
        return setErrorInfo(value);
    }




    public void write(Encoder enc)
    {
        enc.writeUint16(packing_flags);
        if ((packing_flags & 256) != 0)
        {
            enc.writeUint16(this.errorCode.getValue());
        }
        if ((packing_flags & 512) != 0)
        {
            enc.writeSequenceNo(this.commandId);
        }
        if ((packing_flags & 1024) != 0)
        {
            enc.writeUint8(this.classCode);
        }
        if ((packing_flags & 2048) != 0)
        {
            enc.writeUint8(this.commandCode);
        }
        if ((packing_flags & 4096) != 0)
        {
            enc.writeUint8(this.fieldIndex);
        }
        if ((packing_flags & 8192) != 0)
        {
            enc.writeStr16(this.description);
        }
        if ((packing_flags & 16384) != 0)
        {
            enc.writeMap(this.errorInfo);
        }

    }

    public void read(Decoder dec)
    {
        packing_flags = (short) dec.readUint16();
        if ((packing_flags & 256) != 0)
        {
            this.errorCode = ExecutionErrorCode.get(dec.readUint16());
        }
        if ((packing_flags & 512) != 0)
        {
            this.commandId = dec.readSequenceNo();
        }
        if ((packing_flags & 1024) != 0)
        {
            this.classCode = dec.readUint8();
        }
        if ((packing_flags & 2048) != 0)
        {
            this.commandCode = dec.readUint8();
        }
        if ((packing_flags & 4096) != 0)
        {
            this.fieldIndex = dec.readUint8();
        }
        if ((packing_flags & 8192) != 0)
        {
            this.description = dec.readStr16();
        }
        if ((packing_flags & 16384) != 0)
        {
            this.errorInfo = dec.readMap();
        }

    }

    public Map<String,Object> getFields()
    {
        Map<String,Object> result = new LinkedHashMap<String,Object>();

        if ((packing_flags & 256) != 0)
        {
            result.put("errorCode", getErrorCode());
        }
        if ((packing_flags & 512) != 0)
        {
            result.put("commandId", getCommandId());
        }
        if ((packing_flags & 1024) != 0)
        {
            result.put("classCode", getClassCode());
        }
        if ((packing_flags & 2048) != 0)
        {
            result.put("commandCode", getCommandCode());
        }
        if ((packing_flags & 4096) != 0)
        {
            result.put("fieldIndex", getFieldIndex());
        }
        if ((packing_flags & 8192) != 0)
        {
            result.put("description", getDescription());
        }
        if ((packing_flags & 16384) != 0)
        {
            result.put("errorInfo", getErrorInfo());
        }


        return result;
    }


}
