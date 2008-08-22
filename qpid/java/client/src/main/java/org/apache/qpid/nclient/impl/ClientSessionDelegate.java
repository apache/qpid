package org.apache.qpid.nclient.impl;
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


import java.nio.ByteBuffer;

import org.apache.qpid.ErrorCode;

import org.apache.qpid.nclient.MessagePartListener;

import org.apache.qpid.QpidException;
import org.apache.qpid.transport.Header;
import org.apache.qpid.transport.MessageReject;
import org.apache.qpid.transport.MessageTransfer;
import org.apache.qpid.transport.Range;
import org.apache.qpid.transport.Session;
import org.apache.qpid.transport.SessionDetached;
import org.apache.qpid.transport.SessionDelegate;


public class ClientSessionDelegate extends SessionDelegate
{

    //  --------------------------------------------
    //   Message methods
    // --------------------------------------------
    @Override public void messageTransfer(Session session, MessageTransfer xfr)
    {
        MessagePartListener listener = ((ClientSession)session).getMessageListeners()
            .get(xfr.getDestination());
        listener.messageTransfer(xfr);
    }

    @Override public void messageReject(Session session, MessageReject struct)
    {
        for (Range range : struct.getTransfers())
        {
            for (long l = range.getLower(); l <= range.getUpper(); l++)
            {
                System.out.println("message rejected: " +
                        session.getCommand((int) l));
            }
        }
        ((ClientSession)session).setRejectedMessages(struct.getTransfers());
        ((ClientSession)session).notifyException(new QpidException("Message Rejected",ErrorCode.MESSAGE_REJECTED,null));
        session.processed(struct);
    }

}
