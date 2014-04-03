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

import jerr, jrnl
import os.path, sys


#== class EnqMap ==============================================================

class EnqMap(object):
    """Class for maintaining a map of enqueued records, indexing the rid against hdr, fid and transaction lock"""
    
    def __init__(self):
        """Constructor"""
        self.__map = {}
    
    def __str__(self):
        """Print the contents of the map"""
        return self.report(True, True)
        
    def add(self, fid, hdr, lock = False):
        """Add a new record into the map"""
        if hdr.rid in self.__map:
            raise jerr.DuplicateRidError(hdr.rid)
        self.__map[hdr.rid] = [fid, hdr, lock]
    
    def contains(self, rid):
        """Return True if the map contains the given rid"""
        return rid in self.__map
    
    def delete(self, rid):
        """Delete the rid and its associated data from the map"""
        if rid in self.__map:
            if self.get_lock(rid):
                raise jerr.DeleteLockedRecordError(rid)
            del self.__map[rid]
        else:
            raise jerr.JWarning("ERROR: Deleting non-existent rid from EnqMap: rid=0x%x" % rid)
    
    def get(self, rid):
        """Return a list [fid, hdr, lock] for the given rid"""
        if self.contains(rid):
            return self.__map[rid]
        return None
    
    def get_fid(self, rid):
        """Return the fid for the given rid"""
        if self.contains(rid):
            return self.__map[rid][0]
        return None
    
    def get_hdr(self, rid):
        """Return the header record for the given rid"""
        if self.contains(rid):
            return self.__map[rid][1]
        return None
    
    def get_lock(self, rid):
        """Return the transaction lock value for the given rid""" 
        if self.contains(rid):
            return self.__map[rid][2]
        return None
    
    def get_rec_list(self):
        """Return a list of tuples (fid, hdr, lock) for all entries in the map"""
        return self.__map.values()
    
    def lock(self, rid):
        """Set the transaction lock for a given rid to True"""
        if rid in self.__map:
            if not self.__map[rid][2]: # locked
                self.__map[rid][2] = True
            else:
                raise jerr.AlreadyLockedError(rid)
        else:
            raise jerr.JWarning("ERROR: Locking non-existent rid in EnqMap: rid=0x%x" % rid)
        
    def report(self, show_stats, show_records):
        """Return a string containing a text report for all records in the map"""
        if len(self.__map) == 0:
            return "No enqueued records found."
        rstr = "%d enqueued records found" % len(self.__map)
        if show_records:
            rstr += ":"
            rid_list = self.__map.keys()
            rid_list.sort()
            for rid in rid_list:
                if self.__map[rid][2]:
                    lock_str = " [LOCKED]"
                else:
                    lock_str = ""
                rstr += "\n  lfid=%d %s %s" % (rec[0], rec[1], lock_str)
        else:
            rstr += "."
        return rstr
    
    def rids(self):
        """Return a list of rids in the map"""
        return self.__map.keys()
    
    def size(self):
        """Return the number of entries in the map"""
        return len(self.__map)
    
    def unlock(self, rid):
        """Set the transaction lock for a given rid to False"""
        if rid in self.__map:
            if self.__map[rid][2]:
                self.__map[rid][2] = False
            else:
                raise jerr.NotLockedError(rid)
        else:
            raise jerr.NonExistentRecordError("unlock", rid)


#== class TxnMap ==============================================================

