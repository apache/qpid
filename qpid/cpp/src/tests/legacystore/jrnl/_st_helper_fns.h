/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

// NOTE: This file is included in _st_*.cpp files inside the QPID_AUTO_TEST_SUITE()
// definition.

#define MAX_AIO_SLEEPS 500
#define AIO_SLEEP_TIME 1000
#define NUM_TEST_JFILES 4
#define NUM_DEFAULT_JFILES  8
#define JRNL_DEFAULT_FSIZE  24 // Multiples of JRNL_RMGR_PAGE_SIZE
#define TEST_JFSIZE_SBLKS 128
#define DEFAULT_JFSIZE_SBLKS (JRNL_DEFAULT_FSIZE * JRNL_RMGR_PAGE_SIZE)
#define NUM_MSGS 5
#define MSG_REC_SIZE_DBLKS 2
#define MSG_SIZE (MSG_REC_SIZE_DBLKS * JRNL_DBLK_SIZE) - sizeof(enq_hdr) - sizeof(rec_tail)
#define LARGE_MSG_REC_SIZE_DBLKS (JRNL_SBLK_SIZE * JRNL_RMGR_PAGE_SIZE)
#define LARGE_MSG_SIZE (LARGE_MSG_REC_SIZE_DBLKS * JRNL_DBLK_SIZE) - sizeof(enq_hdr) - sizeof(rec_tail)
#define XID_SIZE 64

#define XLARGE_MSG_RATIO (1.0 * LARGE_MSG_REC_SIZE / JRNL_DBLK_SIZE / JRNL_SBLK_SIZE / JRNL_RMGR_PAGE_SIZE)
#define XLARGE_MSG_THRESHOLD (int)(JRNL_DEFAULT_FSIZE * NUM_DEFAULT_JFILES * JRNL_ENQ_THRESHOLD / 100 / LARGE_MSG_RATIO)

#define NUM_JFILES 4
#define JFSIZE_SBLKS 128

const char* tdp = getenv("TMP_DATA_DIR");
const string test_dir(tdp && strlen(tdp) > 0 ? string(tdp) + "/" + test_filename : "/var/tmp/jrnl_test");

class test_dtok : public data_tok
{
private:
    bool flag;
public:
    test_dtok() : data_tok(), flag(false) {}
    virtual ~test_dtok() {}
    bool done() { if (flag || _wstate == NONE) return true; else { flag = true; return false; } }
};

class test_jrnl_cb : public aio_callback {
    virtual void wr_aio_cb(std::vector<data_tok*>& dtokl)
    {
        for (std::vector<data_tok*>::const_iterator i=dtokl.begin(); i!=dtokl.end(); i++)
        {
            test_dtok* dtp = static_cast<test_dtok*>(*i);
            if (dtp->done())
               delete dtp;
        }
    }
    virtual void rd_aio_cb(std::vector<u_int16_t>& /*pil*/) {}
};

class test_jrnl : public jcntl
{
test_jrnl_cb* cb;

public:
    test_jrnl(const std::string& jid, const std::string& jdir, const std::string& base_filename, test_jrnl_cb& cb0) :
        jcntl(jid, jdir, base_filename),
        cb(&cb0) {}
    virtual ~test_jrnl() {}
    void initialize(const u_int16_t num_jfiles, const bool ae, const u_int16_t ae_max_jfiles,
            const u_int32_t jfsize_sblks)
    {
        jcntl::initialize(num_jfiles, ae, ae_max_jfiles, jfsize_sblks, JRNL_WMGR_DEF_PAGES, JRNL_WMGR_DEF_PAGE_SIZE,
                cb);
        _jdir.create_dir();
    }
    void recover(const u_int16_t num_jfiles, const bool ae, const u_int16_t ae_max_jfiles, const u_int32_t jfsize_sblks,
            vector<string>* txn_list, u_int64_t& highest_rid)
    { jcntl::recover(num_jfiles, ae, ae_max_jfiles, jfsize_sblks, JRNL_WMGR_DEF_PAGES, JRNL_WMGR_DEF_PAGE_SIZE, cb,
            txn_list, highest_rid); }
};

