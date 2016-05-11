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

from __future__ import print_function

import atexit as _atexit
import codecs as _codecs
import fnmatch as _fnmatch
import getpass as _getpass
import os as _os
import random as _random
import re as _re
import shutil as _shutil
import subprocess as _subprocess
import sys as _sys
import tarfile as _tarfile
import tempfile as _tempfile
import traceback as _traceback

# See documentation at http://www.ssorj.net/projects/plano.html

def fail(message, *args):
    error(message, *args)

    if isinstance(message, BaseException):
        raise message

    raise Exception(message)

def error(message, *args):
    _print_message("Error", message, args, _sys.stderr)

def warn(message, *args):
    _print_message("Warn", message, args, _sys.stderr)

def notice(message, *args):
    _print_message(None, message, args, _sys.stdout)

def debug(message, *args):
    _print_message("Debug", message, args, _sys.stdout)

def exit(message=None, *args):
    if message is None:
        _sys.exit()

    _print_message("Error", message, args, _sys.stderr)

    _sys.exit(1)

def _print_message(category, message, args, file):
    message = _format_message(category, message, args)

    print(message, file=file)
    file.flush()

def _format_message(category, message, args):
    if isinstance(message, BaseException):
        message = str(message)

        if message == "":
            message = message.__class__.__name__

    if category:
        message = "{0}: {1}".format(category, message)

    if args:
        message = message.format(*args)

    script = split(_sys.argv[0])[1]
    message = "{0}: {1}".format(script, message)

    return message

def flush():
    _sys.stdout.flush()
    _sys.stderr.flush()

absolute_path = _os.path.abspath
normalize_path = _os.path.normpath
exists = _os.path.exists
is_absolute = _os.path.isabs
is_dir = _os.path.isdir
is_file = _os.path.isfile
is_link = _os.path.islink

join = _os.path.join
split = _os.path.split
split_extension = _os.path.splitext

LINE_SEP = _os.linesep
PATH_SEP = _os.sep
PATH_VAR_SEP = _os.pathsep
ENV = _os.environ
ARGS = _sys.argv

current_dir = _os.getcwd

def home_dir(user=""):
    return _os.path.expanduser("~{0}".format(user))

def parent_dir(path):
    path = normalize_path(path)
    parent, child = split(path)

    return parent

def file_name(file):
    file = normalize_path(file)
    dir, name = split(file)

    return name

def name_stem(file):
    name = file_name(file)

    if name.endswith(".tar.gz"):
        name = name[:-3]

    stem, ext = split_extension(name)

    return stem

def name_extension(file):
    name = file_name(file)
    stem, ext = split_extension(name)
    
    return ext

def read(file):
    with _codecs.open(file, encoding="utf-8", mode="r") as f:
        return f.read()

def write(file, string):
    with _codecs.open(file, encoding="utf-8", mode="w") as f:
        f.write(string)

    return file

def append(file, string):
    with _codecs.open(file, encoding="utf-8", mode="a") as f:
        f.write(string)

    return file

def prepend(file, string):
    orig = read(file)
    prepended = string + orig

    return write(file, prepended)

def touch(file):
    return append(file, "")

def tail(file, n):
    return "".join(tail_lines(file, n))

def read_lines(file):
    with _codecs.open(file, encoding="utf-8", mode="r") as f:
        return f.readlines()

def write_lines(file, lines):
    with _codecs.open(file, encoding="utf-8", mode="r") as f:
        f.writelines(lines)

    return file

def append_lines(file, lines):
    with _codecs.open(file, encoding="utf-8", mode="a") as f:
        f.writelines(string)

    return file

def prepend_lines(file, lines):
    orig_lines = read_lines(file)

    with _codecs.open(file, encoding="utf-8", mode="w") as f:
        f.writelines(lines)
        f.writelines(orig_lines)

    return file

# Derived from http://stackoverflow.com/questions/136168/get-last-n-lines-of-a-file-with-python-similar-to-tail
def tail_lines(file, n):
    assert n >= 0

    with _codecs.open(file, encoding="utf-8", mode="r") as f:
        pos = n + 1
        lines = list()

        while len(lines) <= n:
        	try:
        	    f.seek(-pos, 2)
        	except IOError:
        	    f.seek(0)
        	    break
        	finally:
        	    lines = f.readlines()

        	pos *= 2
            
        return lines[-n:]

