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
import os, re, sys, string
from distutils.core import setup, Command
from distutils.command.build import build as _build
from distutils.command.build_py import build_py as _build_py
from distutils.command.clean import clean as _clean
from distutils.command.install_lib import install_lib as _install_lib
from distutils.dep_util import newer
from distutils.dir_util import remove_tree
from distutils.dist import Distribution
from distutils.errors import DistutilsFileError, DistutilsOptionError
from distutils import log
from stat import ST_ATIME, ST_MTIME, ST_MODE, S_IMODE

MAJOR, MINOR = sys.version_info[0:2]

class preprocessor:

  def copy_file(self, src, dst, preserve_mode=1, preserve_times=1,
                link=None, level=1):
    name, actor = self.actor(src, dst)
    if actor:
      if not os.path.isfile(src):
        raise DistutilsFileError, \
            "can't copy '%s': doesn't exist or not a regular file" % src

      if os.path.isdir(dst):
        dir = dst
        dst = os.path.join(dst, os.path.basename(src))
      else:
        dir = os.path.dirname(dst)

      if not self.force and not newer(src, dst):
        return dst, 0

      if os.path.basename(dst) == os.path.basename(src):
        log.info("%s %s -> %s", name, src, dir)
      else:
        log.info("%s %s -> %s", name, src, dst)

      if self.dry_run:
        return (dst, 1)
      else:
        try:
          fsrc = open(src, 'rb')
        except os.error, (errno, errstr):
          raise DistutilsFileError, \
              "could not open '%s': %s" % (src, errstr)

        if os.path.exists(dst):
          try:
            os.unlink(dst)
          except os.error, (errno, errstr):
            raise DistutilsFileError, \
                "could not delete '%s': %s" % (dst, errstr)

        try:
          fdst = open(dst, 'wb')
        except os.error, (errno, errstr):
          raise DistutilsFileError, \
              "could not create '%s': %s" % (dst, errstr)

        try:
          fdst.write(actor(fsrc.read()))
        finally:
          fsrc.close()
          fdst.close()

        if preserve_mode or preserve_times:
          st = os.stat(src)

          if preserve_times:
            os.utime(dst, (st[ST_ATIME], st[ST_MTIME]))
          if preserve_mode:
            os.chmod(dst, S_IMODE(st[ST_MODE]))

        return (dst, 1)
    else:
      return Command.copy_file(self, src, dst, preserve_mode, preserve_times,
                               link, level)

doc_option = [('build-doc', None, 'build directory for documentation')]

class build(_build):

  user_options = _build.user_options + doc_option

  def initialize_options(self):
    _build.initialize_options(self)
    self.build_doc = None

  def finalize_options(self):
    _build.finalize_options(self)
    if self.build_doc is None:
      self.build_doc = "%s/doc" % self.build_base

  def get_sub_commands(self):
    return _build.get_sub_commands(self) + ["build_doc"]

class build_doc(Command):

  user_options = doc_option

  def initialize_options(self):
    self.build_doc = None

  def finalize_options(self):
    self.set_undefined_options('build', ('build_doc', 'build_doc'))

  def run(self):
    try:
      from epydoc.docbuilder import build_doc_index
      from epydoc.docwriter.html import HTMLWriter
    except ImportError, e:
      log.warn('%s -- skipping build_doc', e)
      return

    names = ["qpid.messaging"]
    doc_index = build_doc_index(names, True, True)
    html_writer = HTMLWriter(doc_index)
    self.mkpath(self.build_doc)
    log.info('epydoc %s to %s' % (", ".join(names), self.build_doc))
    html_writer.write(self.build_doc)

class clean(_clean):

  user_options = _clean.user_options + doc_option

  def initialize_options(self):
    _clean.initialize_options(self)
    self.build_doc = None

  def finalize_options(self):
    _clean.finalize_options(self)
    self.set_undefined_options('build', ('build_doc', 'build_doc'))

  def run(self):
    if self.all:
      if os.path.exists(self.build_doc):
        remove_tree(self.build_doc, dry_run=self.dry_run)
      else:
        log.debug("%s doesn't exist -- can't clean it", self.build_doc)
    _clean.run(self)

if MAJOR <= 2 and MINOR <= 3:
  from glob import glob
  from distutils.util import convert_path
  class distclass(Distribution):

    def __init__(self, *args, **kwargs):
      self.package_data = None
      Distribution.__init__(self, *args, **kwargs)
else:
  distclass = Distribution

