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

from env import *

import atexit as _atexit
import os as _os
import platform as _platform
import re as _re
import signal as _signal
import subprocess as _subprocess
import shutil as _shutil
import time as _time
import uuid as _uuid

WINDOWS = _platform.system() == "Windows"

def _unique_id():
    return str(_uuid.uuid4())[:4]

def make_work_dir():
    prog = file_name(ARGS[0])
    name = "{}_{}".format(prog, _unique_id())
    
    return make_dir(join(BUILD_DIR, name))

WORK_DIR = make_work_dir()

notice("Created work dir '{}'", WORK_DIR)

def _init_valgrind_command(command):
    if VALGRIND is None:
        return command, None

    log_file = join(WORK_DIR, "valgrind_{}.log".format(_unique_id()))
    suppressions_file = join(BUILD_DIR, "src", "tests", ".valgrind.supp")
    
    valgrind_command = [
        VALGRIND,
        "--leak-check=full --num-callers=25 --error-exitcode=100",
        "--log-file={}".format(log_file),
        "--suppressions={}".format(suppressions_file),
        "--",
        command,
    ]

    return " ".join(valgrind_command), log_file

def call_with_valgrind(command, *args, **kwargs):
    command, valgrind_log_file = _init_valgrind_command(command)

    try:
        call(command, *args, **kwargs)
    except _subprocess.CalledProcessError as e:
        if e.returncode == 100:
            error("Valgrind reported errors")
            print(read(valgrind_log_file))

        raise

def call_for_output_with_valgrind(command, *args, **kwargs):
    command, valgrind_log_file = _init_valgrind_command(command)

    try:
        return call_for_output(command, *args, **kwargs)
    except _subprocess.CalledProcessError as e:
        if e.returncode == 100:
            error("Valgrind reported errors")
            print(read(valgrind_log_file))

        raise

_brokers = list()
_brokers_by_port = dict()
_broker_port_expr = _re.compile(r"Listening on TCP/TCP6 port ([0-9]+)")
_broker_config_file = join(BUILD_DIR, "src", "tests", "qpidd-empty.conf")

class _Broker(object):
    def __init__(self, dir):
        self.dir = dir

        self.command_file = join(self.dir, "command")
        self.log_file = join(self.dir, "log")
        self.data_dir = join(self.dir, "data")
        
        self.port = None
        self.proc = None
        self.command = None
        self.valgrind_log_file = None

    def __repr__(self):
        args = self.port, self.proc.pid, self.proc.returncode
        return "Broker(port={}, pid={}, exit={})".format(*args)

    def start(self, args):
        make_dir(self.dir)

        command = [
            "qpidd",
            "--port 0",
            "--interface localhost",
            "--no-module-dir",
            "--log-enable info+",
            "--log-source yes",
            "--log-to-stderr no",
            "--log-to-file {}".format(self.log_file),
            "--config {}".format(_broker_config_file),
            "--data-dir {}".format(self.data_dir),
        ]

        if WINDOWS:
            command += [
                "--ssl-cert-store-location LocalMachine",
                "--ssl-cert-name localhost",
                "--ssl-port 0",
            ]
        
        command += [x for x in args if x is not None]
        command = " ".join(command)
        command, valgrind_log_file = _init_valgrind_command(command)

        self.command = command
        self.valgrind_log_file = valgrind_log_file
        
        notice("Calling '{}'", self.command)
        write(self.command_file, self.command)

        self.proc = _subprocess.Popen(self.command, shell=True,
                                      stdout=_subprocess.PIPE)
        self.port = self._wait_for_port()

        assert self.command is not None
        assert self.proc is not None
        assert self.port is not None
        assert self.port not in _brokers_by_port, self.port

        _brokers.append(self)
        _brokers_by_port[self.port] = self

        notice("Started {}", self)
        
    def _wait_for_port(self):
        port = None
        
        while port is None:
            _time.sleep(0.4)
            port = self._scan_port()

        return port

    def _scan_port(self):
        if not exists(self.log_file):
            return
      
        match = _re.search(_broker_port_expr, read(self.log_file))

        if match:
            return match.group(1)

    def stop(self):
        if self.proc.poll() is not None:
            return
            
        notice("Stopping {}", self)

        if WINDOWS:
            call("taskkill /f /t /pid {}", self.proc.pid)
        else:
            self.proc.terminate()

        self.proc.wait()

    def check(self):
        if WINDOWS:
            # Taskkilled windows processes always return 1, so exit
            # codes don't mean anything there
            return 0
    
        notice("Checking {}", self)
        
        if self.proc.returncode == 0:
            return 0

        error("{} exited with code {}", self, self.proc.returncode)
        
        if self.proc.returncode == 100:
            print("Valgrind reported errors:")
            print(read(self.valgrind_log_file))
        else:
            print("Last 100 lines of broker log:")
            print(tail(self.log_file, 100))

        flush()

        error("{} exited with code {}", self, self.proc.returncode)
        
        return self.proc.returncode

def start_broker(dir, *args, **kwargs):
    if not is_absolute(dir):
        dir = join(WORK_DIR, dir)

    auth_disabled = kwargs.get("auth_disabled", True)

    if auth_disabled:
        args = list(args)
        args.append("--auth no")
        
    broker = _Broker(dir)
    broker.start(args)

    return broker.port

def stop_broker(port):
    broker = _brokers_by_port[port]
    broker.stop()

def check_broker(port):
    broker = _brokers_by_port[port]

    if broker.check() != 0:
        exit("Broker failure")

def check_results():
    for broker in _brokers:
        broker.stop()

    errors = False

    for broker in _brokers:
        code = broker.check()

        if code == 0:
            continue

        errors = True

    if errors:
        exit("Broker failure")

    remove(WORK_DIR)

    notice("Tests completed without error")

def _exit_handler():
    if exists(WORK_DIR):
        notice("Output saved in work dir '{}'", WORK_DIR)
    
    for broker in _brokers:
        broker.stop()
        
_atexit.register(_exit_handler)

def configure_broker(broker_port, *args):
    command = [
        "qpid-config",
        "--broker localhost:{}".format(broker_port),
    ]

    command += [x for x in args if x is not None]

    call(" ".join(command))

def run_broker_tests(broker_port, *args):
    command = [
        "qpid-python-test",
        "--broker localhost:{}".format(broker_port),
        "--time",
    ]

    command += [x for x in args if x is not None]

    call(" ".join(command))

def connect_brokers(*args):
    command = ["qpid-route"]
    command += [x for x in args if x is not None]

    call(" ".join(command))
