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

import jerr
import os.path, sys, xml.parsers.expat
from struct import pack, unpack, calcsize
from time import gmtime, strftime

# TODO: Get rid of these! Use jinf instance instead
DBLK_SIZE = 128
SBLK_SIZE = 4 * DBLK_SIZE

# TODO - this is messy - find a better way to handle this
# This is a global, but is set directly by the calling program
JRNL_FILE_SIZE = None

#== class Utils ======================================================================

class Utils(object):
    """Class containing utility functions for dealing with the journal"""

    __printchars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!\"#$%&'()*+,-./:;<=>?@[\\]^_`{\|}~ "
    
    # The @staticmethod declarations are not supported in RHEL4 (python 2.3.x)
    # When RHEL4 support ends, restore these declarations and remove the older
    # staticmethod() declaration.

    #@staticmethod
    def format_data(dsize, data):
        """Format binary data for printing"""
        if data == None:
            return ""
        if Utils._is_printable(data):
            datastr = Utils._split_str(data)
        else:
            datastr = Utils._hex_split_str(data)
        if dsize != len(data):
            raise jerr.DataSizeError(dsize, len(data), datastr)
        return "data(%d)=\"%s\" " % (dsize, datastr)
    format_data = staticmethod(format_data)

    #@staticmethod
    def format_xid(xid, xidsize=None):
        """Format binary XID for printing"""
        if xid == None and xidsize != None:
            if xidsize > 0:
                raise jerr.XidSizeError(xidsize, 0, None)
            return ""
        if Utils._is_printable(xid):
            xidstr = Utils._split_str(xid)
        else:
            xidstr = Utils._hex_split_str(xid)
        if xidsize == None:
            xidsize = len(xid)
        elif xidsize != len(xid):
            raise jerr.XidSizeError(xidsize, len(xid), xidstr)
        return "xid(%d)=\"%s\" " % (xidsize, xidstr)
    format_xid = staticmethod(format_xid)
    
    #@staticmethod
    def inv_str(string):
        """Perform a binary 1's compliment (invert all bits) on a binary string"""
        istr = ""
        for index in range(0, len(string)):
            istr += chr(~ord(string[index]) & 0xff)
        return istr
    inv_str = staticmethod(inv_str)

    #@staticmethod
    def load(fhandle, klass):
        """Load a record of class klass from a file"""
        args = Utils._load_args(fhandle, klass)
        subclass = klass.discriminate(args)
        result = subclass(*args) # create instance of record
        if subclass != klass:
            result.init(fhandle, *Utils._load_args(fhandle, subclass))
        result.skip(fhandle)
        return result
    load = staticmethod(load)
    
    #@staticmethod
    def load_file_data(fhandle, size, data):
        """Load the data portion of a message from file"""
        if size == 0:
            return (data, True)
        if data == None:
            loaded = 0
        else:
            loaded = len(data)
        foverflow = fhandle.tell() + size - loaded > JRNL_FILE_SIZE
        if foverflow:
            rsize = JRNL_FILE_SIZE - fhandle.tell()
        else:
            rsize = size - loaded
        fbin = fhandle.read(rsize)
        if data == None:
            data = unpack("%ds" % (rsize), fbin)[0]
        else:
            data = data + unpack("%ds" % (rsize), fbin)[0]
        return (data, not foverflow)
    load_file_data = staticmethod(load_file_data)

    #@staticmethod
    def rem_bytes_in_blk(fhandle, blk_size):
        """Return the remaining bytes in a block"""
        foffs = fhandle.tell()
        return Utils.size_in_bytes_to_blk(foffs, blk_size) - foffs
    rem_bytes_in_blk = staticmethod(rem_bytes_in_blk)

    #@staticmethod
    def size_in_blks(size, blk_size):
        """Return the size in terms of data blocks"""
        return int((size + blk_size - 1) / blk_size)
    size_in_blks = staticmethod(size_in_blks)

    #@staticmethod
    def size_in_bytes_to_blk(size, blk_size):
        """Return the bytes remaining until the next block boundary"""
        return Utils.size_in_blks(size, blk_size) * blk_size
    size_in_bytes_to_blk = staticmethod(size_in_bytes_to_blk)

    #@staticmethod
    def _hex_split_str(in_str, split_size = 50):
        """Split a hex string into two parts separated by an ellipsis"""
        if len(in_str) <= split_size:
            return Utils._hex_str(in_str, 0, len(in_str))
