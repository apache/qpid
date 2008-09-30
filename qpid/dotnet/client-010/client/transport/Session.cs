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
using System.Collections.Generic;
using System.IO;
using System.Text;
using System.Threading;
using org.apache.qpid.transport.util;
using Frame = org.apache.qpid.transport.network.Frame;
using Logger = org.apache.qpid.transport.util.Logger;


namespace org.apache.qpid.transport
{
    /// <summary>
    ///  Session
    /// 
    /// </summary>
    public class Session : Invoker
    {
        private static readonly Logger log = Logger.get(typeof (Session));
        private static readonly bool ENABLE_REPLAY;

        static Session()
        {
            const string enableReplay = "enable_command_replay";
            try
            {
                String var = Environment.GetEnvironmentVariable(enableReplay);
                if (var != null)
                {
                    ENABLE_REPLAY = bool.Parse(var);
                }
            }
            catch (Exception)
            {
                ENABLE_REPLAY = false;
            }
        }

        private readonly byte[] _name;
        private const long _timeout = 600000;
        private bool _autoSync = false;

        // channel may be null
        private Channel _channel;

        // incoming command count
        private int _commandsIn = 0;
        // completed incoming commands
        private readonly Object _processedLock = new Object();
        private RangeSet _processed = new RangeSet();
        private int _maxProcessed = - 1;
        private int _syncPoint = -1;

        // outgoing command count
        private int _commandsOut = 0;
        private readonly Dictionary<int, Method> _commands = new Dictionary<int, Method>();
        private int _maxComplete = - 1;
        private bool _needSync = false;
        private bool _closed;
        private readonly Dictionary<int, Future> _results = new Dictionary<int, Future>();
        private readonly List<ExecutionException> _exceptions = new List<ExecutionException>();


        public bool Closed
        {
            get
            {
                lock (this)
                {
                    return _closed;
                }
            }
            set
            {
                lock (this)
                {
                    _closed = value;
                }
            }
        }

        public string Name
        {
            get
            {
                ASCIIEncoding enc = new ASCIIEncoding();
                return enc.GetString(_name);
            }
        }

        public Session(byte[] name)
        {
            _name = name;
        }

        public byte[] getName()
        {
            return _name;
        }

        public void setAutoSync(bool value)
        {
            lock (_commands)
            {
                _autoSync = value;
            }
        }

        public Dictionary<int, Method> getOutstandingCommands()
        {
            return _commands;
        }

        public int getCommandsOut()
        {
            return _commandsOut;
        }

        public int CommandsIn
        {
            get { return _commandsIn; }
            set { _commandsIn = value; }
        }

        public int nextCommandId()
        {
            return _commandsIn++;
        }

        public void identify(Method cmd)
        {
            int id = nextCommandId();
            cmd.Id = id;

            if (log.isDebugEnabled())
            {
                log.debug("ID: [{0}] %{1}", _channel, id);
            }

            //if ((id % 65536) == 0)
            if ((id & 0xff) == 0)
            {
                flushProcessed(Option.TIMELY_REPLY);
            }
        }

        public void processed(Method command)
        {
            processed(command.Id);
        }

        public void processed(int command)
        {
            processed(new Range(command, command));
        }

        public void processed(int lower, int upper)
        {
            processed(new Range(lower, upper));
        }

        public void processed(Range range)
        {
            log.debug("{0} processed({1})", this, range);

            bool flush;
            lock (_processedLock)
            {
                _processed.add(range);
                Range first = _processed.getFirst();
                int lower = first.Lower;
                int upper = first.Upper;
                int old = _maxProcessed;
                if (Serial.le(lower, _maxProcessed + 1))
                {
                    _maxProcessed = Serial.max(_maxProcessed, upper);
                }
                flush = Serial.lt(old, _syncPoint) && Serial.ge(_maxProcessed, _syncPoint);
                _syncPoint = _maxProcessed;
            }
            if (flush)
            {
                flushProcessed();
            }
        }

        public void flushProcessed(params Option[] options)
        {
            RangeSet copy;
            lock (_processedLock)
            {
                copy = _processed.copy();
            }
            sessionCompleted(copy, options);
        }

        public void knownComplete(RangeSet kc)
        {
            lock (_processedLock)
            {
                RangeSet newProcessed = new RangeSet();
                foreach (Range pr in _processed)
                {
                    foreach (Range kr in kc)
                    {
                        foreach (Range r in pr.subtract(kr))
                        {
                            newProcessed.add(r);
                        }
                    }
                }
                _processed = newProcessed;
            }
        }

        public void syncPoint()
        {
            int id = CommandsIn - 1;
            log.debug("{0} synced to {1}", this, id);
            bool flush;
            lock (_processedLock)
            {
                _syncPoint = id;
                flush = Serial.ge(_maxProcessed, _syncPoint);
            }
            if (flush)
            {
                flushProcessed();
            }
        }

        public void attach(Channel channel)
        {
            _channel = channel;
            _channel.Session = this;
        }

        public Method getCommand(int id)
        {
            lock (_commands)
            {
                return _commands[id];
            }
        }

