#ifndef _sys_signal_h
#define _sys_signal_h

/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

#ifdef USE_APR
#  include <apr-1/apr_signal.h>
#else
#  include <signal.h>
#endif

namespace qpid {
namespace sys {

typedef void (*SignalHandler)(int);

SignalHandler signal(int sig, SignalHandler handler)
{
#ifdef USE_APR
    return apr_signal(sig, handler);
#else
    return ::signal (sig, handler);
#endif
}

}}



#endif  /*!_sys_signal_h*/
