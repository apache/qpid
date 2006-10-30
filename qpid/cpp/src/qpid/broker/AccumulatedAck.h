/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
#ifndef _AccumulatedAck_
#define _AccumulatedAck_

#include <algorithm>
#include <functional>
#include <list>

namespace qpid {
    namespace broker {
        /**
         * Keeps an accumulated record of acked messages (by delivery
         * tag).
         */
        struct AccumulatedAck{
            /**
             * If not zero, then everything up to this value has been
             * acked.
             */
            u_int64_t range;
            /**
             * List of individually acked messages that are not
             * included in the range marked by 'range'.
             */
            std::list<u_int64_t> individual;

            void update(u_int64_t tag, bool multiple);
            void consolidate();
            void clear();
            bool covers(u_int64_t tag) const;
        };
    }
}


#endif
