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

"""
Module: qlslibs.analyze

Classes for recovery and analysis of a Qpid Linear Store (QLS).
"""

import os.path
import qlslibs.err
import qlslibs.jrnl
import qlslibs.utils

class HighCounter(object):
    def __init__(self):
        self.num = 0
    def check(self, num):
        if self.num < num:
            self.num = num
    def get(self):
        return self.num
    def get_next(self):
        self.num += 1
        return self.num

class JournalRecoveryManager(object):
    TPL_DIR_NAME = 'tpl2'
    JRNL_DIR_NAME = 'jrnl2'
    def __init__(self, directory, args):
        if not os.path.exists(directory):
            raise qlslibs.err.InvalidQlsDirectoryNameError(directory)
        self.directory = directory
        self.args = args
        self.tpl = None
        self.journals = {}
        self.high_rid_counter = HighCounter()
        self.prepared_list = None
    def report(self):
        self._reconcile_transactions(self.prepared_list, self.args.txn)
        if self.tpl is not None:
            self.tpl.report(self.args)
        for queue_name in sorted(self.journals.keys()):
            self.journals[queue_name].report(self.args)
    def run(self):
        tpl_dir = os.path.join(self.directory, JournalRecoveryManager.TPL_DIR_NAME)
        if os.path.exists(tpl_dir):
            self.tpl = Journal(tpl_dir, None, self.args)
            self.tpl.recover(self.high_rid_counter)
            if self.args.show_recovery_recs or self.args.show_all_recs:
                print
        jrnl_dir = os.path.join(self.directory, JournalRecoveryManager.JRNL_DIR_NAME)
        self.prepared_list = self.tpl.txn_map.get_prepared_list() if self.tpl is not None else {}
        if os.path.exists(jrnl_dir):
            for dir_entry in sorted(os.listdir(jrnl_dir)):
                jrnl = Journal(os.path.join(jrnl_dir, dir_entry), self.prepared_list, self.args)
                jrnl.recover(self.high_rid_counter)
                self.journals[jrnl.get_queue_name()] = jrnl
                if self.args.show_recovery_recs or self.args.show_all_recs:
                    print
        print
    def _reconcile_transactions(self, prepared_list, txn_flag):
        print 'Transaction reconciliation report:'
        print '=================================='
        print 'Transaction Prepared List (TPL) contains %d open transaction(s):' % len(prepared_list)
        for xid in prepared_list.keys():
            commit_flag = prepared_list[xid]
            if commit_flag is None:
                status = '[Prepared, neither committed nor aborted - assuming commit]'
            elif commit_flag:
                status = '[Prepared, but interrupted during commit phase]'
            else:
                status = '[Prepared, but interrupted during abort phase]'
            print ' ', qlslibs.utils.format_xid(xid), status
            if prepared_list[xid] is None: # Prepared, but not committed or aborted
                enqueue_record = self.tpl.get_txn_map_record(xid)[0][1]
                dequeue_record = qlslibs.utils.create_record(qlslibs.jrnl.DequeueRecord.MAGIC, \
                                                             qlslibs.jrnl.DequeueRecord.TXN_COMPLETE_COMMIT_FLAG, \
                                                             self.tpl.current_journal_file, \
                                                             self.high_rid_counter.get_next(), \
                                                             enqueue_record.record_id, xid, None)
                if txn_flag:
                    self.tpl.add_record(dequeue_record)
        print
        print 'Open transactions found in queues:'
        print '----------------------------------'
        for queue_name in sorted(self.journals.keys()):
            self.journals[queue_name].reconcile_transactions(prepared_list, txn_flag)
        print
        if len(prepared_list) > 0:
            print 'Creating commit records for the following prepared transactions in TPL:'
            for xid in prepared_list.keys():
                print ' ', qlslibs.utils.format_xid(xid)
                transaction_record = qlslibs.utils.create_record(qlslibs.jrnl.TransactionRecord.MAGIC_COMMIT, 0, \
                                                                 self.tpl.current_journal_file, \
                                                                 self.high_rid_counter.get_next(), None, xid, None)
                if txn_flag:
                    self.tpl.add_record(transaction_record)
            print

