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

# == Warnings =================================================================

class JWarning(Exception):
    """Class to convey a warning"""
    def __init__(self, err):
        """Constructor"""
        Exception.__init__(self, err)

# == Errors ===================================================================

class AllJrnlFilesEmptyCsvError(Exception):
    """All journal files are empty (never been written)"""
    def __init__(self, tnum, exp_num_msgs):
        """Constructor"""
        Exception.__init__(self, "[CSV %d] All journal files are empty, but test expects %d msg(s)." %
                           (tnum, exp_num_msgs))

class AlreadyLockedError(Exception):
    """Error class for trying to lock a record that is already locked"""
    def __init__(self, rid):
        """Constructor"""
        Exception.__init__(self, "Locking record which is already locked in EnqMap: rid=0x%x" % rid)

class BadFileNumberError(Exception):
    """Error class for incorrect or unexpected file number"""
    def __init__(self, file_num):
        """Constructor"""
        Exception.__init__(self, "Bad file number %d" % file_num)

class DataSizeError(Exception):
    """Error class for data size mismatch"""
    def __init__(self, exp_size, act_size, data_str):
        """Constructor"""
        Exception.__init__(self, "Inconsistent data size: expected:%d; actual:%d; data=\"%s\"" %
                           (exp_size, act_size, data_str))

class DeleteLockedRecordError(Exception):
    """Error class for deleting a locked record from the enqueue map"""
    def __init__(self, rid):
        """Constructor"""
        Exception.__init__(self, "Deleting locked record from EnqMap: rid=0x%s" % rid)

class DequeueNonExistentEnqueueError(Exception):
    """Error class for attempting to dequeue a non-existent enqueue record (rid)"""
    def __init__(self, deq_rid):
        """Constructor"""
        Exception.__init__(self, "Dequeuing non-existent enqueue record: rid=0x%s" % deq_rid)

class DuplicateRidError(Exception):
    """Error class for placing duplicate rid into enqueue map"""
    def __init__(self, rid):
        """Constructor"""
        Exception.__init__(self, "Adding duplicate record to EnqMap: rid=0x%x" % rid)
            
class EndianMismatchError(Exception):
    """Error class mismatched record header endian flag"""
    def __init__(self, exp_endianness):
        """Constructor"""
        Exception.__init__(self, "Endian mismatch: expected %s, but current record is %s" %
                           self.endian_str(exp_endianness))
    #@staticmethod
    def endian_str(endianness):
        """Return a string tuple for the endianness error message"""
        if endianness:
            return "big", "little"
        return "little", "big"
    endian_str = staticmethod(endian_str)
 
class ExternFlagDataError(Exception):
    """Error class for the extern flag being set and the internal size > 0"""
    def __init__(self, hdr):
        """Constructor"""
        Exception.__init__(self, "Message data found (msg size > 0) on record with external flag set: hdr=%s" % hdr)
       
class ExternFlagCsvError(Exception):
    """External flag mismatch between record and CSV test file"""
    def __init__(self, tnum, exp_extern_flag):
        """Constructor"""
        Exception.__init__(self, "[CSV %d] External flag mismatch: expected %s" % (tnum, exp_extern_flag))

class ExternFlagWithDataCsvError(Exception):
    """External flag set and Message data found"""
    def __init__(self, tnum):
        """Constructor"""
        Exception.__init__(self, "[CSV %d] Message data found on record with external flag set" % tnum)

class FillExceedsFileSizeError(Exception):
    """Internal error from a fill operation which will exceed the specified file size"""
    def __init__(self, cur_size, file_size):
        """Constructor"""
        Exception.__init__(self, "Filling to size %d > max file size %d" % (cur_size, file_size))

class FillSizeError(Exception):
    """Internal error from a fill operation that did not match the calculated end point in the file"""
    def __init__(self, cur_posn, exp_posn):
        """Constructor"""
        Exception.__init__(self, "Filled to size %d > expected file posn %d" % (cur_posn, exp_posn))

class FirstRecordOffsetMismatch(Exception):
    """Error class for file header fro mismatch with actual record"""
    def __init__(self, fro, actual_offs):
        """Constructor"""
        Exception.__init__(self, "File header first record offset mismatch: fro=0x%x; actual offs=0x%x" %
                           (fro, actual_offs))