/*
* This class is for testing recover functionality by maintaining an internal lfid-pfid map, then creating physical
* journal file stubs (just the fhdr section of the journal) and jinf file. This allows the recover functionality (which
* analyzes these components to determine recover order).
*
* First set up a map or "blueprint" of what the journal should look like for recovery, then have the class create the
* physical files. The jinf object under test then reads and analyzes the created journal, and it's analysis is checked
* against what is expected.
*
* General usage pattern:
* 1. Create instance of lfid_pfid_map.
* 2. Call lfid_pfid_map::journal_create() to simulate initial journal creation.
* 3. (optional) Call lfid_pfid_map::journal_insert() one or more times to simulate the addition of journal files.
* 4. Call lfid_pfid_map::write_journal() to create dummy journal files (files containing only file headers)
* 5. Create and initialize the jinf object under test
* 6. Call jinf::analyze() to determine the pfid order - and thus also first and last lids
* 7. Call lfid_pfid_map::check_analysis() to check the conclusions of the analysis
* 8. Call lfid_pfid_map::destroy_journal() to delete the journal files and reset the lfid_pfid_map object.
* 9. (optional) Back to step 2 for more tests
*
* See the individual methods below for more details.
*/
class lfid_pfid_map
{
    public:
        typedef pair<u_int16_t, file_hdr> lppair;           // Used for loading the map
        typedef multimap<u_int16_t, file_hdr> lpmap;        // Stores the journal "plan" before it is created on-disk
        typedef lpmap::const_iterator lpmap_citr;           // General purpose iterator
        typedef pair<lpmap_citr, lpmap_citr> lpmap_range;   // Range of values returned by multimap's equal_range() fn

    private:
        string _jid;                // Journal id
        string _base_filename;      // Base filename
        lpmap _map;                 // Stores the journal "blueprint" before it is created on-disk
        u_int16_t _num_used_files;  // number of files which contain jorunals
        u_int16_t _oldest_lfid;     // lfid where owi flips; always 0 if !_full
        u_int16_t _last_pfid;       // last pfid (ie last file added)

    public:
        lfid_pfid_map(const string& jid, const string& base_filename) :
                _jid(jid), _base_filename(base_filename), _num_used_files(0), _oldest_lfid(0), _last_pfid(0)
        {}
        virtual ~lfid_pfid_map() {}

        // Mainly used for debugging
        void print()
        {
            int cnt = 0;
            for (lpmap_citr i=_map.begin(); i!=_map.end(); i++, cnt++)
            {
                const file_hdr fh = i->second;
                cout << "  " << cnt << ": owi=" << (fh.get_owi()?"t":"f") << hex << " frid=0x" << fh._rid;
                cout << " pfid=0x" << fh._pfid << " lfid=0x" << fh._lfid << " fro=0x" << fh._fro << dec << endl;
            }
        }

        std::size_t size()
        {
            return _map.size();
        }

        /*
        * Method journal_create(): Used to simulate the initial creation of a journal before file insertions
        * take place.
        *
        *      num_jfiles: The initial journal file count.
        * num_used_jfiles: If this number is less than num_jfiles, it indicates a clean journal that has not yet
        *                  completed its first rotation, and some files are empty (ie all null). The first
        *                  num_used_jfiles will contain file headers, the remainder will be blank.
        *     oldest_lfid: The lfid (==pfid, see note 1 below) at which the owi flag flips. During normal operation,
        *                  each time the journal rotates back to file 0, a flag (called the overwrite indicator or owi)
        *                  is flipped. This flag is saved in the file header. During recovery, if scanning from logical
        *                  file 0 upwards, the file at which this flag reverses from its value in file 0 is the file
        *                  that was to have been overwritten next, and is thus the "oldest" file. Recovery analysis must
        *                  start with this file. oldest_lfid sets the file at which this flag will flip value for the
        *                  simulated recovery analysis. Note that this will be ignored if num_used_jfiles < num_jfiles,
        *                  as it is not possible for an overwrite to have occurred if not all the files have been used.
        *       first_owi: Sets the value of the owi flag in file 0. If set to false, then the flip will be found with
        *                  a true flag (and visa versa).
        *
        * NOTES:
        * 1. By definition, the lfids and pfids coincide for a journal containing no inserted files. Thus pfid == lfid
        *    for all journals created after using initial_journal_create() alone.
        * 2. By definition, if a journal is not full (num_used_jfiles < num_jfiles), then all owi flags for those files
        *    that are used must be the same. It is not possible for an overwrite situation to arise if a journal is not
        *    full.
        * 3. This function acts on map _map only, and does not create any test files. Call write_journal() to do that.
        * 4. This function must be called on a clean test object or on one where the previous test data has been
        *    cleared by calling journal_destroy(). Running this function more than once on existing data will
        *    result in invalid journals which cannot be recovered.
        */
        void journal_create(const u_int16_t num_jfiles,      // Total number of files
                            const u_int16_t num_used_jfiles, // Number of used files, rest empty at end
                            const u_int16_t oldest_lfid = 0, // Fid where owi reverses
                            const u_int16_t bad_lfid = 0,    // Fid where owi reverses again (must be > oldest_lifd),
                                                             // used for testing bad owi detection
                            const bool first_owi = false)    // Value of first owi flag (ie pfid=0)
        {
            const bool full = num_used_jfiles == num_jfiles;
            bool owi = first_owi;
            _oldest_lfid = full ? oldest_lfid : 0;
            for (u_int16_t lfid = 0; lfid < num_jfiles; lfid++)
            {
                const u_int16_t pfid = lfid;
                file_hdr fh;
                if (pfid < num_used_jfiles)
                {
                    _num_used_files = num_used_jfiles;
                    /*
                    * Invert the owi flag from its current value (initially given by first_owi param) only if:
                    * 1. The journal is full (ie all files are used)
                    *    AND
                    * 2. oldest_lfid param is non-zero (this is default, but lfid 0 being inverted is logically
                    *    inconsistent with first_owi parameter being present)
                    *    AND
                    * 3. Either:
                    *    * current lfid == oldest_lfid (ie we are preparing the oldest lfid)
                    *      OR
                    *    * current lfid == bad_lfid AND bad_lfid > oldest (ie we are past the oldest and preparing the
                    *      bad lfid)
                    */
                    if (full && oldest_lfid > 0 &&
                                    (lfid == oldest_lfid || (bad_lfid > oldest_lfid && lfid == bad_lfid)))
                        owi = !owi;
                    const u_int64_t frid = u_int64_t(random());
                    init_fhdr(fh, frid, pfid, lfid, owi);
                }
                _map.insert(lppair(lfid, fh));
            }
        }