#        if len(in_str) > split_size + 25:
#            return Utils._hex_str(in_str, 0, 10) + " ... " + Utils._hex_str(in_str, 55, 65) + " ... " + \
#                   Utils._hex_str(in_str, len(in_str)-10, len(in_str))
        return Utils._hex_str(in_str, 0, 10) + " ... " + Utils._hex_str(in_str, len(in_str)-10, len(in_str))
    _hex_split_str = staticmethod(_hex_split_str)

    #@staticmethod
    def _hex_str(in_str, begin, end):
        """Return a binary string as a hex string"""
        hstr = ""
        for index in range(begin, end):
            if Utils._is_printable(in_str[index]):
                hstr += in_str[index]
            else:
                hstr += "\\%02x" % ord(in_str[index])
        return hstr
    _hex_str = staticmethod(_hex_str)

    #@staticmethod
    def _is_printable(in_str):
        """Return True if in_str in printable; False otherwise."""
        return in_str.strip(Utils.__printchars) == ""
    _is_printable = staticmethod(_is_printable)

    #@staticmethod
    def _load_args(fhandle, klass):
        """Load the arguments from class klass"""
        size = calcsize(klass.FORMAT)
        foffs = fhandle.tell(),
        fbin = fhandle.read(size)
        if len(fbin) != size:
            raise jerr.UnexpectedEndOfFileError(size, len(fbin))
        return foffs + unpack(klass.FORMAT, fbin)
    _load_args = staticmethod(_load_args)

    #@staticmethod
    def _split_str(in_str, split_size = 50):
        """Split a string into two parts separated by an ellipsis if it is longer than split_size"""
        if len(in_str) < split_size:
            return in_str
        return in_str[:25] + " ... " + in_str[-25:]
    _split_str = staticmethod(_split_str)


#== class Hdr =================================================================

class Hdr:
    """Class representing the journal header records"""
 
    FORMAT = "=4sBBHQ"
    HDR_VER = 1
    OWI_MASK = 0x01
    BIG_ENDIAN = sys.byteorder == "big"
    REC_BOUNDARY = DBLK_SIZE

    def __init__(self, foffs, magic, ver, endn, flags, rid):
        """Constructor"""
#        Sizeable.__init__(self)
        self.foffs = foffs
        self.magic = magic
        self.ver = ver
        self.endn = endn
        self.flags = flags
        self.rid = long(rid)
        
    def __str__(self):
        """Return string representation of this header"""
        if self.empty():
            return "0x%08x: <empty>" % (self.foffs)
        if self.magic[-1] == "x":
            return "0x%08x: [\"%s\"]" % (self.foffs, self.magic)
        if self.magic[-1] in ["a", "c", "d", "e", "f", "x"]:
            return "0x%08x: [\"%s\" v=%d e=%d f=0x%04x rid=0x%x]" % (self.foffs, self.magic, self.ver, self.endn,
                                                                     self.flags, self.rid)
        return "0x%08x: <error, unknown magic \"%s\" (possible overwrite boundary?)>" %  (self.foffs, self.magic)
    
    #@staticmethod
    def discriminate(args):
        """Use the last char in the header magic to determine the header type"""
        return _CLASSES.get(args[1][-1], Hdr)
    discriminate = staticmethod(discriminate)

    def empty(self):
        """Return True if this record is empty (ie has a magic of 0x0000"""
        return self.magic == "\x00"*4
    
    def encode(self):
        """Encode the header into a binary string"""
        return pack(Hdr.FORMAT, self.magic, self.ver, self.endn, self.flags, self.rid)

    def owi(self):
        """Return the OWI (overwrite indicator) for this header"""
        return self.flags & self.OWI_MASK != 0

    def skip(self, fhandle):
        """Read and discard the remainder of this record"""
        fhandle.read(Utils.rem_bytes_in_blk(fhandle, self.REC_BOUNDARY))

    def check(self):
        """Check that this record is valid"""
        if self.empty() or self.magic[:3] != "RHM" or self.magic[3] not in ["a", "c", "d", "e", "f", "x"]:
            return True
        if self.magic[-1] != "x":
            if self.ver != self.HDR_VER:
                raise jerr.InvalidHeaderVersionError(self.HDR_VER, self.ver)
            if bool(self.endn) != self.BIG_ENDIAN:
                raise jerr.EndianMismatchError(self.BIG_ENDIAN)
        return False
        

