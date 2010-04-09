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

import org.apache.qpid.transport.codec.BBEncoder;

public class QMFMethodResponseCommand extends QMFCommand
{
    private CompletionCode _status = null;
    private String _msg = null;

    public QMFMethodResponseCommand(final QMFMethodRequestCommand cmd, 
                                    CompletionCode status,
                                    String msg)
    {
        super( new QMFCommandHeader(cmd.getHeader().getVersion(),
                                    cmd.getHeader().getSeq(),
                                    QMFOperation.METHOD_RESPONSE));
        
        if(status == null)
        {
            _status = CompletionCode.OK;
        }
        else
        {
            _status = status;
        }
        
        _msg = msg;
    }
    
    public CompletionCode getStatus()
    {
       return _status;
    }

    public String getStatusText()
    {
       return _msg;
    }

    @Override
    public void encode(final BBEncoder encoder)
    {
        super.encode(encoder);

        encoder.writeUint32(_status.ordinal());

        if(_msg == null)
        {
            encoder.writeStr16(_status.toString());
        }
        else
        {
            encoder.writeStr16(_msg);
        }
    }
}
