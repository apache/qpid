#!/usr/bin/env python

"""
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
"""

import argparse
import os
from shutil import rmtree
from struct import pack
from uuid import uuid4

# Some "constants"
DEFAULT_SBLK_SIZE = 4096 # 32 dblks
DEFAULT_SBLK_SIZE_KB = DEFAULT_SBLK_SIZE / 1024
DEFAULT_EFP_DIR_NAME = 'efp'
DEFAULT_JRNL_EXTENTION = '.jrnl'

def get_directory_size(directory_name):
    ''' Decode the directory name in the format NNNk to a numeric size, where NNN is a number string '''
    try:
        if directory_name[-1] == 'k':
            return int(directory_name[:-1])
    except ValueError:
        pass
    return 0


class EfpToolError(StandardError):
    ''' Parent class for all errors used by efptool '''
    pass

class InvalidPartitionDirectoryError(EfpToolError):
    ''' Error class for invalid partition directory '''
    pass


class Header:
    ''' Abstract class for encoding the initial part of the header struct which is common across all journal headers '''
    FORMAT = '=4sBBHQ'
    HDR_VER = 2
    def __init__(self, magic, hdr_ver, flags, rid):
        self.magic = magic
        self.hdr_ver = hdr_ver
        self.flags = flags
        self.rid = long(rid)
    def is_empty(self):
        ''' Returns TRUE if the header is empty (invalid), tested by looking for a null (all-zero) magic '''
        return self.magic == r'\0'*4
    def encode(self):
        ''' Encode the members of this struct to a binary string for writing to disk '''
        return pack(Header.FORMAT, self.magic, self.hdr_ver, 0xff, self.flags, self.rid)


class FileHeader(Header):
    ''' Class for encoding journal file headers to disk '''
    FORMAT = '=3Q2L2QH'
    def __init__(self, magic, hdr_ver, flags, rid, fro, ts_sec, ts_ns, file_cnt, file_size, file_number, file_name):
        Header.__init__(self, magic, hdr_ver, flags, rid)
        self.fro = fro
        self.ts_sec = ts_sec
        self.ts_ns = ts_ns
        self.file_cnt = file_cnt
        self.file_size = file_size
        self.file_number = file_number
        self.file_name = file_name
    def encode(self):
        ''' Encode the members of this struct to a binary string for writing to disk '''
        return Header.encode(self) + pack(FileHeader.FORMAT, self.fro, self.ts_sec, self.ts_ns, self.file_cnt,
                                          0xffffffff, self.file_size, self.file_number,
                                          len(self.file_name)) + self.file_name