class TxnMap(object):
    """Transaction map, which maps xids to a list of outstanding actions"""
    
    def __init__(self, emap):
        """Constructor, requires an existing EnqMap instance"""
        self.__emap = emap
        self.__map = {}
    
    def __str__(self):
        """Print the contents of the map"""
        return self.report(True, True)
    
    def add(self, fid, hdr):
        """Add a new transactional record into the map"""
        if isinstance(hdr, jrnl.DeqRec):
            try:
                self.__emap.lock(hdr.deq_rid)
            except jerr.JWarning:
                # Not in emap, look for rid in tmap
                l = self.find_rid(hdr.deq_rid, hdr.xid)
                if l != None:
                    if l[2]:
                        raise jerr.AlreadyLockedError(hdr.deq_rid)
                    l[2] = True
        if hdr.xid in self.__map:
            self.__map[hdr.xid].append([fid, hdr, False]) # append to existing list
        else:
            self.__map[hdr.xid] = [[fid, hdr, False]] # create new list
    
    def contains(self, xid):
        """Return True if the xid exists in the map; False otherwise"""
        return xid in self.__map
    
    def delete(self, hdr):
        """Remove a transaction record from the map using either a commit or abort header"""
        if hdr.magic[-1] == "c":
            return self._commit(hdr.xid)
        if hdr.magic[-1] == "a":
            self._abort(hdr.xid)
        else:
            raise jerr.InvalidRecordTypeError("delete from TxnMap", hdr.magic, hdr.rid)
    
    def find_rid(self, rid, xid_hint = None):
        """ Search for and return map list with supplied rid. If xid_hint is supplied, try that xid first"""
        if xid_hint != None and self.contains(xid_hint):
            for l in self.__map[xid_hint]:
                if l[1].rid == rid:
                    return l
        for xid in self.__map.iterkeys():
            if xid_hint == None or xid != xid_hint:
                for l in self.__map[xid]:
                    if l[1].rid == rid:
                        return l
        
    def get(self, xid):
        """Return a list of operations for the given xid"""
        if self.contains(xid):
            return self.__map[xid]
        
    def report(self, show_stats, show_records):
        """Return a string containing a text report for all records in the map"""
        if len(self.__map) == 0:
            return "No outstanding transactions found."
        rstr = "%d outstanding transactions found" % len(self.__map)
        if show_records:
            rstr += ":"
            for xid, tup in self.__map.iteritems():
                rstr += "\n  xid=%s:" % jrnl.Utils.format_xid(xid)
                for i in tup:
                    rstr += "\n   %s" % str(i[1])
        else:
            rstr += "."
        return rstr
    
    def size(self):
        """Return the number of xids in the map"""
        return len(self.__map)
    
    def xids(self):
        """Return a list of xids in the map"""
        return self.__map.keys()
    
    def _abort(self, xid):
        """Perform an abort operation for the given xid record"""
        for _, hdr, _ in self.__map[xid]:
            if isinstance(hdr, jrnl.DeqRec):
                try:
                    self.__emap.unlock(hdr.deq_rid)
                except jerr.NonExistentRecordError, err: # Not in emap, look in current transaction op list (TPL)
                    found_rid = False
                    for _, hdr1, _ in self.__map[xid]:
                        if isinstance(hdr1, jrnl.EnqRec) and hdr1.rid == hdr.deq_rid:
                            found_rid = True
                            break
                    if not found_rid: # Not found in current transaction op list, re-throw error
                        raise err
        del self.__map[xid]
    
    def _commit(self, xid):
        """Perform a commit operation for the given xid record"""
        mismatch_list = []
        for fid, hdr, lock in self.__map[xid]:
            if isinstance(hdr, jrnl.EnqRec):
                self.__emap.add(fid, hdr, lock) # Transfer enq to emap
            else:
                if self.__emap.contains(hdr.deq_rid):
                    self.__emap.unlock(hdr.deq_rid)
                    self.__emap.delete(hdr.deq_rid)
                else:
                    mismatch_list.append("0x%x" % hdr.deq_rid)
        del self.__map[xid]
        return mismatch_list

#== class JrnlAnalyzer ========================================================

