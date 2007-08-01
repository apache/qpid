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

    @Override public void sessionPing(Session session, SessionPing struct) {}

    @Override public void sessionPong(Session session, SessionPong struct) {}

    @Override public void sessionSuspend(Session session, SessionSuspend struct) {}

    @Override public void sessionDetached(Session session, SessionDetached struct) {}

}
