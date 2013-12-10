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

#include <iostream>

#include "sasl/sasl.h"


/*
  Some tests need to distinguish between different versions of 
  SASL.  This encodes and outputs the version number as an integer 
  for easy use in testing scripts.
*/

int
main ( )
{
    // I assume that these are 8-bit quantities....
    int sasl_version = (SASL_VERSION_MAJOR << 16) +
                       (SASL_VERSION_MINOR << 8) +
                       SASL_VERSION_STEP;

    std::cout << sasl_version << std::endl;

    return 0;
}




