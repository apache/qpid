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
package org.apache.qpidity.transport;

import org.apache.qpidity.transport.network.Frame;


/**
 * SessionDelegate
 *
 * @author Rafael H. Schloming
 */

public abstract class SessionDelegate
    extends MethodDelegate<Session>
    implements ProtocolDelegate<Session>
{
    public void init(Session ssn, ProtocolHeader hdr) { }

    public void control(Session ssn, Method method) {
        method.dispatch(ssn, this);
    }

    public void command(Session ssn, Method method) {
        method.setId(ssn.nextCommandId());
        method.dispatch(ssn, this);
        if (!method.hasPayload())
        {
            ssn.processed(method);
        }
    }

    public void header(Session ssn, Header header) { }

    public void data(Session ssn, Data data) { }

    public void error(Session ssn, ProtocolError error) { }

    @Override public void executionResult(Session ssn, ExecutionResult result)
    {
        ssn.result(result.getCommandId(), result.getValue());
    }

    @Override public void sessionCompleted(Session ssn, SessionCompleted cmp)
    {
        RangeSet ranges = cmp.getCommands();
        if (ranges != null)
        {
            for (Range range : ranges)
            {
                ssn.complete(range.getLower(), range.getUpper());
            }
        }
    }

    @Override public void sessionFlush(Session ssn, SessionFlush flush)
    {
        if (flush.getCompleted())
        {
            ssn.flushProcessed();
        }
        if (flush.getConfirmed())
        {
            throw new Error("not implemented");
        }
        if (flush.getExpected())
        {
            throw new Error("not implemented");
        }
    }

    @Override public void sessionCommandPoint(Session ssn, SessionCommandPoint scp)
    {
        ssn.commandsIn = scp.getCommandId();
    }

    @Override public void executionSync(Session ssn, ExecutionSync sync)
    {
        ssn.syncPoint();
    }

}
