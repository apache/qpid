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
#ifndef _QueuePolicy_
#define _QueuePolicy_

#include <FieldTable.h>

namespace qpid {
    namespace broker {
        class QueuePolicy
        {
            static const std::string maxCountKey;
            static const std::string maxSizeKey;

            const u_int32_t maxCount;
            const u_int64_t maxSize;
            u_int32_t count;
            u_int64_t size;
            
            static int getInt(const qpid::framing::FieldTable& settings, const std::string& key, int defaultValue);

        public:
            QueuePolicy(u_int32_t maxCount, u_int64_t maxSize);
            QueuePolicy(const qpid::framing::FieldTable& settings);
            void enqueued(u_int64_t size);
            void dequeued(u_int64_t size);
            void update(qpid::framing::FieldTable& settings);
            bool limitExceeded();
            u_int32_t getMaxCount() const { return maxCount; }
            u_int64_t getMaxSize() const { return maxSize; }           
        };
    }
}


#endif
