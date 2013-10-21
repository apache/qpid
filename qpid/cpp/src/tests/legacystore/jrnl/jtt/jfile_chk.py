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

import sys
import getopt
import string
import xml.parsers.expat
from struct import unpack, calcsize
from time import gmtime, strftime

dblk_size = 128
sblk_size = 4 * dblk_size
jfsize = None
hdr_ver = 1

TEST_NUM_COL = 0
NUM_MSGS_COL = 5
MIN_MSG_SIZE_COL = 7
MAX_MSG_SIZE_COL = 8
MIN_XID_SIZE_COL = 9
MAX_XID_SIZE_COL = 10
AUTO_DEQ_COL = 11
TRANSIENT_COL = 12
EXTERN_COL = 13
COMMENT_COL = 20

owi_mask       = 0x01
transient_mask = 0x10
extern_mask    = 0x20

printchars = '0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ!"#$%&\'()*+,-./:;<=>?@[\\]^_`{|}~ '



#== global functions ===========================================================

def load(f, klass):
    args = load_args(f, klass)
    subclass = klass.discriminate(args)
    result = subclass(*args)
    if subclass != klass:
        result.init(f, *load_args(f, subclass))
    result.skip(f)
    return result;

def load_args(f, klass):
    size = calcsize(klass.format)
    foffs = f.tell(),
    bin = f.read(size)
    if len(bin) != size:
        raise Exception("end of file")
    return foffs + unpack(klass.format, bin)

def size_blks(size, blk_size):
    return (size + blk_size - 1)/blk_size

def rem_in_blk(f, blk_size):
    foffs = f.tell()
    return (size_blks(f.tell(), blk_size) * blk_size) - foffs;

def file_full(f):
    return f.tell() >= jfsize

def isprintable(s):
    return s.strip(printchars) == ''

def print_xid(xidsize, xid):
    if xid == None:
        if xidsize > 0:
            raise Exception('Inconsistent XID size: xidsize=%d, xid=None' % xidsize)
        return ''
    if isprintable(xid):
        xidstr = split_str(xid)
    else:
        xidstr = hex_split_str(xid)
    if xidsize != len(xid):
        raise Exception('Inconsistent XID size: xidsize=%d, xid(%d)=\"%s\"' % (xidsize, len(xid), xidstr))
    return 'xid(%d)=\"%s\" ' % (xidsize, xidstr)

def print_data(dsize, data):
    if data == None:
        return ''
    if isprintable(data):
        datastr = split_str(data)
    else:
        datastr = hex_split_str(data)
    if dsize != len(data):
        raise Exception('Inconsistent data size: dsize=%d, data(%d)=\"%s\"' % (dsize, len(data), datastr))
    return 'data(%d)=\"%s\" ' % (dsize, datastr)

def hex_split_str(s, split_size = 50):
    if len(s) <= split_size:
        return hex_str(s, 0, len(s))
    if len(s) > split_size + 25:
        return hex_str(s, 0, 10) + ' ... ' + hex_str(s, 55, 65) + ' ... ' + hex_str(s, len(s)-10, len(s))
    return hex_str(s, 0, 10) + ' ... ' + hex_str(s, len(s)-10, len(s))

def hex_str(s, b, e):
    o = ''
    for i in range(b, e):
        if isprintable(s[i]):
            o += s[i]
        else:
            o += '\\%02x' % ord(s[i])
    return o

def split_str(s, split_size = 50):
    if len(s) < split_size:
        return s
    return s[:25] + ' ... ' + s[-25:]

def inv_str(s):
    si = ''
    for i in range(0,len(s)):
        si += chr(~ord(s[i]) & 0xff)
    return si

def load_file_data(f, size, data):
    if size == 0:
        return (data, True)
    if data == None:
        loaded = 0
    else:
        loaded = len(data)
    foverflow = f.tell() + size - loaded > jfsize
    if foverflow:
        rsize = jfsize - f.tell()
    else:
        rsize = size - loaded
    bin = f.read(rsize)
    if data == None:
        data = unpack('%ds' % (rsize), bin)[0]
    else:
        data = data + unpack('%ds' % (rsize), bin)[0]
    return (data, not foverflow)

def exit(code, qflag):
    if code != 0 or not qflag:
        print out.getvalue()
    out.close()
    sys.exit(code)