        /*
        * Method journal_insert(): Used to simulate the insertion of journal files into an existing journal.
        *
        *  after_lfid: The logical file id (lfid) after which the new file is to be inserted.
        *   num_files: The number of files to be inserted.
        * adjust_lids: Flag indicating that the lids of files _following_ the inserted files are to be adjusted upwards
        *              by the number of inserted files. Not doing so simulates a recovery immediately after insertion
        *              but before the following files are overwritten with their new lids. If this is set false, then:
        *              a) after_lfid MUST be the most recent file (_oldest_lfid-1 ie last lfid before owi changes).
        *              b) This call must be the last insert call.
        *
        * NOTES:
        * 1. It is not possible to insert before lfid/pfid 0; thus these are always coincidental. This operation is
        *    logically equivalent to inserting after the last lfid, which is possible.
        * 2. It is not possible to insert into a journal that is not full. Doing so will result in an unrecoverable
        *    journal (one that is logically inconsistent that can never occur in reality).
        * 3. If a journal is stopped/interrupted immediately after a file insertion, there could be duplicate lids in
        *    play at recovery, as the following file lids in their headers are only overwritten when the file is
        *    eventually written to during normal operation. The owi flags, however, are used to determine which of the
        *    ambiguous lids are the inserted files.
        * 4. This function acts on map _map only, and does not create any test files. Call write_journal() to do that.
        */
        void journal_insert(const u_int16_t after_lfid,     // Insert files after this lfid
                            const u_int16_t num_files = 1,  // Number of files to insert
                            const bool adjust_lids = true)  // Adjust lids following inserted files
        {
            if (num_files == 0) return;
            _num_used_files += num_files;
            const u_int16_t num_jfiles_before_append = _map.size();
            lpmap_citr i = _map.find(after_lfid);
            if (i == _map.end()) BOOST_FAIL("Unable to find lfid=" << after_lfid << " in map.");
            const file_hdr fh_before = (*i).second;

            // Move overlapping lids (if req'd)
            if (adjust_lids && after_lfid < num_jfiles_before_append - 1)
            {
                for (u_int16_t lfid = num_jfiles_before_append - 1; lfid > after_lfid; lfid--)
                {
                    lpmap_citr itr = _map.find(lfid);
                    if (itr == _map.end()) BOOST_FAIL("Unable to find lfid=" << after_lfid << " in map.");
                    file_hdr fh = itr->second;
                    _map.erase(lfid);
                    fh._lfid += num_files;
                    if (lfid == _oldest_lfid)
                        _oldest_lfid += num_files;
                    _map.insert(lppair(fh._lfid, fh));
                }
            }

            // Add new file headers
            u_int16_t pfid = num_jfiles_before_append;
            u_int16_t lfid = after_lfid + 1;
            while (pfid < num_jfiles_before_append + num_files)
            {
                const u_int64_t frid = u_int64_t(random());
                const size_t fro = 0x200;
                const file_hdr fh(RHM_JDAT_FILE_MAGIC, RHM_JDAT_VERSION, frid, pfid, lfid, fro, fh_before.get_owi(),
                        true);
                _map.insert(lppair(lfid, fh));
                _last_pfid = pfid;
                pfid++;
                lfid++;
            }
        }

