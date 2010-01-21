#ifndef QPID_SYS_CLUSTERSAFE_H
#define QPID_SYS_CLUSTERSAFE_H

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

#include "qpid/CommonImportExport.h"

namespace qpid {
namespace sys {

/**
 * Assertion to add to code that modifies clustered state.
 *
 * In a non-clustered broker this is a no-op.
 *
 * In a clustered broker, checks that it is being called
 * in a context where it is safe to  modify clustered state.
 * If not it aborts the process as this is a serious bug.
 *
 * This function is in the common library rather than the cluster
 * library because it is called by code in the broker library.
 */
QPID_COMMON_EXTERN void assertClusterSafe();

/**
 * Base class for classes that encapsulate state which is replicated
 * to all members of a cluster. Acts as a marker for clustered state
 * and provides functions to assist detecting bugs in cluster
 * behavior.
 */
struct ClusterSafeScope {
    ClusterSafeScope();
    ~ClusterSafeScope();
};

/**
 * Enable cluster-safe assertions. By defaul they are no-ops.
 */
void enableClusterSafe();

}} // namespace qpid::sys

#endif  /*!QPID_SYS_CLUSTERSAFE_H*/