#== class FileHdr =============================================================

class FileHdr(Hdr):
    """Class for file headers, found at the beginning of journal files"""

    FORMAT = "=2H4x3Q"
    REC_BOUNDARY = SBLK_SIZE
        
    def __str__(self):
        """Return a string representation of the this FileHdr instance"""
        return "%s fid=%d lid=%d fro=0x%08x t=%s" % (Hdr.__str__(self), self.fid, self.lid, self.fro,
                                                     self.timestamp_str())
    
    def encode(self):
        """Encode this class into a binary string"""
        return Hdr.encode(self) + pack(FileHdr.FORMAT, self.fid, self.lid, self.fro, self.time_sec, self.time_ns)

    def init(self, fhandle, foffs, fid, lid, fro, time_sec, time_ns):
        """Initialize this instance to known values"""
        self.fid = fid
        self.lid = lid
        self.fro = fro
        self.time_sec = time_sec
        self.time_ns = time_ns

    def timestamp(self):
        """Get the timestamp of this record as a tuple (secs, nsecs)"""
        return (self.time_sec, self.time_ns)

    def timestamp_str(self):
        """Get the timestamp of this record in string format"""
        time = gmtime(self.time_sec)
        fstr = "%%a %%b %%d %%H:%%M:%%S.%09d %%Y" % (self.time_ns)
        return strftime(fstr, time)


#== class DeqRec ==============================================================

class DeqRec(Hdr):
    """Class for a dequeue record"""

    FORMAT = "=QQ"

    def __str__(self):
        """Return a string representation of the this DeqRec instance"""
        return "%s %sdrid=0x%x" % (Hdr.__str__(self), Utils.format_xid(self.xid, self.xidsize), self.deq_rid)

    def init(self, fhandle, foffs, deq_rid, xidsize):
        """Initialize this instance to known values"""
        self.deq_rid = deq_rid
        self.xidsize = xidsize
        self.xid = None
        self.deq_tail = None
        self.xid_complete = False
        self.tail_complete = False
        self.tail_bin = None
        self.tail_offs = 0
        self.load(fhandle)
    
    def encode(self):
        """Encode this class into a binary string"""
        buf = Hdr.encode(self) + pack(DeqRec.FORMAT, self.deq_rid, self.xidsize)
        if self.xidsize > 0:
            fmt = "%ds" % (self.xidsize)
            buf += pack(fmt, self.xid)
            buf += self.deq_tail.encode()
        return buf

    def load(self, fhandle):
        """Load the remainder of this record (after the header has been loaded"""
        if self.xidsize == 0:
            self.xid_complete = True
            self.tail_complete = True
        else:
            if not self.xid_complete:
                (self.xid, self.xid_complete) = Utils.load_file_data(fhandle, self.xidsize, self.xid)
            if self.xid_complete and not self.tail_complete:
                ret = Utils.load_file_data(fhandle, calcsize(RecTail.FORMAT), self.tail_bin)
                self.tail_bin = ret[0]
                if ret[1]:
                    self.deq_tail = RecTail(self.tail_offs, *unpack(RecTail.FORMAT, self.tail_bin))
                    magic_err = self.deq_tail.magic_inv != Utils.inv_str(self.magic)
                    rid_err = self.deq_tail.rid != self.rid
                    if magic_err or rid_err:
                        raise jerr.InvalidRecordTailError(magic_err, rid_err, self)
                    self.skip(fhandle)
                self.tail_complete = ret[1]
        return self.complete()

    def complete(self):
        """Returns True if the entire record is loaded, False otherwise"""
        return self.xid_complete and self.tail_complete