class EfpArgParser(argparse.ArgumentParser):
    ''' Empty file pool argument parser '''
    def __init__(self):
        ''' Constructor '''
        argparse.ArgumentParser.__init__(self,
                                         description='Qpid linear store Empty File Pool (EFP) maintenance tool. '
                                                     'Used to view, create, remove and set the sizes and numbers of '
                                                     'files in the EFP.',
                                         prog='efptool',
                                         epilog='NOTE: Pool directories under the efp directory are named as the file '
                                         'size it contains in kb, and followed by the letter \'k\'. (eg a pool '
                                         'containing 160 kb files is named \'160k\'.)')
        self.add_argument('store_directory', metavar='STORE-DIR',
                          help='Use store directory DIR to create pool (required)')
        self.add_argument('-a', '--add', action='store_true',
                          help='Add new pool of NF files sized FS in partition PN. Must be used with --partition, '
                               '--size and --num-files')
        self.add_argument('-r', '--remove', action='store_true',
                          help='Remove existing pool of file sized FS in partition PN. Must be used with --partition '
                               'and --size')
        self.add_argument('-f', '--freshen', action='store_true',
                          help='Freshen the file pool of files sized FS in partition PN to NF files. If --partition is '
                               'used, then in partition PN, otherwise all partitions. If --file-size is used, then for '
                               'pools of file size FS, otherwise for all pools. Must be used with --num-files')
        self.add_argument('-l', '--list', action='store_true',
                          help='List the store pools and partitions in store directory STORE-DIR')
        self.add_argument('-p', '--partition', metavar='PN',
                          help='Name of partition in which pool add, delete or freshen action is to be performed')
        self.add_argument('-s', '--file-size', metavar='FS',
                          help='Add pool containing file size FS KiB (1024 bytes), must be a multiple of 4 KiB')
        self.add_argument('-n', '--num-files', metavar='NF',
                          help='Set the number of files for the add or freshen action to NF')
        self.add_argument('-v', '--version', action='version', version='%(prog)s 0.8')
    @staticmethod
    def validate_args(args):
        ''' Static validation function which checks that required dependent args are present as well as no mutually
            exclusive args'''
        valid = True
        if not os.path.exists(args.store_directory):
            valid = False
            print 'ERROR: directory', args.store_directory, 'does not exist'
        if args.add:
            if args.partition is None:
                valid = False
                print 'ERROR: Must use --partition/-p with --add/-a'
            if args.file_size is None:
                valid = False
                print 'ERROR: Must use --file-size/-s with --add/-a'
            if args.num_files is None:
                valid = False
                print 'ERROR: Must use --num-files/-n with --add/-a'
        if args.remove:
            if args.partition is None:
                valid = False
                print 'ERROR: Must use --partition/-p with --remove/-r'
            if args.file_size is None:
                valid = False
                print 'ERROR: Must use --file-size/-s with --remove/-r'
        if args.freshen:
            if args.num_files is None:
                valid = False
                print 'ERROR: Must use --num-files/-n with --freshen/-f'
        if args.add and args.remove:
            valid = False
            print 'ERROR: Cannot use --add/-a and --remove/-r together'
        if args.add and args.freshen:
            valid = False
            print 'ERROR: Cannot use --add/-a and --freshen/-f together'
        if args.remove and args.freshen:
            valid = False
            print 'ERROR: Cannot use --remove/-r and --freshen/-f together'
        if not args.file_size is None:
            try:
                if int(args.file_size) % DEFAULT_SBLK_SIZE_KB != 0:
                    valid = False
                    print 'ERROR: --size/-s: %s is not a multiple of %d' % (args.file_size, DEFAULT_SBLK_SIZE_KB)
            except ValueError:
                print 'ERROR: --size/-s: \'%s\' not an integer' % args.file_size
                valid = False
        return valid


class EmptyFilePool:
    ''' Class for managing a directory containing empty journal files, known as an empty file pool '''
    def __init__(self, partition, file_size_kb):
        self.partition = partition
        self.file_size_kb = file_size_kb
        self.size_str = EmptyFilePool.get_dir_name(file_size_kb)
        self.directory = os.path.join(partition.efp_directory, self.size_str)
        self.empty_file_names = []
        self.cum_file_size = 0
        if not os.path.exists(self.directory):
            os.mkdir(self.directory)
    def __str__(self):
        return 'Pool %s (size: %dk) at %s containing %d files with total size %d' % (self.size_str,
                self.file_size_kb, self.partition.efp_directory, len(self.empty_file_names), self.cum_file_size)
    def get_num_files(self):
        ''' Return the number of empty files currently in this EFP '''
        return len(self.empty_file_names)
    def validate_efp_file(self, empty_file_name):
        ''' Checks that the file name (which is related to the file size it contains) is a valid multiple of 4kb '''
        file_size = os.path.getsize(empty_file_name)
        expected_file_size = (self.file_size_kb * 1024) + DEFAULT_SBLK_SIZE
        if file_size != expected_file_size:
            print 'WARNING: File %s not of correct size (size=%d, expected=%d): Ignoring' % (empty_file_name, file_size,
                                                                                             expected_file_size)
            return False
        return True
        # TODO - read and decode header, verify validity
    def read(self):
        ''' Read the directory and parse all empty files found there '''
        for dir_entry in os.listdir(self.directory):
            fpn = os.path.join(self.directory, dir_entry)
            if self.validate_efp_file(fpn):
                self.add_efp_file(fpn)
                #print 'Found', os.path.join(self.directory, dir_entry), 'of size', os.path.getsize(fpn)
    def create_new_efp_files(self, num_files):
        ''' Create one or more new empty journal files of the prescribed size for this EFP '''
        cum_file_size = 0
        for _ in range(num_files):
            cum_file_size += self.create_new_efp_file()
        return cum_file_size
    def create_new_efp_file(self):
        ''' Create a single new empty journal file of the prescribed size for this EFP '''
        file_name = str(uuid4()) + DEFAULT_JRNL_EXTENTION
        file_header = FileHeader(r'\0\0\0\0', 0, 0, 0, 0, 0, 0, 0, self.file_size_kb, 0, file_name)
        efh = file_header.encode()
        efh_bytes = len(efh)
        f = open(os.path.join(self.directory, file_name), 'wb')
        f.write(efh)
        f.write('\xff' * (DEFAULT_SBLK_SIZE - efh_bytes))
        f.write('\x00' * (int(self.file_size_kb) * 1024))
        f.close()
        fqfn = os.path.join(self.directory, file_name)
        self.add_efp_file(fqfn)
        return os.path.getsize(fqfn)
    def add_efp_file(self, efp_file_name):
        ''' Add a single journal file of the appropriate size to this EFP. No file size check is made here. '''
        self.empty_file_names.append(efp_file_name)
        self.cum_file_size += os.path.getsize(efp_file_name)
    @staticmethod
    def get_dir_name(file_size_kb):
        ''' Static function to create an EFP directory name from the size of the files it contains '''
        return '%dk' % file_size_kb


