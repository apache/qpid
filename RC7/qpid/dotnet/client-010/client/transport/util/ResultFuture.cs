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

using System;
using System.Threading;
using org.apache.qpid.transport;
using org.apache.qpid.transport.util;

namespace common.org.apache.qpid.transport.util
{
    public class ResultFuture : Future
    {
        const long _timeout = 60000;
        private Struct _result;
        private Session _session;
        private static readonly Logger log = Logger.get(typeof(ResultFuture));

        public Struct get(long timeout)
        {
            lock (this)
            {
                DateTime start = DateTime.Now;
                long elapsed = 0;
                while (! _session.Closed && timeout - elapsed > 0 && _result == null)
                {
                        log.debug("{0} waiting for result: {1}", _session, this );
                        Monitor.Wait(this, (int) (timeout - elapsed));
                        elapsed = (long) (DateTime.Now.Subtract(start)).TotalMilliseconds;
                }
            }
            if( _session.Closed )
            {
                throw new SessionException(_session.getExceptions());
            }
           return _result;
        }

        public Struct Result
        {
            get { return get(_timeout); }
            set
            {
                lock (this)
                {
                    _result = value;
                    Monitor.PulseAll(this);
                }
            }
        }

        public Session Session
        {
            set { _session = value; }
        }

        public String toString()
        {
            return String.Format("Future({0})", _result);
        }

    }
}