        /*
        * Get the list of pfids in the map in order of lfid. The pfids are appended to the supplied vector. Only
        * as many headers as are in the map are appended.
        * NOTE: will clear any contents from supplied vector before appending pfid list.
        */
        void get_pfid_list(vector<u_int16_t>& pfid_list)
        {
            pfid_list.clear();
            for (lpmap_citr i = _map.begin(); i != _map.end(); i++)
                pfid_list.push_back(i->second._pfid);
        }

        /*
        * Get the list of lfids in the map. The lfids are appended to the supplied vector in the order they appear
        * in the map (which is not necessarily the natural or sorted order).
        * NOTE: will clear any contents from supplied vector before appending lfid list.
        */
        void get_lfid_list(vector<u_int16_t>& lfid_list)
        {
            lfid_list.clear();
            lfid_list.assign(_map.size(), 0);
            for (lpmap_citr i = _map.begin(); i != _map.end(); i++)
                lfid_list[i->second._pfid] = i->first;
        }

        /*
        * Method check_analysis(): Used to check the result of the test jinf object analysis by comparing the pfid order
        * array it produces against the internal map.
        *
        * ji: A ref to the jinf object under test.
        */
        void check_analysis(jinf& ji)   // jinf object under test after analyze() has been called
        {
            BOOST_CHECK_EQUAL(ji.get_first_pfid(), get_first_pfid());
            BOOST_CHECK_EQUAL(ji.get_last_pfid(), get_last_pfid());

            jinf::pfid_list& pfidl = ji.get_pfid_list();
            const u_int16_t num_jfiles = _map.size();
            const bool all_used = _num_used_files == num_jfiles;
            BOOST_CHECK_EQUAL(pfidl.size(), _num_used_files);

            const u_int16_t lfid_start = all_used ? _oldest_lfid : 0;
            // Because a simulated failure would leave lfid dups in map and last_fid would not exist in map in this
            // case, we must find lfid_stop via pfid instead. Search for pfid == num_files.
            lpmap_citr itr = _map.begin();
            while (itr != _map.end() && itr->second._pfid != _num_used_files - 1) itr++;
            if (itr == _map.end())
                BOOST_FAIL("check(): Unable to find pfid=" << (_num_used_files - 1) << " in map.");
            const u_int16_t lfid_stop = itr->second._lfid;

            std::size_t fidl_index = 0;
            for (u_int16_t lfid_cnt = lfid_start; lfid_cnt < lfid_stop; lfid_cnt++, fidl_index++)
            {
                const u_int16_t lfid = lfid_cnt % num_jfiles;
                lpmap_citr itr = _map.find(lfid);
                if (itr == _map.end())
                    BOOST_FAIL("check(): Unable to find lfid=" << lfid << " in map.");
                BOOST_CHECK_EQUAL(itr->second._pfid, pfidl[fidl_index]);
            }
        }

        /*
        * Method get_pfid(): Look up a pfid from a known lfid.
        */
        u_int16_t get_pfid(const u_int16_t lfid, const bool initial_owi = false)
        {
            switch (_map.count(lfid))
            {
                case 1:
                    return _map.find(lfid)->second._pfid;
                case 2:
                    for (lpmap_citr itr = _map.lower_bound(lfid); itr != _map.upper_bound(lfid); itr++)
                    {
                        if (itr->second.get_owi() != initial_owi)
                            return itr->second._pfid;
                    }
                default:;
            }
            BOOST_FAIL("get_pfid(): lfid=" << lfid << " not found in map.");
            return 0xffff;
        }

        /*
        * Method get_first_pfid(): Look up the first (oldest, or next-to-be-overwritten) pfid in the analysis sequence.
        */
        u_int16_t get_first_pfid()
        {
            return get_pfid(_oldest_lfid);
        }

        /*
        * Method get_last_pfid(): Look up the last (newest, or most recently written) pfid in the analysis sequence.
        */
        u_int16_t get_last_pfid()
        {
            u_int16_t flfid = 0;
            if (_num_used_files == _map.size()) // journal full?
            {
                if (_oldest_lfid)
                {
                    // if failed insert, cycle past duplicate lids
                    while (_map.count(_oldest_lfid) == 2)
                        _oldest_lfid++;
                    while (_map.find(_oldest_lfid) != _map.end() && _map.find(_oldest_lfid)->second.get_owi() == false)
                        _oldest_lfid++;
                    flfid = _oldest_lfid - 1;
                }
                else
                    flfid = _map.size() - 1;
            }
            else
                flfid = _num_used_files - 1;
            return get_pfid(flfid, true);
        }