class Partition:
    ''' Class representing a partition, which may contain one or more EFP directories directly under it '''
    def __init__(self, root_path, partition_num, efp_directory_name):
        self.root_path = root_path
        self.efp_directory = os.path.join(root_path, efp_directory_name)
        self.partition_num = partition_num
        self.pools = {}
        self.num_files = 0
        self.cum_file_size = 0
        self.validate_efp_directory()
    def __lt__(self, other):
        return self.partition_num < other.partition_num
    def __str__(self):
        s = '  Partition %d at %s:\n' % (self.partition_num, self.root_path)
        if len(self.pools) > 0:
            for pn in self.pools.iterkeys():
                s += '    %s containing %d files\n' % (pn, self.pools[pn].get_num_files())
            s += '    [%d files with cumulative size %d]\n' % (self.num_files, self.cum_file_size)
        else:
            s += '    <empty partition>\n'
        return s
    def validate_efp_directory(self):
        ''' Check that the partition directory is valid '''
        # TODO: Add checks for permissions to write and sufficient space
        if not os.path.exists(self.efp_directory) or not os.path.isdir(self.efp_directory):
            raise InvalidPartitionDirectoryError
    def read(self):
        ''' Read the partition, identifying EFP directories. Read each EFP directory found. '''
        for dir_entry in os.listdir(self.efp_directory):
            size_kb = get_directory_size(dir_entry)
            if size_kb > 0 and size_kb % DEFAULT_SBLK_SIZE_KB == 0:
                efp = EmptyFilePool(self, size_kb)
                efp.read()
                self.num_files += efp.get_num_files()
                self.cum_file_size += efp.cum_file_size
                self.pools[dir_entry] = efp
    def create_new_efp_files(self, file_size_kb, num_files):
        ''' Create new EFP files in this partition '''
        dir_name = EmptyFilePool.get_dir_name(file_size_kb)
        if dir_name in self.pools.keys():
            efp = self.pools[dir_name]
        else:
            efp = EmptyFilePool(self, file_size_kb)
        cum_file_size = efp.create_new_efp_files(num_files)
        self.cum_file_size += cum_file_size
        self.num_files += num_files
        return cum_file_size
            

