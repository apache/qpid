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

/**
 * \file lpmgr.h
 *
 * Qpid asynchronous store plugin library
 *
 * Class mrg::journal::lpmgr. See class documentation for details.
 *
 * \author Kim van der Riet
 */

#ifndef QPID_LEGACYSTORE_JRNL_LPMGR_H
#define QPID_LEGACYSTORE_JRNL_LPMGR_H

namespace mrg
{
namespace journal
{
    class jcntl;
    class lpmgr;
}
}

#include "qpid/legacystore/jrnl/fcntl.h"
#include <vector>

namespace mrg
{
namespace journal
{

    /**
    * \brief LFID-PFID manager. This class maps the logical file id (lfid) to the physical file id (pfid) so that files
    * may be inserted into the file ring buffer in (nearly) arbitrary logical locations while the physical ids continue
    * to be appended. NOTE: NOT THREAD SAFE.
    *
    * The entire functionality of the LFID-PFID manager is to maintain an array of pointers to fcntl objects which have
    * a one-to-one relationship to the physical %journal files. The logical file id (lfid) is used as an index to the
    * array to read the mapped physical file id (pfid). By altering the order of these pointers within the array, the
    * mapping of logical to physical files may be altered. This can be used to allow for the logical insertion of
    * %journal files into a ring buffer, even though the physical file ids must be appended to those that preceded them.
    *
    * Since the insert() operation uses after-lfid as its position parameter, it is not possible to insert before lfid
    * 0 - i.e. It is only possible to insert after an existing lfid. Consequently, lfid 0 and pfid 0 are always
    * coincident in a %journal. Note, however, that inserting before lfid 0 is logically equivilent to inserting after
    * the last lfid.
    *
    * When one or more files are inserted after a particular lfid, the lfids of the following files are incremented. The
    * pfids of the inserted files follow those of all existing files, thus leading to a lfid-pfid discreppancy (ie no
    * longer a one-to-one mapping):
    *
    * Example: Before insertion, %journal file headers would look as follows:
    * <pre>
    *          Logical view (sorted by lfid):               Physical view (sorted by pfid):
    *          +---+---+---+---+---+---+                    +---+---+---+---+---+---+
    * pfid --> | 0 | 1 | 2 | 3 | 4 | 5 |           pfid --> | 0 | 1 | 2 | 3 | 4 | 5 |
    * lfid --> | 0 | 1 | 2 | 3 | 4 | 5 |           lfid --> | 0 | 1 | 2 | 3 | 4 | 5 |
    *          +---+---+---+---+---+---+                    +---+---+---+---+---+---+
    * </pre>
    *
    * After insertion of 2 files after lid 2 (marked with *s):
    * <pre>
    *          Logical view (sorted by lfid):               Physical view (sorted by pfid):
    *          +---+---+---+---+---+---+---+---+            +---+---+---+---+---+---+---+---+
    * pfid --> | 0 | 1 | 2 |*6*|*7*| 3 | 4 | 5 |   pfid --> | 0 | 1 | 2 | 3 | 4 | 5 |*6*|*7*|
    * lfid --> | 0 | 1 | 2 |*3*|*4*| 5 | 6 | 7 |   lfid --> | 0 | 1 | 2 | 5 | 6 | 7 |*3*|*4*|
    *          +---+---+---+---+---+---+---+---+            +---+---+---+---+---+---+---+---+
    * </pre>
    *
    * The insert() function updates the internal map immediately, but the physical files (which have both the pfid and
    * lfid written into the file header) are only updated as they are overwritten in the normal course of enqueueing
    * and dequeueing messages. If the %journal should fail after insertion but before the files following those inserted
    * are overwritten, then duplicate lfids will be present (though no duplicate pfids are possible). The overwrite
    * indicator (owi) flag and the pfid numbers may be used to resolve the ambiguity and determine the logically earlier
    * lfid in this case.
    *
    * Example: Before insertion, the current active write file being lfid/pfid 2 as determined by the owi flag, %journal
    * file headers would look as follows:
    * <pre>
    *          Logical view (sorted by lfid):               Physical view (sorted by pfid):
    *          +---+---+---+---+---+---+                    +---+---+---+---+---+---+
    * pfid --> | 0 | 1 | 2 | 3 | 4 | 5 |           pfid --> | 0 | 1 | 2 | 3 | 4 | 5 |
    * lfid --> | 0 | 1 | 2 | 3 | 4 | 5 |           lfid --> | 0 | 1 | 2 | 3 | 4 | 5 |
    *  owi --> | t | t | t | f | f | f |            owi --> | t | t | t | f | f | f |
    *          +---+---+---+---+---+---+                    +---+---+---+---+---+---+
    * </pre>
    *
    * After inserting 2 files after lfid 2 and then 3 (the newly inserted file) - marked with *s:
    * <pre>
    *          Logical view (sorted by lfid):               Physical view (sorted by pfid):
    *          +---+---+---+---+---+---+---+---+            +---+---+---+---+---+---+---+---+
    * pfid --> | 0 | 1 | 2 |*6*|*7*| 3 | 4 | 5 |   pfid --> | 0 | 1 | 2 | 3 | 4 | 5 |*3*|*4*|
    * lfid --> | 0 | 1 | 2 |*3*|*4*| 3 | 4 | 5 |   lfid --> | 0 | 1 | 2 | 3 | 4 | 5 |*3*|*4*|
    *  owi --> | t | t | t | t | t | f | f | f |    owi --> | t | t | t | f | f | f | t | t |
    *          +---+---+---+---+---+---+---+---+            +---+---+---+---+---+---+---+---+
    * </pre>
    *
    * If a broker failure occurs at this point, then there are two independent tests that may be made to resolve
    * duplicate lfids during recovery in such cases:
    * <ol>
    *   <li>The correct lfid has owi flag that matches that of pfid/lfid 0</li>
    *   <li>The most recently inserted (hence correct) lfid has pfids that are higher than the duplicate that was not
    *       overwritten</li>
    * </ol>
    *
    * NOTE: NOT THREAD SAFE. Provide external thread protection if used in multi-threaded environments.
    */
    class lpmgr
    {
    public:
        /**
        * \brief Function pointer to function that will create a new fcntl object and return its pointer.
        *
        * \param jcp        Pointer to jcntl instance from which journal file details will be obtained.
        * \param lfid       Logical file ID for new fcntl instance.
        * \param pfid       Physical file ID for file associated with new fcntl instance.
        * \param rdp        Pointer to rcvdat instance which conatins recovery information for new fcntl instance when
        *                   recovering an existing file, or null if a new file is to be created.
        */
        typedef fcntl* (new_obj_fn_ptr)(jcntl* const jcp,
                                        const u_int16_t lfid,
                                        const u_int16_t pfid,
                                        const rcvdat* const rdp);

