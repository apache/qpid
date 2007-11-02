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
AM_CXXFLAGS = $(WARNING_CFLAGS)

INCLUDES =					\
  -I$(top_srcdir)/gen				\
  -I$(top_srcdir)/lib/broker			\
  -I$(top_srcdir)/lib/common			\
  -I$(top_srcdir)/lib/common/framing		\
  -I$(top_srcdir)/lib/common/sys

LDADD =						\
  ../lib/broker/libqpidbroker.la		\
  ../lib/common/libqpidcommon.la		\
  -lboost_program_options

sbin_PROGRAMS = qpidd
qpidd_SOURCES = qpidd.cpp

# Force build of qpidd during dist phase so help2man will work.
dist-hook: $(sbin_PROGRAMS)
