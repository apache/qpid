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
package org.apache.qpidity;

import org.apache.qpidity.api.StreamingMessageListener;


/**
 * CommonSessionDelegate
 */

public class CommonSessionDelegate extends Delegate<Session>
{

    @Override public void sessionAttached(Session session, SessionAttached struct) {}

    @Override public void sessionFlow(Session session, SessionFlow struct) {}

    @Override public void sessionFlowOk(Session session, SessionFlowOk struct) {}

    @Override public void sessionClose(Session session, SessionClose struct) {}

    @Override public void sessionClosed(Session session, SessionClosed struct) {}

    @Override public void sessionResume(Session session, SessionResume struct) {}

    @Override public void sessionSuspend(Session session, SessionSuspend struct) {}

    @Override public void sessionDetached(Session session, SessionDetached struct) {}

    @Override public void messageTransfer(Session context, MessageTransfer struct)
    {
        StreamingMessageListener l = context.messagListeners.get(struct.getDestination());
        l.messageTransfer(struct.getDestination(),new Option[0]);
    }

    // ---------------------------------------------------------------
    //  Non generated methods - but would like if they are also generated.
    //  These methods should be called from Body and Header Handlers.
    //  If these methods are generated as part of the delegate then
    //  I can call these methods from the BodyHandler and HeaderHandler
    //  in a generic way

    //  I have used destination to indicate my intent of receiving
    //  some form of correlation to know which consumer this data belongs to.
    //  It can be anything as long as I can make the right correlation
    // ----------------------------------------------------------------
    public void data(Session context,String destination,byte[] src) throws QpidException
    {
        StreamingMessageListener l = context.messagListeners.get(destination);
        l.data(src);
    }

    public void endData(Session context,String destination) throws QpidException
    {
        StreamingMessageListener l = context.messagListeners.get(destination);
        l.endData();
    }

    public void messageHeaders(Session context,String destination,Header... headers) throws QpidException
    {
        StreamingMessageListener l = context.messagListeners.get(destination);
        l.endData();
    }

}
