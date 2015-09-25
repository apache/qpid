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
Module: qlslibs.jrnl

Contains journal record classes.
"""

import qlslibs.err
import qlslibs.utils
import string
import struct
import time

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
        self.truncated_flag = False
    def encode(self):
        return struct.pack(RecordHeader.FORMAT, self.magic, self.version, self.user_flags, self.serial, self.record_id)
    def load(self, file_handle):
        pass
    @staticmethod
    def discriminate(args):
        """Use the last char in the header magic to determine the header type"""
        return CLASSES.get(args[1][-1], RecordHeader)
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
            if self.version != qlslibs.utils.DEFAULT_RECORD_VERSION:
                raise qlslibs.err.InvalidRecordVersionError(file_header, self, qlslibs.utils.DEFAULT_RECORD_VERSION)
            if self.serial != file_header.serial:
                return False
        return True
    def to_rh_string(self):
        """Return string representation of this header"""
        if self.is_empty():
            return '0x%08x: <empty>' % (self.file_offset)
        if self.magic[-1] == 'x':
            return '0x%08x: [X]' % (self.file_offset)
        if self.magic[-1] in ['a', 'c', 'd', 'e', 'f', 'x']:
            return '0x%08x: [%c v=%d f=0x%04x rid=0x%x]' % \
                (self.file_offset, self.magic[-1].upper(), self.version, self.user_flags, self.record_id)
        return '0x%08x: <error, unknown magic "%s" (possible overwrite boundary?)>' %  (self.file_offset, self.magic)
    def _get_warnings(self):
        warn_str = ''
        for warn in self.warnings:
            warn_str += '<%s>' % warn
        return warn_str

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
            self.valid_flag = qlslibs.utils.inv_str(self.xmagic) == record.magic and \
                              self.serial == record.serial and \
                              self.record_id == record.record_id and \
                              qlslibs.utils.adler32(record.checksum_encode()) == self.checksum
        return self.valid_flag
    def to_string(self):
        """Return a string representation of the this RecordTail instance"""
        if self.valid_flag is not None:
            if not self.valid_flag:
                return '[INVALID RECORD TAIL]'
        magic = qlslibs.utils.inv_str(self.xmagic)
        magic_char = magic[-1].upper() if magic[-1] in string.printable else '?'
        return '[%c cs=0x%08x rid=0x%x]' % (magic_char, self.checksum, self.record_id)

class FileHeader(RecordHeader):
    FORMAT = '<2H4x5QH'
    MAGIC = 'QLSf'
    def init(self, file_handle, _, file_header_size_sblks, partition_num, efp_data_size_kb, first_record_offset,
             timestamp_sec, timestamp_ns, file_num, queue_name_len):
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
    def encode(self):
        if self.queue_name is None:
            return RecordHeader.encode(self) + struct.pack(self.FORMAT, self.file_header_size_sblks, \
                                                           self.partition_num, self.efp_data_size_kb, \
                                                           self.first_record_offset, self.timestamp_sec, \
                                                           self.timestamp_ns, self.file_num, 0)
        return RecordHeader.encode(self) + struct.pack(self.FORMAT, self.file_header_size_sblks, self.partition_num, \
                                                       self.efp_data_size_kb, self.first_record_offset, \
                                                       self.timestamp_sec, self.timestamp_ns, self.file_num, \
                                                       self.queue_name_len) + self.queue_name
    def get_file_size(self):
        """Sum of file header size and data size"""
        return (self.file_header_size_sblks * qlslibs.utils.DEFAULT_SBLK_SIZE) + (self.efp_data_size_kb * 1024)
    def load(self, file_handle):
        self.queue_name = file_handle.read(self.queue_name_len)
    def is_end_of_file(self):
        return self.file_handle.tell() >= self.get_file_size()
    def is_valid(self, is_empty):
        if not RecordHeader.is_header_valid(self, self):
            return False
        if self.file_handle is None or self.file_header_size_sblks == 0 or self.partition_num == 0 or \
           self.efp_data_size_kb == 0:
            return False
        if is_empty:
            if self.first_record_offset != 0 or self.timestamp_sec != 0 or self.timestamp_ns != 0 or \
               self.file_num != 0 or self.queue_name_len != 0:
                return False
        else:
            if self.first_record_offset == 0 or self.timestamp_sec == 0 or self.timestamp_ns == 0 or \
               self.file_num == 0 or self.queue_name_len == 0:
                return False
            if self.queue_name is None:
                return False
            if len(self.queue_name) != self.queue_name_len:
                return False
        return True
    def timestamp_str(self):
        """Get the timestamp of this record in string format"""
        now = time.gmtime(self.timestamp_sec)
        fstr = '%%a %%b %%d %%H:%%M:%%S.%09d %%Y' % (self.timestamp_ns)
        return time.strftime(fstr, now)
    def to_string(self):
        """Return a string representation of the this FileHeader instance"""
        return '%s fnum=0x%x fro=0x%08x p=%d s=%dk t=%s %s' % (self.to_rh_string(), self.file_num,
                                                             self.first_record_offset, self.partition_num,
                                                             self.efp_data_size_kb, self.timestamp_str(),
                                                             self._get_warnings())

class EnqueueRecord(RecordHeader):
    FORMAT = '<2Q'
    MAGIC = 'QLSe'
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
    def checksum_encode(self): # encode excluding record tail
        cs_bytes = RecordHeader.encode(self) + struct.pack(self.FORMAT, self.xid_size, self.data_size)
        if self.xid is not None:
            cs_bytes += self.xid
        if self.data is not None:
            cs_bytes += self.data
        return cs_bytes
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
        self.xid, self.xid_complete = qlslibs.utils.load_data(file_handle, self.xid, self.xid_size)
        if not self.xid_complete:
            return True
        if self.is_external():
            self.data_complete = True
        else:
            self.data, self.data_complete = qlslibs.utils.load_data(file_handle, self.data, self.data_size)
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
    def to_string(self, show_xid_flag, show_data_flag, txtest_flag):
        """Return a string representation of the this EnqueueRecord instance"""
        if self.truncated_flag:
            return '%s xid(%d) data(%d) [Truncated, no more files in journal]' % (RecordHeader.__str__(self),
                                                                                  self.xid_size, self.data_size)
        if self.record_tail is None:
            record_tail_str = ''
        else:
            record_tail_str = self.record_tail.to_string()
        return '%s %s %s %s %s %s' % (self.to_rh_string(),
                                      qlslibs.utils.format_xid(self.xid, self.xid_size, show_xid_flag),
                                      qlslibs.utils.format_data(self.data, self.data_size, show_data_flag, txtest_flag),
                                      record_tail_str, self._print_flags(), self._get_warnings())
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

class DequeueRecord(RecordHeader):
    FORMAT = '<2Q'
    MAGIC = 'QLSd'
    TXN_COMPLETE_COMMIT_FLAG = 0x10
    def init(self, _, dequeue_record_id, xid_size):
        self.dequeue_record_id = dequeue_record_id
        self.xid_size = xid_size
        self.transaction_prepared_list_flag = False
        self.xid = None
        self.xid_complete = False
        self.record_tail = None
    def checksum_encode(self): # encode excluding record tail
        return RecordHeader.encode(self) + struct.pack(self.FORMAT, self.dequeue_record_id, self.xid_size) + \
            self.xid
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
        self.xid, self.xid_complete = qlslibs.utils.load_data(file_handle, self.xid, self.xid_size)
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
    def to_string(self, show_xid_flag, _u1, _u2):
        """Return a string representation of the this DequeueRecord instance"""
        if self.truncated_flag:
            return '%s xid(%d) drid=0x%x [Truncated, no more files in journal]' % (RecordHeader.__str__(self),
                                                                                   self.xid_size,
                                                                                   self.dequeue_record_id)
        if self.record_tail is None:
            record_tail_str = ''
        else:
            record_tail_str = self.record_tail.to_string()
        return '%s drid=0x%x %s %s %s %s' % (self.to_rh_string(), self.dequeue_record_id,
                                             qlslibs.utils.format_xid(self.xid, self.xid_size, show_xid_flag),
                                             record_tail_str, self._print_flags(), self._get_warnings())
    def _print_flags(self):
        """Utility function to decode the flags field in the header and print a string representation"""
        if self.transaction_prepared_list_flag:
            if self.is_transaction_complete_commit():
                return '[COMMIT]'
            else:
                return '[ABORT]'
        return ''

class TransactionRecord(RecordHeader):
    FORMAT = '<Q'
    MAGIC_ABORT = 'QLSa'
    MAGIC_COMMIT = 'QLSc'
    def init(self, _, xid_size):
        self.xid_size = xid_size
        self.xid = None
        self.xid_complete = False
        self.record_tail = None
    def checksum_encode(self): # encode excluding record tail
        return RecordHeader.encode(self) + struct.pack(self.FORMAT, self.xid_size) + self.xid
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
        self.xid, self.xid_complete = qlslibs.utils.load_data(file_handle, self.xid, self.xid_size)
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
    def to_string(self, show_xid_flag, _u1, _u2):
        """Return a string representation of the this TransactionRecord instance"""
        if self.truncated_flag:
            return '%s xid(%d) [Truncated, no more files in journal]' % (RecordHeader.__str__(self), self.xid_size)
        if self.record_tail is None:
            record_tail_str = ''
        else:
            record_tail_str = self.record_tail.to_string()
        return '%s %s %s %s' % (self.to_rh_string(),
                                qlslibs.utils.format_xid(self.xid, self.xid_size, show_xid_flag),
                                record_tail_str, self._get_warnings())

# =============================================================================

CLASSES = {
    'a': TransactionRecord,
    'c': TransactionRecord,
    'd': DequeueRecord,
    'e': EnqueueRecord,
}

if __name__ == '__main__':
    print 'This is a library, and cannot be executed.'
