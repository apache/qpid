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

#include <qpid/nexus/iovec.h>
#include <qpid/nexus/alloc.h>
#include <string.h>

#define NX_IOVEC_MAX 64

struct nx_iovec_t {
    struct iovec  iov_array[NX_IOVEC_MAX];
    struct iovec *iov;
    int           iov_count;
};


ALLOC_DECLARE(nx_iovec_t);
ALLOC_DEFINE(nx_iovec_t);


nx_iovec_t *nx_iovec(int vector_count)
{
    nx_iovec_t *iov = new_nx_iovec_t();
    if (!iov)
        return 0;

    memset(iov, 0, sizeof(nx_iovec_t));

    iov->iov_count = vector_count;
    if (vector_count > NX_IOVEC_MAX)
        iov->iov = (struct iovec*) malloc(sizeof(struct iovec) * vector_count);
    else
        iov->iov = &iov->iov_array[0];

    return iov;
}


void nx_iovec_free(nx_iovec_t *iov)
{
    if (!iov)
        return;

    if (iov->iov && iov->iov != &iov->iov_array[0])
        free(iov->iov);

    free_nx_iovec_t(iov);
}


struct iovec *nx_iovec_array(nx_iovec_t *iov)
{
    if (!iov)
        return 0;
    return iov->iov;
}


int nx_iovec_count(nx_iovec_t *iov)
{
    if (!iov)
        return 0;
    return iov->iov_count;
}