        /*
        * Method write_journal(): Used to create the dummy journal files from the built-up map created by calling
        * initial_journal_create() and optionally journal_append() one or more times. Since the jinf object reads the
        * jinf file and the file headers only, the create object creates a dummy journal file containing only a file
        * header (512 bytes each) and a single jinf file which contains the journal metadata required for recovery
        * analysis.
        */
        void write_journal(const bool ae, const u_int16_t ae_max_jfiles, const u_int32_t fsize_sblks = JFSIZE_SBLKS)
        {
            create_jinf(ae, ae_max_jfiles);
            u_int16_t pfid = 0;
            for (lpmap_citr itr = _map.begin(); itr != _map.end(); itr++, pfid++)
            {
                if (itr->second._pfid == 0 && itr->second._magic == 0) // empty header, use pfid counter instead
                    create_journal_file(pfid, itr->second, _base_filename, fsize_sblks);
                else
                    create_journal_file(itr->second._pfid, itr->second, _base_filename, fsize_sblks);
            }
        }

        /*
        * Method destroy_journal(): Destroy the files created by create_journal() and reset the lfid_pfid_map test
        * object. A new test may be started using the same lfid_pfid_map test object once this call has been made.
        */
        void destroy_journal()
        {
            for (u_int16_t pfid = 0; pfid < _map.size(); pfid++)
            {
                string fn = create_journal_filename(pfid, _base_filename);
                BOOST_WARN_MESSAGE(::unlink(fn.c_str()) == 0, "destroy_journal(): Failed to remove file " << fn);
            }
            clean_journal_info_file(_base_filename);
            _map.clear();
            _num_used_files = 0;
            _oldest_lfid = 0;
            _last_pfid = 0;
        }

        /*
        * Method create_new_jinf(): This static call creates a default jinf file only. This is used to test the read
        * constructor of a jinf test object which reads a jinf file at instantiation.
        */
        static void create_new_jinf(const string jid, const string base_filename, const bool ae)
        {
            if (jdir::exists(test_dir))
                jdir::delete_dir(test_dir);
            create_jinf(NUM_JFILES, ae, (ae ? 5 * NUM_JFILES : 0), jid, base_filename);
        }

        /*
        * Method clean_journal_info_file(): This static method deletes only a jinf file without harming any other
        * journal file or its directory. This is used to clear those tests which rely only on the existence of a
        * jinf file.
        */
        static void clean_journal_info_file(const string base_filename)
        {
            stringstream fn;
            fn << test_dir << "/" << base_filename << "." << JRNL_INFO_EXTENSION;
            BOOST_WARN_MESSAGE(::unlink(fn.str().c_str()) == 0, "clean_journal_info_file(): Failed to remove file " <<
                            fn.str());
        }

        static string create_journal_filename(const u_int16_t pfid, const string base_filename)
        {
            stringstream fn;
            fn << test_dir << "/" << base_filename << ".";
            fn << setfill('0') << hex << setw(4) << pfid << "." << JRNL_DATA_EXTENSION;
            return fn.str();
        }

    private:
        static void init_fhdr(file_hdr& fh,
                              const u_int64_t frid,
                              const u_int16_t pfid,
                              const u_int16_t lfid,
                              const bool owi,
                              const bool no_enq = false)
        {
            fh._magic = RHM_JDAT_FILE_MAGIC;
            fh._version = RHM_JDAT_VERSION;
#if defined(JRNL_BIG_ENDIAN)
            fh._eflag = RHM_BENDIAN_FLAG;
#else
            fh._eflag = RHM_LENDIAN_FLAG;
#endif
            fh._uflag = owi ? rec_hdr::HDR_OVERWRITE_INDICATOR_MASK : 0;
            fh._rid = frid;
            fh._pfid = pfid;
            fh._lfid = lfid;
            fh._fro = no_enq ? 0 : 0x200;
            timespec ts;
            ::clock_gettime(CLOCK_REALTIME, &ts);
            fh._ts_sec = ts.tv_sec;
            fh._ts_nsec = ts.tv_nsec;
        }

        void create_jinf(const bool ae, const u_int16_t ae_max_jfiles)
        {
            if (jdir::exists(test_dir))
                jdir::delete_dir(test_dir);
            create_jinf(_map.size(), ae, ae_max_jfiles, _jid, _base_filename);
        }

        static void create_jinf(u_int16_t num_files, const bool ae, const u_int16_t ae_max_jfiles, const string jid,
                const string base_filename)
        {
            jdir::create_dir(test_dir); // Check test dir exists; create it if not
            timespec ts;
            ::clock_gettime(CLOCK_REALTIME, &ts);
            jinf ji(jid, test_dir, base_filename, num_files, ae, ae_max_jfiles, JFSIZE_SBLKS, JRNL_WMGR_DEF_PAGE_SIZE,
                    JRNL_WMGR_DEF_PAGES, ts);
            ji.write();
        }

