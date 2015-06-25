#ifndef _sys_Probes
#define _sys_Probes
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

#include "config.h"

#ifdef HAVE_SYS_SDT_H
#include <sys/sdt.h>
#endif

// Pragmatically it seems that Linux and Solaris versions of sdt.h which support 
// user static probes define up to DTRACE_PROBE8, but FreeBSD 8 which doesn't
// support usdt only defines up to DTRACE_PROBE4 - FreeBSD 9 which does support usdt
// defines up to DTRACE_PROBE5.

#ifdef DTRACE_PROBE5
// Versions for Linux Systemtap/Solaris/FreeBSD 9
#define QPID_PROBE(probe) DTRACE_PROBE(qpid, probe)
#define QPID_PROBE1(probe, p1) DTRACE_PROBE1(qpid, probe, p1)
#define QPID_PROBE2(probe, p1, p2) DTRACE_PROBE2(qpid, probe, p1, p2)
#define QPID_PROBE3(probe, p1, p2, p3) DTRACE_PROBE3(qpid, probe, p1, p2, p3)
#define QPID_PROBE4(probe, p1, p2, p3, p4) DTRACE_PROBE4(qpid, probe, p1, p2, p3, p4)
#define QPID_PROBE5(probe, p1, p2, p3, p4, p5) DTRACE_PROBE5(qpid, probe, p1, p2, p3, p4, p5)
#else
// FreeBSD 8
#define QPID_PROBE(probe)
#define QPID_PROBE1(probe, p1)
#define QPID_PROBE2(probe, p1, p2)
#define QPID_PROBE3(probe, p1, p2, p3)
#define QPID_PROBE4(probe, p1, p2, p3, p4)
#define QPID_PROBE5(probe, p1, p2, p3, p4, p5)
#endif

#ifdef DTRACE_PROBE8
// Versions for Linux Systemtap
#define QPID_PROBE6(probe, p1, p2, p3, p4, p5, p6) DTRACE_PROBE6(qpid, probe, p1, p2, p3, p4, p5, p6)
#define QPID_PROBE7(probe, p1, p2, p3, p4, p5, p6, p7) DTRACE_PROBE7(qpid, probe, p1, p2, p3, p4, p5, p6, p7)
#define QPID_PROBE8(probe, p1, p2, p3, p4, p5, p6, p7, p8) DTRACE_PROBE8(qpid, probe, p1, p2, p3, p4, p5, p6, p7, p8)
#else
// Versions for Solaris/FreeBSD
#define QPID_PROBE6(probe, p1, p2, p3, p4, p5, p6)
#define QPID_PROBE7(probe, p1, p2, p3, p4, p5, p6, p7)
#define QPID_PROBE8(probe, p1, p2, p3, p4, p5, p6, p7, p8)
#endif

#endif // _sys_Probes
