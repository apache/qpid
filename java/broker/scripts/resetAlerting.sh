#!/bin/bash
#
# Alerting Rest Scripts to renabled the alerts on the queue.
#
# Defaults to Localhost broker
#

if [ -z "$QPID_ALERT_HOME" ]; then
    export QPID_ALERT_HOME=$(dirname $(dirname $(readlink -f $0)))
    export PATH=${PATH}:${QPID_ALERT_HOME}/bin
fi

USERNAME=$1
PASSWORD=$2
HOSTNAME=$3
PORT=$4

CLI="$QPID_ALERT_HOME/bin/qpid-cli -h ${HOSTNAME:-localhost} -p ${PORT:-8999}"
AUTH=
if [ -n $USERNAME ] ; then
   if [ "$USERNAME" == "-h" ] ; then
       echo "resetAlerting.sh: [<username> <password> [<hostname> [<port>]]]"
       exit 0
   fi
   if [ -n $PASSWORD ] ; then
       AUTH="-u $USERNAME -w $PASSWORD"
   else 
       echo "Password must be specified with username"
   fi
fi
  

OUTPUT=0

runCommand()
{
  RET=`$CLI $1 $AUTH`
}

resetQueue()
{
    vhost=$1
    queue=$2
    runCommand "get -o queue -v $vhost -n $queue  -a MaximumQueueDepth"
    rawQDepth=$RET
    # Note that MaxQueueDepth is returned as Kb but set as b!
    queueDepth=$[ $rawQDepth * 1024 ]
    runCommand "get -o queue -v $vhost -n $queue  -a MaximumMessageAge"
    messageAge=$RET
    runCommand "get -o queue -v $vhost -n $queue  -a MaximumMessageCount"
    messageCount=$RET
    runCommand "get -o queue -v $vhost -n $queue  -a MaximumMessageSize" 
    messageSize=$RET
    
    if [ $OUTPUT == 1 ] ; then    
     echo Current Values:
     echo MaximumQueueDepth   : $queueDepth
     echo MaximumMessageAge   : $messageAge
     echo MaximumMessageCount : $messageCount
     echo MaximumMessageSize  : $messageSize        
    fi
    
    runCommand "set -o queue -v $vhost -n $queue  -a MaximumMessageSize -s $messageSize"
    runCommand "set -o queue -v $vhost -n $queue  -a MaximumMessageAge -s $messageAge"
    runCommand "set -o queue -v $vhost -n $queue  -a MaximumMessageCount -s $messageCount"
    runCommand "set -o queue -v $vhost -n $queue  -a MaximumQueueDepth -s $queueDepth"
}

resetVirtualHost()
{
 vhost=$1
 ignore=0
 for queue in `$CLI list -o queue -v $vhost $AUTH |grep '|' | cut -d '|' -f 1 ` ; do
 
   if [ $ignore == 0 ] ; then
     ignore=1
   else 
     resetQueue $vhost $queue
   fi
 
 done
}

VHOST=`$CLI list -o virtualhost $AUTH`
COUNT=`echo $VHOST | grep -c VirtualHost`
if [ $COUNT -gt 0 ] ; then
   for vhost in `echo $VHOST |grep VirtualHost|cut -d '=' -f 3` ; do

      echo "Resetting alert levels for $vhost";
      resetVirtualHost $vhost;
   done
   echo "Alerting levels reset"
else
   echo $VHOST
fi