        static void create_journal_file(const u_int16_t pfid,
                                        const file_hdr& fh,
                                        const string base_filename,
                                        const u_int32_t fsize_sblks = JFSIZE_SBLKS,
                                        const char fill_char = 0)
        {
            const std::string filename = create_journal_filename(pfid, base_filename);
            ofstream of(filename.c_str(), ofstream::out | ofstream::trunc);
            if (!of.good())
                BOOST_FAIL("Unable to open test journal file \"" << filename << "\" for writing.");

            write_file_header(filename, of, fh, fill_char);
            write_file_body(of, fsize_sblks, fill_char);

            of.close();
            if (of.fail() || of.bad())
                BOOST_FAIL("Error closing test journal file \"" << filename << "\".");
        }

        static void write_file_header(const std::string& filename,
                                      ofstream& of,
                                      const file_hdr& fh,
                                      const char fill_char)
        {
            // write file header
            u_int32_t cnt = sizeof(file_hdr);
            of.write((const char*)&fh, cnt);
            if (of.fail() || of.bad())
                BOOST_FAIL("Error writing file header to test journal file \"" << filename << "\".");

            // fill remaining sblk with fill char
            while (cnt++ < JRNL_DBLK_SIZE * JRNL_SBLK_SIZE)
            {
                of.put(fill_char);
                if (of.fail() || of.bad())
                    BOOST_FAIL("Error writing filler to test journal file \"" << filename << "\".");
            }
        }

        static void write_file_body(ofstream& of, const u_int32_t fsize_sblks, const char fill_char)
        {
            if (fsize_sblks > 1)
            {
                std::vector<char> sblk_buffer(JRNL_DBLK_SIZE * JRNL_SBLK_SIZE, fill_char);
                u_int32_t fwritten_sblks = 0; // hdr
                while (fwritten_sblks++ < fsize_sblks)
                    of.write(&sblk_buffer[0], JRNL_DBLK_SIZE * JRNL_SBLK_SIZE);
            }
        }
};

const string
get_test_name(const string& file, const string& test_name)
{
    cout << test_filename << "." << test_name << ": " << flush;
    return file + "." + test_name;
}

bool
check_iores(const string& ctxt, const iores ret, const iores exp_ret, test_dtok* dtp)
{
    if (ret != exp_ret)
    {
        delete dtp;
        BOOST_FAIL(ctxt << ": Expected " << iores_str(exp_ret) << "; got " << iores_str(ret));
    }
    return false;
}

bool
handle_jcntl_response(const iores res, jcntl& jc, unsigned& aio_sleep_cnt, const std::string& ctxt, const iores exp_ret,
                test_dtok* dtp)
{
    if (res == RHM_IORES_PAGE_AIOWAIT)
    {
        if (++aio_sleep_cnt <= MAX_AIO_SLEEPS)
        {
            jc.get_wr_events(0); // *** GEV2
            usleep(AIO_SLEEP_TIME);
        }
        else
            return check_iores(ctxt, res, exp_ret, dtp);
    }
    else
        return check_iores(ctxt, res, exp_ret, dtp);
    return true;
}

u_int64_t
enq_msg(jcntl& jc,
        const u_int64_t rid,
        const string& msg,
        const bool transient,
        const iores exp_ret = RHM_IORES_SUCCESS)
{
    ostringstream ctxt;
    ctxt << "enq_msg(" << rid << ")";
    test_dtok* dtp = new test_dtok;
    BOOST_CHECK_MESSAGE(dtp != 0, "Data token allocation failed (dtp == 0).");
    dtp->set_rid(rid);
    dtp->set_external_rid(true);
    try
    {
        iores res = jc.enqueue_data_record(msg.c_str(), msg.size(), msg.size(), dtp, transient);
        check_iores(ctxt.str(), res, exp_ret, dtp);
        u_int64_t dtok_rid = dtp->rid();
        if (dtp->done()) delete dtp;
        return dtok_rid;
    }
    catch (exception& e) { delete dtp; throw; }
}

u_int64_t
enq_extern_msg(jcntl& jc, const u_int64_t rid, const std::size_t msg_size, const bool transient,
        const iores exp_ret = RHM_IORES_SUCCESS)
{
    ostringstream ctxt;
    ctxt << "enq_extern_msg(" << rid << ")";
    test_dtok* dtp = new test_dtok;
    BOOST_CHECK_MESSAGE(dtp != 0, "Data token allocation failed (dtp == 0).");
    dtp->set_rid(rid);
    dtp->set_external_rid(true);
    try
    {
    iores res = jc.enqueue_extern_data_record(msg_size, dtp, transient);
    check_iores(ctxt.str(), res, exp_ret, dtp);
    u_int64_t dtok_rid = dtp->rid();
    if (dtp->done()) delete dtp;
    return dtok_rid;
    }
    catch (exception& e) { delete dtp; throw; }
}