ann = re.compile(r"([ \t]*)@([_a-zA-Z][_a-zA-Z0-9]*)([ \t\n\r]+def[ \t]+)([_a-zA-Z][_a-zA-Z0-9]*)")
line = re.compile(r"\n([ \t]*)[^ \t\n#]+")

class build_py(preprocessor, _build_py):

  if MAJOR <= 2 and MINOR <= 3:
    def initialize_options(self):
      _build_py.initialize_options(self)
      self.package_data = None

    def finalize_options(self):
      _build_py.finalize_options(self)
      self.package_data = self.distribution.package_data
      self.data_files = self.get_data_files()

    def get_data_files (self):
      data = []
      if not self.packages:
        return data
      for package in self.packages:
        # Locate package source directory
        src_dir = self.get_package_dir(package)

        # Compute package build directory
        build_dir = os.path.join(*([self.build_lib] + package.split('.')))

        # Length of path to strip from found files
        plen = 0
        if src_dir:
          plen = len(src_dir)+1

        # Strip directory from globbed filenames
        filenames = [file[plen:]
                     for file in self.find_data_files(package, src_dir)]
        data.append((package, src_dir, build_dir, filenames))
      return data

    def find_data_files (self, package, src_dir):
      globs = (self.package_data.get('', [])
               + self.package_data.get(package, []))
      files = []
      for pattern in globs:
        # Each pattern has to be converted to a platform-specific path
        filelist = glob(os.path.join(src_dir, convert_path(pattern)))
        # Files that match more than one pattern are only added once
        files.extend([fn for fn in filelist if fn not in files])
      return files

    def build_package_data (self):
      lastdir = None
      for package, src_dir, build_dir, filenames in self.data_files:
        for filename in filenames:
          target = os.path.join(build_dir, filename)
          self.mkpath(os.path.dirname(target))
          self.copy_file(os.path.join(src_dir, filename), target,
                         preserve_mode=False)

    def build_packages(self):
      _build_py.build_packages(self)
      self.build_package_data()

  # end if MAJOR <= 2 and MINOR <= 3

  def backport(self, input):
    output = ""
    pos = 0
    while True:
      m = ann.search(input, pos)
      if m:
        indent, decorator, idef, function = m.groups()
        output += input[pos:m.start()]
        output += "%s#@%s%s%s" % (indent, decorator, idef, function)
        pos = m.end()

        subst = "\n%s%s = %s(%s)\n" % (indent, function, decorator, function)
        npos = pos
        while True:
          n = line.search(input, npos)
          if not n:
            input += subst
            break
          if len(n.group(1)) <= len(indent):
            idx = n.start()
            input = input[:idx] + subst + input[idx:]
            break
          npos = n.end()
      else:
        break

    output += input[pos:]
    return output

  def actor(self, src, dst):
    base, ext = os.path.splitext(src)
    if ext == ".py" and MAJOR <= 2 and MINOR <= 3:
      return "backporting", self.backport
    else:
      return None, None

def pclfile(xmlfile):
  return "%s.pcl" % os.path.splitext(xmlfile)[0]

class install_lib(_install_lib):

  def get_outputs(self):
    outputs = _install_lib.get_outputs(self)
    extra = []
    for of in outputs:
      if os.path.basename(of) == "amqp-0-10-qpid-errata.xml":
        extra.append(pclfile(of))
    return outputs + extra

  def install(self):
    outfiles = _install_lib.install(self)
    extra = []
    for of in outfiles:
      if os.path.basename(of) == "amqp-0-10-qpid-errata.xml":
        tgt = pclfile(of)
        if self.force or newer(of, tgt):
          log.info("caching %s to %s" % (of, os.path.basename(tgt)))
          if not self.dry_run:
            from qpid.ops import load_types
            load_types(of)
        extra.append(tgt)
    return outfiles + extra

setup(name="qpid-python",
      version="0.10",
      author="Apache Qpid",
      author_email="dev@qpid.apache.org",
      packages=["mllib", "qpid", "qpid.messaging", "qpid.tests",
                "qpid.tests.messaging"],
      package_data={"qpid": ["specs/*.dtd", "specs/*.xml"]},
      scripts=["qpid-python-test"],
      url="http://qpid.apache.org/",
      license="Apache Software License",
      description="Python client implementation for Apache Qpid",
      cmdclass={"build": build,
                "build_py": build_py,
                "build_doc": build_doc,
                "clean": clean,
                "install_lib": install_lib},
      distclass=distclass)
