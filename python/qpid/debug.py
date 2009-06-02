import traceback, time, sys

from threading import RLock

def stackdump(*args):
  print args
  code = []
  for threadId, stack in sys._current_frames().items():
    code.append("\n# ThreadID: %s" % threadId)
    for filename, lineno, name, line in traceback.extract_stack(stack):
      code.append('File: "%s", line %d, in %s' % (filename, lineno, name))
      if line:
        code.append("  %s" % (line.strip()))
  print "\n".join(code)

import signal
signal.signal(signal.SIGQUIT, stackdump)

#out = open("/tmp/stacks.txt", "write")

class LoudLock:

  def __init__(self):
    self.lock = RLock()

  def acquire(self, blocking=1):
    import threading
    while not self.lock.acquire(blocking=0):
      time.sleep(1)
      print >> out, "TRYING"
#      print self.lock._RLock__owner, threading._active
#      stackdump()
      traceback.print_stack(None, None, out)
      print >> out, "TRYING"
    print >> out, "ACQUIRED"
    traceback.print_stack(None, None, out)
    print >> out, "ACQUIRED"
    return True

  def _is_owned(self):
    return self.lock._is_owned()

  def release(self):
    self.lock.release()

