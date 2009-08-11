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

%include stl.i

%module qmfengine

%typemap (in) void *
{
    $1 = (void *) $input;
}

%typemap (out) void *
{
    $result = (VALUE) $1;
}

%typemap (in) uint16_t
{
    $1 = FIX2UINT ($input);
}

%typemap (out) uint16_t
{
    $result = UINT2NUM((unsigned short) $1);
}

%typemap (in) uint32_t
{
    $1 = FIX2UINT ($input);
}

%typemap (out) uint32_t
{
    $result = UINT2NUM((unsigned int) $1);
}

%typemap (typecheck, precedence=SWIG_TYPECHECK_INTEGER) uint32_t {
   $1 = FIXNUM_P($input);
}

%typemap (in) uint64_t
{
    $1 = FIX2ULONG ($input);
}

%typemap (out) uint64_t
{
    $result = ULONG2NUM((unsigned long) $1);
}

%typemap (typecheck, precedence=SWIG_TYPECHECK_INTEGER) uint64_t {
   $1 = FIXNUM_P($input);
}


%include "../qmfengine.i"