#== class TxnRec ==============================================================

class TxnRec(Hdr):
    """Class for a transaction commit/abort record"""

    FORMAT = "=Q"

    def __str__(self):
        """Return a string representation of the this TxnRec instance"""
        return "%s %s" % (Hdr.__str__(self), Utils.format_xid(self.xid, self.xidsize))

    def init(self, fhandle, foffs, xidsize):
        """Initialize this instance to known values"""
        self.xidsize = xidsize
        self.xid = None
        self.tx_tail = None
        self.xid_complete = False
        self.tail_complete = False
        self.tail_bin = None
        self.tail_offs = 0
        self.load(fhandle)
    
    def encode(self):
        """Encode this class into a binary string"""
        return Hdr.encode(self) + pack(TxnRec.FORMAT, self.xidsize) + pack("%ds" % self.xidsize, self.xid) + \
               self.tx_tail.encode()

    def load(self, fhandle):
        """Load the remainder of this record (after the header has been loaded"""
        if not self.xid_complete:
            ret = Utils.load_file_data(fhandle, self.xidsize, self.xid)
            self.xid = ret[0]
            self.xid_complete = ret[1]
        if self.xid_complete and not self.tail_complete:
            ret = Utils.load_file_data(fhandle, calcsize(RecTail.FORMAT), self.tail_bin)
            self.tail_bin = ret[0]
            if ret[1]:
                self.tx_tail = RecTail(self.tail_offs, *unpack(RecTail.FORMAT, self.tail_bin))
                magic_err = self.tx_tail.magic_inv != Utils.inv_str(self.magic)
                rid_err = self.tx_tail.rid != self.rid
                if magic_err or rid_err:
                    raise jerr.InvalidRecordTailError(magic_err, rid_err, self)
                self.skip(fhandle)
            self.tail_complete = ret[1]
        return self.complete()

    def complete(self):
        """Returns True if the entire record is loaded, False otherwise"""
        return self.xid_complete and self.tail_complete


#== class EnqRec ==============================================================