class EnqueueMap(object):
    """
    Map of enqueued records in a QLS journal
    """
    def __init__(self, journal):
        self.journal = journal
        self.enq_map = {}
    def add(self, journal_file, enq_record, locked_flag):
        if enq_record.record_id in self.enq_map:
            raise qlslibs.err.DuplicateRecordIdError(self.journal.current_file_header, enq_record)
        self.enq_map[enq_record.record_id] = [journal_file, enq_record, locked_flag]
    def contains(self, rid):
        """Return True if the map contains the given rid"""
        return rid in self.enq_map
    def delete(self, journal_file, deq_record):
        if deq_record.dequeue_record_id in self.enq_map:
            enq_list = self.enq_map[deq_record.dequeue_record_id]
            del self.enq_map[deq_record.dequeue_record_id]
            return enq_list
        else:
            raise qlslibs.err.RecordIdNotFoundError(journal_file.file_header, deq_record)
    def get(self, record_id):
        if record_id in self.enq_map:
            return self.enq_map[record_id]
        return None
    def lock(self, journal_file, dequeue_record):
        if dequeue_record.dequeue_record_id not in self.enq_map:
            raise qlslibs.err.RecordIdNotFoundError(journal_file.file_header, dequeue_record)
        self.enq_map[dequeue_record.dequeue_record_id][2] = True
    def report_str(self, args):
        """Return a string containing a text report for all records in the map"""
        if len(self.enq_map) == 0:
            return 'No enqueued records found.'
        rstr = '%d enqueued records found' % len(self.enq_map)
        if args.show_recovered_recs:
            rstr += ":"
            rid_list = self.enq_map.keys()
            rid_list.sort()
            for rid in rid_list:
                journal_file, record, locked_flag = self.enq_map[rid]
                rstr += '\n  0x%x:' % journal_file.file_header.file_num
                rstr += record.to_string(args.show_xids, args.show_data, args.txtest)
                if locked_flag:
                    rstr += ' [LOCKED]'
        else:
            rstr += '.'
        return rstr
    def unlock(self, journal_file, dequeue_record):
        """Set the transaction lock for a given record_id to False"""
        if dequeue_record.dequeue_record_id in self.enq_map:
            if self.enq_map[dequeue_record.dequeue_record_id][2]:
                self.enq_map[dequeue_record.dequeue_record_id][2] = False
            else:
                raise qlslibs.err.RecordNotLockedError(journal_file.file_header, dequeue_record)
        else:
            raise qlslibs.err.RecordIdNotFoundError(journal_file.file_header, dequeue_record)

