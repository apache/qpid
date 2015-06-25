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
Module: qlslibs.err

Contains error classes.
"""

# --- Parent classes

class QlsError(Exception):
    """Base error class for QLS errors and exceptions"""
    def __init__(self):
        Exception.__init__(self)
    def __str__(self):
        return ''

class QlsRecordError(QlsError):
    """Base error class for individual records"""
    def __init__(self, file_header, record):
        QlsError.__init__(self)
        self.file_header = file_header
        self.record = record
    def get_expected_fro(self):
        return self.file_header.first_record_offset
    def get_file_number(self):
        return self.file_header.file_num
    def get_queue_name(self):
        return self.file_header.queue_name
    def get_record_id(self):
        return self.record.record_id
    def get_record_offset(self):
        return self.record.file_offset
    def __str__(self):
        return 'queue="%s" file_id=0x%x record_offset=0x%x record_id=0x%x' % \
            (self.file_header.queue_name, self.file_header.file_num, self.record.file_offset, self.record.record_id)

# --- Error classes

class AlreadyLockedError(QlsRecordError):
    """Transactional record to be locked is already locked"""
    def __init__(self, file_header, record):
        QlsRecordError.__init__(self, file_header, record)
    def __str__(self):
        return 'Transactional operation already locked in TransactionMap: ' + QlsRecordError.__str__(self)

class DataSizeError(QlsError):
    """Error class for Data size mismatch"""
    def __init__(self, expected_size, actual_size, data_str):
        QlsError.__init__(self)
        self.expected_size = expected_size
        self.actual_size = actual_size
        self.xid_str = data_str
    def __str__(self):
        return 'Inconsistent data size: expected:%d; actual:%d; data="%s"' % \
            (self.expected_size, self.actual_size, self.data_str)

class DuplicateRecordIdError(QlsRecordError):
    """Duplicate Record Id in Enqueue Map"""
    def __init__(self, file_header, record):
        QlsRecordError.__init__(self, file_header, record)
    def __str__(self):
        return 'Duplicate Record Id in enqueue map: ' + QlsRecordError.__str__(self)

class EnqueueCountUnderflowError(QlsRecordError):
    """Attempted to decrement enqueue count past 0"""
    def __init__(self, file_header, record):
        QlsRecordError.__init__(self, file_header, record)
    def __str__(self):
        return 'Enqueue record count underflow: ' + QlsRecordError.__str__(self)

class ExternalDataError(QlsRecordError):
    """Data present in Enqueue record when external data flag is set"""
    def __init__(self, file_header, record):
        QlsRecordError.__init__(self, file_header, record)
    def __str__(self):
        return 'Data present in external data record: ' + QlsRecordError.__str__(self)

class FirstRecordOffsetMismatchError(QlsRecordError):
    """First Record Offset (FRO) does not match file header"""
    def __init__(self, file_header, record):
        QlsRecordError.__init__(self, file_header, record)
    def __str__(self):
        return 'First record offset mismatch: ' + QlsRecordError.__str__(self) + ' expected_offset=0x%x' % \
            self.file_header.first_record_offset

class InsufficientSpaceOnDiskError(QlsError):
    """Insufficient space on disk"""
    def __init__(self, directory, space_avail, space_requried):
        QlsError.__init__(self)
        self.directory = directory
        self.space_avail = space_avail
        self.space_required = space_requried
    def __str__(self):
        return 'Insufficient space on disk: directory=%s; avail_space=%d required_space=%d' % \
               (self.directory, self.space_avail, self.space_required)

class InvalidClassError(QlsError):
    """Invalid class name or type"""
    def __init__(self, class_name):
        QlsError.__init__(self)
        self.class_name = class_name
    def __str__(self):
        return 'Invalid class name "%s"' % self.class_name

class InvalidEfpDirectoryNameError(QlsError):
    """Invalid EFP directory name - should be NNNNk, where NNNN is a number (of any length)"""
    def __init__(self, directory_name):
        QlsError.__init__(self)
        self.directory_name = directory_name
    def __str__(self):
        return 'Invalid EFP directory name "%s"' % self.directory_name

#class InvalidFileSizeString(QlsError):
#    """Invalid file size string"""
#    def __init__(self, file_size_string):
#        QlsError.__init__(self)
#        self.file_size_string = file_size_string
#    def __str__(self):
#        return 'Invalid file size string "%s"' % self.file_size_string

class InvalidPartitionDirectoryNameError(QlsError):
    """Invalid EFP partition name - should be pNNN, where NNN is a 3-digit partition number"""
    def __init__(self, directory_name):
        QlsError.__init__(self)
        self.directory_name = directory_name
    def __str__(self):
        return 'Invalid partition directory name "%s"' % self.directory_name

class InvalidQlsDirectoryNameError(QlsError):
    """Invalid QLS directory name"""
    def __init__(self, directory_name):
        QlsError.__init__(self)
        self.directory_name = directory_name
    def __str__(self):
        return 'Invalid QLS directory name "%s"' % self.directory_name

class InvalidRecordTypeError(QlsRecordError):
    """Error class for any operation using an invalid record type"""
    def __init__(self, file_header, record, error_msg):
        QlsRecordError.__init__(self, file_header, record)
        self.error_msg = error_msg
    def __str__(self):
        return 'Invalid record type: ' + QlsRecordError.__str__(self) + ':' + self.error_msg

class InvalidRecordVersionError(QlsRecordError):
    """Invalid record version"""
    def __init__(self, file_header, record, expected_version):
        QlsRecordError.__init__(self, file_header, record)
        self.expected_version = expected_version
    def __str__(self):
        return 'Invalid record version: queue="%s" ' + QlsRecordError.__str__(self) + \
            ' ver_found=0x%x ver_expected=0x%x' % (self.record_header.version, self.expected_version)

class NoMoreFilesInJournalError(QlsError):
    """Raised when trying to obtain the next file in the journal and there are no more files"""
    def __init__(self, queue_name):
        QlsError.__init__(self)
        self.queue_name = queue_name
    def __str__(self):
        return 'No more journal files in queue "%s"' % self.queue_name

class NonTransactionalRecordError(QlsRecordError):
    """Transactional operation on non-transactional record"""
    def __init__(self, file_header, record, operation):
        QlsRecordError.__init__(self, file_header, record)
        self.operation = operation
    def __str__(self):
        return 'Transactional operation on non-transactional record: ' + QlsRecordError.__str__() + \
            ' operation=%s' % self.operation

class PartitionDoesNotExistError(QlsError):
    """Partition name does not exist on disk"""
    def __init__(self, partition_directory):
        QlsError.__init__(self)
        self.partition_directory = partition_directory
    def __str__(self):
        return 'Partition %s does not exist' % self.partition_directory

class PoolDirectoryAlreadyExistsError(QlsError):
    """Pool directory already exists"""
    def __init__(self, pool_directory):
        QlsError.__init__(self)
        self.pool_directory = pool_directory
    def __str__(self):
        return 'Pool directory %s already exists' % self.pool_directory

class PoolDirectoryDoesNotExistError(QlsError):
    """Pool directory does not exist"""
    def __init__(self, pool_directory):
        QlsError.__init__(self)
        self.pool_directory = pool_directory
    def __str__(self):
        return 'Pool directory %s does not exist' % self.pool_directory

class RecordIdNotFoundError(QlsRecordError):
    """Record Id not found in enqueue map"""
    def __init__(self, file_header, record):
        QlsRecordError.__init__(self, file_header, record)
    def __str__(self):
        return 'Record Id not found in enqueue map: ' + QlsRecordError.__str__()

class RecordNotLockedError(QlsRecordError):
    """Record in enqueue map is not locked"""
    def __init__(self, file_header, record):
        QlsRecordError.__init__(self, file_header, record)
    def __str__(self):
        return 'Record in enqueue map is not locked: ' + QlsRecordError.__str__()

class UnexpectedEndOfFileError(QlsError):
    """The bytes read from a file is less than that expected"""
    def __init__(self, size_read, size_expected, file_offset, file_name):
        QlsError.__init__(self)
        self.size_read = size_read
        self.size_expected = size_expected
        self.file_offset = file_offset
        self.file_name = file_name
    def __str__(self):
        return 'Tried to read %d at offset %d in file "%s"; only read %d' % \
            (self.size_read, self.file_offset, self.file_name, self.size_expected)

class WritePermissionError(QlsError):
    """No write permission"""
    def __init__(self, directory):
        QlsError.__init__(self)
        self.directory = directory
    def __str__(self):
        return 'No write permission in directory %s' % self.directory

class XidSizeError(QlsError):
    """Error class for Xid size mismatch"""
    def __init__(self, expected_size, actual_size, xid_str):
        QlsError.__init__(self)
        self.expected_size = expected_size
        self.actual_size = actual_size
        self.xid_str = xid_str
    def __str__(self):
        return 'Inconsistent xid size: expected:%d; actual:%d; xid="%s"' % \
            (self.expected_size, self.actual_size, self.xid_str)

# =============================================================================

if __name__ == "__main__":
    print "This is a library, and cannot be executed."