class EmptyFilePoolManager:
    ''' High level class for managing partitions and the empty file pools they contian '''
    def __init__(self, args_):
        self.args = args_
        self.store_directory = os.path.abspath(args_.store_directory)
        self.partitions = []
        self.pools = {}
        self.total_num_files = 0
        self.total_cum_file_size = 0
        self.current_partition = None
        print 'Reading store directory', self.store_directory
        for dir_entry in os.listdir(self.store_directory):
            if len(dir_entry) == 4 and dir_entry[0] == 'p':
                pn = int(dir_entry[1:])
                try:
                    self.partitions.append(Partition(os.path.join(self.store_directory, dir_entry), pn,
                                                     DEFAULT_EFP_DIR_NAME))
                except InvalidPartitionDirectoryError:
                    pass
        for p in self.partitions:
            p.read()
            for efpl in p.pools.iterkeys():
                if efpl not in self.pools:
                    self.pools[efpl] = []
                self.pools[efpl].append(p.pools[efpl])
            self.total_num_files += p.num_files
            self.total_cum_file_size += p.cum_file_size
    def __str__(self):
        s = '\nPartitions (%d):\n' % len(self.partitions)
        for p in sorted(self.partitions):
            s += str(p)
        s += '\nPools (%d):\n' % len(self.pools)
        for lk in self.pools.iterkeys():
            l = self.pools[lk]
            s += '  %s size=%d (%d):\n' % (lk, get_directory_size(lk), len(l))
            for efp in l:
                s += '    in partition %d (%s) containing %d files\n' % (efp.partition.partition_num,
                                                                         efp.partition.efp_directory,
                                                                         efp.get_num_files())
        s += '\nTotal files: %d\nTotal cumulative file size: %d' % (self.total_num_files, self.total_cum_file_size)
        return s
    def check_args(self):
        ''' Value check of args. The names of partitions and pools are validated against the discovered instances '''
        valid = True
        self.current_partition = None
        if not self.args.partition is None:
            try:
                if self.args.partition[0] == 'p': # string partition name, eg 'p001'
                    pn = int(self.args.partition[1:])
                else: # numeric partition, eg '1'
                    pn = int(self.args.partition)
                found = False
                for p in self.partitions:
                    if p.partition_num == pn:
                        self.current_partition = p
                        found = True
                        break
                if not found:
                    print 'ERROR: --partition/-p: Partition %s does not exist' % self.args.partition
                    valid = False
            except ValueError:
                print 'ERROR: --partition/-p: Partition %s does not exist' % self.args.partition
                valid = False
        if not self.current_partition is None:
            if self.args.add:
                if EmptyFilePool.get_dir_name(int(self.args.file_size)) in self.current_partition.pools.keys():
                    n = EmptyFilePool.get_dir_name(int(self.args.file_size))
                    print 'ERROR: --add/-a: Pool directory %s already exists' % n
                    valid = False
            if self.args.remove:
                if not EmptyFilePool.get_dir_name(int(self.args.file_size)) in self.current_partition.pools.keys():
                    fs = int(self.args.file_size)
                    print 'ERROR: --remove/-r: Pool %s does not exist' % EmptyFilePool.get_dir_name(fs)
                    valid = False
        return valid
    def run(self):
        ''' Run the command-line args. Entry point for this class '''
        if self.check_args():
            if self.args.add:
                self.add_file_pool(self.current_partition, int(self.args.file_size), int(self.args.num_files))
            if self.args.remove:
                self.remove_file_pool(self.current_partition, int(self.args.file_size))
            if self.args.freshen:
                self.freshen_file_pool(self.current_partition, self.args.file_size, int(self.args.num_files))
            if self.args.list:
                print self
    def add_file_pool(self, partition, file_size_kb, num_files):
        ''' Add an EFP in the specified partition of the specified size containing the specified number of files '''
        dir_name = EmptyFilePool.get_dir_name(file_size_kb)
        print 'Adding pool \'%s\' to partition %s' % (dir_name, partition.partition_num)
        self.total_cum_file_size += partition.create_new_efp_files(file_size_kb, num_files)
        self.total_num_files += num_files
    def remove_file_pool(self, partition, file_size_kb):
        ''' Remove an existing EFP from the specified partition and of the specified size ''' 
        dir_name = EmptyFilePool.get_dir_name(file_size_kb)
        print 'Removing pool \'%s\' from partition %s' % (dir_name, partition.partition_num)
        self.partitions.remove(partition)
        rmtree(os.path.join(partition.efp_directory, dir_name))
    def freshen_file_pool(self, partition, file_size_kb_arg, num_files):
        ''' Freshen an EFP in the specified partition and of the specified size to the specified number of files '''
        if partition is None:
            pl = self.partitions
            a1 = 'all partitions'
        else:
            pl = [partition]
            a1 = 'partition %d' % partition.partition_num
        if file_size_kb_arg is None:
            a2 = 'all pools'
        else:
            a2 = 'pool \'%s\'' % EmptyFilePool.get_dir_name(int(file_size_kb_arg))
        print 'Freshening %s in %s to %d files' % (a2, a1, num_files)
        for p in pl: # Partition objects
            if file_size_kb_arg is None:
                fsl = p.pools.keys()
            else:
                fsl = ['%sk' % file_size_kb_arg]
            for fs in fsl:
                efp = p.pools[fs]
                num_files_needed = num_files - efp.get_num_files()
                if num_files_needed > 0:
                    p.create_new_efp_files(get_directory_size(fs), num_files_needed)
                else:
                    t = (p.pools[fs].size_str, p.partition_num, efp.get_num_files())
                    print '  WARNING: Pool %s in partition %s already contains %d files: no action taken' % t
        


# --- main entry point ---

ARGS = EfpArgParser().parse_args()
if (EfpArgParser.validate_args(ARGS)):
    EFP = EmptyFilePoolManager(ARGS)
    EFP.run()