u_int64_t
enq_txn_msg(jcntl& jc, const u_int64_t rid, const string& msg, const string& xid, const bool transient,
                const iores exp_ret = RHM_IORES_SUCCESS)
{
    ostringstream ctxt;
    ctxt << "enq_txn_msg(" << rid << ")";
    test_dtok* dtp = new test_dtok;
    BOOST_CHECK_MESSAGE(dtp != 0, "Data token allocation failed (dtp == 0).");
    dtp->set_rid(rid);
    dtp->set_external_rid(true);
    try
    {
        iores res = jc.enqueue_txn_data_record(msg.c_str(), msg.size(), msg.size(), dtp, xid,
                transient);
        check_iores(ctxt.str(), res, exp_ret, dtp);
        u_int64_t dtok_rid = dtp->rid();
        if (dtp->done()) delete dtp;
        return dtok_rid;
    }
    catch (exception& e) { delete dtp; throw; }
}

u_int64_t
enq_extern_txn_msg(jcntl& jc, const u_int64_t rid, const std::size_t msg_size, const string& xid, const bool transient,
                const iores exp_ret = RHM_IORES_SUCCESS)
{
    ostringstream ctxt;
    ctxt << "enq_extern_txn_msg(" << rid << ")";
    test_dtok* dtp = new test_dtok;
    BOOST_CHECK_MESSAGE(dtp != 0, "Data token allocation failed (dtp == 0).");
    dtp->set_rid(rid);
    dtp->set_external_rid(true);
    try
    {
        iores res = jc.enqueue_extern_txn_data_record(msg_size, dtp, xid, transient);
        check_iores(ctxt.str(), res, exp_ret, dtp);
        u_int64_t dtok_rid = dtp->rid();
        if (dtp->done()) delete dtp;
        return dtok_rid;
    }
    catch (exception& e) { delete dtp; throw; }
}

u_int64_t
deq_msg(jcntl& jc, const u_int64_t drid, const u_int64_t rid, const iores exp_ret = RHM_IORES_SUCCESS)
{
    ostringstream ctxt;
    ctxt << "deq_msg(" << drid << ")";
    test_dtok* dtp = new test_dtok;
    BOOST_CHECK_MESSAGE(dtp != 0, "Data token allocation failed (dtp == 0).");
    dtp->set_rid(rid);
    dtp->set_dequeue_rid(drid);
    dtp->set_external_rid(true);
    dtp->set_wstate(data_tok::ENQ);
    try
    {
        iores res = jc.dequeue_data_record(dtp);
        check_iores(ctxt.str(), res, exp_ret, dtp);
        u_int64_t dtok_rid = dtp->rid();
        if (dtp->done()) delete dtp;
        return dtok_rid;
    }
    catch (exception& e) { delete dtp; throw; }
}

u_int64_t
deq_txn_msg(jcntl& jc, const u_int64_t drid, const u_int64_t rid, const string& xid,
                const iores exp_ret = RHM_IORES_SUCCESS)
{
    ostringstream ctxt;
    ctxt << "deq_txn_msg(" << drid << ")";
    test_dtok* dtp = new test_dtok;
    BOOST_CHECK_MESSAGE(dtp != 0, "Data token allocation failed (dtp == 0).");
    dtp->set_rid(rid);
    dtp->set_dequeue_rid(drid);
    dtp->set_external_rid(true);
    dtp->set_wstate(data_tok::ENQ);
    try
    {
        iores res = jc.dequeue_txn_data_record(dtp, xid);
        check_iores(ctxt.str(), res, exp_ret, dtp);
        u_int64_t dtok_rid = dtp->rid();
        if (dtp->done()) delete dtp;
        return dtok_rid;
    }
    catch (exception& e) { delete dtp; throw; }
}

u_int64_t
txn_abort(jcntl& jc, const u_int64_t rid, const string& xid, const iores exp_ret = RHM_IORES_SUCCESS)
{
    test_dtok* dtp = new test_dtok;
    BOOST_CHECK_MESSAGE(dtp != 0, "Data token allocation failed (dtp == 0).");
    dtp->set_rid(rid);
    dtp->set_external_rid(true);
    try
    {
        iores res = jc.txn_abort(dtp, xid);
        check_iores("txn_abort", res, exp_ret, dtp);
        u_int64_t dtok_rid = dtp->rid();
        if (dtp->done()) delete dtp;
        return dtok_rid;
    }
    catch (exception& e) { delete dtp; throw; }
}