#== class Sizeable =============================================================

class Sizeable:

    def size(self):
        classes = [self.__class__]

        size = 0
        while classes:
            cls = classes.pop()
            if hasattr(cls, "format"):
                size += calcsize(cls.format)
            classes.extend(cls.__bases__)

        return size


#== class Hdr ==================================================================

class Hdr(Sizeable):
 
    format = '=4sBBHQ'
    
    def discriminate(args):
        return CLASSES.get(args[1][-1], Hdr)
    discriminate = staticmethod(discriminate)

    def __init__(self, foffs, magic, ver, end, flags, rid):
        self.foffs = foffs
        self.magic = magic
        self.ver = ver
        self.end = end
        self.flags = flags
        self.rid = rid
        if self.magic[-1] not in ['0x00', 'a', 'c', 'd', 'e', 'f', 'x']:
            error = 3
        
    def __str__(self):
        if self.empty():
            return '0x%08x: <empty>' % (self.foffs)
        if self.magic[-1] == 'x':
            return '0x%08x: [\"%s\"]' % (self.foffs, self.magic)
        if self.magic[-1] in ['a', 'c', 'd', 'e', 'f', 'x']:
            return '0x%08x: [\"%s\" v=%d e=%d f=0x%04x rid=0x%x]' % (self.foffs, self.magic, self.ver, self.end, self.flags, self.rid)
        return '0x%08x: <error, unknown magic \"%s\" (possible overwrite boundary?)>' %  (self.foffs, self.magic)

    def empty(self):
        return self.magic == '\x00'*4

    def owi(self):
        return self.flags & owi_mask != 0

    def skip(self, f):
        f.read(rem_in_blk(f, dblk_size))

    def check(self):
        if self.empty() or self.magic[:3] != 'RHM' or self.magic[3] not in ['a', 'c', 'd', 'e', 'f', 'x']:
            return True
        if self.ver != hdr_ver and self.magic[-1] != 'x':
            raise Exception('%s: Invalid header version: found %d, expected %d.' % (self, self.ver, hdr_ver))
        return False
        

#== class FileHdr ==============================================================

class FileHdr(Hdr):

    format = '=2H4x3Q'

    def init(self, f, foffs, fid, lid, fro, time_sec, time_ns):
        self.fid = fid
        self.lid = lid
        self.fro = fro
        self.time_sec = time_sec
        self.time_ns = time_ns
        
    def __str__(self):
        return '%s fid=%d lid=%d fro=0x%08x t=%s' % (Hdr.__str__(self), self.fid, self.lid, self.fro, self.timestamp_str())

    def skip(self, f):
        f.read(rem_in_blk(f, sblk_size))

    def timestamp(self):
        return (self.time_sec, self.time_ns)

    def timestamp_str(self):
        ts = gmtime(self.time_sec)
        fstr = '%%a %%b %%d %%H:%%M:%%S.%09d %%Y' % (self.time_ns)
        return strftime(fstr, ts)


#== class DeqHdr ===============================================================

class DeqHdr(Hdr):

    format = '=QQ'

    def init(self, f, foffs, deq_rid, xidsize):
        self.deq_rid = deq_rid
        self.xidsize = xidsize
        self.xid = None
        self.deq_tail = None
        self.xid_complete = False
        self.tail_complete = False
        self.tail_bin = None
        self.tail_offs = 0
        self.load(f)

    def load(self, f):
        if self.xidsize == 0:
            self.xid_complete = True
            self.tail_complete = True
        else:
            if not self.xid_complete:
                ret = load_file_data(f, self.xidsize, self.xid)
                self.xid = ret[0]
                self.xid_complete = ret[1]
            if self.xid_complete and not self.tail_complete:
                ret = load_file_data(f, calcsize(RecTail.format), self.tail_bin)
                self.tail_bin = ret[0]
                if ret[1]:
                    self.enq_tail = RecTail(self.tail_offs, *unpack(RecTail.format, self.tail_bin))
                    if self.enq_tail.magic_inv != inv_str(self.magic) or self.enq_tail.rid != self.rid:
                        print " > %s" % self
                        raise Exception('Invalid dequeue record tail (magic=%s; rid=%d) at 0x%08x' % (self.enq_tail, self.enq_tail.rid, self.enq_tail.foffs))
                    self.enq_tail.skip(f)
                self.tail_complete = ret[1]
        return self.complete()

    def complete(self):
        return self.xid_complete and self.tail_complete

    def __str__(self):
        return '%s %sdrid=0x%x' % (Hdr.__str__(self), print_xid(self.xidsize, self.xid), self.deq_rid)


