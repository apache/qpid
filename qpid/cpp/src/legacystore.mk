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
#
# Legacy store plugin makefile fragment, to be included in Makefile.am
# NOTE: this fragment only includes legacystore sources in
# the distribution, it does not build the store.
#
# To build the store use the cmake build system, see ../INSTALL
#

EXTRA_DIST +=						\
	qpid/legacystore/BindingDbt.cpp			\
	qpid/legacystore/BindingDbt.h			\
	qpid/legacystore/BufferValue.cpp		\
	qpid/legacystore/BufferValue.h			\
	qpid/legacystore/Cursor.h			\
	qpid/legacystore/DataTokenImpl.cpp		\
	qpid/legacystore/DataTokenImpl.h		\
	qpid/legacystore/IdDbt.cpp			\
	qpid/legacystore/IdDbt.h			\
	qpid/legacystore/IdSequence.cpp			\
	qpid/legacystore/IdSequence.h			\
	qpid/legacystore/JournalImpl.cpp		\
	qpid/legacystore/JournalImpl.h			\
	qpid/legacystore/MessageStoreImpl.cpp		\
	qpid/legacystore/MessageStoreImpl.h		\
	qpid/legacystore/PreparedTransaction.cpp	\
	qpid/legacystore/PreparedTransaction.h		\
	qpid/legacystore/StoreException.h		\
	qpid/legacystore/StorePlugin.cpp		\
	qpid/legacystore/TxnCtxt.cpp			\
	qpid/legacystore/TxnCtxt.h			\
	qpid/legacystore/jrnl/aio.cpp			\
	qpid/legacystore/jrnl/aio.h			\
	qpid/legacystore/jrnl/aio_callback.h		\
	qpid/legacystore/jrnl/cvar.cpp			\
	qpid/legacystore/jrnl/cvar.h			\
	qpid/legacystore/jrnl/data_tok.cpp		\
	qpid/legacystore/jrnl/data_tok.h		\
	qpid/legacystore/jrnl/deq_hdr.h			\
	qpid/legacystore/jrnl/deq_rec.cpp		\
	qpid/legacystore/jrnl/deq_rec.h			\
	qpid/legacystore/jrnl/enq_hdr.h			\
	qpid/legacystore/jrnl/enq_map.cpp		\
	qpid/legacystore/jrnl/enq_map.h			\
	qpid/legacystore/jrnl/enq_rec.cpp		\
	qpid/legacystore/jrnl/enq_rec.h			\
	qpid/legacystore/jrnl/enums.h			\
	qpid/legacystore/jrnl/fcntl.cpp			\
	qpid/legacystore/jrnl/fcntl.h			\
	qpid/legacystore/jrnl/file_hdr.h		\
	qpid/legacystore/jrnl/jcfg.h			\
	qpid/legacystore/jrnl/jcntl.cpp			\
	qpid/legacystore/jrnl/jcntl.h			\
	qpid/legacystore/jrnl/jdir.cpp			\
	qpid/legacystore/jrnl/jdir.h			\
	qpid/legacystore/jrnl/jerrno.cpp		\
	qpid/legacystore/jrnl/jerrno.h			\
	qpid/legacystore/jrnl/jexception.cpp		\
	qpid/legacystore/jrnl/jexception.h		\
	qpid/legacystore/jrnl/jinf.cpp			\
	qpid/legacystore/jrnl/jinf.h			\
	qpid/legacystore/jrnl/jrec.cpp			\
	qpid/legacystore/jrnl/jrec.h			\
	qpid/legacystore/jrnl/lp_map.cpp		\
	qpid/legacystore/jrnl/lp_map.h			\
	qpid/legacystore/jrnl/lpmgr.cpp			\
	qpid/legacystore/jrnl/lpmgr.h			\
	qpid/legacystore/jrnl/pmgr.cpp			\
	qpid/legacystore/jrnl/pmgr.h			\
	qpid/legacystore/jrnl/rcvdat.h			\
	qpid/legacystore/jrnl/rec_hdr.h			\
	qpid/legacystore/jrnl/rec_tail.h		\
	qpid/legacystore/jrnl/rfc.cpp			\
	qpid/legacystore/jrnl/rfc.h			\
	qpid/legacystore/jrnl/rmgr.cpp			\
	qpid/legacystore/jrnl/rmgr.h			\
	qpid/legacystore/jrnl/rrfc.cpp			\
	qpid/legacystore/jrnl/rrfc.h			\
	qpid/legacystore/jrnl/slock.cpp			\
	qpid/legacystore/jrnl/slock.h			\
	qpid/legacystore/jrnl/smutex.cpp		\
	qpid/legacystore/jrnl/smutex.h			\
	qpid/legacystore/jrnl/time_ns.cpp		\
	qpid/legacystore/jrnl/time_ns.h			\
	qpid/legacystore/jrnl/txn_hdr.h			\
	qpid/legacystore/jrnl/txn_map.cpp		\
	qpid/legacystore/jrnl/txn_map.h			\
	qpid/legacystore/jrnl/txn_rec.cpp		\
	qpid/legacystore/jrnl/txn_rec.h			\
	qpid/legacystore/jrnl/wmgr.cpp			\
	qpid/legacystore/jrnl/wmgr.h			\
	qpid/legacystore/jrnl/wrfc.cpp			\
	qpid/legacystore/jrnl/wrfc.h