class TransactionMap(object):
    """
    Map of open transactions used while recovering a QLS journal
    """
    def __init__(self, enq_map):
        self.txn_map = {}
        self.enq_map = enq_map
    def abort(self, xid):
        """Perform an abort operation for the given xid record"""
        for journal_file, record, _ in self.txn_map[xid]:
            if isinstance(record, qlslibs.jrnl.DequeueRecord):
                if self.enq_map.contains(record.dequeue_record_id):
                    self.enq_map.unlock(journal_file, record)
            else:
                journal_file.decr_enq_cnt(record)
        del self.txn_map[xid]
    def add(self, journal_file, record):
        if record.xid is None:
            raise qlslibs.err.NonTransactionalRecordError(journal_file.file_header, record, 'TransactionMap.add()')
        if isinstance(record, qlslibs.jrnl.DequeueRecord):
            try:
                self.enq_map.lock(journal_file, record)
            except qlslibs.err.RecordIdNotFoundError:
                # Not in emap, look for rid in tmap - should not happen in practice
                txn_op = self._find_record_id(record.xid, record.dequeue_record_id)
                if txn_op != None:
                    if txn_op[2]:
                        raise qlslibs.err.AlreadyLockedError(journal_file.file_header, record)
                    txn_op[2] = True
        if record.xid in self.txn_map:
            self.txn_map[record.xid].append([journal_file, record, False]) # append to existing list
        else:
            self.txn_map[record.xid] = [[journal_file, record, False]] # create new list
    def commit(self, xid):
        """Perform a commit operation for the given xid record"""
        mismatch_list = []
        for journal_file, record, lock in self.txn_map[xid]:
            if isinstance(record, qlslibs.jrnl.EnqueueRecord):
                self.enq_map.add(journal_file, record, lock) # Transfer enq to emap
            else:
                if self.enq_map.contains(record.dequeue_record_id):
                    self.enq_map.unlock(journal_file, record)
                    self.enq_map.delete(journal_file, record)[0].decr_enq_cnt(record)
                else:
                    mismatch_list.append('0x%x' % record.dequeue_record_id)
        del self.txn_map[xid]
        return mismatch_list
    def contains(self, xid):
        """Return True if the xid exists in the map; False otherwise"""
        return xid in self.txn_map
    def delete(self, journal_file, transaction_record):
        """Remove a transaction record from the map using either a commit or abort header"""
        if transaction_record.magic[-1] == 'c':
            return self.commit(transaction_record.xid)
        if transaction_record.magic[-1] == 'a':
            self.abort(transaction_record.xid)
        else:
            raise qlslibs.err.InvalidRecordTypeError(journal_file.file_header, transaction_record,
                                                     'delete from Transaction Map')
    def get(self, xid):
        if xid in self.txn_map:
            return self.txn_map[xid]
        return None
    def get_prepared_list(self):
        """
        Prepared list is a map of xid(key) to one of None, True or False. These represent respectively:
        None: prepared, but neither committed or aborted (interrupted before commit or abort)
        False: prepared and aborted (interrupted before abort complete)
        True: prepared and committed (interrupted before commit complete)
        """
        prepared_list = {}
        for xid in self.get_xid_list():
            for _, record, _ in self.txn_map[xid]:
                if isinstance(record, qlslibs.jrnl.EnqueueRecord):
                    prepared_list[xid] = None
                else:
                    prepared_list[xid] = record.is_transaction_complete_commit()
        return prepared_list
    def get_xid_list(self):
        return self.txn_map.keys()
    def report_str(self, args):
        """Return a string containing a text report for all records in the map"""
        if len(self.txn_map) == 0:
            return 'No outstanding transactions found.'
        rstr = '%d outstanding transaction(s)' % len(self.txn_map)
        if args.show_recovered_recs:
            rstr += ':'
            for xid, op_list in self.txn_map.iteritems():
                rstr += '\n  %s containing %d operations:' % (qlslibs.utils.format_xid(xid), len(op_list))
                for journal_file, record, _ in op_list:
                    rstr += '\n    0x%x:' % journal_file.file_header.file_num
                    rstr += record.to_string(args.show_xids, args.show_data, args.txtest)
        else:
            rstr += '.'
        return rstr
    def _find_record_id(self, xid, record_id):
        """ Search for and return map list with supplied rid."""
        if xid in self.txn_map:
            for txn_op in self.txn_map[xid]:
                if txn_op[1].record_id == record_id:
                    return txn_op
        for this_xid in self.txn_map.iterkeys():
            for txn_op in self.txn_map[this_xid]:
                if txn_op[1].record_id == record_id:
                    return txn_op
        return None

