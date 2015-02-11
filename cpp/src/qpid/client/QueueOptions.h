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

#include "qpid/client/ClientImportExport.h"
#include "qpid/framing/FieldTable.h"

#ifndef _QueueOptions_
#define _QueueOptions_

namespace qpid {
namespace client {

enum QueueSizePolicy {NONE, REJECT, FLOW_TO_DISK, RING, RING_STRICT};
enum QueueOrderingPolicy {FIFO, LVQ, LVQ_NO_BROWSE};

/**
 * A help class to set options on the Queue. Create a configured args while
 * still allowing any custom configuration via the FieldTable base class
 */
class QPID_CLIENT_CLASS_EXTERN QueueOptions: public framing::FieldTable
{
  public:
    QPID_CLIENT_EXTERN QueueOptions();
    QPID_CLIENT_EXTERN virtual ~QueueOptions();

    /**
     * Sets the queue sizing policy
     *
     * @param sp SizePolicy
     * REJECT - reject if queue greater than size/count
     * FLOW_TO_DISK - page messages to disk from this point is greater than size/count
     * RING - limit the queue to size/count and over-write old messages round a ring
     * RING_STRICT - limit the queue to size/count and reject is head == tail
     * NONE - Use default broker sizing policy
     * @param maxSize Set the max number of bytes for the sizing policies
     * @param setMaxCount Set the max number of messages for the sizing policies
     */
    QPID_CLIENT_EXTERN void setSizePolicy(QueueSizePolicy sp, uint64_t maxSize, uint32_t maxCount );

    /**
     * Sets the odering policy on the Queue, default ordering is FIFO.
     */
    QPID_CLIENT_EXTERN void setOrdering(QueueOrderingPolicy op);

    /**
     * Use broker defualt sizing ploicy
     */
    QPID_CLIENT_EXTERN void clearSizePolicy();

    /**
     * get the key used match LVQ in args for message transfer
     */
    QPID_CLIENT_EXTERN void getLVQKey(std::string& key);

    /**
     * Use default odering policy
     */
    QPID_CLIENT_EXTERN void clearOrdering();

    static QPID_CLIENT_EXTERN const std::string strMaxCountKey;
    static QPID_CLIENT_EXTERN const std::string strMaxSizeKey;
    static QPID_CLIENT_EXTERN const std::string strTypeKey;
    static QPID_CLIENT_EXTERN const std::string strREJECT;
    static QPID_CLIENT_EXTERN const std::string strFLOW_TO_DISK;
    static QPID_CLIENT_EXTERN const std::string strRING;
    static QPID_CLIENT_EXTERN const std::string strRING_STRICT;
    static QPID_CLIENT_EXTERN const std::string strLastValueQueue;
    static QPID_CLIENT_EXTERN const std::string strLVQMatchProperty;
    static QPID_CLIENT_EXTERN const std::string strLastValueQueueNoBrowse;
    static QPID_CLIENT_EXTERN const std::string strQueueEventMode;
};

}
}


#endif