#== class TxnHdr ===============================================================

class TxnHdr(Hdr):

    format = '=Q'

    def init(self, f, foffs, xidsize):
        self.xidsize = xidsize
        self.xid = None
        self.tx_tail = None
        self.xid_complete = False
        self.tail_complete = False
        self.tail_bin = None
        self.tail_offs = 0
        self.load(f)

    def load(self, f):
        if not self.xid_complete:
            ret = load_file_data(f, self.xidsize, self.xid)
            self.xid = ret[0]
            self.xid_complete = ret[1]
        if self.xid_complete and not self.tail_complete:
            ret = load_file_data(f, calcsize(RecTail.format), self.tail_bin)
            self.tail_bin = ret[0]
            if ret[1]:
                self.enq_tail = RecTail(self.tail_offs, *unpack(RecTail.format, self.tail_bin))
                if self.enq_tail.magic_inv != inv_str(self.magic) or self.enq_tail.rid != self.rid:
                    print " > %s" % self
                    raise Exception('Invalid transaction record tail (magic=%s; rid=%d) at 0x%08x' % (self.enq_tail, self.enq_tail.rid, self.enq_tail.foffs))
                self.enq_tail.skip(f)
            self.tail_complete = ret[1]
        return self.complete()

    def complete(self):
        return self.xid_complete and self.tail_complete

    def __str__(self):
        return '%s %s' % (Hdr.__str__(self), print_xid(self.xidsize, self.xid))


#== class RecTail ==============================================================

class RecTail(Sizeable):

    format = '=4sQ'

    def __init__(self, foffs, magic_inv, rid):
        self.foffs = foffs
        self.magic_inv = magic_inv
        self.rid = rid

    def __str__(self):
        magic = inv_str(self.magic_inv)
        return '[\"%s\" rid=0x%x]' % (magic, self.rid)

    def skip(self, f):
        f.read(rem_in_blk(f, dblk_size))


#== class EnqRec ===============================================================

class EnqRec(Hdr):

    format = '=QQ'

    def init(self, f, foffs, xidsize, dsize):
        self.xidsize = xidsize
        self.dsize = dsize
        self.transient = self.flags & transient_mask > 0
        self.extern = self.flags & extern_mask > 0
        self.xid = None
        self.data = None
        self.enq_tail = None
        self.xid_complete = False
        self.data_complete = False
        self.tail_complete = False
        self.tail_bin = None
        self.tail_offs = 0
        self.load(f)

    def load(self, f):
        if not self.xid_complete:
            ret = load_file_data(f, self.xidsize, self.xid)
            self.xid = ret[0]
            self.xid_complete = ret[1]
        if self.xid_complete and not self.data_complete:
            if self.extern:
                self.data_complete = True
            else:
                ret = load_file_data(f, self.dsize, self.data)
                self.data = ret[0]
                self.data_complete = ret[1]
        if self.data_complete and not self.tail_complete:
            ret = load_file_data(f, calcsize(RecTail.format), self.tail_bin)
            self.tail_bin = ret[0]
            if ret[1]:
                self.enq_tail = RecTail(self.tail_offs, *unpack(RecTail.format, self.tail_bin))
                if self.enq_tail.magic_inv != inv_str(self.magic) or self.enq_tail.rid != self.rid:
                    print " > %s" % self
                    raise Exception('Invalid enqueue record tail (magic=%s; rid=%d) at 0x%08x' % (self.enq_tail, self.enq_tail.rid, self.enq_tail.foffs))
                self.enq_tail.skip(f)
            self.tail_complete = ret[1]
        return self.complete()

    def complete(self):
        return self.xid_complete and self.data_complete and self.tail_complete

    def print_flags(self):
        s = ''
        if self.transient:
            s = '*TRANSIENT'
        if self.extern:
            if len(s) > 0:
                s += ',EXTERNAL'
            else:
                s = '*EXTERNAL'
        if len(s) > 0:
            s += '*'
        return s

    def __str__(self):
        return '%s %s%s %s %s' % (Hdr.__str__(self), print_xid(self.xidsize, self.xid), print_data(self.dsize, self.data), self.enq_tail, self.print_flags())


