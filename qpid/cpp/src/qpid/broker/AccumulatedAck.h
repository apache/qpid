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
#ifndef _AccumulatedAck_
#define _AccumulatedAck_

#include <algorithm>
#include <functional>
#include <list>
#include <ostream>
#include "DeliveryId.h"

namespace qpid {
    namespace broker {

        struct Range
        {
            DeliveryId start;
            DeliveryId end;

            Range(DeliveryId s, DeliveryId e);
            bool contains(DeliveryId i) const;
            bool intersect(const Range& r) const;
            bool merge(const Range& r);
            bool mergeable(const DeliveryId& r) const;
        };
        /**
         * Keeps an accumulated record of acked messages (by delivery
         * tag).
         */
        class AccumulatedAck {
	public:
            /**
             * If not zero, then everything up to this value has been
             * acked.
             */
            DeliveryId mark;
            /**
             * List of individually acked messages that are not
             * included in the range marked by 'range'.
             */
            std::list<Range> ranges;

            AccumulatedAck(DeliveryId r) : mark(r) {}
            void update(DeliveryId firstTag, DeliveryId lastTag);
            void consolidate();
            void clear();
            bool covers(DeliveryId tag) const;
        };
        std::ostream& operator<<(std::ostream&, const Range&);
        std::ostream& operator<<(std::ostream&, const AccumulatedAck&);
    }
}


#endif
