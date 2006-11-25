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
using System.Text;
using log4net;
using Qpid.Client.qms.failover;

namespace Qpid.Client.qms
{
    public class FailoverPolicy
    {
        private static readonly ILog _logger = LogManager.GetLogger(typeof(FailoverPolicy));

        private const long MINUTE = 60000L;

        private const long DEFAULT_METHOD_TIMEOUT = 1 * MINUTE;
        private const long DEFAULT_FAILOVER_TIMEOUT = 4 * MINUTE;

        private FailoverMethod[] _methods = new FailoverMethod[1];

        private int _currentMethod;

        private int _methodsRetries;

        private int _currentRetry;

        private bool _timing;

        private long _lastMethodTime;
        private long _lastFailTime;

        public FailoverPolicy(ConnectionInfo connectionInfo)
        {
            FailoverMethod method;

            //todo This should be integrated in to the connection url when it supports
            // multiple strategies.

            _methodsRetries = 0;

            if (connectionInfo.getFailoverMethod() == null)
            {
                if (connectionInfo.getBrokerCount() > 1)
                {
                    method = new FailoverRoundRobin(connectionInfo);
                }
                else
                {
                    method = new FailoverSingleServer(connectionInfo);
                }
            }
            else
            {
                string failoverMethod = connectionInfo.getFailoverMethod();

    /*
                if (failoverMethod.equals(FailoverMethod.RANDOM))
                {
                    //todo write a random connection Failover
                }
    */
                if (failoverMethod.Equals(FailoverMethodConstants.ROUND_ROBIN))
                {
                    method = new FailoverRoundRobin(connectionInfo);
                }
                else
                {
                    throw new NotImplementedException("Dynamic loading of FailoverMethods not yet implemented.");
//                    try
//                    {
//                        Type[] constructorSpec = {ConnectionInfo.class};
//                        Object [] params = {connectionInfo};
//
//                        method = (FailoverMethod) ClassLoader.getSystemClassLoader().
//                                loadClass(failoverMethod).
//                                getConstructor(constructorSpec).newInstance(params);
//                    }
//                    catch (Exception cnfe)
//                    {
//                        throw new IllegalArgumentException("Unknown failover method:" + failoverMethod);
//                    }
                }
            }

            if (method == null)
            {
                throw new ArgumentException("Unknown failover method specified.");
            }

            reset();

            _methods[_currentMethod] = method;
        }

        public FailoverPolicy(FailoverMethod method) : this(method, 0)
        {
        }

        public FailoverPolicy(FailoverMethod method, int retries)
        {
            _methodsRetries = retries;

            reset();

            _methods[_currentMethod] = method;
        }

        private void reset()
        {
            _currentMethod = 0;
            _currentRetry = 0;
            _timing = false;

        }

        public bool FailoverAllowed()
        {
            bool failoverAllowed;

            if (_timing)
            {
                long now = CurrentTimeMilliseconds();

                if ((now - _lastMethodTime) >= DEFAULT_METHOD_TIMEOUT)
                {
                    _logger.Info("Failover method timeout");
                    _lastMethodTime = now;

                    if (!nextMethod())
                    {
                        return false;
                    }


                }
                else if ((now - _lastFailTime) >= DEFAULT_FAILOVER_TIMEOUT)
                {
                    _logger.Info("Failover timeout");
                    return false;
                }
                else
                {
                    _lastMethodTime = now;
                }
            }
            else
            {
                _timing = true;
                _lastMethodTime = CurrentTimeMilliseconds();
                _lastFailTime = _lastMethodTime;
            }


            if (_methods[_currentMethod].failoverAllowed())
            {
                failoverAllowed = true;
            }
            else
            {
                if (_currentMethod < (_methods.Length - 1))
                {
                    nextMethod();
                    _logger.Info("Changing method to " + _methods[_currentMethod].methodName());
                    return FailoverAllowed();
                }
                else
                {
                    return cycleMethods();
                }
            }

            return failoverAllowed;
        }

        private static long CurrentTimeMilliseconds()
        {
            return DateTime.Now.Ticks / TimeSpan.TicksPerMillisecond;
        }

        private bool nextMethod()
        {
            if (_currentMethod < (_methods.Length - 1))
            {
                _currentMethod++;
                _methods[_currentMethod].reset();
                return true;
            }
            else
            {
                return cycleMethods();
            }
        }

        private bool cycleMethods()
        {
            if (_currentRetry < _methodsRetries)
            {
                _currentRetry++;

                _currentMethod = 0;

                _logger.Info("Retrying methods starting with " + _methods[_currentMethod].methodName());
                _methods[_currentMethod].reset();
                return FailoverAllowed();
            }
            else
            {
                _logger.Debug("All failover methods exhausted");
                return false;
            }
        }

        /**
         * Notification that connection was successful.
         */
        public void attainedConnection()
        {
            _currentRetry = 0;

            _methods[_currentMethod].attainedConnection();

            _timing = false;
        }

        public BrokerInfo GetCurrentBrokerInfo()
        {
            return _methods[_currentMethod].GetCurrentBrokerInfo();
        }

        public BrokerInfo GetNextBrokerInfo()
        {
            return _methods[_currentMethod].getNextBrokerDetails();
        }

        public void setBroker(BrokerInfo broker)
        {
            _methods[_currentMethod].setBroker(broker);
        }

        public void addMethod(FailoverMethod method)
        {
            int len = _methods.Length + 1;
            FailoverMethod[] newMethods = new FailoverMethod[len];
            _methods.CopyTo(newMethods, 0);
//            System.arraycopy(_methods, 0, newMethods, 0, _methods.length);
            int index = len - 1;
            newMethods[index] = method;
            _methods = newMethods;
        }

        public void setMethodRetries(int retries)
        {
            _methodsRetries = retries;
        }

        public FailoverMethod getCurrentMethod()
        {
            if (_currentMethod >= 0 && _currentMethod < (_methods.Length - 1))
            {
                return _methods[_currentMethod];
            }
            else
            {
                return null;
            }
        }

        public String toString()
        {
            StringBuilder sb = new StringBuilder();

            sb.Append("Failover Policy:\n");

            if (FailoverAllowed())
            {
                sb.Append("Failover allowed\n");
            }
            else
            {
                sb.Append("Failover not allowed\n");
            }

            sb.Append("Failover policy methods\n");
            for (int i = 0; i < _methods.Length; i++)
            {

                if (i == _currentMethod)
                {
                    sb.Append(">");
                }
                sb.Append(_methods[i].ToString());
            }

            return sb.ToString();
        }
    }
}