_temp_dir = _tempfile.mkdtemp(prefix="plano.")

def _get_temp_file(key):
    assert not key.startswith("_")

    return join(_temp_dir, "_file_{0}".format(key))

def _remove_temp_dir():
    _shutil.rmtree(_temp_dir, ignore_errors=True)

_atexit.register(_remove_temp_dir)

def read_temp(key):
    file = _get_temp_file(key)
    return read(file)

def write_temp(key, string):
    file = _get_temp_file(key)
    return write(file, string)

def append_temp(key, string):
    file = _get_temp_file(key)
    return append(file, string)

def prepend_temp(key, string):
    file = _get_temp_file(key)
    return prepend(file, string)

def make_temp(key):
    return append_temp(key, "")

def open_temp(key, mode="r"):
    file = _get_temp_file(key)
    return _codecs.open(file, encoding="utf-8", mode=mode)

# This one is deleted on process exit
def make_temp_dir():
    return _tempfile.mkdtemp(prefix="_dir_", dir=_temp_dir)

# This one sticks around
def make_user_temp_dir():
    temp_dir = _tempfile.gettempdir()
    user = _getpass.getuser()
    user_temp_dir = join(temp_dir, user)

    return make_dir(user_temp_dir)

def copy(from_path, to_path):
    notice("Copying '{0}' to '{1}'", from_path, to_path)

    to_dir = parent_dir(to_path)

    if to_dir:
        make_dir(to_dir)

    if is_dir(from_path):
        _copytree(from_path, to_path, symlinks=True)
    else:
        _shutil.copy(from_path, to_path)

    return to_path

def move(from_path, to_path):
    notice("Moving '{0}' to '{1}'", from_path, to_path)

    _shutil.move(from_path, to_path)

    return to_path

def rename(path, expr, replacement):
    path = normalize_path(path)
    parent_dir, name = split(path)
    to_name = string_replace(name, expr, replacement)
    to_path = join(parent_dir, to_name)

    notice("Renaming '{0}' to '{1}'", path, to_path)

    move(path, to_path)

    return to_path

def remove(path):
    notice("Removing '{0}'", path)

    if not exists(path):
        return

    if is_dir(path):
        _shutil.rmtree(path, ignore_errors=True)
    else:
        _os.remove(path)

    return path

def make_link(source_path, link_file):
    if exists(link_file):
        assert read_link(link_file) == source_path
        return

    _os.symlink(source_path, link_file)

    return link_file

def read_link(file):
    return _os.readlink(file)

def find(dir, *patterns):
    matched_paths = set()

    if not patterns:
        patterns = ("*",)

    for root, dirs, files in _os.walk(dir):
        for pattern in patterns:
            matched_dirs = _fnmatch.filter(dirs, pattern)
            matched_files = _fnmatch.filter(files, pattern)

            matched_paths.update([join(root, x) for x in matched_dirs])
            matched_paths.update([join(root, x) for x in matched_files])

    return sorted(matched_paths)

def find_any_one(dir, *patterns):
    paths = find(dir, *patterns)

    if len(paths) == 0:
        return
    
    return paths[0]

def find_only_one(dir, *patterns):
    paths = find(dir, *patterns)

    if len(paths) == 0:
        return
    
    assert len(paths) == 1

    return paths[0]

# find_via_expr?

def string_replace(string, expr, replacement, count=0):
    return _re.sub(expr, replacement, string, count)

def make_dir(dir):
    if not exists(dir):
        _os.makedirs(dir)

    return dir

# Returns the current working directory so you can change it back
def change_dir(dir):
    notice("Changing directory to '{0}'", dir)

    cwd = current_dir()
    _os.chdir(dir)
    return cwd

def list_dir(dir, *patterns):
    assert is_dir(dir)

    names = _os.listdir(dir)

    if not patterns:
        return sorted(names)

    matched_names = set()

    for pattern in patterns:
        matched_names.update(_fnmatch.filter(names, pattern))

    return sorted(matched_names)

class working_dir(object):
    def __init__(self, dir):
        self.dir = dir
        self.prev_dir = None

    def __enter__(self):
        self.prev_dir = change_dir(self.dir)
        return self.dir

    def __exit__(self, type, value, traceback):
        change_dir(self.prev_dir)

def _init_call(command, args, kwargs):
    if args:
        command = command.format(*args)

    if "shell" not in kwargs:
        kwargs["shell"] = True

    notice("Calling '{0}'", command)

    return command, kwargs

