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

#include <qpid/dispatch/buffer.h>
#include "alloc_private.h"

int message_tests();
int field_tests();

int main(int argc, char** argv)
{
    ssize_t buffer_size = 512;

    if (argc > 1) {
        buffer_size = atoi(argv[1]);
        if (buffer_size < 1)
            return 1;
    }

    dx_alloc_initialize();
    dx_buffer_set_size(buffer_size);

    int result = 0;
    result += message_tests();
    result += field_tests();
    return result;
}

