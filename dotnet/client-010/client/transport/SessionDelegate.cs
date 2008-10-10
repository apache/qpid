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

namespace org.apache.qpid.transport
{
    /// <summary> 
    /// SessionDelegate
    /// 
    /// </summary>
    public abstract class SessionDelegate : MethodDelegate<Session>, ProtocolDelegate<Session>
    {
        public void Init(Session ssn, ProtocolHeader hdr)
        {
        }

        public void Control(Session ssn, Method method)
        {
            method.dispatch(ssn, this);
        }

        public void Command(Session ssn, Method method)
        {
            ssn.identify(method);
            method.dispatch(ssn, this);
            if (!method.hasPayload())
            {
                ssn.processed(method);
            }
        }

        public void Error(Session ssn, ProtocolError error)
        {
        }

        public override void executionResult(Session ssn, ExecutionResult result)
        {
            ssn.result(result.getCommandId(), result.getValue());
        }

        public override void executionException(Session ssn, ExecutionException exc)
        {
            ssn.addException(exc);
        }

        public override void sessionCompleted(Session ssn, SessionCompleted cmp)
        {           
                RangeSet ranges = cmp.getCommands();
                RangeSet known = null;
                if (cmp.getTimelyReply())
                {
                    known = new RangeSet();
                }

                if (ranges != null)
                {
                    foreach (Range range in ranges)
                    {
                        bool advanced = ssn.complete(range.Lower, range.Upper);
                        if (advanced && known != null)
                        {
                            known.add(range);
                        }
                    }
                }

                if (known != null)
                {
                    ssn.sessionKnownCompleted(known);
                }           
        }

        public override void sessionKnownCompleted(Session ssn, SessionKnownCompleted kcmp)
        {
            RangeSet kc = kcmp.getCommands();
            if (kc != null)
            {
                ssn.knownComplete(kc);
            }
        }

        public override void sessionFlush(Session ssn, SessionFlush flush)
        {
            if (flush.getCompleted())
            {
                ssn.flushProcessed();
            }
            if (flush.getConfirmed())
            {
                ssn.flushProcessed();
            }
            if (flush.getExpected())
            {
               // to be done
                //throw new Exception("not implemented");
            }
        }

        public override void sessionCommandPoint(Session ssn, SessionCommandPoint scp)
        {
            ssn.CommandsIn = scp.getCommandId();
        }

        public override void executionSync(Session ssn, ExecutionSync sync)
        {
            ssn.syncPoint();
        }
    }
}