    private:
        bool _ae;                       ///< Auto-expand mode
        u_int16_t _ae_max_jfiles;       ///< Max file count for auto-expansion; 0 = no limit
        std::vector<fcntl*> _fcntl_arr; ///< Array of pointers to fcntl objects

    public:
        lpmgr();
        virtual ~lpmgr();

        /**
        * \brief Initialize from scratch for a known number of %journal files. All lfid values are identical to pfid
        * values (which is normal before any inserts have occurred).
        *
        * \param num_jfiles Number of files to be created, and consequently the number of fcntl objects in array
        *                   _fcntl_arr.
        * \param ae         If true, allows auto-expansion; if false, disables auto-expansion.
        * \param ae_max_jfiles The maximum number of files allowed for auto-expansion. Cannot be lower than the current
        *                   number of files. However, a zero value disables the limit checks, and allows unlimited
        *                   expansion.
        * \param jcp        Pointer to jcntl instance. This is used to find the file path and base filename so that
        *                   new files may be created.
        * \param fp         Pointer to function which creates and returns a pointer to a new fcntl object (and hence
        *                   causes a new %journal file to be created).
        */
        void initialize(const u_int16_t num_jfiles,
                        const bool ae,
                        const u_int16_t ae_max_jfiles,
                        jcntl* const jcp,
                        new_obj_fn_ptr fp);

        /**
        * \brief Initialize from a known lfid-pfid map pfid_list (within rcvdat param rd), which is usually obtained
        * from a recover. The index of pfid_list is the logical file id (lfid); the value contained in the vector is
        * the physical file id (pfid).
        *
        * \param rd         Ref to rcvdat struct which contains recovery data and the pfid_list.
        * \param jcp        Pointer to jcntl instance. This is used to find the file path and base filename so that
        *                   new files may be created.
        * \param fp         Pointer to function which creates and returns a pointer to a new fcntl object (and hence
        *                   causes a new %journal file to be created).
        */
        void recover(const rcvdat& rd,
                     jcntl* const jcp,
                     new_obj_fn_ptr fp);

