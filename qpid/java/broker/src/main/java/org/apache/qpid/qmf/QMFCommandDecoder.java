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

package org.apache.qpid.qmf;

import org.apache.qpid.transport.codec.BBDecoder;

import java.nio.ByteBuffer;

public class QMFCommandDecoder
{
    private BBDecoder _decoder;


    private static final QMFOperation[] OP_CODES = new QMFOperation[256];
    private final QMFService _qmfService;

    static
    {
        for(QMFOperation op : QMFOperation.values())
        {
            OP_CODES[op.getOpcode()] = op;
        }
    }

    public QMFCommandDecoder(final QMFService qmfService, ByteBuffer buf)
    {
        _qmfService = qmfService;
        _decoder = new BBDecoder();
        _decoder.init(buf);
    }

    public QMFCommand decode()
    {
        if(_decoder.hasRemaining())
        {
            QMFCommandHeader header = readQMFHeader();

            switch(header.getOperation())
            {
                case BROKER_REQUEST:
                     return new QMFBrokerRequestCommand(header, _decoder);
                case PACKAGE_QUERY:
                     return new QMFPackageQueryCommand(header, _decoder);
                case CLASS_QUERY:
                     return new QMFClassQueryCommand(header, _decoder);
                case SCHEMA_REQUEST:
                     return new QMFSchemaRequestCommand(header, _decoder);
                case METHOD_REQUEST:
                     return new QMFMethodRequestCommand(header, _decoder, _qmfService);
                case GET_QUERY:
                     return new QMFGetQueryCommand(header, _decoder);
                default:
                    System.out.println("Unknown command");

            }

            return null;
        }
        else
        {
            return null;
        }
    }

    private QMFCommandHeader readQMFHeader()
    {
        if(_decoder.readInt8() == (byte) 'A'
            && _decoder.readInt8() == (byte) 'M')
        {
            byte version = _decoder.readInt8();
            short opCode = _decoder.readUint8();
            int seq = _decoder.readInt32();

            return new QMFCommandHeader(version, seq, OP_CODES[opCode]);

        }
        return null;
    }
}