class JournalStatistics(object):
    """Journal statistics"""
    def __init__(self):
        self.total_record_count = 0
        self.transient_record_count = 0
        self.filler_record_count = 0
        self.enqueue_count = 0
        self.dequeue_count = 0
        self.transaction_record_count = 0
        self.transaction_enqueue_count = 0
        self.transaction_dequeue_count = 0
        self.transaction_commit_count = 0
        self.transaction_abort_count = 0
        self.transaction_operation_count = 0
    def __str__(self):
        fstr = 'Total record count: %d\n' + \
               'Transient record count: %d\n' + \
               'Filler_record_count: %d\n' + \
               'Enqueue_count: %d\n' + \
               'Dequeue_count: %d\n' + \
               'Transaction_record_count: %d\n' + \
               'Transaction_enqueue_count: %d\n' + \
               'Transaction_dequeue_count: %d\n' + \
               'Transaction_commit_count: %d\n' + \
               'Transaction_abort_count: %d\n' + \
               'Transaction_operation_count: %d\n'
        return fstr % (self.total_record_count,
                       self.transient_record_count,
                       self.filler_record_count,
                       self.enqueue_count,
                       self.dequeue_count,
                       self.transaction_record_count,
                       self.transaction_enqueue_count,
                       self.transaction_dequeue_count,
                       self.transaction_commit_count,
                       self.transaction_abort_count,
                       self.transaction_operation_count)