        /**
        * \brief Insert num_jfiles files after lfid index after_lfid. This causes all lfids after after_lfid to be
        * increased by num_jfiles.
        *
        * Note that it is not possible to insert <i>before</i> lfid 0, and thus lfid 0 should always point to pfid 0.
        * Inserting before lfid 0 is logically equivilent to inserting after the last lfid in a circular buffer.
        *
        * \param after_lfid Lid index after which to insert file(s).
        * \param jcp        Pointer to jcntl instance. This is used to find the file path and base filename so that
        *                   new files may be created.
        * \param fp         Pointer to function which creates and returns a pointer to a new fcntl object (and hence
        *                   causes a new %journal file to be created).
        * \param num_jfiles The number of files by which to increase.
        */
        void insert(const u_int16_t after_lfid,
                    jcntl* const jcp,
                    new_obj_fn_ptr fp,
                    const u_int16_t num_jfiles = 1);

        /**
        * \brief Clears _fcntl_arr and deletes all fcntl instances.
        */
        void finalize();

        /**
        * \brief Returns true if initialized; false otherwise. After construction, will return false until initialize()
        * is called; thereafter true until finalize() is called, whereupon it will return false again.
        *
        * \return True if initialized; false otherwise.
        */
        inline bool is_init() const { return _fcntl_arr.size() > 0; }

        /**
        * \brief Returns true if auto-expand mode is enabled; false if not.
        *
        * \return True if auto-expand mode is enabled; false if not.
        */
        inline bool is_ae() const { return _ae; }

        /**
        * \brief Sets the auto-expand mode to enabled if ae is true, to disabled otherwise. The value of _ae_max_jfiles
        * must be valid to succeed (i.e. _ae_max_jfiles must be greater than the current number of files or be zero).
        *
        * \param ae         If true will enable auto-expand mode; if false will disable it.
        */
        void set_ae(const bool ae);

        /**
        * \brief Returns the number of %journal files, including any that were appended or inserted since
        * initialization.
        *
        * \return Number of %journal files if initialized; 0 otherwise.
        */
        inline u_int16_t num_jfiles() const { return static_cast<u_int16_t>(_fcntl_arr.size()); }

        /**
        * \brief Returns the maximum number of files allowed for auto-expansion.
        *
        * \return Maximum number of files allowed for auto-expansion. A zero value represents a disabled limit
        *   - i.e. unlimited expansion.
        */
        inline u_int16_t ae_max_jfiles() const { return _ae_max_jfiles; }

        /**
        * \brief Sets the maximum number of files allowed for auto-expansion. A zero value disables the limit.
        *
        * \param ae_max_jfiles The maximum number of files allowed for auto-expansion. Cannot be lower than the current
        *                   number of files. However, a zero value disables the limit checks, and allows unlimited
        *                   expansion.
        */
        void set_ae_max_jfiles(const u_int16_t ae_max_jfiles);

        /**
        * \brief Calculates the number of future files available for auto-expansion.
        *
        * \return The number of future files available for auto-expansion.
        */
        u_int16_t ae_jfiles_rem() const;

        /**
        * \brief Get a pointer to fcntl instance for a given lfid.
        *
        * \return Pointer to fcntl object corresponding to logical file id lfid, or 0 if lfid is out of range
        *   (greater than number of files in use).
        */
        inline fcntl* get_fcntlp(const u_int16_t lfid) const
                { if (lfid >= _fcntl_arr.size()) return 0; return _fcntl_arr[lfid]; }

        // Testing functions
        void get_pfid_list(std::vector<u_int16_t>& pfid_list) const;
        void get_lfid_list(std::vector<u_int16_t>& lfid_list) const;

    protected:

        /**
        * \brief Append num_jfiles files to the end of the logical and file id sequence. This is similar to extending
        * the from-scratch initialization.
        *
        * \param jcp        Pointer to jcntl instance. This is used to find the file path and base filename so that
        *                   new files may be created.
        * \param fp         Pointer to function which creates and returns a pointer to a new fcntl object (and hence
        *                   causes a new %journal file to be created).
        * \param num_jfiles The number of files by which to increase.
        */
        void append(jcntl* const jcp,
                    new_obj_fn_ptr fp,
                    const u_int16_t num_jfiles = 1);

    };

} // namespace journal
} // namespace mrg

#endif // ifndef QPID_LEGACYSTORE_JRNL_LPMGR_H
