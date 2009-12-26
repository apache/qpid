#!/bin/bash
#
# Script to stop the running of a monitored broker
# and the associated monitoring processes.
#
# Looks in the specifed directory for pid files and 
# stops those proceses
#


usage()
{
  echo "Usage: $0 <LOG_DIR>"
}

#
# Attempt to gracefully kill processs
#
stopRun()
{
  kill $PIDS
}


#
# Forcibly stop processes
#
forceStopRun()
{
  kill -9 $PIDS
}

#
# Show usage if we are not started correctly
#
if [ $# != 1 ] ; then
 usage
 exit 1
fi

LOG_DIR=$1


PIDS=`cat $LOG_DIR/*.pid`

echo "Preparing to stop:"
#
# Escape the path so we can use sed to remove it.
#
path=`echo $LOG_DIR|sed -e s/\\\//\\\\\\\\\\\//g`

for i in `ls $LOG_DIR/*.pid` ; do
  # Remove path from pid item then remove any final '/' 
  echo $i|cut -d '.' -f 1| sed -e s/$path// |tr '/' ' '
done

status=`ps $PIDS |wc -l`

if [ $status == 1 ] ; then
 echo "Processes do not appear to be running."
 echo "Have they already been stopped?"
 exit 0
fi

attempts=3

while [ ! $status == 1 ] ; do
 stopRun
 sleep 1
 status=`ps $PIDS |wc -l`
 
  if [ $status == 1 ] ; then
    echo "Done"
    exit 0
  else  
   attempts=$[ $attempts - 1 ]
 
   if [ $attempts == 0 ] ; then
     break
   fi
   
  echo "Sleeping as processes not stopped"
  sleep 2

  fi
done

# If we haven't been able to kill the processes then 
# forcibly do it
if [ ! $status == 1 ] ; then
 forceStopRun
 sleep 1
 status=`ps $PIDS |wc -l`
 
  if [ $status == 1 ] ; then
    echo "Done"
  else
    echo "Stop failed"
    exit 1
  fi 
else
 echo "Done"
fi
