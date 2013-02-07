#ifndef __nexus_iovec_h__
#define __nexus_iovec_h__ 1
/*
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
 */

#include <sys/uio.h>

typedef struct nx_iovec_t nx_iovec_t;

nx_iovec_t *nx_iovec(int vector_count);
void nx_iovec_free(nx_iovec_t *iov);
struct iovec *nx_iovec_array(nx_iovec_t *iov);
int nx_iovec_count(nx_iovec_t *iov);


#endif
