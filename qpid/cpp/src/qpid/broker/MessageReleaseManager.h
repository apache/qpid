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
#ifndef _broker_MessageReleaseManager_h
#define _broker_MessageReleaseManager_h

namespace qpid {
    namespace broker {

        class MessageReleaseManager
        {
        private:
            bool releaseBlocked;
            bool releaseRequested;
            bool released;

        public:
            MessageReleaseManager(): releaseBlocked(false), releaseRequested(false), released(false) {}
            virtual ~MessageReleaseManager() {}

            bool isReleaseBlocked() const { return releaseBlocked; }
            void blockRelease() { if (!released) releaseBlocked = true; }

            bool isReleaseRequested() const { return releaseRequested; }
            void setReleaseRequested() { if (!released) releaseRequested = true; }

            bool isReleased() const { return released; }
            void setReleased() { released = true; }

            bool canRelease() { return !releaseBlocked && releaseRequested; }
        };

    }
}


#endif  /*_broker_MessageReleaseManager_h*/