class InvalidHeaderVersionError(Exception):
    """Error class for invalid record header version"""
    def __init__(self, exp_ver, act_ver):
        """Constructor"""
        Exception.__init__(self, "Invalid header version: expected:%d, actual:%d." % (exp_ver, act_ver))

class InvalidRecordTypeError(Exception):
    """Error class for any operation using an invalid record type"""
    def __init__(self, operation, magic, rid):
        """Constructor"""
        Exception.__init__(self, "Invalid record type for operation: operation=%s record magic=%s, rid=0x%x" %
                           (operation, magic, rid))

class InvalidRecordTailError(Exception):
    """Error class for invalid record tail"""
    def __init__(self, magic_err, rid_err, rec):
        """Constructor"""
        Exception.__init__(self, " > %s *INVALID TAIL RECORD (%s)*" % (rec, self.tail_err_str(magic_err, rid_err)))
    #@staticmethod
    def tail_err_str(magic_err, rid_err):
        """Return a string indicating the tail record error(s)"""
        estr = ""
        if magic_err:
            estr = "magic bad"
            if rid_err:
                estr += ", "
        if rid_err:
            estr += "rid mismatch"
        return estr
    tail_err_str = staticmethod(tail_err_str)

class NonExistentRecordError(Exception):
    """Error class for any operation on an non-existent record"""
    def __init__(self, operation, rid):
        """Constructor"""
        Exception.__init__(self, "Operation on non-existent record: operation=%s; rid=0x%x" % (operation, rid))

class NotLockedError(Exception):
    """Error class for unlocking a record which is not locked in the first place"""
    def __init__(self, rid):
        """Constructor"""
        Exception.__init__(self, "Unlocking record which is not locked in EnqMap: rid=0x%x" % rid)

class JournalSpaceExceededError(Exception):
    """Error class for when journal space of resized journal is too small to contain the transferred records"""
    def __init__(self):
        """Constructor"""
        Exception.__init__(self, "Ran out of journal space while writing records")

class MessageLengthCsvError(Exception):
    """Message length mismatch between record and CSV test file"""
    def __init__(self, tnum, exp_msg_len, actual_msg_len):
        """Constructor"""
        Exception.__init__(self, "[CSV %d] Message length mismatch: expected %d; found %d" %
                           (tnum, exp_msg_len, actual_msg_len))

class NumMsgsCsvError(Exception):
    """Number of messages found mismatched with CSV file"""
    def __init__(self, tnum, exp_num_msgs, actual_num_msgs):
        """Constructor"""
        Exception.__init__(self, "[CSV %s] Incorrect number of messages: expected %d, found %d" %
                           (tnum, exp_num_msgs, actual_num_msgs))

class TransactionCsvError(Exception):
    """Transaction mismatch between record and CSV file"""
    def __init__(self, tnum, exp_transactional):
        """Constructor"""
        Exception.__init__(self, "[CSV %d] Transaction mismatch: expected %s" % (tnum, exp_transactional))

class UnexpectedEndOfFileError(Exception):
    """Error class for unexpected end-of-file during reading"""
    def __init__(self, exp_size, curr_offs):
        """Constructor"""
        Exception.__init__(self, "Unexpected end-of-file: expected file size:%d; current offset:%d" %
                           (exp_size, curr_offs))

class XidLengthCsvError(Exception):
    """Message Xid length mismatch between record and CSV file"""
    def __init__(self, tnum, exp_xid_len, actual_msg_len):
        """Constructor"""
        Exception.__init__(self, "[CSV %d] Message XID mismatch: expected %d; found %d" %
                           (tnum, exp_xid_len, actual_msg_len))

class XidSizeError(Exception):
    """Error class for Xid size mismatch"""
    def __init__(self, exp_size, act_size, xid_str):
        """Constructor"""
        Exception.__init__(self, "Inconsistent xid size: expected:%d; actual:%d; xid=\"%s\"" %
                           (exp_size, act_size, xid_str))

# =============================================================================

if __name__ == "__main__":
    print "This is a library, and cannot be executed."