        public bool complete(int lower, int upper)
        {
            //avoid autoboxing
            if (log.isDebugEnabled())
            {
                log.debug("{0} complete({1}, {2})", this, lower, upper);
            }
            lock (_commands)
            {
                int old = _maxComplete;
                for (int id = Serial.max(_maxComplete, lower); Serial.le(id, upper); id++)
                {
                    _commands.Remove(id);
                }
                if (Serial.le(lower, _maxComplete + 1))
                {
                    _maxComplete = Serial.max(_maxComplete, upper);
                }
                log.debug("{0} commands remaining: {1}", this, _commands);
                Monitor.PulseAll(_commands);
                return Serial.gt(_maxComplete, old);
            }
        }

        protected override void invoke(Method m)
        {
            if (Closed)
            {
                List<ExecutionException> exc = getExceptions();
                if (exc.Count > 0)
                {
                    throw new SessionException(exc);
                }
                else if (_close != null)
                {
                    throw new ConnectionException(_close);
                }
                else
                {
                    throw new SessionClosedException();
                }
            }

            if (m.EncodedTrack == Frame.L4)
            {
                lock (_commands)
                {
                    int next = _commandsOut++;
                    m.Id = next;
                    if (next == 0)
                    {
                        sessionCommandPoint(0, 0);
                    }
                    if (ENABLE_REPLAY)
                    {
                        _commands.Add(next, m);
                    }
                    if (_autoSync)
                    {
                        m.Sync = true;
                    }
                    _needSync = ! m.Sync;
                    _channel.method(m);
                    if (_autoSync)
                    {
                        sync();
                    }

                    // flush every 64K commands to avoid ambiguity on
                    // wraparound
                    if ((next%65536) == 0)
                    {
                        sessionFlush(Option.COMPLETED);
                    }
                }
            }
            else
            {
                _channel.method(m);
            }
        }

        public void sync()
        {
            sync(_timeout);
        }

        public void sync(long timeout)
        {
            log.debug("{0} sync()", this);
            lock (_commands)
            {
                int point = _commandsOut - 1;

                if (_needSync && Serial.lt(_maxComplete, point))
                {
                    executionSync(Option.SYNC);
                }

                DateTime start = DateTime.Now;
                long elapsed = 0;

                while (! Closed && elapsed < timeout && Serial.lt(_maxComplete, point))
                {
                    log.debug("{0}   waiting for[{1}]: {2}, {3}", this, point,
                              _maxComplete, _commands);
                    Monitor.Wait(_commands, (int) (timeout - elapsed));
                    elapsed = DateTime.Now.Subtract(start).Milliseconds;
                }

                if (Serial.lt(_maxComplete, point))
                {
                    if (Closed)
                    {
                        throw new SessionException(getExceptions());
                    }
                    else
                    {
                        throw new Exception
                            (String.Format
                                 ("timed out waiting for sync: complete = {0}, point = {1}", _maxComplete, point));
                    }
                }
            }
        }


        public void result(int command, Struct result)
        {
            Future future;
            lock (_results)
            {
                if (_results.ContainsKey(command))
                {
                    future = _results[command];
                    _results.Remove(command);
                }
                else
                {
                    throw new Exception(String.Format("Cannot ger result {0} for {1}", command, result));
                }
            }
            future.Result = result;
        }

        public void addException(ExecutionException exc)
        {
            lock (_exceptions)
            {
                _exceptions.Add(exc);
            }
        }

        private ConnectionClose _close = null;

        public void closeCode(ConnectionClose close)
        {
            _close = close;
        }

        public List<ExecutionException> getExceptions()
        {
            lock (_exceptions)
            {
                return new List<ExecutionException>(_exceptions);
            }
        }

        public override Future invoke(Method m, Future future)     
        {
            lock (_commands)
            {
                future.Session = this;
                int command = _commandsOut;
                lock (_results)
                {
                    _results.Add(command, future);
                }
                invoke(m);
            }
            return future;
        }


        public void messageTransfer(String destination,
                                    MessageAcceptMode acceptMode,
                                    MessageAcquireMode acquireMode,
                                    Header header,
                                    byte[] body,
                                    params Option[] options)
        {
            MemoryStream mbody = new MemoryStream();
            mbody.Write(body,0, body.Length);
            messageTransfer(destination, acceptMode, acquireMode, header,
                            mbody, options);
        }

        public void messageTransfer(String destination,
                                    MessageAcceptMode acceptMode,
                                    MessageAcquireMode acquireMode,
                                    Header header,
                                    String body,
                                    params Option[] options)
        {
            messageTransfer(destination, acceptMode, acquireMode, header,
                            new MemoryStream(Convert.ToByte(body)), options);
        }

        public void close()
        {
            sessionRequestTimeout(0);
            sessionDetach(_name);
            lock (_commands)
            {
                DateTime start = DateTime.Now;
                long elapsed = 0;

                while (! Closed && elapsed < _timeout)
                {
                    Monitor.Wait(_commands, (int) (_timeout - elapsed));
                    elapsed = DateTime.Now.Subtract(start).Milliseconds;
                }
            }
        }

        public void exception(Exception t)
        {
            log.error(t, "Caught exception");
        }

        public void closed()
        {
            Closed = true;
            lock (_commands)
            {
                Monitor.PulseAll(_commands);
            }
            lock (_results)
            {
                foreach (Future result in _results.Values)
                {
                    lock (result)
                    {
                        Monitor.PulseAll(result);
                    }
                }
            }
            _channel.Session = null;
            _channel = null;
        }

        public String toString()
        {
            return String.Format("session:{0}", _name);
        }
    }
}