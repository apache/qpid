#!/usr/bin/env python
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

import argparse
import os
import os.path
from qls import efp
from qls import jrnl

class QqpdLinearStoreAnalyzer(object):
    """
    Top-level store analyzer. Will analyze the directory in args.qls_dir as the top-level Qpid Linear Store (QLS)
    directory. The following may be analyzed:
    * The Empty File Pool (if --efp is specified in the arguments)
    * The Linear Store
    * The Transaction Prepared List (TPL)
    """
    QLS_ANALYZE_VERSION = '0.1'
    def __init__(self):
        self.args = None
        self._process_args()
        self.qls_dir = os.path.abspath(self.args.qls_dir)
        self.efp_manager = efp.EfpManager(self.qls_dir)
        self.jrnl_recovery_mgr = jrnl.JournalRecoveryManager(self.qls_dir)
    def _analyze_efp(self):
        self.efp_manager.run(self.args)
    def _analyze_journals(self):
        self.jrnl_recovery_mgr.run(self.args)
    def _process_args(self):
        parser = argparse.ArgumentParser(description = 'Qpid Linear Store Analyzer')
        parser.add_argument('qls_dir', metavar='DIR',
                            help='Qpid Linear Store (QLS) directory to be analyzed')
        parser.add_argument('--efp', action='store_true',
                            help='Analyze the Emtpy File Pool (EFP) and show stats')
        parser.add_argument('--stats', action='store_true',
                            help='Print journal record stats')
        parser.add_argument('--txn', action='store_true',
                            help='Reconcile incomplete transactions')
        parser.add_argument('--version', action='version', version='%(prog)s ' +
                            QqpdLinearStoreAnalyzer.QLS_ANALYZE_VERSION)
        self.args = parser.parse_args()
        if not os.path.exists(self.args.qls_dir):
            parser.error('Journal path "%s" does not exist' % self.args.qls_dir)
    def report(self):
        if self.args.efp:
            self.efp_manager.report()
        self.jrnl_recovery_mgr.report(self.args.stats)
    def run(self):
        if self.args.efp:
            self._analyze_efp()
        self._analyze_journals()

#==============================================================================
# main program
#==============================================================================

if __name__ == "__main__":
    # TODO: Remove this in due course
    print 'WARNING: This program is still a work in progress and is largely untested.'
    print '* USE AT YOUR OWN RISK *'
    print
    M = QqpdLinearStoreAnalyzer()
    M.run()
    M.report()