#== class Main =================================================================

class Main:
    def __init__(self, argv):
        self.bfn = None
        self.csvfn = None
        self.jdir = None
        self.aflag = False
        self.hflag = False
        self.qflag = False
        self.tnum = None
        self.num_jfiles = None
        self.num_msgs = None
        self.msg_len = None
        self.auto_deq = None
        self.xid_len = None
        self.transient = None
        self.extern = None

        self.file_start = 0
        self.file_num = 0
        self.fro = 0x200
        self.emap = {}
        self.tmap = {}
        self.rec_cnt = 0
        self.msg_cnt = 0
        self.txn_msg_cnt = 0
        self.fhdr = None
        self.f = None
        self.first_rec = False
        self.last_file = False
        self.last_rid = -1
        self.fhdr_owi_at_msg_start = None

        self.proc_args(argv)
        self.proc_csv()
        self.read_jinf()
    
    def run(self):
        try:
            start_info = self.analyze_files()
            stop = self.advance_file(*start_info)
        except Exception:
            print 'WARNING: All journal files are empty.'
            if self.num_msgs > 0:
                raise Exception('All journal files are empty, but %d msgs expectd.' % self.num_msgs)
            else:
                stop = True
        while not stop:
            warn = ''
            if file_full(self.f):
                stop = self.advance_file()
                if stop:
                    break
            hdr = load(self.f, Hdr)
            if hdr.empty():
                stop = True;
                break
            if hdr.check():
                stop = True;
            else:
                self.rec_cnt += 1
                self.fhdr_owi_at_msg_start = self.fhdr.owi()
                if self.first_rec:
                    if self.fhdr.fro != hdr.foffs:
                        raise Exception('File header first record offset mismatch: fro=0x%08x; rec_offs=0x%08x' % (self.fhdr.fro, hdr.foffs))
                    else:
                        if not self.qflag: print ' * fro ok: 0x%08x' % self.fhdr.fro
                    self.first_rec = False
                if isinstance(hdr, EnqRec) and not stop:
                    while not hdr.complete():
                        stop = self.advance_file()
                        if stop:
                            break
                        hdr.load(self.f)
                    if self.extern != None:
                        if hdr.extern:
                            if hdr.data != None:
                                raise Exception('Message data found on external record')
                        else:
                            if self.msg_len > 0 and len(hdr.data) != self.msg_len:
                                raise Exception('Message length (%d) incorrect; expected %d' % (len(hdr.data), self.msg_len))
                    else:
                        if self.msg_len > 0 and len(hdr.data) != self.msg_len:
                            raise Exception('Message length (%d) incorrect; expected %d' % (len(hdr.data), self.msg_len))
                    if self.xid_len > 0 and len(hdr.xid) != self.xid_len:
                        print '  ERROR: XID length (%d) incorrect; expected %d' % (len(hdr.xid), self.xid_len)
                        sys.exit(1)
                        #raise Exception('XID length (%d) incorrect; expected %d' % (len(hdr.xid), self.xid_len))
                    if self.transient != None:
                        if self.transient:
                            if not hdr.transient:
                                raise Exception('Expected transient record, found persistent')
                        else:
                            if hdr.transient:
                                raise Exception('Expected persistent record, found transient')
                    stop = not self.check_owi(hdr)
                    if  stop:
                        warn = ' (WARNING: OWI mismatch - could be overwrite boundary.)'
                    else:
                        self.msg_cnt += 1
                        if self.aflag or self.auto_deq:
                            if hdr.xid == None:
                                self.emap[hdr.rid] = (self.fhdr.fid, hdr, False)
                            else:
                                self.txn_msg_cnt += 1
                                if hdr.xid in self.tmap:
                                    self.tmap[hdr.xid].append((self.fhdr.fid, hdr)) #Append tuple to existing list
                                else:
                                    self.tmap[hdr.xid] = [(self.fhdr.fid, hdr)] # Create new list
                elif isinstance(hdr, DeqHdr) and not stop:
                    while not hdr.complete():
                        stop = self.advance_file()
                        if stop:
                            break
                        hdr.load(self.f)
                    stop = not self.check_owi(hdr)
                    if stop:
                        warn = ' (WARNING: OWI mismatch - could be overwrite boundary.)'
                    else:
                        if self.auto_deq != None:
                            if not self.auto_deq:
                                warn = ' WARNING: Dequeue record rid=%d found in non-dequeue test - ignoring.' % hdr.rid
                        if self.aflag or self.auto_deq:
                            if hdr.xid == None:
                                if hdr.deq_rid in self.emap:
                                    if self.emap[hdr.deq_rid][2]:
                                        warn = ' (WARNING: dequeue rid 0x%x dequeues locked enqueue record 0x%x)' % (hdr.rid, hdr.deq_rid)
                                    del self.emap[hdr.deq_rid]
                                else:
                                    warn = ' (WARNING: rid being dequeued 0x%x not found in enqueued records)' % hdr.deq_rid
                            else:
                                if hdr.deq_rid in self.emap:
                                    t = self.emap[hdr.deq_rid]
                                    self.emap[hdr.deq_rid] = (t[0], t[1], True) # Lock enq record
                                if hdr.xid in self.tmap:
                                    self.tmap[hdr.xid].append((self.fhdr.fid, hdr)) #Append to existing list
                                else:
                                    self.tmap[hdr.xid] = [(self.fhdr.fid, hdr)] # Create new list
                elif isinstance(hdr, TxnHdr) and not stop:
                    while not hdr.complete():
                        stop = self.advance_file()
                        if stop:
                            break
                        hdr.load(self.f)
                    stop = not self.check_owi(hdr)
                    if stop:
                        warn = ' (WARNING: OWI mismatch - could be overwrite boundary.)'
                    else:
                        if hdr.xid in self.tmap:
                            mismatched_rids = []
                            if hdr.magic[-1] == 'c': # commit
                                for rec in self.tmap[hdr.xid]:
                                    if isinstance(rec[1], EnqRec):
                                        self.emap[rec[1].rid] = (rec[0], rec[1], False) # Transfer enq to emap
                                    elif isinstance(rec[1], DeqHdr):
                                        if rec[1].deq_rid in self.emap:
                                            del self.emap[rec[1].deq_rid] # Delete from emap
                                        else:
                                            mismatched_rids.append('0x%x' % rec[1].deq_rid)
                                    else:
                                        raise Exception('Unknown header found in txn map: %s' % rec[1])
                            elif hdr.magic[-1] == 'a': # abort
                                for rec in self.tmap[hdr.xid]:
                                    if isinstance(rec[1], DeqHdr):
                                        if rec[1].deq_rid in self.emap:
                                            t = self.emap[rec[1].deq_rid]
                                            self.emap[rec[1].deq_rid] = (t[0], t[1], False) # Unlock enq record
                            del self.tmap[hdr.xid]
                            if len(mismatched_rids) > 0:
                                warn = ' (WARNING: transactional dequeues not found in enqueue map; rids=%s)' % mismatched_rids
                        else:
                            warn = ' (WARNING: %s not found in transaction map)' % print_xid(len(hdr.xid), hdr.xid)
                if not self.qflag: print ' > %s%s' % (hdr, warn)
                if not stop:
                    stop = (self.last_file and hdr.check()) or hdr.empty() or self.fhdr.empty()

    def analyze_files(self):
        fname = ''
        fnum = -1
        rid = -1
        fro = -1
        tss = ''
        if not self.qflag: print 'Analyzing journal files:'
        owi_found = False
        for i in range(0, self.num_jfiles):
            jfn = self.jdir + '/' + self.bfn + '.%04d.jdat' % i
            f = open(jfn)
            fhdr = load(f, Hdr)
            if fhdr.empty():
                if not self.qflag:
                    print '  %s: file empty' % jfn
                break
            if i == 0:
                init_owi = fhdr.owi()
                fname = jfn
                fnum = i
                rid = fhdr.rid
                fro = fhdr.fro
                tss = fhdr.timestamp_str()
            elif fhdr.owi() != init_owi and not owi_found:
                fname = jfn
                fnum = i
                rid = fhdr.rid
                fro = fhdr.fro
                tss = fhdr.timestamp_str()
                owi_found = True
            if not self.qflag:
                print '  %s: owi=%s rid=0x%x, fro=0x%08x ts=%s' % (jfn, fhdr.owi(), fhdr.rid, fhdr.fro, fhdr.timestamp_str())
        if fnum < 0 or rid < 0 or fro < 0:
            raise Exception('All journal files empty')
        if not self.qflag: print '  Oldest complete file: %s: rid=%d, fro=0x%08x ts=%s' % (fname, rid, fro, tss)
        return (fnum, rid, fro)

    def advance_file(self, *start_info):
        seek_flag = False
        if len(start_info) == 3:
            self.file_start = self.file_num = start_info[0]
            self.fro = start_info[2]
            seek_flag = True
        if self.f != None and file_full(self.f):
            self.file_num = self.incr_fnum()
            if self.file_num == self.file_start:
                return True
            if self.file_start == 0:
                self.last_file = self.file_num == self.num_jfiles - 1
            else:
                self.last_file = self.file_num == self.file_start - 1
        if self.file_num < 0 or self.file_num >= self.num_jfiles:
            raise Exception('Bad file number %d' % self.file_num)
        jfn = self.jdir + '/' + self.bfn + '.%04d.jdat' % self.file_num
        self.f = open(jfn)
        self.fhdr = load(self.f, Hdr)
        if seek_flag and self.f.tell() != self.fro:
            self.f.seek(self.fro)
        self.first_rec = True
        if not self.qflag: print jfn, ": ", self.fhdr
        return False

    def incr_fnum(self):
        self.file_num += 1
        if self.file_num >= self.num_jfiles:
            self.file_num = 0;
        return self.file_num

    def check_owi(self, hdr):
        return self.fhdr_owi_at_msg_start == hdr.owi()

    def check_rid(self, hdr):
        if  self.last_rid != -1 and hdr.rid <= self.last_rid:
            return False
        self.last_rid = hdr.rid
        return True

    def read_jinf(self):
        filename = self.jdir + '/' + self.bfn + '.jinf'
        try:
            f = open(filename, 'r')
        except IOError:
            print 'ERROR: Unable to open jinf file %s' % filename
            sys.exit(1)
        p = xml.parsers.expat.ParserCreate()
        p.StartElementHandler = self.handleStartElement
        p.CharacterDataHandler = self.handleCharData
        p.EndElementHandler = self.handleEndElement
        p.ParseFile(f)
        if self.num_jfiles == None:
            print 'ERROR: number_jrnl_files not found in jinf file "%s"!' % filename
        if jfsize == None:
            print 'ERROR: jrnl_file_size_sblks not found in jinf file "%s"!' % filename
        if self.num_jfiles == None or jfsize == None:
            sys.exit(1)

    def handleStartElement(self, name, attrs):
        global jfsize
        if name == 'number_jrnl_files':
            self.num_jfiles = int(attrs['value'])
        if name == 'jrnl_file_size_sblks':
            jfsize = (int(attrs['value']) + 1) * sblk_size

    def handleCharData(self, data): pass

    def handleEndElement(self, name): pass

    def proc_csv(self):
        if self.csvfn != None and self.tnum != None:
            tparams = self.get_test(self.csvfn, self.tnum)
            if tparams == None:
                print 'ERROR: Test %d not found in CSV file "%s"' % (self.tnum, self.csvfn)
                sys.exit(1)
            self.num_msgs = tparams['num_msgs']
            if tparams['min_size'] == tparams['max_size']:
                self.msg_len = tparams['max_size']
            else:
                self.msg_len = 0
            self.auto_deq = tparams['auto_deq']
            if tparams['xid_min_size'] == tparams['xid_max_size']:
                self.xid_len = tparams['xid_max_size']
            else:
                self.xid_len = 0
            self.transient = tparams['transient']
            self.extern = tparams['extern']

    def get_test(self, filename, tnum):
        try:
            f=open(filename, 'r')
        except IOError:
            print 'ERROR: Unable to open CSV file "%s"' % filename
            sys.exit(1)
        for l in f:
            sl = l.strip().split(',')
            if len(sl[0]) > 0 and sl[0][0] != '"':
                try:
                    if (int(sl[TEST_NUM_COL]) == tnum):
                        return { 'num_msgs':int(sl[NUM_MSGS_COL]),
                                 'min_size':int(sl[MIN_MSG_SIZE_COL]),
                                 'max_size':int(sl[MAX_MSG_SIZE_COL]),
                                 'auto_deq':not (sl[AUTO_DEQ_COL] == 'FALSE' or sl[AUTO_DEQ_COL] == '0'),
                                 'xid_min_size':int(sl[MIN_XID_SIZE_COL]),
                                 'xid_max_size':int(sl[MAX_XID_SIZE_COL]),
                                 'transient':not (sl[TRANSIENT_COL] == 'FALSE' or sl[TRANSIENT_COL] == '0'),
                                 'extern':not (sl[EXTERN_COL] == 'FALSE' or sl[EXTERN_COL] == '0'),
                                 'comment':sl[COMMENT_COL] }
                except Exception:
                    pass
        return None
        
    def proc_args(self, argv):
        try:
            opts, args = getopt.getopt(sys.argv[1:], "ab:c:d:hqt:", ["analyse", "base-filename=", "csv-filename=", "dir=", "help", "quiet", "test-num="])
        except getopt.GetoptError:
            self.usage()
            sys.exit(2)
        for o, a in opts:
            if o in ("-h", "--help"):
                self.usage()
                sys.exit()
            if o in ("-a", "--analyze"):
                self.aflag = True
            if o in ("-b", "--base-filename"):
                self.bfn = a
            if o in ("-c", "--csv-filename"):
                self.csvfn = a
            if o in ("-d", "--dir"):
                self.jdir = a
            if o in ("-q", "--quiet"):
                self.qflag = True
            if o in ("-t", "--test-num"):
                if not a.isdigit():
                    print 'ERROR: Illegal test-num argument. Must be a non-negative number'
                    sys.exit(2)
                self.tnum = int(a)
        if self.bfn == None or self.jdir == None:
            print 'ERROR: Missing requred args.'
            self.usage()
            sys.exit(2)
        if self.tnum != None and self.csvfn == None:
            print 'ERROR: Test number specified, but not CSV file'
            self.usage()
            sys.exit(2)

    def usage(self):
        print 'Usage: %s opts' % sys.argv[0]
        print '  where opts are in either short or long format (*=req\'d):'
        print '  -a --analyze                  Analyze enqueue/dequeue records'
        print '  -b --base-filename [string] * Base filename for journal files'
        print '  -c --csv-filename  [string]   CSV filename containing test parameters'
        print '  -d --dir           [string] * Journal directory containing journal files'
        print '  -h --help                     Print help'
        print '  -q --quiet                    Quiet (reduced output)'
        print '  -t --test-num      [int]      Test number from CSV file - only valid if CSV file named'

    def report(self):
        if not self.qflag:
            print
            print ' === REPORT ===='
            if self.num_msgs > 0 and self.msg_cnt != self.num_msgs:
                print 'WARNING: Found %d messages; %d expected.' % (self.msg_cnt, self.num_msgs)
            if len(self.emap) > 0:
                print
                print 'Remaining enqueued records (sorted by rid): '
                keys = sorted(self.emap.keys())
                for k in keys:
                    if self.emap[k][2] == True: # locked
                        locked = ' (locked)'
                    else:
                        locked = ''
                    print "  fid=%d %s%s" % (self.emap[k][0], self.emap[k][1], locked)
                print 'WARNING: Enqueue-Dequeue mismatch, %d enqueued records remain.' % len(self.emap)
            if len(self.tmap) > 0:
                txn_rec_cnt = 0
                print
                print 'Remaining transactions: '
                for t in self.tmap:
                    print_xid(len(t), t)
                    for r in self.tmap[t]:
                        print "  fid=%d %s" % (r[0], r[1])
                    print " Total: %d records for xid %s" % (len(self.tmap[t]), t)
                    txn_rec_cnt += len(self.tmap[t])
                print 'WARNING: Incomplete transactions, %d xids remain containing %d records.' % (len(self.tmap), txn_rec_cnt)
            print '%d enqueues, %d journal records processed.' % (self.msg_cnt, self.rec_cnt)


#===============================================================================

CLASSES = {
    "a": TxnHdr,
    "c": TxnHdr,
    "d": DeqHdr,
    "e": EnqRec,
    "f": FileHdr
}

m = Main(sys.argv)
m.run()
m.report()

sys.exit(None)
