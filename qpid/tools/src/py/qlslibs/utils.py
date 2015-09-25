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
Module: qlslibs.utils

Contains helper functions for qpid_qls_analyze.
"""

import os
import qlslibs.jrnl
import stat
import string
import struct
import subprocess
import zlib

DEFAULT_DBLK_SIZE = 128
DEFAULT_SBLK_SIZE = 4096 # 32 dblks
DEFAULT_SBLK_SIZE_KB = DEFAULT_SBLK_SIZE / 1024
DEFAULT_RECORD_VERSION = 2
DEFAULT_HEADER_SIZE_SBLKS = 1

def adler32(data):
    """return the adler32 checksum of data"""
    return zlib.adler32(data) & 0xffffffff

def create_record(magic, uflags, journal_file, record_id, dequeue_record_id, xid, data):
    """Helper function to construct a record with xid, data (where applicable) and consistent tail with checksum"""
    record_class = qlslibs.jrnl.CLASSES.get(magic[-1])
    record = record_class(0, magic, DEFAULT_RECORD_VERSION, uflags, journal_file.file_header.serial, record_id)
    xid_length = len(xid) if xid is not None else 0
    if isinstance(record, qlslibs.jrnl.EnqueueRecord):
        data_length = len(data) if data is not None else 0
        record.init(None, xid_length, data_length)
    elif isinstance(record, qlslibs.jrnl.DequeueRecord):
        record.init(None, dequeue_record_id, xid_length)
    elif isinstance(record, qlslibs.jrnl.TransactionRecord):
        record.init(None, xid_length)
    else:
        raise qlslibs.err.InvalidClassError(record.__class__.__name__)
    if xid is not None:
        record.xid = xid
        record.xid_complete = True
    if data is not None:
        record.data = data
        record.data_complete = True
    record.record_tail = _mk_record_tail(record)
    return record

def efp_directory_size(directory_name):
    """"Decode the directory name in the format NNNk to a numeric size, where NNN is a number string"""
    try:
        if directory_name[-1] == 'k':
            return int(directory_name[:-1])
    except ValueError:
        pass
    return 0

def format_data(data, data_size=None, show_data_flag=True, txtest_flag=False):
    """Format binary data for printing"""
    return _format_binary(data, data_size, show_data_flag, 'data', qlslibs.err.DataSizeError, False, txtest_flag)

def format_xid(xid, xid_size=None, show_xid_flag=True):
    """Format binary XID for printing"""
    return _format_binary(xid, xid_size, show_xid_flag, 'xid', qlslibs.err.XidSizeError, True, False)

def get_avail_disk_space(path):
    df_proc = subprocess.Popen(["df", path], stdout=subprocess.PIPE)
    output = df_proc.communicate()[0]
    return int(output.split('\n')[1].split()[3])

def has_write_permission(path):
    stat_info = os.stat(path)
    return bool(stat_info.st_mode & stat.S_IRGRP)

def inv_str(in_string):
    """Perform a binary 1's compliment (invert all bits) on a binary string"""
    istr = ''
    for index in range(0, len(in_string)):
        istr += chr(~ord(in_string[index]) & 0xff)
    return istr

def load(file_handle, klass):
    """Load a record of class klass from a file"""
    args = load_args(file_handle, klass)
    subclass = klass.discriminate(args)
    result = subclass(*args) # create instance of record
    if subclass != klass:
        result.init(*load_args(file_handle, subclass))
    return result

def load_args(file_handle, klass):
    """Load the arguments from class klass"""
    size = struct.calcsize(klass.FORMAT)
    foffs = file_handle.tell(),
    fbin = file_handle.read(size)
    if len(fbin) != size:
        raise qlslibs.err.UnexpectedEndOfFileError(len(fbin), size, foffs, file_handle.name)
    return foffs + struct.unpack(klass.FORMAT, fbin)

def load_data(file_handle, element, element_size):
    """Read element_size bytes of binary data from file_handle into element"""
    if element_size == 0:
        return element, True
    if element is None:
        element = file_handle.read(element_size)
    else:
        read_size = element_size - len(element)
        element += file_handle.read(read_size)
    return element, len(element) == element_size

def skip(file_handle, boundary):
    """Read and discard disk bytes until the next multiple of boundary"""
    if not file_handle.closed:
        file_handle.read(_rem_bytes_in_block(file_handle, boundary))

#--- protected functions ---

def _format_binary(bin_str, bin_size, show_bin_flag, prefix, err_class, hex_num_flag, txtest_flag):
    """Format binary XID for printing"""
    if bin_str is None and bin_size is not None:
        if bin_size > 0:
            raise err_class(bin_size, len(bin_str), bin_str)
        return ''
    if bin_size is None:
        bin_size = len(bin_str)
    elif bin_size != len(bin_str):
        raise err_class(bin_size, len(bin_str), bin_str)
    out_str = '%s(%d)' % (prefix, bin_size)
    if txtest_flag:
        out_str += '=\'%s\'' % _txtest_msg_str(bin_str)
    elif show_bin_flag:
        if _is_printable(bin_str):
            binstr = '"%s"' % _split_str(bin_str)
        elif hex_num_flag:
            binstr = '0x%s' % _str_to_hex_num(bin_str)
        else:
            binstr = _hex_split_str(bin_str, 50, 10, 10)
        out_str += '=\'%s\'' % binstr
    return out_str

def _hex_str(in_str, begin, end):
    """Return a binary string as a hex string"""
    hstr = ''
    for index in range(begin, end):
        if _is_printable(in_str[index]):
            hstr += in_str[index]
        else:
            hstr += '\\%02x' % ord(in_str[index])
    return hstr

def _hex_split_str(in_str, split_size, head_size, tail_size):
    """Split a hex string into two parts separated by an ellipsis"""
    if len(in_str) <= split_size:
        return _hex_str(in_str, 0, len(in_str))
    return _hex_str(in_str, 0, head_size) + ' ... ' + _hex_str(in_str, len(in_str)-tail_size, len(in_str))

def _txtest_msg_str(bin_str):
    """Extract the message number used in qpid-txtest"""
    msg_index = bin_str.find('msg')
    if msg_index >= 0:
        end_index = bin_str.find('\x00', msg_index)
        assert end_index >= 0
        return bin_str[msg_index:end_index]
    return None

def _is_printable(in_str):
    """Return True if in_str in printable; False otherwise."""
    for this_char in in_str:
        if this_char not in string.letters and this_char not in string.digits and this_char not in string.punctuation:
            return False
    return True

def _mk_record_tail(record):
    record_tail = qlslibs.jrnl.RecordTail(None)
    record_tail.xmagic = inv_str(record.magic)
    record_tail.checksum = adler32(record.checksum_encode())
    record_tail.serial = record.serial
    record_tail.record_id = record.record_id
    return record_tail

def _rem_bytes_in_block(file_handle, block_size):
    """Return the remaining bytes in a block"""
    foffs = file_handle.tell()
    return (_size_in_blocks(foffs, block_size) * block_size) - foffs

def _size_in_blocks(size, block_size):
    """Return the size in terms of data blocks"""
    return int((size + block_size - 1) / block_size)

def _split_str(in_str, split_size = 50):
    """Split a string into two parts separated by an ellipsis if it is longer than split_size"""
    if len(in_str) < split_size:
        return in_str
    return in_str[:25] + ' ... ' + in_str[-25:]

def _str_to_hex_num(in_str):
    """Turn a string into a hex number representation, little endian assumed (ie LSB is first, MSB is last)"""
    return ''.join(x.encode('hex') for x in reversed(in_str))
