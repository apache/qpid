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

import os
import os.path
import qls.err

class EfpManager(object):
    """
    Top level class to analyze the Qpid Linear Store (QLS) directory for the partitions that make up the
    Empty File Pool (EFP).
    """
    def __init__(self, directory):
        if not os.path.exists(directory):
            raise qls.err.InvalidQlsDirectoryNameError(directory)
        self.directory = directory
        self.partitions = []
    def report(self):
        print 'Found', len(self.partitions), 'partition(s).'
        if (len(self.partitions)) > 0:
            EfpPartition.print_report_table_header()
            for ptn in self.partitions:
                ptn.print_report_table_line()
            print
            for ptn in self.partitions:
                ptn.report()
    def run(self, args):
        for dir_entry in os.listdir(self.directory):
            try:
                efpp = EfpPartition(os.path.join(self.directory, dir_entry))
                efpp.scan()
                self.partitions.append(efpp)
            except qls.err.InvalidPartitionDirectoryNameError:
                pass

class EfpPartition(object):
    """
    Class that represents a EFP partition. Each partition contains one or more Empty File Pools (EFPs).
    """
    PTN_DIR_PREFIX = 'p'
    EFP_DIR_NAME = 'efp'
    def __init__(self, directory):
        self.base_dir = os.path.basename(directory)
        if self.base_dir[0] is not EfpPartition.PTN_DIR_PREFIX:
            raise qls.err.InvalidPartitionDirectoryNameError(directory)
        try:
            self.partition_number = int(self.base_dir[1:])
        except ValueError:
            raise qls.err.InvalidPartitionDirectoryNameError(directory)
        self.directory = directory
        self.pools = []
        self.efp_count = 0
        self.tot_file_count = 0
        self.tot_file_size_kb = 0
    def get_directory(self):
        return self.directory
    def get_efp_count(self):
        return self.efp_count
    def get_name(self):
        return self.base_dir
    def get_number(self):
        return self.partition_number
    def get_number_pools(self):
        return len(self.pools)
    def get_tot_file_count(self):
        return self.tot_file_count
    def get_tot_file_size_kb(self):
        return self.tot_file_size_kb
    @staticmethod
    def print_report_table_header():
        print 'p_no no_efp tot_files tot_size_kb directory'
        print '---- ------ --------- ----------- ---------'
    def print_report_table_line(self):
        print '%4d %6d %9d %11d %s' % (self.get_number(), self.get_efp_count(), self.get_tot_file_count(),
                                       self.get_tot_file_size_kb(), self.get_directory())
    def report(self):
        print 'Partition %s:' % self.base_dir
        EmptyFilePool.print_report_table_header()
        for pool in self.pools:
            pool.print_report_table_line()
        print
    def scan(self):
        if os.path.exists(self.directory):
            efp_dir = os.path.join(self.directory, EfpPartition.EFP_DIR_NAME)
            for dir_entry in os.listdir(efp_dir):
                efp = EmptyFilePool(os.path.join(efp_dir, dir_entry))
                self.efp_count += 1
                self.tot_file_count += efp.get_tot_file_count()
                self.tot_file_size_kb += efp.get_tot_file_size_kb()
                self.pools.append(efp)

class EmptyFilePool(object):
    """
    Class that represents a single Empty File Pool within a partition. Each EFP contains pre-formatted linear store
    journal files (but it may also be empty).
    """
    EFP_DIR_SUFFIX = 'k'
    def __init__(self, directory):
        self.base_dir = os.path.basename(directory)
        if self.base_dir[-1] is not EmptyFilePool.EFP_DIR_SUFFIX:
            raise qls.err.InvalidEfpDirectoryNameError(directory)
        try:
            self.data_size_kb = int(os.path.basename(self.base_dir)[:-1])
        except ValueError:
            raise qls.err.InvalidEfpDirectoryNameError(directory)
        self.directory = directory
        self.files = os.listdir(directory)
    def get_directory(self):
        return self.directory
    def get_file_data_size_kb(self):
        return self.data_size_kb
    def get_name(self):
        return self.base_dir
    def get_tot_file_count(self):
        return len(self.files)
    def get_tot_file_size_kb(self):
        return self.data_size_kb * len(self.files)
    @staticmethod
    def print_report_table_header():
        print 'file_size_kb file_count tot_file_size_kb efp_directory'
        print '------------ ---------- ---------------- -------------'
    def print_report_table_line(self):
        print '%12d %10d %16d %s' % (self.get_file_data_size_kb(), self.get_tot_file_count(),
                                     self.get_tot_file_size_kb(), self.get_directory())

# =============================================================================

if __name__ == "__main__":
    print "This is a library, and cannot be executed."