class Journal(object):
    """
    Instance of a Qpid Linear Store (QLS) journal.
    """
    JRNL_SUFFIX = 'jrnl'
    def __init__(self, directory, xid_prepared_list, args):
        self.directory = directory
        self.queue_name = os.path.basename(directory)
        self.files = {}
        self.file_num_list = None
        self.file_num_itr = None
        self.enq_map = EnqueueMap(self)
        self.txn_map = TransactionMap(self.enq_map)
        self.current_journal_file = None
        self.first_rec_flag = None
        self.statistics = JournalStatistics()
        self.xid_prepared_list = xid_prepared_list # This is None for the TPL instance only
        self.args = args
        self.last_record_offset = None # TODO: Move into JournalFile
        self.num_filler_records_required = None # TODO: Move into JournalFile
        self.fill_to_offset = None
    def add_record(self, record):
        """Used for reconciling transactions only - called from JournalRecoveryManager._reconcile_transactions()"""
        if isinstance(record, qlslibs.jrnl.EnqueueRecord) or isinstance(record, qlslibs.jrnl.DequeueRecord):
            if record.xid_size > 0:
                self.txn_map.add(self.current_journal_file, record)
            else:
                self.enq_map.add(self.current_journal_file, record, False)
        elif isinstance(record, qlslibs.jrnl.TransactionRecord):
            self.txn_map.delete(self.current_journal_file, record)
        else:
            raise qlslibs.err.InvalidRecordTypeError(self.current_journal_file, record, 'add to Journal')
    def get_enq_map_record(self, rid):
        return self.enq_map.get(rid)
    def get_txn_map_record(self, xid):
        return self.txn_map.get(xid)
    def get_outstanding_txn_list(self):
        return self.txn_map.get_xid_list()
    def get_queue_name(self):
        return self.queue_name
    def recover(self, high_rid_counter):
        print 'Recovering %s...' % self.queue_name,
        self._analyze_files()
        try:
            while self._get_next_record(high_rid_counter):
                pass
            self._check_alignment()
        except qlslibs.err.NoMoreFilesInJournalError:
            print 'No more files in journal'
        except qlslibs.err.FirstRecordOffsetMismatchError as err:
            print '0x%08x: **** FRO ERROR: queue=\"%s\" fid=0x%x fro actual=0x%08x expected=0x%08x' % \
            (err.get_expected_fro(), err.get_queue_name(), err.get_file_number(), err.get_record_offset(),
             err.get_expected_fro())
        print 'done'
    def reconcile_transactions(self, prepared_list, txn_flag):
        xid_list = self.txn_map.get_xid_list()
        if len(xid_list) > 0:
            print self.queue_name, 'contains', len(xid_list), 'open transaction(s):'
        for xid in xid_list:
            if xid in prepared_list.keys():
                commit_flag = prepared_list[xid]
                if commit_flag is None:
                    print ' ', qlslibs.utils.format_xid(xid), '- Assuming commit after prepare'
                    if txn_flag:
                        self.txn_map.commit(xid)
                elif commit_flag:
                    print ' ', qlslibs.utils.format_xid(xid), '- Completing interrupted commit operation'
                    if txn_flag:
                        self.txn_map.commit(xid)
                else:
                    print ' ', qlslibs.utils.format_xid(xid), '- Completing interrupted abort operation'
                    if txn_flag:
                        self.txn_map.abort(xid)
            else:
                print ' ', qlslibs.utils.format_xid(xid), '- Ignoring, not in prepared transaction list'
                if txn_flag:
                    self.txn_map.abort(xid)
    def report(self, args):
        print 'Journal "%s":' % self.queue_name
        print '=' * (11 + len(self.queue_name))
        if args.stats:
            print str(self.statistics)
        print self.enq_map.report_str(args)
        print self.txn_map.report_str(args)
        JournalFile.report_header()
        for file_num in sorted(self.files.keys()):
            self.files[file_num].report()
        #TODO: move this to JournalFile, append to file info
        if self.num_filler_records_required is not None and self.fill_to_offset is not None:
            print '0x%x:0x%08x: %d filler records required for DBLK alignment to 0x%08x' % \
                  (self.current_journal_file.file_header.file_num, self.last_record_offset,
                   self.num_filler_records_required, self.fill_to_offset)
        print
    #--- protected functions ---
    def _analyze_files(self):
        for dir_entry in os.listdir(self.directory):
            dir_entry_bits = dir_entry.split('.')
            if len(dir_entry_bits) == 2 and dir_entry_bits[1] == Journal.JRNL_SUFFIX:
                fq_file_name = os.path.join(self.directory, dir_entry)
                file_handle = open(fq_file_name)
                args = qlslibs.utils.load_args(file_handle, qlslibs.jrnl.RecordHeader)
                file_hdr = qlslibs.jrnl.FileHeader(*args)
                file_hdr.init(file_handle, *qlslibs.utils.load_args(file_handle, qlslibs.jrnl.FileHeader))
                if file_hdr.is_header_valid(file_hdr):
                    file_hdr.load(file_handle)
                    if file_hdr.is_valid(False):
                        qlslibs.utils.skip(file_handle,
                                           file_hdr.file_header_size_sblks * qlslibs.utils.DEFAULT_SBLK_SIZE)
                        self.files[file_hdr.file_num] = JournalFile(file_hdr)
        self.file_num_list = sorted(self.files.keys())
        self.file_num_itr = iter(self.file_num_list)
    def _check_alignment(self): # TODO: Move into JournalFile
        if self.last_record_offset is None: # Empty file, _check_file() never run
            return
        remaining_sblks = self.last_record_offset % qlslibs.utils.DEFAULT_SBLK_SIZE
        if remaining_sblks == 0:
            self.num_filler_records_required = 0
        else:
            self.num_filler_records_required = (qlslibs.utils.DEFAULT_SBLK_SIZE - remaining_sblks) / \
                                               qlslibs.utils.DEFAULT_DBLK_SIZE
            self.fill_to_offset = self.last_record_offset + \
                                  (self.num_filler_records_required * qlslibs.utils.DEFAULT_DBLK_SIZE)
            if self.args.show_recovery_recs or self.args.show_all_recs:
                print '0x%x:0x%08x: %d filler records required for DBLK alignment to 0x%08x' % \
                      (self.current_journal_file.file_header.file_num, self.last_record_offset,
                       self.num_filler_records_required, self.fill_to_offset)
    def _check_file(self):
        if self.current_journal_file is not None:
            if not self.current_journal_file.file_header.is_end_of_file():
                return True
            if self.current_journal_file.file_header.is_end_of_file():
                self.last_record_offset = self.current_journal_file.file_header.file_handle.tell()
        if not self._get_next_file():
            return False
        fhdr = self.current_journal_file.file_header
        fhdr.file_handle.seek(fhdr.first_record_offset)
        return True
    def _get_next_file(self):
        if self.current_journal_file is not None:
            file_handle = self.current_journal_file.file_header.file_handle
            if not file_handle.closed: # sanity check, should not be necessary
                file_handle.close()
        file_num = 0
        try:
            while file_num == 0:
                file_num = self.file_num_itr.next()
        except StopIteration:
            pass
        if file_num == 0:
            return False
        self.current_journal_file = self.files[file_num]
        self.first_rec_flag = True
        if self.args.show_recovery_recs or self.args.show_all_recs:
            file_header = self.current_journal_file.file_header
            print '0x%x:%s' % (file_header.file_num, file_header.to_string())
        return True
    def _get_next_record(self, high_rid_counter):
        if not self._check_file():
            return False
        self.last_record_offset = self.current_journal_file.file_header.file_handle.tell()
        this_record = qlslibs.utils.load(self.current_journal_file.file_header.file_handle, qlslibs.jrnl.RecordHeader)
        if not this_record.is_header_valid(self.current_journal_file.file_header):
            return False
        if self.first_rec_flag:
            if this_record.file_offset != self.current_journal_file.file_header.first_record_offset:
                raise qlslibs.err.FirstRecordOffsetMismatchError(self.current_journal_file.file_header, this_record)
            self.first_rec_flag = False
        self.statistics.total_record_count += 1
        start_journal_file = self.current_journal_file
        if isinstance(this_record, qlslibs.jrnl.EnqueueRecord):
            ok_flag = self._handle_enqueue_record(this_record, start_journal_file)
            high_rid_counter.check(this_record.record_id)
            if self.args.show_recovery_recs or self.args.show_all_recs:
                print '0x%x:%s' % (start_journal_file.file_header.file_num, \
                                   this_record.to_string(self.args.show_xids, self.args.show_data, self.args.txtest))
        elif isinstance(this_record, qlslibs.jrnl.DequeueRecord):
            ok_flag = self._handle_dequeue_record(this_record, start_journal_file)
            high_rid_counter.check(this_record.record_id)
            if self.args.show_recovery_recs or self.args.show_all_recs:
                print '0x%x:%s' % (start_journal_file.file_header.file_num, this_record.to_string(self.args.show_xids, None, None))
        elif isinstance(this_record, qlslibs.jrnl.TransactionRecord):
            ok_flag = self._handle_transaction_record(this_record, start_journal_file)
            high_rid_counter.check(this_record.record_id)
            if self.args.show_recovery_recs or self.args.show_all_recs:
                print '0x%x:%s' % (start_journal_file.file_header.file_num, this_record.to_string(self.args.show_xids, None, None))
        else:
            self.statistics.filler_record_count += 1
            ok_flag = True
            if self.args.show_all_recs:
                print '0x%x:%s' % (start_journal_file.file_header.file_num, this_record)
        qlslibs.utils.skip(self.current_journal_file.file_header.file_handle, qlslibs.utils.DEFAULT_DBLK_SIZE)
        return ok_flag
    def _handle_enqueue_record(self, enqueue_record, start_journal_file):
        while enqueue_record.load(self.current_journal_file.file_header.file_handle):
            if not self._get_next_file():
                enqueue_record.truncated_flag = True
                return False
        if not enqueue_record.is_valid(start_journal_file):
            return False
        if enqueue_record.is_external() and enqueue_record.data != None:
            raise qlslibs.err.ExternalDataError(self.current_journal_file.file_header, enqueue_record)
        if enqueue_record.is_transient():
            self.statistics.transient_record_count += 1
            return True
        if enqueue_record.xid_size > 0:
            self.txn_map.add(start_journal_file, enqueue_record)
            self.statistics.transaction_operation_count += 1
            self.statistics.transaction_record_count += 1
            self.statistics.transaction_enqueue_count += 1
        else:
            self.enq_map.add(start_journal_file, enqueue_record, False)
        start_journal_file.incr_enq_cnt()
        self.statistics.enqueue_count += 1
        return True
    def _handle_dequeue_record(self, dequeue_record, start_journal_file):
        while dequeue_record.load(self.current_journal_file.file_header.file_handle):
            if not self._get_next_file():
                dequeue_record.truncated_flag = True
                return False
        if not dequeue_record.is_valid(start_journal_file):
            return False
        if dequeue_record.xid_size > 0:
            if self.xid_prepared_list is None: # ie this is the TPL
                dequeue_record.transaction_prepared_list_flag = True
            elif not self.enq_map.contains(dequeue_record.dequeue_record_id):
                dequeue_record.warnings.append('NOT IN EMAP') # Only for non-TPL records
            self.txn_map.add(start_journal_file, dequeue_record)
            self.statistics.transaction_operation_count += 1
            self.statistics.transaction_record_count += 1
            self.statistics.transaction_dequeue_count += 1
        else:
            try:
                self.enq_map.delete(start_journal_file, dequeue_record)[0].decr_enq_cnt(dequeue_record)
            except qlslibs.err.RecordIdNotFoundError:
                dequeue_record.warnings.append('NOT IN EMAP')
        self.statistics.dequeue_count += 1
        return True
    def _handle_transaction_record(self, transaction_record, start_journal_file):
        while transaction_record.load(self.current_journal_file.file_header.file_handle):
            if not self._get_next_file():
                transaction_record.truncated_flag = True
                return False
        if not transaction_record.is_valid(start_journal_file):
            return False
        if transaction_record.magic[-1] == 'a': # Abort
            self.statistics.transaction_abort_count += 1
        elif transaction_record.magic[-1] == 'c': # Commit
            self.statistics.transaction_commit_count += 1
        else:
            raise InvalidRecordTypeError('Unknown transaction record magic \'%s\'' % transaction_record.magic)
        if self.txn_map.contains(transaction_record.xid):
            self.txn_map.delete(self.current_journal_file, transaction_record)
        else:
            transaction_record.warnings.append('NOT IN TMAP')