def call(command, *args, **kwargs):
    command, kwargs = _init_call(command, args, kwargs)
    _subprocess.check_call(command, **kwargs)

def call_for_output(command, *args, **kwargs):
    command, kwargs = _init_call(command, args, kwargs)
    return _subprocess_check_output(command, **kwargs)

def make_archive(input_dir, output_dir, archive_stem):
    temp_dir = make_temp_dir()
    temp_input_dir = join(temp_dir, archive_stem)

    copy(input_dir, temp_input_dir)
    make_dir(output_dir)

    output_file = "{0}.tar.gz".format(join(output_dir, archive_stem))
    output_file = absolute_path(output_file)

    with working_dir(temp_dir):
        call("tar -czf {0} {1}", output_file, archive_stem)

    return output_file

def extract_archive(archive_file, output_dir):
    assert is_file(archive_file)

    if not exists(output_dir):
        make_dir(output_dir)

    archive_file = absolute_path(archive_file)

    with working_dir(output_dir):
        call("tar -xf {0}", archive_file)

    return output_dir

def rename_archive(archive_file, new_archive_stem):
    assert is_file(archive_file)

    if name_stem(archive_file) == new_archive_stem:
        return

    temp_dir = make_temp_dir()

    extract_archive(archive_file, temp_dir)

    input_name = list_dir(temp_dir)[0]
    input_dir = join(temp_dir, input_name)
    output_file = make_archive(input_dir, temp_dir, new_archive_stem)
    output_name = file_name(output_file)
    archive_dir = parent_dir(archive_file)
    new_archive_file = join(archive_dir, output_name)

    move(output_file, new_archive_file)
    remove(archive_file)

    return new_archive_file

def random_port(min=49152, max=65535):
    return _random.randint(min, max)

# Modified copytree impl that allows for already existing destination
# dirs
def _copytree(src, dst, symlinks=False, ignore=None):
    """Recursively copy a directory tree using copy2().

    If exception(s) occur, an Error is raised with a list of reasons.

    If the optional symlinks flag is true, symbolic links in the
    source tree result in symbolic links in the destination tree; if
    it is false, the contents of the files pointed to by symbolic
    links are copied.

    The optional ignore argument is a callable. If given, it
    is called with the `src` parameter, which is the directory
    being visited by copytree(), and `names` which is the list of
    `src` contents, as returned by os.listdir():

        callable(src, names) -> ignored_names

    Since copytree() is called recursively, the callable will be
    called once for each directory that is copied. It returns a
    list of names relative to the `src` directory that should
    not be copied.

    XXX Consider this example code rather than the ultimate tool.

    """
    names = _os.listdir(src)
    if ignore is not None:
        ignored_names = ignore(src, names)
    else:
        ignored_names = set()

    if not exists(dst):
        _os.makedirs(dst)
    errors = []
    for name in names:
        if name in ignored_names:
            continue
        srcname = _os.path.join(src, name)
        dstname = _os.path.join(dst, name)
        try:
            if symlinks and _os.path.islink(srcname):
                linkto = _os.readlink(srcname)
                _os.symlink(linkto, dstname)
            elif _os.path.isdir(srcname):
                _copytree(srcname, dstname, symlinks, ignore)
            else:
                # Will raise a SpecialFileError for unsupported file types
                _shutil.copy2(srcname, dstname)
        # catch the Error from the recursive copytree so that we can
        # continue with other files
        except _shutil.Error as err:
            errors.extend(err.args[0])
        except EnvironmentError as why:
            errors.append((srcname, dstname, str(why)))
    try:
        _shutil.copystat(src, dst)
    except OSError as why:
        if _shutil.WindowsError is not None and isinstance \
               (why, _shutil.WindowsError):
            # Copying file access times may fail on Windows
            pass
        else:
            errors.append((src, dst, str(why)))
    if errors:
        raise _shutil.Error(errors)

# For Python 2.6 compatibility
def _subprocess_check_output(command, **kwargs):
    kwargs["stdout"] = _subprocess.PIPE
    
    proc = _subprocess.Popen(command, **kwargs)
    output = proc.communicate()[0]
    exit_code = proc.poll()

    if exit_code not in (None, 0):
        error = _subprocess.CalledProcessError(exit_code, command)
        error.output = output

        raise error

    return output
