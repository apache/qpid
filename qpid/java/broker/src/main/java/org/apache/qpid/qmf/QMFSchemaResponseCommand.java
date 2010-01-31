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

import org.apache.qpid.server.virtualhost.VirtualHost;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.transport.codec.BBEncoder;

import java.util.Collection;

public class QMFSchemaResponseCommand extends QMFCommand
{
    private final QMFClass _qmfClass;


    public QMFSchemaResponseCommand(QMFSchemaRequestCommand qmfSchemaRequestCommand, QMFClass qmfClass)
    {
        super(new QMFCommandHeader(qmfSchemaRequestCommand.getHeader().getVersion(),
                                   qmfSchemaRequestCommand.getHeader().getSeq(),
                                   QMFOperation.SCHEMA_RESPONSE));
        _qmfClass = qmfClass;
    }

    @Override
    public void encode(BBEncoder encoder)
    {
        super.encode(encoder);
        encoder.writeUint8(_qmfClass.getType().getValue());
        encoder.writeStr8(_qmfClass.getPackage().getName());
        encoder.writeStr8(_qmfClass.getName());
        encoder.writeBin128(_qmfClass.getSchemaHash());

        Collection<QMFProperty> props = _qmfClass.getProperties();
        Collection<QMFStatistic> stats = _qmfClass.getStatistics();
        Collection<QMFMethod> methods = _qmfClass.getMethods();

        encoder.writeUint16(props.size());
        if(_qmfClass.getType() == QMFClass.Type.OBJECT)
        {
            encoder.writeUint16(stats.size());
            encoder.writeUint16(methods.size());
        }

        for(QMFProperty prop : props)
        {
            prop.encode(encoder);
        }

        if(_qmfClass.getType() == QMFClass.Type.OBJECT)
        {

            for(QMFStatistic stat : stats)
            {
                stat.encode(encoder);
            }

            for(QMFMethod method : methods)
            {
                method.encode(encoder);
            }
        }

    }
}
