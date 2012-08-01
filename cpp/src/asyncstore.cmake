#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#
#
# Async store library CMake fragment, to be included in CMakeLists.txt
# 

# Journal 2 source files
set (jrnl2_SOURCES
	qpid/asyncStore/jrnl2/AsyncJournal.cpp
	qpid/asyncStore/jrnl2/DataOpState.cpp
	qpid/asyncStore/jrnl2/DataToken.cpp
	qpid/asyncStore/jrnl2/DataWrComplState.cpp
	qpid/asyncStore/jrnl2/DequeueHeader.cpp
	qpid/asyncStore/jrnl2/EnqueueHeader.cpp
	qpid/asyncStore/jrnl2/EventHeader.cpp
	qpid/asyncStore/jrnl2/FileHeader.cpp
	qpid/asyncStore/jrnl2/JournalDirectory.cpp
	qpid/asyncStore/jrnl2/JournalError.cpp
	qpid/asyncStore/jrnl2/JournalParameters.cpp
	qpid/asyncStore/jrnl2/JournalRunState.cpp
	qpid/asyncStore/jrnl2/RecordHeader.cpp
	qpid/asyncStore/jrnl2/RecordTail.cpp
	qpid/asyncStore/jrnl2/ScopedLock.cpp
	qpid/asyncStore/jrnl2/Streamable.cpp
	qpid/asyncStore/jrnl2/TransactionHeader.cpp
)

# AsyncStore source files
set (asyncStore_SOURCES
    qpid/asyncStore/AsyncOperation.cpp
	qpid/asyncStore/AsyncStoreImpl.cpp
	qpid/asyncStore/AsyncStoreOptions.cpp
	qpid/asyncStore/ConfigHandleImpl.cpp
	qpid/asyncStore/EnqueueHandleImpl.cpp
	qpid/asyncStore/EventHandleImpl.cpp
	qpid/asyncStore/MessageHandleImpl.cpp
	qpid/asyncStore/OperationQueue.cpp
	qpid/asyncStore/Plugin.cpp
	qpid/asyncStore/QueueHandleImpl.cpp
	qpid/asyncStore/RunState.cpp
	qpid/asyncStore/TxnHandleImpl.cpp
    qpid/broker/AsyncResultHandle.cpp
    qpid/broker/AsyncResultHandleImpl.cpp
    qpid/broker/AsyncResultQueueImpl.cpp
	qpid/broker/ConfigHandle.cpp
	qpid/broker/EnqueueHandle.cpp
	qpid/broker/EventHandle.cpp
	qpid/broker/MessageHandle.cpp
	qpid/broker/QueueAsyncContext.cpp
	qpid/broker/QueueHandle.cpp
    qpid/broker/SimpleDeliverable.cpp
    qpid/broker/SimpleDeliveryRecord.cpp
	qpid/broker/SimpleMessage.cpp
    qpid/broker/SimpleMessageAsyncContext.cpp
    qpid/broker/SimpleMessageDeque.cpp
	qpid/broker/SimpleQueue.cpp
	qpid/broker/SimpleQueuedMessage.cpp
	qpid/broker/SimpleTxnAccept.cpp
    qpid/broker/SimpleTxnBuffer.cpp
	qpid/broker/SimpleTxnPublish.cpp
    qpid/broker/TxnAsyncContext.cpp
    qpid/broker/TxnHandle.cpp
)

if (UNIX)
    add_library (asyncStore MODULE
	    ${jrnl2_SOURCES}
	    ${asyncStore_SOURCES}
    )
    set_target_properties (asyncStore PROPERTIES
        PREFIX ""
        OUTPUT_NAME asyncStore
        SOVERSION ${asyncStore_version}
    )
    target_link_libraries (asyncStore
	    aio
	    rt
	    uuid
    )
endif (UNIX)