class JrnlAnalyzer(object):
    """
    This class analyzes a set of journal files and determines which is the last to be written
    (the newest file),  and hence which should be the first to be read for recovery (the oldest
    file).
    
    The analysis is performed on construction; the contents of the JrnlInfo object passed provide
    the recovery details.
    """

    def __init__(self, jinf):
        """Constructor"""
        self.__oldest = None
        self.__jinf = jinf
        self.__flist = self._analyze()
                
    def __str__(self):
        """String representation of this JrnlAnalyzer instance, will print out results of analysis."""
        ostr = "Journal files analyzed in directory %s (* = earliest full):\n" % self.__jinf.get_current_dir()
        if self.is_empty():
            ostr += "  <All journal files are empty>\n"
        else:
            for tup in self.__flist:
                tmp = " "
                if tup[0] == self.__oldest[0]:
                    tmp = "*"
                ostr += "  %s %s: owi=%-5s rid=0x%x, fro=0x%x ts=%s\n" % (tmp, os.path.basename(tup[1]), tup[2],
                                                                          tup[3], tup[4], tup[5])
            for i in range(self.__flist[-1][0] + 1, self.__jinf.get_num_jrnl_files()):
                ostr += "    %s.%04x.jdat: <empty>\n" % (self.__jinf.get_jrnl_base_name(), i) 
        return ostr
        
    # Analysis
    
    def get_oldest_file(self):
        """Return a tuple (ordnum, jfn, owi, rid, fro, timestamp) for the oldest data file found in the journal"""
        return self.__oldest

    def get_oldest_file_index(self):
        """Return the ordinal number of the oldest data file found in the journal"""
        if self.is_empty():
            return None
        return self.__oldest[0]

    def is_empty(self):
        """Return true if the analysis found that the journal file has never been written to"""
        return len(self.__flist) == 0
    
    def _analyze(self):
        """Perform the journal file analysis by reading and comparing the file headers of each journal data file"""
        owi_found = False
        flist = []
        for i in range(0, self.__jinf.get_num_jrnl_files()):
            jfn = os.path.join(self.__jinf.get_current_dir(), "%s.%04x.jdat" % (self.__jinf.get_jrnl_base_name(), i))
            fhandle = open(jfn)
            fhdr = jrnl.Utils.load(fhandle, jrnl.Hdr)
            if fhdr.empty():
                break
            this_tup = (i, jfn, fhdr.owi(), fhdr.rid, fhdr.fro, fhdr.timestamp_str())
            flist.append(this_tup)
            if i == 0:
                init_owi = fhdr.owi()
                self.__oldest = this_tup
            elif fhdr.owi() != init_owi and not owi_found:
                self.__oldest = this_tup
                owi_found = True
        return flist
        

#== class JrnlReader ====================================================