class EnqRec(Hdr):
    """Class for a enqueue record"""

    FORMAT = "=QQ"
    TRANSIENT_MASK = 0x10
    EXTERN_MASK = 0x20

    def __str__(self):
        """Return a string representation of the this EnqRec instance"""
        return "%s %s%s %s %s" % (Hdr.__str__(self), Utils.format_xid(self.xid, self.xidsize),
                                  Utils.format_data(self.dsize, self.data), self.enq_tail, self.print_flags())
    
    def encode(self):
        """Encode this class into a binary string"""
        buf = Hdr.encode(self) + pack(EnqRec.FORMAT, self.xidsize, self.dsize)
        if self.xidsize > 0:
            buf += pack("%ds" % self.xidsize, self.xid)
        if self.dsize > 0:
            buf += pack("%ds" % self.dsize, self.data)
        if self.xidsize > 0 or self.dsize > 0:
            buf += self.enq_tail.encode()
        return buf

    def init(self, fhandle, foffs, xidsize, dsize):
        """Initialize this instance to known values"""
        self.xidsize = xidsize
        self.dsize = dsize
        self.transient = self.flags & self.TRANSIENT_MASK > 0
        self.extern = self.flags & self.EXTERN_MASK > 0
        self.xid = None
        self.data = None
        self.enq_tail = None
        self.xid_complete = False
        self.data_complete = False
        self.tail_complete = False
        self.tail_bin = None
        self.tail_offs = 0
        self.load(fhandle)

    def load(self, fhandle):
        """Load the remainder of this record (after the header has been loaded"""
        if not self.xid_complete:
            ret = Utils.load_file_data(fhandle, self.xidsize, self.xid)
            self.xid = ret[0]
            self.xid_complete = ret[1]
        if self.xid_complete and not self.data_complete:
            if self.extern:
                self.data_complete = True
            else:
                ret = Utils.load_file_data(fhandle, self.dsize, self.data)
                self.data = ret[0]
                self.data_complete = ret[1]
        if self.data_complete and not self.tail_complete:
            ret = Utils.load_file_data(fhandle, calcsize(RecTail.FORMAT), self.tail_bin)
            self.tail_bin = ret[0]
            if ret[1]:
                self.enq_tail = RecTail(self.tail_offs, *unpack(RecTail.FORMAT, self.tail_bin))
                magic_err = self.enq_tail.magic_inv != Utils.inv_str(self.magic)
                rid_err = self.enq_tail.rid != self.rid
                if magic_err or rid_err:
                    raise jerr.InvalidRecordTailError(magic_err, rid_err, self)
                self.skip(fhandle)
            self.tail_complete = ret[1]
        return self.complete()

    def complete(self):
        """Returns True if the entire record is loaded, False otherwise"""
        return self.xid_complete and self.data_complete and self.tail_complete

    def print_flags(self):
        """Utility function to decode the flags field in the header and print a string representation"""
        fstr = ""
        if self.transient:
            fstr = "*TRANSIENT"
        if self.extern:
            if len(fstr) > 0:
                fstr += ",EXTERNAL"
            else:
                fstr = "*EXTERNAL"
        if len(fstr) > 0:
            fstr += "*"
        return fstr


#== class RecTail =============================================================

class RecTail:
    """Class for a record tail - for all records where either an XID or data separate the header from the end of the
    record"""

    FORMAT = "=4sQ"

    def __init__(self, foffs, magic_inv, rid):
        """Initialize this instance to known values"""
        self.foffs = foffs
        self.magic_inv = magic_inv
        self.rid = long(rid)

    def __str__(self):
        """Return a string representation of the this RecTail instance"""
        magic = Utils.inv_str(self.magic_inv)
        return "[\"%s\" rid=0x%x]" % (magic, self.rid)
    
    def encode(self):
        """Encode this class into a binary string"""
        return pack(RecTail.FORMAT, self.magic_inv, self.rid)                


#== class JrnlInfo ============================================================

