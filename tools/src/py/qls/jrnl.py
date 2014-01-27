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

import os
import os.path
import qls.err
import string
import struct
from time import gmtime, strftime

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
    TPL_DIR_NAME = 'tpl'
    JRNL_DIR_NAME = 'jrnl'
    def __init__(self, directory):
        if not os.path.exists(directory):
            raise qls.err.InvalidQlsDirectoryNameError(directory)
        self.directory = directory
        self.tpl = None
        self.journals = {}
        self.high_rid_counter = HighCounter()
    def report(self, print_stats_flag):
        if self.tpl is not None:
            self.tpl.report(print_stats_flag)
        for queue_name in sorted(self.journals.keys()):
            self.journals[queue_name].report(print_stats_flag)
    def run(self, args):
        tpl_dir = os.path.join(self.directory, JournalRecoveryManager.TPL_DIR_NAME)
        if os.path.exists(tpl_dir):
            self.tpl = Journal(tpl_dir, None)
            self.tpl.recover(self.high_rid_counter)
            print
        jrnl_dir = os.path.join(self.directory, JournalRecoveryManager.JRNL_DIR_NAME)
        prepared_list = self.tpl.txn_map.get_prepared_list()
        if os.path.exists(jrnl_dir):
            for dir_entry in sorted(os.listdir(jrnl_dir)):
                jrnl = Journal(os.path.join(jrnl_dir, dir_entry), prepared_list)
                jrnl.recover(self.high_rid_counter)
                self.journals[jrnl.get_queue_name()] = jrnl
                print
        self._reconcile_transactions(prepared_list, args.txn)
    def _reconcile_transactions(self, prepared_list, txn_flag):
        print 'Transaction reconciliation report:'
        print len(prepared_list), 'open transaction(s) found in prepared transaction list:'
        for xid in prepared_list.keys():
            commit_flag = prepared_list[xid]
            if commit_flag is None:
                status = '[Prepared, neither committed nor aborted - assuming commit]'
            elif commit_flag:
                status = '[Prepared, but interrupted during commit phase]'
            else:
                status = '[Prepared, but interrupted during abort phase]'
            print ' ', Utils.format_xid(xid), status
            if prepared_list[xid] is None: # Prepared, but not committed or aborted
                enqueue_record = self.tpl.get_txn_map_record(xid)
                dequeue_record = Utils.create_record('QLSd', DequeueRecord.TXN_COMPLETE_COMMIT_FLAG, \
                                                     self.tpl.current_journal_file, self.high_rid_counter.get_next(), \
                                                     enqueue_record.record_id, xid, None)
                if txn_flag:
                    self.tpl.add_record(dequeue_record)
        for queue_name in sorted(self.journals.keys()):
            self.journals[queue_name].reconcile_transactions(prepared_list, txn_flag)
        if len(prepared_list) > 0:
            print 'Completing prepared transactions in prepared transaction list:'
        for xid in prepared_list.keys():
            print ' ', Utils.format_xid(xid)
            transaction_record = Utils.create_record('QLSc', 0, self.tpl.current_journal_file, \
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
            raise qls.err.DuplicateRecordIdError(self.journal.current_file_header, enq_record)
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
            raise qls.err.RecordIdNotFoundError(journal_file.file_header, deq_record)
    def get(self, record_id):
        if record_id in self.enq_map:
            return self.enq_map[record_id]
        return None
    def lock(self, journal_file, dequeue_record):
        if dequeue_record.dequeue_record_id not in self.enq_map:
            raise qls.err.RecordIdNotFoundError(journal_file.file_header, dequeue_record)
        self.enq_map[dequeue_record.dequeue_record_id][2] = True
    def report_str(self, _, show_records):
        """Return a string containing a text report for all records in the map"""
        if len(self.enq_map) == 0:
            return 'No enqueued records found.'
        rstr = '%d enqueued records found' % len(self.enq_map)
        if show_records:
            rstr += ":"
            rid_list = self.enq_map.keys()
            rid_list.sort()
            for rid in rid_list:
                journal_file, record, locked_flag = self.enq_map[rid]
                if locked_flag:
                    lock_str = '[LOCKED]'
                else:
                    lock_str = ''
                rstr += '\n  %d:%s %s' % (journal_file.file_header.file_num, record, lock_str)
        else:
            rstr += '.'
        return rstr
    def unlock(self, journal_file, dequeue_record):
        """Set the transaction lock for a given record_id to False"""
        if dequeue_record.dequeue_record_id in self.enq_map:
            if self.enq_map[dequeue_record.dequeue_record_id][2]:
                self.enq_map[dequeue_record.dequeue_record_id][2] = False
            else:
                raise qls.err.RecordNotLockedError(journal_file.file_header, dequeue_record)
        else:
            raise qls.err.RecordIdNotFoundError(journal_file.file_header, dequeue_record)

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
            if isinstance(record, DequeueRecord):
                if self.enq_map.contains(record.dequeue_record_id):
                    self.enq_map.unlock(journal_file, record)
            else:
                journal_file.decr_enq_cnt(record)
        del self.txn_map[xid]
    def add(self, journal_file, record):
        if record.xid is None:
            raise qls.err.NonTransactionalRecordError(journal_file.file_header, record, 'TransactionMap.add()')
        if isinstance(record, DequeueRecord):
            try:
                self.enq_map.lock(journal_file, record)
            except qls.err.RecordIdNotFoundError:
                # Not in emap, look for rid in tmap - should not happen in practice
                txn_op = self._find_record_id(record.xid, record.dequeue_record_id)
                if txn_op != None:
                    if txn_op[2]:
                        raise qls.err.AlreadyLockedError(journal_file.file_header, record)
                    txn_op[2] = True
        if record.xid in self.txn_map:
            self.txn_map[record.xid].append([journal_file, record, False]) # append to existing list
        else:
            self.txn_map[record.xid] = [[journal_file, record, False]] # create new list
    def commit(self, xid):
        """Perform a commit operation for the given xid record"""
        mismatch_list = []
        for journal_file, record, lock in self.txn_map[xid]:
            if isinstance(record, EnqueueRecord):
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
            raise qls.err.InvalidRecordTypeError(journal_file.file_header, transaction_record,
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
                if isinstance(record, EnqueueRecord):
                    prepared_list[xid] = None
                else:
                    prepared_list[xid] = record.is_transaction_complete_commit()
        return prepared_list
    def get_xid_list(self):
        return self.txn_map.keys()
    def report_str(self, _, show_records):
        """Return a string containing a text report for all records in the map"""
        if len(self.txn_map) == 0:
            return 'No outstanding transactions found.'
        rstr = '%d outstanding transaction(s)' % len(self.txn_map)
        if show_records:
            rstr += ':'
            for xid, op_list in self.txn_map.iteritems():
                rstr += '\n  %s containing %d operations:' % (Utils.format_xid(xid), len(op_list))
                for journal_file, record, _ in op_list:
                    rstr += '\n    %d:%s' % (journal_file.file_header.file_num, record)
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
    def __init__(self, directory, xid_prepared_list):
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
    def add_record(self, record):
        if isinstance(record, EnqueueRecord) or isinstance(record, DequeueRecord):
            if record.xid_size > 0:
                self.txn_map.add(self.current_journal_file, record)
            else:
                self.enq_map.add(self.current_journal_file, record, False)
        elif isinstance(record, TransactionRecord):
            self.txn_map.delete(self.current_journal_file, record)
        else:
            raise qls.err.InvalidRecordTypeError(self.current_journal_file, record, 'add to Journal')
    def get_enq_map_record(self, rid):
        return self.enq_map.get(rid)
    def get_txn_map_record(self, xid):
        return self.txn_map.get(xid)
    def get_outstanding_txn_list(self):
        return self.txn_map.get_xid_list()
    def get_queue_name(self):
        return self.queue_name
    def recover(self, high_rid_counter):
        print 'Recovering', self.queue_name #DEBUG
        self._analyze_files()
        try:
            while self._get_next_record(high_rid_counter):
                pass
        except qls.err.NoMoreFilesInJournalError:
            #print '[No more files in journal]' # DEBUG
            #print #DEBUG
            pass
    def reconcile_transactions(self, prepared_list, txn_flag):
        xid_list = self.txn_map.get_xid_list()
        if len(xid_list) > 0:
            print self.queue_name, 'contains', len(xid_list), 'open transaction(s):'
        for xid in xid_list:
            if xid in prepared_list.keys():
                commit_flag = prepared_list[xid]
                if commit_flag is None:
                    print ' ', Utils.format_xid(xid), '- Assuming commit after prepare'
                    if txn_flag:
                        self.txn_map.commit(xid)
                elif commit_flag:
                    print ' ', Utils.format_xid(xid), '- Completing interrupted commit operation'
                    if txn_flag:
                        self.txn_map.commit(xid)
                else:
                    print ' ', Utils.format_xid(xid), '- Completing interrupted abort operation'
                    if txn_flag:
                        self.txn_map.abort(xid)
            else:
                print '  ', Utils.format_xid(xid), '- Aborting, not in prepared transaction list'
                if txn_flag:
                    self.txn_map.abort(xid)
    def report(self, print_stats_flag):
        print 'Journal "%s":' % self.queue_name
        if print_stats_flag:
            print str(self.statistics)
        print self.enq_map.report_str(True, True)
        print self.txn_map.report_str(True, True)
        JournalFile.report_header()
        for file_num in sorted(self.files.keys()):
            self.files[file_num].report()
        print
    #--- protected functions ---
    def _analyze_files(self):
        for dir_entry in os.listdir(self.directory):
            dir_entry_bits = dir_entry.split('.')
            if len(dir_entry_bits) == 2 and dir_entry_bits[1] == JournalRecoveryManager.JRNL_DIR_NAME:
                fq_file_name = os.path.join(self.directory, dir_entry)
                file_handle = open(fq_file_name)
                args = Utils.load_args(file_handle, RecordHeader)
                file_hdr = FileHeader(*args)
                file_hdr.init(file_handle, *Utils.load_args(file_handle, FileHeader))
                if not file_hdr.is_header_valid(file_hdr):
                    break
                file_hdr.load(file_handle)
                if not file_hdr.is_valid():
                    break
                Utils.skip(file_handle, file_hdr.file_header_size_sblks * Utils.SBLK_SIZE)
                self.files[file_hdr.file_num] = JournalFile(file_hdr)
        self.file_num_list = sorted(self.files.keys())
        self.file_num_itr = iter(self.file_num_list)
    def _check_file(self):
        if self.current_journal_file is not None and not self.current_journal_file.file_header.is_end_of_file():
            return
        self._get_next_file()
        fhdr = self.current_journal_file.file_header
        fhdr.file_handle.seek(fhdr.first_record_offset)
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
            raise qls.err.NoMoreFilesInJournalError(self.queue_name)
        self.current_journal_file = self.files[file_num]
        self.first_rec_flag = True
        print self.current_journal_file.file_header
        #print '[file_num=0x%x]' % self.current_journal_file.file_num #DEBUG
    def _get_next_record(self, high_rid_counter):
        self._check_file()
        this_record = Utils.load(self.current_journal_file.file_header.file_handle, RecordHeader)
        if not this_record.is_header_valid(self.current_journal_file.file_header):
            return False
        if self.first_rec_flag:
            if this_record.file_offset != self.current_journal_file.file_header.first_record_offset:
                raise qls.err.FirstRecordOffsetMismatchError(self.current_journal_file.file_header, this_record)
            self.first_rec_flag = False
        high_rid_counter.check(this_record.record_id)
        self.statistics.total_record_count += 1
        if isinstance(this_record, EnqueueRecord):
            self._handle_enqueue_record(this_record)
            print this_record
        elif isinstance(this_record, DequeueRecord):
            self._handle_dequeue_record(this_record)
            print this_record
        elif isinstance(this_record, TransactionRecord):
            self._handle_transaction_record(this_record)
            print this_record
        else:
            self.statistics.filler_record_count += 1
        Utils.skip(self.current_journal_file.file_header.file_handle, Utils.DBLK_SIZE)
        return True
    def _handle_enqueue_record(self, enqueue_record):
        start_journal_file = self.current_journal_file
        while enqueue_record.load(self.current_journal_file.file_header.file_handle):
            self._get_next_file()
        if not enqueue_record.is_valid(self.current_journal_file):
            return
        if enqueue_record.is_external() and enqueue_record.data != None:
            raise qls.err.ExternalDataError(self.current_journal_file.file_header, enqueue_record)
        if enqueue_record.is_transient():
            self.statistics.transient_record_count += 1
            return
        if enqueue_record.xid_size > 0:
            self.txn_map.add(start_journal_file, enqueue_record)
            self.statistics.transaction_operation_count += 1
            self.statistics.transaction_record_count += 1
            self.statistics.transaction_enqueue_count += 1
        else:
            self.enq_map.add(start_journal_file, enqueue_record, False)
        start_journal_file.incr_enq_cnt()
        self.statistics.enqueue_count += 1
        #print enqueue_record, # DEBUG
    def _handle_dequeue_record(self, dequeue_record):
        start_journal_file = self.current_journal_file
        while dequeue_record.load(self.current_journal_file.file_header.file_handle):
            self._get_next_file()
        if not dequeue_record.is_valid(self.current_journal_file):
            return
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
            except qls.err.RecordIdNotFoundError:
                dequeue_record.warnings.append('NOT IN EMAP')
        self.statistics.dequeue_count += 1
        #print dequeue_record, # DEBUG
    def _handle_transaction_record(self, transaction_record):
        while transaction_record.load(self.current_journal_file.file_header.file_handle):
            self._get_next_file()
        if not transaction_record.is_valid(self.current_journal_file):
            return
        if transaction_record.magic[-1] == 'a':
            self.statistics.transaction_abort_count += 1
        else:
            self.statistics.transaction_commit_count += 1
        if self.txn_map.contains(transaction_record.xid):
            self.txn_map.delete(self.current_journal_file, transaction_record)
        else:
            transaction_record.warnings.append('NOT IN TMAP')
#        if transaction_record.magic[-1] == 'c': # commits only
#            self._txn_obj_list[hdr.xid] = hdr
        self.statistics.transaction_record_count += 1
        #print transaction_record, # DEBUG
    def _load_data(self, record):
        while not record.is_complete:
            record.load(self.current_journal_file.file_handle)

class JournalFile(object):
    def __init__(self, file_header):
        self.file_header = file_header
        self.enq_cnt = 0
        self.deq_cnt = 0
    def incr_enq_cnt(self):
        self.enq_cnt += 1
    def decr_enq_cnt(self, record):
        if self.enq_cnt <= self.deq_cnt:
            raise qls.err.EnqueueCountUnderflowError(self.file_header, record)
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
        print '%8d %7d %4d %4dk %s %s' % (self.file_header.file_num, self.get_enq_cnt(), self.file_header.partition_num,
                                          self.file_header.efp_data_size_kb,
                                          os.path.basename(self.file_header.file_handle.name), comment)

class RecordHeader(object):
    FORMAT = '<4s2H2Q'
    def __init__(self, file_offset, magic, version, user_flags, serial, record_id):
        self.file_offset = file_offset
        self.magic = magic
        self.version = version
        self.user_flags = user_flags
        self.serial = serial
        self.record_id = record_id
        self.warnings = []
    def load(self, file_handle):
        pass
    @staticmethod
    def discriminate(args):
        """Use the last char in the header magic to determine the header type"""
        return _CLASSES.get(args[1][-1], RecordHeader)
    def is_empty(self):
        """Return True if this record is empty (ie has a magic of 0x0000"""
        return self.magic == '\x00'*4
    def is_header_valid(self, file_header):
        """Check that this record is valid"""
        if self.is_empty():
            return False
        if self.magic[:3] != 'QLS' or self.magic[3] not in ['a', 'c', 'd', 'e', 'f', 'x']:
            return False
        if self.magic[-1] != 'x':
            if self.version != Utils.RECORD_VERSION:
                raise qls.err.InvalidRecordVersionError(file_header, self, Utils.RECORD_VERSION)
            if self.serial != file_header.serial:
                #print '[serial mismatch at 0x%x]' % self.file_offset #DEBUG
                #print #DEBUG
                return False
        return True
    def _get_warnings(self):
        warn_str = ''
        for warn in self.warnings:
            warn_str += '<%s>' % warn
        return warn_str
    def __str__(self):
        """Return string representation of this header"""
        if self.is_empty():
            return '0x%08x: <empty>' % (self.file_offset)
        if self.magic[-1] == 'x':
            return '0x%08x: [X]' % (self.file_offset)
        if self.magic[-1] in ['a', 'c', 'd', 'e', 'f', 'x']:
            return '0x%08x: [%c v=%d f=0x%04x rid=0x%x]' % \
                (self.file_offset, self.magic[-1].upper(), self.version, self.user_flags, self.record_id)
        return '0x%08x: <error, unknown magic "%s" (possible overwrite boundary?)>' %  (self.file_offset, self.magic)

class RecordTail(object):
    FORMAT = '<4sL2Q'
    def __init__(self, file_handle): # TODO - clumsy, only allows reading from disk. Move all disk stuff to laod()
        self.file_offset = file_handle.tell() if file_handle is not None else 0
        self.complete = False
        self.read_size = struct.calcsize(RecordTail.FORMAT)
        self.fbin = file_handle.read(self.read_size) if file_handle is not None else None
        self.valid_flag = None
        if self.fbin is not None and len(self.fbin) >= self.read_size:
            self.complete = True
            self.xmagic, self.checksum, self.serial, self.record_id = struct.unpack(RecordTail.FORMAT, self.fbin)
    def load(self, file_handle):
        """Used to continue load of RecordTail object if it is split between files"""
        if not self.is_complete:
            self.fbin += file_handle.read(self.read_size - len(self.fbin))
            if (len(self.fbin)) >= self.read_size:
                self.complete = True
                self.xmagic, self.checksum, self.serial, self.record_id = struct.unpack(RecordTail.FORMAT, self.fbin)
    def is_complete(self):
        return self.complete
    def is_valid(self, record):
        if self.valid_flag is None:
            if not self.complete:
                return False
            self.valid_flag = Utils.inv_str(self.xmagic) == record.magic and \
                              self.serial == record.serial and \
                              self.record_id == record.record_id
            # TODO: When we can verify the checksum, add this here
        return self.valid_flag
    def __str__(self):
        """Return a string representation of the this RecordTail instance"""
        if self.valid_flag is not None:
            if not self.valid_flag:
                return '[INVALID RECORD TAIL]'
        magic = Utils.inv_str(self.xmagic)
        magic_char = magic[-1].upper() if magic[-1] in string.printable else '?'
        return '[%c cs=0x%08x rid=0x%x]' % (magic_char, self.checksum, self.record_id)

class FileHeader(RecordHeader):
    FORMAT = '<2H4x5QH'
    def init(self, file_handle, _, file_header_size_sblks, partition_num, efp_data_size_kb,
             first_record_offset, timestamp_sec, timestamp_ns, file_num, queue_name_len):
        self.file_handle = file_handle
        self.file_header_size_sblks = file_header_size_sblks
        self.partition_num = partition_num
        self.efp_data_size_kb = efp_data_size_kb
        self.first_record_offset = first_record_offset
        self.timestamp_sec = timestamp_sec
        self.timestamp_ns = timestamp_ns
        self.file_num = file_num
        self.queue_name_len = queue_name_len
        self.queue_name = None
    def load(self, file_handle):
        self.queue_name = file_handle.read(self.queue_name_len)
    def get_file_size(self):
        """Sum of file header size and data size"""
        return (self.file_header_size_sblks * Utils.SBLK_SIZE) + (self.efp_data_size_kb * 1024)
    def is_end_of_file(self):
        return self.file_handle.tell() >= self.get_file_size()
    def is_valid(self):
        if not RecordHeader.is_header_valid(self, self):
            return False
        if self.file_handle is None or self.file_header_size_sblks == 0 or self.partition_num == 0 or \
           self.efp_data_size_kb == 0 or self.first_record_offset == 0 or self.timestamp_sec == 0 or \
           self.timestamp_ns == 0 or self.file_num == 0:
            return False
        if self.queue_name_len == 0:
            return False
        if self.queue_name is None:
            return False
        if len(self.queue_name) != self.queue_name_len:
            return False
        return True
    def timestamp_str(self):
        """Get the timestamp of this record in string format"""
        time = gmtime(self.timestamp_sec)
        fstr = '%%a %%b %%d %%H:%%M:%%S.%09d %%Y' % (self.timestamp_ns)
        return strftime(fstr, time)
    def __str__(self):
        """Return a string representation of the this FileHeader instance"""
        return '%s fnum=%d fro=0x%08x p=%d s=%dk t=%s %s' % (RecordHeader.__str__(self), self.file_num,
                                                             self.first_record_offset, self.partition_num,
                                                             self.efp_data_size_kb, self.timestamp_str(),
                                                             self._get_warnings())

class EnqueueRecord(RecordHeader):
    FORMAT = '<2Q'
    EXTERNAL_FLAG_MASK = 0x20
    TRANSIENT_FLAG_MASK = 0x10
    def init(self, _, xid_size, data_size):
        self.xid_size = xid_size
        self.data_size = data_size
        self.xid = None
        self.xid_complete = False
        self.data = None
        self.data_complete = False
        self.record_tail = None
    def is_external(self):
        return self.user_flags & EnqueueRecord.EXTERNAL_FLAG_MASK > 0
    def is_transient(self):
        return self.user_flags & EnqueueRecord.TRANSIENT_FLAG_MASK > 0
    def is_valid(self, journal_file):
        if not RecordHeader.is_header_valid(self, journal_file.file_header):
            return False
        if not (self.xid_complete and self.data_complete):
            return False
        if self.xid_size > 0 and len(self.xid) != self.xid_size:
            return False
        if self.data_size > 0 and len(self.data) != self.data_size:
            return False
        if self.xid_size > 0 or self.data_size > 0:
            if self.record_tail is None:
                return False
            if not self.record_tail.is_valid(self):
                return False
        return True
    def load(self, file_handle):
        """Return True when load is incomplete and must be called again with new file handle"""
        self.xid, self.xid_complete = Utils.load_data(file_handle, self.xid, self.xid_size)
        if not self.xid_complete:
            return True
        if self.is_external():
            self.data_complete = True
        else:
            self.data, self.data_complete = Utils.load_data(file_handle, self.data, self.data_size)
            if not self.data_complete:
                return True
        if self.xid_size > 0 or self.data_size > 0:
            if self.record_tail is None:
                self.record_tail = RecordTail(file_handle)
            elif not self.record_tail.is_complete():
                self.record_tail.load(file_handle) # Continue loading partially loaded tail
            if self.record_tail.is_complete():
                self.record_tail.is_valid(self)
            else:
                return True
        return False
    def _print_flags(self):
        """Utility function to decode the flags field in the header and print a string representation"""
        fstr = ''
        if self.is_transient():
            fstr = '[TRANSIENT'
        if self.is_external():
            if len(fstr) > 0:
                fstr += ',EXTERNAL'
            else:
                fstr = '*EXTERNAL'
        if len(fstr) > 0:
            fstr += ']'
        return fstr
    def __str__(self):
        """Return a string representation of the this EnqueueRecord instance"""
        if self.record_tail is None:
            record_tail_str = ''
        else:
            record_tail_str = str(self.record_tail)
        return '%s %s %s %s %s %s' % (RecordHeader.__str__(self), Utils.format_xid(self.xid, self.xid_size),
                                      Utils.format_data(self.data_size, self.data), record_tail_str,
                                      self._print_flags(), self._get_warnings())

class DequeueRecord(RecordHeader):
    FORMAT = '<2Q'
    TXN_COMPLETE_COMMIT_FLAG = 0x10
    def init(self, _, dequeue_record_id, xid_size):
        self.dequeue_record_id = dequeue_record_id
        self.xid_size = xid_size
        self.transaction_prepared_list_flag = False
        self.xid = None
        self.xid_complete = False
        self.record_tail = None
    def is_transaction_complete_commit(self):
        return self.user_flags & DequeueRecord.TXN_COMPLETE_COMMIT_FLAG > 0
    def is_valid(self, journal_file):
        if not RecordHeader.is_header_valid(self, journal_file.file_header):
            return False
        if self.xid_size > 0:
            if not self.xid_complete:
                return False
            if self.xid_size > 0 and len(self.xid) != self.xid_size:
                return False
            if self.record_tail is None:
                return False
            if not self.record_tail.is_valid(self):
                return False
        return True
    def load(self, file_handle):
        """Return True when load is incomplete and must be called again with new file handle"""
        self.xid, self.xid_complete = Utils.load_data(file_handle, self.xid, self.xid_size)
        if not self.xid_complete:
            return True
        if self.xid_size > 0:
            if self.record_tail is None:
                self.record_tail = RecordTail(file_handle)
            elif not self.record_tail.is_complete():
                self.record_tail.load(file_handle)
            if self.record_tail.is_complete():
                self.record_tail.is_valid(self)
            else:
                return True
        return False
    def _print_flags(self):
        """Utility function to decode the flags field in the header and print a string representation"""
        if self.transaction_prepared_list_flag:
            if self.is_transaction_complete_commit():
                return '[COMMIT]'
            else:
                return '[ABORT]'
        return ''
    def __str__(self):
        """Return a string representation of the this DequeueRecord instance"""
        if self.record_tail is None:
            record_tail_str = ''
        else:
            record_tail_str = str(self.record_tail)
        return '%s %s drid=0x%x %s %s %s' % (RecordHeader.__str__(self), Utils.format_xid(self.xid, self.xid_size),
                                             self.dequeue_record_id, record_tail_str, self._print_flags(),
                                             self._get_warnings())

class TransactionRecord(RecordHeader):
    FORMAT = '<Q'
    def init(self, _, xid_size):
        self.xid_size = xid_size
        self.xid = None
        self.xid_complete = False
        self.record_tail = None
    def is_valid(self, journal_file):
        if not RecordHeader.is_header_valid(self, journal_file.file_header):
            return False
        if not self.xid_complete or len(self.xid) != self.xid_size:
            return False
        if self.record_tail is None:
            return False
        if not self.record_tail.is_valid(self):
            return False
        return True
    def load(self, file_handle):
        """Return True when load is incomplete and must be called again with new file handle"""
        self.xid, self.xid_complete = Utils.load_data(file_handle, self.xid, self.xid_size)
        if not self.xid_complete:
            return True
        if self.xid_size > 0:
            if self.record_tail is None:
                self.record_tail = RecordTail(file_handle)
            elif not self.record_tail.is_complete():
                self.record_tail.load(file_handle)
            if self.record_tail.is_complete():
                self.record_tail.is_valid(self)
            else:
                return True
        return False
    def __str__(self):
        """Return a string representation of the this TransactionRecord instance"""
        if self.record_tail is None:
            record_tail_str = ''
        else:
            record_tail_str = str(self.record_tail)
        return '%s %s %s %s' % (RecordHeader.__str__(self), Utils.format_xid(self.xid, self.xid_size), record_tail_str,
                                self._get_warnings())

class Utils(object):
    """Class containing utility functions for dealing with the journal"""
    DBLK_SIZE = 128
    RECORD_VERSION = 2
    SBLK_SIZE = 4096
    @staticmethod
    def create_record(magic, uflags, journal_file, record_id, dequeue_record_id, xid, data):
        record_class = _CLASSES.get(magic[-1])
        record = record_class(0, magic, Utils.RECORD_VERSION, uflags, journal_file.file_header.serial, record_id)
        xid_length = len(xid) if xid is not None else 0
        if isinstance(record, EnqueueRecord):
            data_length = len(data) if data is not None else 0
            record.init(None, xid_length, data_length)
        elif isinstance(record, DequeueRecord):
            record.init(None, dequeue_record_id, xid_length)
        elif isinstance(record, TransactionRecord):
            record.init(None, xid_length)
        else:
            raise qls.err.InvalidClassError(record.__class__.__name__)
        if xid is not None:
            record.xid = xid
            record.xid_complete = True
        if data is not None:
            record.data = data
            record.data_complete = True
        record.record_tail = RecordTail(None)
        record.record_tail.xmagic = Utils.inv_str(magic)
        record.record_tail.checksum = 0 # TODO: when we can calculate checksums, add this here
        record.record_tail.serial = record.serial
        record.record_tail.record_id = record.record_id
        return record
    @staticmethod
    def format_data(dsize, data):
        """Format binary data for printing"""
        if data == None:
            return ''
        # << DEBUG >>
        begin = data.find('msg')
        end = data.find('\0', begin)
        return 'data="%s"' % data[begin:end]
        # << END DEBUG
        if Utils._is_printable(data):
            datastr = Utils._split_str(data)
        else:
            datastr = Utils._hex_split_str(data)
        if dsize != len(data):
            raise qls.err.DataSizeError(dsize, len(data), datastr)
        return 'data(%d)="%s"' % (dsize, datastr)
    @staticmethod
    def format_xid(xid, xidsize=None):
        """Format binary XID for printing"""
        if xid == None and xidsize != None:
            if xidsize > 0:
                raise qls.err.XidSizeError(xidsize, 0, None)
            return ''
        if Utils._is_printable(xid):
            xidstr = '"%s"' % Utils._split_str(xid)
        else:
            xidstr = '0x%s' % Utils._hex_split_str(xid)
        if xidsize == None:
            xidsize = len(xid)
        elif xidsize != len(xid):
            raise qls.err.XidSizeError(xidsize, len(xid), xidstr)
        return 'xid(%d)=%s' % (xidsize, xidstr)
    @staticmethod
    def inv_str(in_string):
        """Perform a binary 1's compliment (invert all bits) on a binary string"""
        istr = ''
        for index in range(0, len(in_string)):
            istr += chr(~ord(in_string[index]) & 0xff)
        return istr
    @staticmethod
    def load(file_handle, klass):
        """Load a record of class klass from a file"""
        args = Utils.load_args(file_handle, klass)
        subclass = klass.discriminate(args)
        result = subclass(*args) # create instance of record
        if subclass != klass:
            result.init(*Utils.load_args(file_handle, subclass))
        return result
    @staticmethod
    def load_args(file_handle, klass):
        """Load the arguments from class klass"""
        size = struct.calcsize(klass.FORMAT)
        foffs = file_handle.tell(),
        fbin = file_handle.read(size)
        if len(fbin) != size:
            raise qls.err.UnexpectedEndOfFileError(len(fbin), size, foffs, file_handle.name)
        return foffs + struct.unpack(klass.FORMAT, fbin)
    @staticmethod
    def load_data(file_handle, element, element_size):
        if element_size == 0:
            return element, True
        if element is None:
            element = file_handle.read(element_size)
        else:
            read_size = element_size - len(element)
            element += file_handle.read(read_size)
        return element, len(element) == element_size
    @staticmethod
    def skip(file_handle, boundary):
        """Read and discard disk bytes until the next multiple of boundary"""
        file_handle.read(Utils._rem_bytes_in_block(file_handle, boundary))
    #--- protected functions ---
    @staticmethod
    def _hex_str(in_str, begin, end):
        """Return a binary string as a hex string"""
        hstr = ''
        for index in range(begin, end):
            if Utils._is_printable(in_str[index]):
                hstr += in_str[index]
            else:
                hstr += '\\%02x' % ord(in_str[index])
        return hstr
    @staticmethod
    def _hex_split_str(in_str):#, split_size = 50):
        """Split a hex string into two parts separated by an ellipsis"""
#        if len(in_str) <= split_size:
#            return Utils._hex_str(in_str, 0, len(in_str))
#        return Utils._hex_str(in_str, 0, 10) + ' ... ' + Utils._hex_str(in_str, len(in_str)-10, len(in_str))
        return ''.join(x.encode('hex') for x in reversed(in_str))
    @staticmethod
    def _is_printable(in_str):
        """Return True if in_str in printable; False otherwise."""
        for this_char in in_str:
            if this_char not in string.printable:
                return False
        return True
    @staticmethod
    def _rem_bytes_in_block(file_handle, block_size):
        """Return the remaining bytes in a block"""
        foffs = file_handle.tell()
        return (Utils._size_in_blocks(foffs, block_size) * block_size) - foffs
    @staticmethod
    def _size_in_blocks(size, block_size):
        """Return the size in terms of data blocks"""
        return int((size + block_size - 1) / block_size)
    @staticmethod
    def _split_str(in_str, split_size = 50):
        """Split a string into two parts separated by an ellipsis if it is longer than split_size"""
        if len(in_str) < split_size:
            return in_str
        return in_str[:25] + ' ... ' + in_str[-25:]

# =============================================================================

_CLASSES = {
    'a': TransactionRecord,
    'c': TransactionRecord,
    'd': DequeueRecord,
    'e': EnqueueRecord,
}

if __name__ == '__main__':
    print 'This is a library, and cannot be executed.'