u_int64_t
txn_commit(jcntl& jc, const u_int64_t rid, const string& xid, const iores exp_ret = RHM_IORES_SUCCESS)
{
    test_dtok* dtp = new test_dtok;
    BOOST_CHECK_MESSAGE(dtp != 0, "Data token allocation failed (dtp == 0).");
    dtp->set_rid(rid);
    dtp->set_external_rid(true);
    try
    {
        iores res = jc.txn_commit(dtp, xid);
        check_iores("txn_commit", res, exp_ret, dtp);
        u_int64_t dtok_rid = dtp->rid();
        if (dtp->done()) delete dtp;
        return dtok_rid;
    }
    catch (exception& e) { delete dtp; throw; }
}

void
read_msg(jcntl& jc, string& msg, string& xid, bool& transient, bool& external, const iores exp_ret = RHM_IORES_SUCCESS)
{
    void* mp = 0;
    std::size_t msize = 0;
    void* xp = 0;
    std::size_t xsize = 0;
    test_dtok* dtp = new test_dtok;
    BOOST_CHECK_MESSAGE(dtp != 0, "Data token allocation failed (dtp == 0).");
    dtp->set_wstate(data_tok::ENQ);

    unsigned aio_sleep_cnt = 0;
    try
    {
        iores res = jc.read_data_record(&mp, msize, &xp, xsize, transient, external, dtp);
        while (handle_jcntl_response(res, jc, aio_sleep_cnt, "read_msg", exp_ret, dtp))
            res = jc.read_data_record(&mp, msize, &xp, xsize, transient, external, dtp);
    }
    catch (exception& e) { delete dtp; throw; }

    if (mp)
        msg.assign((char*)mp, msize);
    if (xp)
    {
        xid.assign((char*)xp, xsize);
        std::free(xp);
        xp = 0;
    }
    else if (mp)
    {
        std::free(mp);
        mp = 0;
    }
    delete dtp;
}

/*
 * Returns the number of messages of size msg_rec_size_dblks that will fit into an empty journal with or without
 * corresponding dequeues (controlled by include_deq) without a threshold - ie until the journal is full. Assumes
 * that dequeue records fit into one dblk.
 */
u_int32_t
num_msgs_to_full(const u_int16_t num_files, const u_int32_t file_size_dblks, const u_int32_t msg_rec_size_dblks,
                bool include_deq)
{
    u_int32_t rec_size_dblks = msg_rec_size_dblks;
    if (include_deq)
        rec_size_dblks++;
    return u_int32_t(::floor(1.0 * num_files * file_size_dblks / rec_size_dblks));
}

/*
 * Returns the number of messages of size msg_rec_size_dblks that will fit into an empty journal before the enqueue
 * threshold of JRNL_ENQ_THRESHOLD (%).
 */
u_int32_t
num_msgs_to_threshold(const u_int16_t num_files, const u_int32_t file_size_dblks, const u_int32_t msg_rec_size_dblks)
{
    return u_int32_t(::floor(1.0 * num_files * file_size_dblks * JRNL_ENQ_THRESHOLD / msg_rec_size_dblks / 100));
}

/*
 * Returns the amount of space reserved in dblks (== num dequeues assuming dequeue size of 1 dblk) for the enqueue
 * threshold of JRNL_ENQ_THRESHOLD (%).
 */
u_int32_t
num_dequeues_rem(const u_int16_t num_files, const u_int32_t file_size_dblks)
{
    /*
     *  Fraction of journal remaining after threshold is used --------------+
     *  Total no. dblks in journal ------+                                  |
     *                                   |                                  |
     *                      +------------+------------+   +-----------------+---------------------+
     */
    return u_int32_t(::ceil(num_files * file_size_dblks * (1.0 - (1.0 * JRNL_ENQ_THRESHOLD / 100))));
}

string&
create_msg(string& s, const int msg_num, const int len)
{
    ostringstream oss;
    oss << "MSG_" << setfill('0') << setw(6) << msg_num << "_";
    for (int i=12; i<=len; i++)
        oss << (char)('0' + i%10);
    s.assign(oss.str());
    return s;
}

string&
create_xid(string& s, const int msg_num, const int len)
{
    ostringstream oss;
    oss << "XID_" << setfill('0') << setw(6) << msg_num << "_";
    for (int i=11; i<len; i++)
        oss << (char)('a' + i%26);
    s.assign(oss.str());
    return s;
}

long
get_seed()
{
    timespec ts;
    if (::clock_gettime(CLOCK_REALTIME, &ts))
        BOOST_FAIL("Unable to read clock to generate seed.");
    long tenths = ts.tv_nsec / 100000000;
    return long(10 * ts.tv_sec + tenths); // time in tenths of a second
}
