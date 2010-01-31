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

public class QMFCommandCompletionCommand extends QMFCommand
{

    private final CompletionCode _status;
    private final String _text;

    public QMFCommandCompletionCommand(QMFCommand command)
    {
        this(command, CompletionCode.OK, "");
    }
    public QMFCommandCompletionCommand(QMFCommand command, CompletionCode status, String text)
    {
        super( new QMFCommandHeader(command.getHeader().getVersion(),
                                    command.getHeader().getSeq(),
                                    QMFOperation.COMMAND_COMPLETION));

        _status = status;
        _text = text;
    }


    @Override
    public void encode(BBEncoder encoder)
    {
        super.encode(encoder);
        encoder.writeInt32(_status.ordinal());
        encoder.writeStr8(_text);
    }
}
