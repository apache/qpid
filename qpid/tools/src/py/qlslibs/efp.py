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
Module: qlslibs.efp

Contains empty file pool (EFP) classes.
"""

import os
import os.path
import qlslibs.err
import shutil
import uuid

class EfpManager(object):
    """
    Top level class to analyze the Qpid Linear Store (QLS) directory for the partitions that make up the
    Empty File Pool (EFP).
    """
    def __init__(self, directory, disk_space_required_kb):
        if not os.path.exists(directory):
            raise qlslibs.err.InvalidQlsDirectoryNameError(directory)
        self.directory = directory
        self.disk_space_required_kb = disk_space_required_kb
        self.efp_partitions = []
        self.efp_pools = {}
        self.total_num_files = 0
        self.total_cum_file_size_kb = 0
        self.current_efp_partition = None
    def add_file_pool(self, file_size_kb, num_files):
        """ Add an EFP in the specified partition of the specified size containing the specified number of files """
        dir_name = EmptyFilePool.get_directory_name(file_size_kb)
        print 'Adding pool \'%s\' to partition %s' % (dir_name, self.current_efp_partition.partition_number)
        self.total_cum_file_size_kb += self.current_efp_partition.create_new_efp(file_size_kb, num_files)
        self.total_num_files += num_files
    def freshen_file_pool(self, file_size_kb, num_files):
        """ Freshen an EFP in the specified partition and of the specified size to the specified number of files """
        if self.current_efp_partition is None:
            partition_list = self.efp_partitions
            partition_str = 'all partitions'
        else:
            partition_list = [self.current_efp_partition]
            partition_str = 'partition %d' % self.current_efp_partition.partition_number
        if file_size_kb is None:
            pool_str = 'all pools'
        else:
            pool_str = 'pool \'%s\'' % EmptyFilePool.get_directory_name(int(file_size_kb))
        print 'Freshening %s in %s to %d files' % (pool_str, partition_str, num_files)
        for self.current_efp_partition in partition_list: # Partition objects
            if file_size_kb is None:
                file_size_list = self.current_efp_partition.efp_pools.keys()
            else:
                file_size_list = ['%sk' % file_size_kb]
            for file_size in file_size_list:
                efp = self.current_efp_partition.efp_pools[file_size]
                num_files_needed = num_files - efp.get_tot_file_count()
                if num_files_needed > 0:
                    self.current_efp_partition.create_new_efp_files(qlslibs.utils.efp_directory_size(file_size),
                                                                    num_files_needed)
                else:
                    print '  WARNING: Pool %s in partition %s already contains %d files: no action taken' % \
                          (self.current_efp_partition.efp_pools[file_size].size_str,
                           self.current_efp_partition.partition_number, efp.get_num_files())
    def remove_file_pool(self, file_size_kb):
        """ Remove an existing EFP from the specified partition and of the specified size """
        dir_name = EmptyFilePool.get_directory_name(file_size_kb)
        print 'Removing pool \'%s\' from partition %s' % (dir_name, self.current_efp_partition.partition_number)
        self.efp_partitions.remove(self.current_efp_partition)
        shutil.rmtree(os.path.join(self.current_efp_partition.efp_directory, dir_name))
    def report(self):
        print 'Empty File Pool (EFP) report'
        print '============================'
        print 'Found', len(self.efp_partitions), 'partition(s)'
        if (len(self.efp_partitions)) > 0:
            sorted_efp_partitions = sorted(self.efp_partitions, key=lambda x: x.partition_number)
            EfpPartition.print_report_table_header()
            for ptn in sorted_efp_partitions:
                ptn.print_report_table_line()
            print
            for ptn in sorted_efp_partitions:
                ptn.report()
    def run(self, arg_tup):
        self._analyze_efp()
        if arg_tup is not None:
            _, arg_file_size, arg_num_files, arg_add, arg_remove, arg_freshen, arg_list = arg_tup
            self._check_args(arg_tup)
            if arg_add:
                self.add_file_pool(int(arg_file_size), int(arg_num_files))
            if arg_remove:
                self.remove_file_pool(int(arg_file_size))
            if arg_freshen:
                self.freshen_file_pool(arg_file_size, int(arg_num_files))
            if arg_list:
                self.report()
    def _analyze_efp(self):
        for dir_entry in os.listdir(self.directory):
            try:
                efp_partition = EfpPartition(os.path.join(self.directory, dir_entry), self.disk_space_required_kb)
                efp_partition.scan()
                self.efp_partitions.append(efp_partition)
                for efpl in efp_partition.efp_pools.iterkeys():
                    if efpl not in self.efp_pools:
                        self.efp_pools[efpl] = []
                    self.efp_pools[efpl].append(efp_partition.efp_pools[efpl])
                self.total_num_files += efp_partition.tot_file_count
                self.total_cum_file_size_kb += efp_partition.tot_file_size_kb
            except qlslibs.err.InvalidPartitionDirectoryNameError:
                pass
    def _check_args(self, arg_tup):
        """ Value check of args. The names of partitions and pools are validated against the discovered instances """
        arg_partition, arg_file_size, _, arg_add, arg_remove, arg_freshen, _ = arg_tup
        if arg_partition is not None:
            try:
                if arg_partition[0] == 'p': # string partition name, eg 'p001'
                    partition_num = int(arg_partition[1:])
                else: # numeric partition, eg '1'
                    partition_num = int(arg_partition)
                found = False
                for partition in self.efp_partitions:
                    if partition.partition_number == partition_num:
                        self.current_efp_partition = partition
                        found = True
                        break
                if not found:
                    raise qlslibs.err.PartitionDoesNotExistError(arg_partition)
            except ValueError:
                raise qlslibs.err.InvalidPartitionDirectoryNameError(arg_partition)
        if self.current_efp_partition is not None:
            pool_list = self.current_efp_partition.efp_pools.keys()
            efp_directory_name = EmptyFilePool.get_directory_name(int(arg_file_size))
            if arg_add and efp_directory_name in pool_list:
                raise qlslibs.err.PoolDirectoryAlreadyExistsError(efp_directory_name)
            if (arg_remove or arg_freshen) and efp_directory_name not in pool_list:
                raise qlslibs.err.PoolDirectoryDoesNotExistError(efp_directory_name)

class EfpPartition(object):
    """
    Class that represents a EFP partition. Each partition contains one or more Empty File Pools (EFPs).
    """
    PTN_DIR_PREFIX = 'p'
    EFP_DIR_NAME = 'efp'
    def __init__(self, directory, disk_space_required_kb):
        self.directory = directory
        self.partition_number = None
        self.efp_pools = {}
        self.tot_file_count = 0
        self.tot_file_size_kb = 0
        self._validate_partition_directory(disk_space_required_kb)
    def create_new_efp_files(self, file_size_kb, num_files):
        """ Create new EFP files in this partition """
        dir_name = EmptyFilePool.get_directory_name(file_size_kb)
        if dir_name in self.efp_pools.keys():
            efp = self.efp_pools[dir_name]
        else:
            efp = EmptyFilePool(os.path.join(self.directory, EfpPartition.EFP_DIR_NAME), dir_name)
        this_tot_file_size_kb = efp.create_new_efp_files(num_files)
        self.tot_file_size_kb += this_tot_file_size_kb
        self.tot_file_count += num_files
        return this_tot_file_size_kb
    @staticmethod
    def print_report_table_header():
        print 'p_no no_efp tot_files tot_size_kb directory'
        print '---- ------ --------- ----------- ---------'
    def print_report_table_line(self):
        print '%4d %6d %9d %11d %s' % (self.partition_number, len(self.efp_pools), self.tot_file_count,
                                       self.tot_file_size_kb, self.directory)
    def report(self):
        print 'Partition %s:' % os.path.basename(self.directory)
        if len(self.efp_pools) > 0:
            EmptyFilePool.print_report_table_header()
            for dir_name in self.efp_pools.keys():
                self.efp_pools[dir_name].print_report_table_line()
        else:
            print '<empty - no EFPs found in this partition>'
        print
    def scan(self):
        if os.path.exists(self.directory):
            efp_dir = os.path.join(self.directory, EfpPartition.EFP_DIR_NAME)
            for dir_entry in os.listdir(efp_dir):
                efp = EmptyFilePool(os.path.join(efp_dir, dir_entry), self.partition_number)
                efp.scan()
                self.tot_file_count += efp.get_tot_file_count()
                self.tot_file_size_kb += efp.get_tot_file_size_kb()
                self.efp_pools[dir_entry] = efp
    def _validate_partition_directory(self, disk_space_required_kb):
        if os.path.basename(self.directory)[0] is not EfpPartition.PTN_DIR_PREFIX:
            raise qlslibs.err.InvalidPartitionDirectoryNameError(self.directory)
        try:
            self.partition_number = int(os.path.basename(self.directory)[1:])
        except ValueError:
            raise qlslibs.err.InvalidPartitionDirectoryNameError(self.directory)
        if not qlslibs.utils.has_write_permission(self.directory):
            raise qlslibs.err.WritePermissionError(self.directory)
        if disk_space_required_kb is not None:
            space_avail = qlslibs.utils.get_avail_disk_space(self.directory)
            if space_avail < (disk_space_required_kb * 1024):
                raise qlslibs.err.InsufficientSpaceOnDiskError(self.directory, space_avail,
                                                               disk_space_required_kb * 1024)

class EmptyFilePool(object):
    """
    Class that represents a single Empty File Pool within a partition. Each EFP contains pre-formatted linear store
    journal files (but it may also be empty).
    """
    EFP_DIR_SUFFIX = 'k'
    EFP_JRNL_EXTENTION = '.jrnl'
    EFP_INUSE_DIRNAME = 'in_use'
    EFP_RETURNED_DIRNAME = 'returned'
    def __init__(self, directory, partition_number):
        self.base_dir_name = os.path.basename(directory)
        self.directory = directory
        self.partition_number = partition_number
        self.data_size_kb = None
        self.efp_files = []
        self.in_use_files = []
        self.returned_files = []
        self._validate_efp_directory()
    def create_new_efp_files(self, num_files):
        """ Create one or more new empty journal files of the prescribed size for this EFP """
        this_total_file_size = 0
        for _ in range(num_files):
            this_total_file_size += self._create_new_efp_file()
        return this_total_file_size
    def get_directory(self):
        return self.directory
    @staticmethod
    def get_directory_name(file_size_kb):
        """ Static function to create an EFP directory name from the size of the files it contains """
        return '%dk' % file_size_kb
    def get_tot_file_count(self):
        return len(self.efp_files)
    def get_tot_file_size_kb(self):
        return self.data_size_kb * len(self.efp_files)
    @staticmethod
    def print_report_table_header():
        print '             ---------- efp ------------ --------- in_use ---------- -------- returned ---------'
        print 'data_size_kb file_count tot_file_size_kb file_count tot_file_size_kb file_count tot_file_size_kb efp_directory'
        print '------------ ---------- ---------------- ---------- ---------------- ---------- ---------------- -------------'
    def print_report_table_line(self):
        print '%12d %10d %16d %10d %16d %10d %16d %s' % (self.data_size_kb, len(self.efp_files),
                                                         self.data_size_kb * len(self.efp_files),
                                                         len(self.in_use_files),
                                                         self.data_size_kb * len(self.in_use_files),
                                                         len(self.returned_files),
                                                         self.data_size_kb * len(self.returned_files),
                                                         self.get_directory())
    def scan(self):
        for efp_file in os.listdir(self.directory):
            if efp_file == self.EFP_INUSE_DIRNAME:
                for in_use_file in os.listdir(os.path.join(self.directory, self.EFP_INUSE_DIRNAME)):
                    self.in_use_files.append(in_use_file)
                continue
            if efp_file == self.EFP_RETURNED_DIRNAME:
                for returned_file in os.listdir(os.path.join(self.directory, self.EFP_RETURNED_DIRNAME)):
                    self.returned_files.append(returned_file)
                continue
            if self._validate_efp_file(os.path.join(self.directory, efp_file)):
                self.efp_files.append(efp_file)
    def _add_efp_file(self, efp_file_name):
        """ Add a single journal file of the appropriate size to this EFP. No file size check is made here. """
        self.efp_files.append(efp_file_name)
    def _create_new_efp_file(self):
        """ Create a single new empty journal file of the prescribed size for this EFP """
        file_name = str(uuid.uuid4()) + EmptyFilePool.EFP_JRNL_EXTENTION
        file_header = qlslibs.jrnl.FileHeader(0, qlslibs.jrnl.FileHeader.MAGIC, qlslibs.utils.DEFAULT_RECORD_VERSION,
                                              0, 0, 0)
        file_header.init(None, None, qlslibs.utils.DEFAULT_HEADER_SIZE_SBLKS, self.partition_number, self.data_size_kb,
                         0, 0, 0, 0, 0)
        efh = file_header.encode()
        efh_bytes = len(efh)
        file_handle = open(os.path.join(self.directory, file_name), 'wb')
        file_handle.write(efh)
        file_handle.write('\xff' * (qlslibs.utils.DEFAULT_SBLK_SIZE - efh_bytes))
        file_handle.write('\x00' * (int(self.data_size_kb) * 1024))
        file_handle.close()
        fqfn = os.path.join(self.directory, file_name)
        self._add_efp_file(fqfn)
        return os.path.getsize(fqfn)
    def _validate_efp_directory(self):
        if self.base_dir_name[-1] is not EmptyFilePool.EFP_DIR_SUFFIX:
            raise qlslibs.err.InvalidEfpDirectoryNameError(self.directory)
        try:
            self.data_size_kb = int(os.path.basename(self.base_dir_name)[:-1])
        except ValueError:
            raise qlslibs.err.InvalidEfpDirectoryNameError(self.directory)
    def _validate_efp_file(self, efp_file):
        file_size = os.path.getsize(efp_file)
        expected_file_size = (self.data_size_kb * 1024) + qlslibs.utils.DEFAULT_SBLK_SIZE
        if file_size != expected_file_size:
            print 'WARNING: File %s not of correct size (size=%d, expected=%d): Ignoring' % (efp_file, file_size,
                                                                                             expected_file_size)
            return False
        file_handle = open(efp_file)
        args = qlslibs.utils.load_args(file_handle, qlslibs.jrnl.RecordHeader)
        file_hdr = qlslibs.jrnl.FileHeader(*args)
        file_hdr.init(file_handle, *qlslibs.utils.load_args(file_handle, qlslibs.jrnl.FileHeader))
        if not file_hdr.is_header_valid(file_hdr):
            file_handle.close()
            return False
        file_hdr.load(file_handle)
        file_handle.close()
        if not file_hdr.is_valid(True):
            return False
        return True


# =============================================================================

if __name__ == "__main__":
    print "This is a library, and cannot be executed."
