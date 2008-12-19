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

#include "WriteEstimate.h"
#include "qpid/log/Statement.h"
#include <boost/current_function.hpp>

namespace qpid {
namespace cluster {

WriteEstimate::WriteEstimate(size_t initial)
    : growing(true), estimate(initial), lastEstimate(initial) {}

size_t WriteEstimate::sending(size_t buffered) {
    // We want to send a doOutput request for enough data such
    // that if estimate bytes are written before it is self
    // delivered then what is left in the buffer plus the doOutput
    // request will be estimate bytes.

    size_t predictLeft = (buffered > estimate) ? buffered - estimate : 0;
    size_t request = (estimate > predictLeft) ? estimate - predictLeft : 0;
    return request;   
}

size_t pad(size_t value) { return value + value/2; }

void WriteEstimate::delivered(size_t last, size_t sent, size_t buffered) {
    lastEstimate = last;
    size_t wrote =  sent > buffered ? sent - buffered : 0;
    if (wrote == 0)             // No change
        return; 
    if (buffered > 0) { // Buffer was over-stocked, we wrote to capacity.
        growing = false;
        estimate = pad(wrote); // Estimate at 1.5 write for padding.
    }
    else if (wrote > estimate) { // Wrote everything, buffer was under-stocked
        if (growing)
            estimate = std::max(estimate*2, pad(wrote)); // Grow quickly if we have not yet seen an over-stock.
        else
            estimate = pad(wrote);
    }
}

}} // namespace qpid::cluster