class JrnlReader(object):
    """
    This class contains an Enqueue Map (emap), a transaction map (tmap) and a transaction
    object list (txn_obj_list) which are populated by reading the journals from the oldest
    to the newest and analyzing each record. The JrnlInfo and JrnlAnalyzer
    objects supplied on construction provide the information used for the recovery.
    
    The analysis is performed on construction.
    """
    
    def __init__(self, jinfo, jra, qflag = False, rflag = False, vflag = False):
        """Constructor, which reads all """
        self._jinfo = jinfo
        self._jra = jra
        self._qflag = qflag
        self._rflag = rflag
        self._vflag = vflag
        
        # test callback functions for CSV tests
        self._csv_store_chk = None
        self._csv_start_cb = None
        self._csv_enq_cb = None
        self._csv_deq_cb = None
        self._csv_txn_cb = None
        self._csv_end_cb = None
        
        self._emap = EnqMap()
        self._tmap = TxnMap(self._emap)
        self._txn_obj_list = {}
        
        self._file = None
        self._file_hdr = None
        self._file_num = None
        self._first_rec_flag = None
        self._fro = None
        self._last_file_flag = None
        self._start_file_num = None
        self._file_hdr_owi = None
        self._warning = []
        
        self._abort_cnt = 0
        self._commit_cnt = 0
        self._msg_cnt = 0
        self._rec_cnt = 0
        self._txn_msg_cnt = 0
    
    def __str__(self):
        """Print out all the undequeued records"""
        return self.report(True, self._rflag)
    
    def emap(self):
        """Get the enqueue map"""
        return self._emap

    def get_abort_cnt(self):
        """Get the cumulative number of transactional aborts found"""
        return self._abort_cnt

    def get_commit_cnt(self):
        """Get the cumulative number of transactional commits found"""
        return self._commit_cnt

    def get_msg_cnt(self):
        """Get the cumulative number of messages found"""
        return self._msg_cnt
    
    def get_rec_cnt(self):
        """Get the cumulative number of journal records (including fillers) found"""
        return self._rec_cnt

    def is_last_file(self):
        """Return True if the last file is being read"""
        return self._last_file_flag
    
    def report(self, show_stats = True, show_records = False):
        """Return a string containing a report on the file analysis"""
        rstr = self._emap.report(show_stats, show_records) + "\n" + self._tmap.report(show_stats, show_records)
        #TODO - print size analysis here - ie how full, sparse, est. space remaining before enq threshold
        return rstr
    
    def run(self):
        """Perform the read of the journal"""
        if self._csv_start_cb != None and self._csv_start_cb(self._csv_store_chk):
            return
        if self._jra.is_empty():
            return
        stop = self._advance_jrnl_file(*self._jra.get_oldest_file())
        while not stop and not self._get_next_record():
            pass
        if self._csv_end_cb != None and self._csv_end_cb(self._csv_store_chk):
            return
        if not self._qflag:
            print
    
    def set_callbacks(self, csv_store_chk, csv_start_cb = None, csv_enq_cb = None, csv_deq_cb = None, csv_txn_cb = None,
                      csv_end_cb = None):
        """Set callbacks for checks to be made at various points while reading the journal"""
        self._csv_store_chk = csv_store_chk
        self._csv_start_cb = csv_start_cb
        self._csv_enq_cb = csv_enq_cb
        self._csv_deq_cb = csv_deq_cb
        self._csv_txn_cb = csv_txn_cb
        self._csv_end_cb = csv_end_cb
    
    def tmap(self):
        """Return the transaction map"""
        return self._tmap
    
    def get_txn_msg_cnt(self):
        """Get the cumulative transactional message count"""
        return self._txn_msg_cnt
    
    def txn_obj_list(self):
        """Get a cumulative list of transaction objects (commits and aborts)"""
        return self._txn_obj_list
    
    def _advance_jrnl_file(self, *oldest_file_info):
        """Rotate to using the next journal file. Return False if the operation was successful, True if there are no
        more files to read."""
        fro_seek_flag = False
        if len(oldest_file_info) > 0:
            self._start_file_num = self._file_num = oldest_file_info[0]
            self._fro = oldest_file_info[4]
            fro_seek_flag = True # jump to fro to start reading
            if not self._qflag and not self._rflag:
                if self._vflag:
                    print "Recovering journals..."
                else:
                    print "Recovering journals",
        if self._file != None and self._is_file_full():
            self._file.close()
            self._file_num = self._incr_file_num()
            if self._file_num == self._start_file_num:
                return True
            if self._start_file_num == 0:
                self._last_file_flag = self._file_num == self._jinfo.get_num_jrnl_files() - 1
            else:
                self._last_file_flag = self._file_num == self._start_file_num - 1
        if self._file_num < 0 or self._file_num >= self._jinfo.get_num_jrnl_files():
            raise jerr.BadFileNumberError(self._file_num)
        jfn = os.path.join(self._jinfo.get_current_dir(), "%s.%04x.jdat" %
                           (self._jinfo.get_jrnl_base_name(), self._file_num))
        self._file = open(jfn)
        self._file_hdr = jrnl.Utils.load(self._file, jrnl.Hdr)
        if fro_seek_flag and self._file.tell() != self._fro:
            self._file.seek(self._fro)
        self._first_rec_flag = True
        if not self._qflag:
            if self._rflag:
                print jfn, ": ", self._file_hdr
            elif self._vflag:
                print "* Reading %s" % jfn
            else:
                print ".",
                sys.stdout.flush()
        return False

    def _check_owi(self, hdr):
        """Return True if the header's owi indicator matches that of the file header record; False otherwise. This can
        indicate whether the last record in a file has been read and now older records which have not yet been
        overwritten are now being read."""
        return self._file_hdr_owi == hdr.owi()
    
    def _is_file_full(self):
        """Return True if the current file is full (no more write space); false otherwise"""
        return self._file.tell() >= self._jinfo.get_jrnl_file_size_bytes()
    
    def _get_next_record(self):
        """Get the next record in the file for analysis"""
        if self._is_file_full():
            if self._advance_jrnl_file():
                return True
        try:
            hdr = jrnl.Utils.load(self._file, jrnl.Hdr)
        except:
            return True
        if hdr.empty():
            return True
        if hdr.check():
            return True
        self._rec_cnt += 1
        self._file_hdr_owi = self._file_hdr.owi()
        if self._first_rec_flag:
            if self._file_hdr.fro != hdr.foffs:
                raise jerr.FirstRecordOffsetMismatch(self._file_hdr.fro, hdr.foffs)
            else:
                if self._rflag:
                    print " * fro ok: 0x%x" % self._file_hdr.fro
                self._first_rec_flag = False
        stop = False
        if   isinstance(hdr, jrnl.EnqRec):
            stop = self._handle_enq_rec(hdr)
        elif isinstance(hdr, jrnl.DeqRec):
            stop = self._handle_deq_rec(hdr)
        elif isinstance(hdr, jrnl.TxnRec):
            stop = self._handle_txn_rec(hdr)
        wstr = ""
        for warn in self._warning:
            wstr += " (%s)" % warn
        if self._rflag:
            print " > %s  %s" % (hdr, wstr)
        self._warning = []
        return stop
     
    def _handle_deq_rec(self, hdr):
        """Process a dequeue ("RHMd") record"""
        if self._load_rec(hdr):
            return True
        
        # Check OWI flag
        if not self._check_owi(hdr):
            self._warning.append("WARNING: OWI mismatch - could be overwrite boundary.")
            return True
        # Test hook
        if self._csv_deq_cb != None and self._csv_deq_cb(self._csv_store_chk, hdr):
            return True
        
        try:
            if hdr.xid == None:
                self._emap.delete(hdr.deq_rid)
            else:
                self._tmap.add(self._file_hdr.fid, hdr)
        except jerr.JWarning, warn:
            self._warning.append(str(warn))
        return False
    
    def _handle_enq_rec(self, hdr):
        """Process a dequeue ("RHMe") record"""
        if self._load_rec(hdr):
            return True
        
        # Check extern flag
        if hdr.extern and hdr.data != None:
            raise jerr.ExternFlagDataError(hdr)
        # Check OWI flag
        if not self._check_owi(hdr):
            self._warning.append("WARNING: OWI mismatch - could be overwrite boundary.")
            return True
        # Test hook
        if self._csv_enq_cb != None and self._csv_enq_cb(self._csv_store_chk, hdr):
            return True
        
        if hdr.xid == None:
            self._emap.add(self._file_hdr.fid, hdr)
        else:
            self._txn_msg_cnt += 1
            self._tmap.add(self._file_hdr.fid, hdr)
        self._msg_cnt += 1
        return False
    
    def _handle_txn_rec(self, hdr):
        """Process a transaction ("RHMa or RHMc") record"""
        if self._load_rec(hdr):
            return True
        
        # Check OWI flag
        if not self._check_owi(hdr):
            self._warning.append("WARNING: OWI mismatch - could be overwrite boundary.")
            return True
        # Test hook
        if self._csv_txn_cb != None and self._csv_txn_cb(self._csv_store_chk, hdr):
            return True
               
        if hdr.magic[-1] == "a":
            self._abort_cnt += 1
        else:
            self._commit_cnt += 1
        
        if self._tmap.contains(hdr.xid):
            mismatched_rids = self._tmap.delete(hdr)
            if mismatched_rids != None and len(mismatched_rids) > 0:
                self._warning.append("WARNING: transactional dequeues not found in enqueue map; rids=%s" %
                                     mismatched_rids)
        else:
            self._warning.append("WARNING: %s not found in transaction map" % jrnl.Utils.format_xid(hdr.xid))
        if hdr.magic[-1] == "c": # commits only
            self._txn_obj_list[hdr.xid] = hdr
        return False

    def _incr_file_num(self):
        """Increment the number of files read with wraparound (ie after file n-1, go to 0)"""
        self._file_num += 1
        if self._file_num >= self._jinfo.get_num_jrnl_files():
            self._file_num = 0
        return self._file_num
    
    def _load_rec(self, hdr):
        """Load a single record for the given header. There may be arbitrarily large xids and data components."""
        while not hdr.complete():
            if self._advance_jrnl_file():
                return True
            hdr.load(self._file)
        return False

# =============================================================================

if __name__ == "__main__":
    print "This is a library, and cannot be executed."
