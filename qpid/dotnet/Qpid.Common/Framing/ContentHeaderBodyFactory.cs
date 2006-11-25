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
using log4net;
using Qpid.Buffer;

namespace Qpid.Framing
{
    public class ContentHeaderBodyFactory : IBodyFactory
    {
        private static readonly ILog _log = LogManager.GetLogger(typeof(ContentHeaderBodyFactory));

        private static readonly ContentHeaderBodyFactory _instance = new ContentHeaderBodyFactory();

        public static ContentHeaderBodyFactory GetInstance()
        {
            return _instance;
        }

        private ContentHeaderBodyFactory()
        {
            _log.Debug("Creating content header body factory");
        }

        #region IBodyFactory Members

        public IBody CreateBody(ByteBuffer inbuf)
        {
            // all content headers are the same - it is only the properties that differ.
            // the content header body further delegates construction of properties
            return new ContentHeaderBody();
        }

        #endregion
    }
}
