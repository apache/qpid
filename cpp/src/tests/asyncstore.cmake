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
# Async store test CMake fragment, to be included in tests/CMakeLists.txt
# 

# New journal perf test (jrnl2Perf)
set (jrnl2Perf_SOURCES
	storePerftools/jrnlPerf/Journal.cpp
	storePerftools/jrnlPerf/JournalParameters.cpp
	storePerftools/jrnlPerf/PerfTest.cpp
	storePerftools/jrnlPerf/TestResult.cpp
	
	storePerftools/common/Parameters.cpp
    storePerftools/common/PerftoolError.cpp
	storePerftools/common/ScopedTimable.cpp
	storePerftools/common/ScopedTimer.cpp
	storePerftools/common/Streamable.cpp
	storePerftools/common/TestParameters.cpp
	storePerftools/common/TestResult.cpp
	storePerftools/common/Thread.cpp
)

if (UNIX)
    add_executable (jrnl2Perf ${jrnl2Perf_SOURCES})
    set_target_properties (jrnl2Perf PROPERTIES
	    COMPILE_FLAGS "-DJOURNAL2"
    )
    target_link_libraries (jrnl2Perf
	    asyncStore
	    qpidbroker
	    rt
    )
endif (UNIX)

# Async store perf test (asyncPerf)
set (asyncStorePerf_SOURCES
    storePerftools/asyncPerf/MessageConsumer.cpp
    storePerftools/asyncPerf/MessageProducer.cpp
	storePerftools/asyncPerf/PerfTest.cpp
#    storePerftools/asyncPerf/SimpleDeliverable.cpp
#    storePerftools/asyncPerf/SimpleDeliveryRecord.cpp
#	storePerftools/asyncPerf/SimpleMessage.cpp
#    storePerftools/asyncPerf/SimpleMessageAsyncContext.cpp
#    storePerftools/asyncPerf/SimpleMessageDeque.cpp
#	storePerftools/asyncPerf/SimpleQueue.cpp
#	storePerftools/asyncPerf/SimpleQueuedMessage.cpp
#	storePerftools/asyncPerf/SimpleTxnAccept.cpp
#	storePerftools/asyncPerf/SimpleTxnPublish.cpp
	storePerftools/asyncPerf/TestOptions.cpp
	storePerftools/asyncPerf/TestResult.cpp
	
	storePerftools/common/Parameters.cpp
    storePerftools/common/PerftoolError.cpp
	storePerftools/common/ScopedTimable.cpp
	storePerftools/common/ScopedTimer.cpp
	storePerftools/common/Streamable.cpp
	storePerftools/common/TestOptions.cpp
	storePerftools/common/TestResult.cpp
	storePerftools/common/Thread.cpp
)

if (UNIX)
    add_executable (asyncStorePerf ${asyncStorePerf_SOURCES})
    set_target_properties (asyncStorePerf PROPERTIES
	    COMPILE_FLAGS "-DJOURNAL2"
    )
    target_link_libraries (asyncStorePerf
	    boost_program_options
	    asyncStore
	    qpidbroker
	    qpidcommon
	    qpidtypes
	    rt
    )
endif (UNIX)
