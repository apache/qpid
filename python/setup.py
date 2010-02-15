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
import os, re, sys
from distutils.core import setup, Command
from distutils.command.build_py import build_py as _build_py
from distutils.command.install import install as _install
from distutils.command.install_lib import install_lib
from distutils.dep_util import newer
from distutils.errors import DistutilsFileError
from distutils import log
from stat import ST_ATIME, ST_MTIME, ST_MODE, S_IMODE

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


ann = re.compile(r"([ \t]*)@([_a-zA-Z][_a-zA-Z0-9]*)([ \t\n\r]+def[ \t]+)([_a-zA-Z][_a-zA-Z0-9]*)")
line = re.compile(r"\n([ \t]*)[^ \t\n#]+")

major, minor = sys.version_info[0:2]

class build_py(preprocessor, _build_py):

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
    if ext == ".py" and major <= 2 and minor <= 3:
      return "backporting", self.backport
    else:
      return None, None

options = [('amqp-spec-dir=', None, "location of the AMQP specifications")]

class install(_install):

  user_options = _install.user_options + options

  def initialize_options(self):
    _install.initialize_options(self)
    self.amqp_spec_dir = None

  def get_sub_commands(self):
    return ['qpid_config'] + _install.get_sub_commands(self)

class qpid_config(preprocessor, install_lib):

  user_options = options

  def initialize_options(self):
    install_lib.initialize_options(self)
    self.prefix = None
    self.amqp_spec_dir = None

  def finalize_options(self):
    install_lib.finalize_options(self)
    self.set_undefined_options('install',
                               ('prefix', 'prefix'),
                               ('amqp_spec_dir', 'amqp_spec_dir'))
    if self.amqp_spec_dir is None:
      self.amqp_spec_dir = "%s/share/amqp" % self.prefix

  def get_outputs(self):
    return [os.path.join(self.install_dir, "qpid_config.py"),
            os.path.join(self.install_dir, "qpid_config.pyc")]

  def install(self):
    self.mkpath(self.install_dir)
    file, _ = self.copy_file("qpid_config.py", self.install_dir)
    return [file]

  def configure(self, input):
    idx = input.index("AMQP_SPEC_DIR")
    end = input.index(os.linesep, idx)
    return input[:idx] + \
        ('AMQP_SPEC_DIR="%s"' % self.amqp_spec_dir) + \
        input[end:]

  def actor(self, src, dst):
    file = os.path.basename(src)
    if file == "qpid_config.py":
      return "configuring", self.configure
    else:
      return None, None

setup(name="qpid-python",
      version="0.7",
      author="Apache Qpid",
      author_email="dev@qpid.apache.org",
      packages=["mllib", "qpid", "qpid.tests"],
      scripts=["qpid-python-test"],
      url="http://qpid.apache.org/",
      license="Apache Software License",
      description="Python client implementation for Apache Qpid",
      cmdclass={"build_py": build_py,
                "install": install,
                "qpid_config": qpid_config})