class JrnlInfo(object):
    """
    This object reads and writes journal information files (<basename>.jinf). Methods are provided
    to read a file, query its properties and reset just those properties necessary for normalizing
    and resizing a journal.
    
    Normalizing: resetting the directory and/or base filename to different values. This is necessary
    if a set of journal files is copied from one location to another before being restored, as the
    value of the path in the file no longer matches the actual path.
    
    Resizing: If the journal geometry parameters (size and number of journal files) changes, then the
    .jinf file must reflect these changes, as this file is the source of information for journal
    recovery.
    
    NOTE: Data size vs File size: There are methods which return the data size and file size of the
    journal files.
    
    +-------------+--------------------/ /----------+
    | File header |           File data             |
    +-------------+--------------------/ /----------+
    |             |                                 |
    |             |<---------- Data size ---------->|
    |<------------------ File Size ---------------->|
    
    Data size: The size of the data content of the journal, ie that part which stores the data records.
    
    File size: The actual disk size of the journal including data and the file header which precedes the
    data.
    
    The file header is fixed to 1 sblk, so  file size = jrnl size + sblk size.
    """
    
    def __init__(self, jdir, bfn = "JournalData"):
        """Constructor"""
        self.__jdir = jdir
        self.__bfn = bfn
        self.__jinf_dict = {}
        self._read_jinf()
    
    def __str__(self):
        """Create a string containing all of the journal info contained in the jinf file"""
        ostr = "Journal info file %s:\n" % os.path.join(self.__jdir, "%s.jinf" % self.__bfn)
        for key, val in self.__jinf_dict.iteritems():
            ostr += "  %s = %s\n" % (key, val)
        return ostr
    
    def normalize(self, jdir = None, bfn = None):
        """Normalize the directory (ie reset the directory path to match the actual current location) for this
        jinf file"""
        if jdir == None:
            self.__jinf_dict["directory"] = self.__jdir
        else:
            self.__jdir = jdir
            self.__jinf_dict["directory"] = jdir
        if bfn != None:
            self.__bfn = bfn
            self.__jinf_dict["base_filename"] = bfn
    
    def resize(self, num_jrnl_files = None, jrnl_file_size = None):
        """Reset the journal size information to allow for resizing the journal"""
        if num_jrnl_files != None:
            self.__jinf_dict["number_jrnl_files"] = num_jrnl_files
        if jrnl_file_size != None:
            self.__jinf_dict["jrnl_file_size_sblks"] = jrnl_file_size * self.get_jrnl_dblk_size_bytes()

    def write(self, jdir = None, bfn = None):
        """Write the .jinf file"""
        self.normalize(jdir, bfn)
        if not os.path.exists(self.get_jrnl_dir()):
            os.makedirs(self.get_jrnl_dir())
        fhandle = open(os.path.join(self.get_jrnl_dir(), "%s.jinf" % self.get_jrnl_base_name()), "w")
        fhandle.write("<?xml version=\"1.0\" ?>\n")
        fhandle.write("<jrnl>\n")
        fhandle.write("  <journal_version value=\"%d\" />\n" % self.get_jrnl_version())
        fhandle.write("  <journal_id>\n")
        fhandle.write("    <id_string value=\"%s\" />\n" % self.get_jrnl_id())
        fhandle.write("    <directory value=\"%s\" />\n" % self.get_jrnl_dir())
        fhandle.write("    <base_filename value=\"%s\" />\n" % self.get_jrnl_base_name())
        fhandle.write("  </journal_id>\n")
        fhandle.write("  <creation_time>\n")
        fhandle.write("    <seconds value=\"%d\" />\n" % self.get_creation_time()[0])
        fhandle.write("    <nanoseconds value=\"%d\" />\n" % self.get_creation_time()[1])
        fhandle.write("    <string value=\"%s\" />\n" % self.get_creation_time_str())
        fhandle.write("  </creation_time>\n")
        fhandle.write("  <journal_file_geometry>\n")
        fhandle.write("    <number_jrnl_files value=\"%d\" />\n" % self.get_num_jrnl_files())
        fhandle.write("    <auto_expand value=\"%s\" />\n" % str.lower(str(self.get_auto_expand())))
        fhandle.write("    <jrnl_file_size_sblks value=\"%d\" />\n" % self.get_jrnl_data_size_sblks())
        fhandle.write("    <JRNL_SBLK_SIZE value=\"%d\" />\n" % self.get_jrnl_sblk_size_dblks())
        fhandle.write("    <JRNL_DBLK_SIZE value=\"%d\" />\n" % self.get_jrnl_dblk_size_bytes())
        fhandle.write("  </journal_file_geometry>\n")
        fhandle.write("  <cache_geometry>\n")
        fhandle.write("    <wcache_pgsize_sblks value=\"%d\" />\n" % self.get_wr_buf_pg_size_sblks())
        fhandle.write("    <wcache_num_pages value=\"%d\" />\n" % self.get_num_wr_buf_pgs())
        fhandle.write("    <JRNL_RMGR_PAGE_SIZE value=\"%d\" />\n" % self.get_rd_buf_pg_size_sblks())
        fhandle.write("    <JRNL_RMGR_PAGES value=\"%d\" />\n" % self.get_num_rd_buf_pgs())
        fhandle.write("  </cache_geometry>\n")
        fhandle.write("</jrnl>\n")
        fhandle.close()
    
    # Journal ID
    
    def get_jrnl_version(self):
        """Get the journal version"""
        return self.__jinf_dict["journal_version"]
    
    def get_jrnl_id(self):
        """Get the journal id"""
        return self.__jinf_dict["id_string"]
    
    def get_current_dir(self):
        """Get the current directory of the store (as opposed to that value saved in the .jinf file)"""
        return self.__jdir
    
    def get_jrnl_dir(self):
        """Get the journal directory stored in the .jinf file"""
        return self.__jinf_dict["directory"]
    
    def get_jrnl_base_name(self):
        """Get the base filename - that string used to name the journal files <basefilename>-nnnn.jdat and
        <basefilename>.jinf"""
        return self.__jinf_dict["base_filename"]
    
    # Journal creation time
    
    def get_creation_time(self):
        """Get journal creation time as a tuple (secs, nsecs)"""
        return (self.__jinf_dict["seconds"], self.__jinf_dict["nanoseconds"])
    
    def get_creation_time_str(self):
        """Get journal creation time as a string"""
        return self.__jinf_dict["string"]
    
    # --- Files and geometry ---
    
    def get_num_jrnl_files(self):
        """Get number of data files in the journal"""
        return self.__jinf_dict["number_jrnl_files"]
    
    def get_auto_expand(self):
        """Return True if auto-expand is enabled; False otherwise"""
        return self.__jinf_dict["auto_expand"]
    
    def get_jrnl_sblk_size_dblks(self):
        """Get the journal softblock size in dblks"""
        return self.__jinf_dict["JRNL_SBLK_SIZE"]
     
    def get_jrnl_sblk_size_bytes(self):
        """Get the journal softblock size in bytes"""
        return self.get_jrnl_sblk_size_dblks() * self.get_jrnl_dblk_size_bytes()
   
    def get_jrnl_dblk_size_bytes(self):
        """Get the journal datablock size in bytes"""
        return self.__jinf_dict["JRNL_DBLK_SIZE"]
    
    def get_jrnl_data_size_sblks(self):
        """Get the data capacity (excluding the file headers) for one journal file in softblocks"""
        return self.__jinf_dict["jrnl_file_size_sblks"]
    
    def get_jrnl_data_size_dblks(self):
        """Get the data capacity (excluding the file headers) for one journal file in datablocks"""
        return self.get_jrnl_data_size_sblks() * self.get_jrnl_sblk_size_dblks()
    
    def get_jrnl_data_size_bytes(self):
        """Get the data capacity (excluding the file headers) for one journal file in bytes"""
        return self.get_jrnl_data_size_dblks() * self.get_jrnl_dblk_size_bytes()
    
    def get_jrnl_file_size_sblks(self):
        """Get the size of one journal file on disk (including the file headers) in softblocks"""
        return self.get_jrnl_data_size_sblks() + 1
    
    def get_jrnl_file_size_dblks(self):
        """Get the size of one journal file on disk (including the file headers) in datablocks"""
        return self.get_jrnl_file_size_sblks() * self.get_jrnl_sblk_size_dblks()
    
    def get_jrnl_file_size_bytes(self):
        """Get the size of one journal file on disk (including the file headers) in bytes"""
        return self.get_jrnl_file_size_dblks() * self.get_jrnl_dblk_size_bytes()
    
    def get_tot_jrnl_data_size_sblks(self):
        """Get the size of the entire jouranl's data capacity (excluding the file headers) for all files together in
        softblocks"""
        return self.get_num_jrnl_files() * self.get_jrnl_data_size_bytes()
    
    def get_tot_jrnl_data_size_dblks(self):
        """Get the size of the entire jouranl's data capacity (excluding the file headers) for all files together in
        datablocks"""
        return self.get_num_jrnl_files() * self.get_jrnl_data_size_dblks()
    
    def get_tot_jrnl_data_size_bytes(self):
        """Get the size of the entire jouranl's data capacity (excluding the file headers) for all files together in
        bytes"""
        return self.get_num_jrnl_files() * self.get_jrnl_data_size_bytes()
    
    # Read and write buffers
    
    def get_wr_buf_pg_size_sblks(self):
        """Get the size of the write buffer pages in softblocks"""
        return self.__jinf_dict["wcache_pgsize_sblks"]
    
    def get_wr_buf_pg_size_dblks(self):
        """Get the size of the write buffer pages in datablocks"""
        return self.get_wr_buf_pg_size_sblks() * self.get_jrnl_sblk_size_dblks()
    
    def get_wr_buf_pg_size_bytes(self):
        """Get the size of the write buffer pages in bytes"""
        return self.get_wr_buf_pg_size_dblks() * self.get_jrnl_dblk_size_bytes()
    
    def get_num_wr_buf_pgs(self):
        """Get the number of write buffer pages"""
        return self.__jinf_dict["wcache_num_pages"]
    
    def get_rd_buf_pg_size_sblks(self):
        """Get the size of the read buffer pages in softblocks"""
        return self.__jinf_dict["JRNL_RMGR_PAGE_SIZE"]
    
    def get_rd_buf_pg_size_dblks(self):
        """Get the size of the read buffer pages in datablocks"""
        return self.get_rd_buf_pg_size_sblks * self.get_jrnl_sblk_size_dblks()
    
    def get_rd_buf_pg_size_bytes(self):
        """Get the size of the read buffer pages in bytes"""
        return self.get_rd_buf_pg_size_dblks * self.get_jrnl_dblk_size_bytes()
    
    def get_num_rd_buf_pgs(self):
        """Get the number of read buffer pages"""
        return self.__jinf_dict["JRNL_RMGR_PAGES"]
    
    def _read_jinf(self):
        """Read and initialize this instance from an existing jinf file located at the directory named in the
        constructor - called by the constructor"""
        fhandle = open(os.path.join(self.__jdir, "%s.jinf" % self.__bfn), "r")
        parser = xml.parsers.expat.ParserCreate()
        parser.StartElementHandler = self._handle_xml_start_elt
        parser.CharacterDataHandler = self._handle_xml_char_data
        parser.EndElementHandler = self._handle_xml_end_elt
        parser.ParseFile(fhandle)
        fhandle.close()

    def _handle_xml_start_elt(self, name, attrs):
        """Callback for handling XML start elements. Used by the XML parser."""
        # bool values
        if name == "auto_expand":
            self.__jinf_dict[name] = attrs["value"] == "true"
        # long values
        elif name == "seconds" or \
             name == "nanoseconds":
            self.__jinf_dict[name] = long(attrs["value"])
        # int values
        elif name == "journal_version" or \
             name == "number_jrnl_files" or \
             name == "jrnl_file_size_sblks" or \
             name == "JRNL_SBLK_SIZE" or \
             name == "JRNL_DBLK_SIZE" or \
             name == "wcache_pgsize_sblks" or \
             name == "wcache_num_pages" or \
             name == "JRNL_RMGR_PAGE_SIZE" or \
             name == "JRNL_RMGR_PAGES":
            self.__jinf_dict[name] = int(attrs["value"])
        # strings
        elif "value" in attrs:
            self.__jinf_dict[name] = attrs["value"]

    def _handle_xml_char_data(self, data):
        """Callback for handling character data (ie within <elt>...</elt>). The jinf file does not use this in its
        data. Used by the XML parser.""" 
        pass

    def _handle_xml_end_elt(self, name):
        """Callback for handling XML end elements. Used by XML parser."""
        pass
        

#==============================================================================

_CLASSES = {
    "a": TxnRec,
    "c": TxnRec,
    "d": DeqRec,
    "e": EnqRec,
    "f": FileHdr
}

if __name__ == "__main__":
    print "This is a library, and cannot be executed."