#        if transaction_record.magic[-1] == 'c': # commits only
#            self._txn_obj_list[hdr.xid] = hdr
        self.statistics.transaction_record_count += 1
        return True
    def _load_data(self, record):
        while not record.is_complete:
            record.load(self.current_journal_file.file_handle)

class JournalFile(object):
    def __init__(self, file_header):
        self.file_header = file_header
        self.enq_cnt = 0
        self.deq_cnt = 0
        self.num_filler_records_required = None
    def incr_enq_cnt(self):
        self.enq_cnt += 1
    def decr_enq_cnt(self, record):
        if self.enq_cnt <= self.deq_cnt:
            raise qlslibs.err.EnqueueCountUnderflowError(self.file_header, record)
        self.deq_cnt += 1
    def get_enq_cnt(self):
        return self.enq_cnt - self.deq_cnt
    def is_outstanding_enq(self):
        return self.enq_cnt > self.deq_cnt
    @staticmethod
    def report_header():
        print 'file_num enq_cnt p_no   efp journal_file'
        print '-------- ------- ---- ----- ------------'
    def report(self):
        comment = '<uninitialized>' if self.file_header.file_num == 0 else ''
        file_num_str = '0x%x' % self.file_header.file_num
        print '%8s %7d %4d %4dk %s %s' % (file_num_str, self.get_enq_cnt(), self.file_header.partition_num,
                                          self.file_header.efp_data_size_kb,
                                          os.path.basename(self.file_header.file_handle.name), comment)
