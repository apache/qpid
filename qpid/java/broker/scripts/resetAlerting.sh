#!/bin/bash
#
# Alerting Rest Scripts to renabled the alerts on the queue.
#
# Defaults to Localhost broker
#

CLI=./build/bin/qpid-cli
OUTPUT=0


resetQueue()
{
    vhost=$1
    queue=$2
    echo "Resetting Values for $queue on $vhost"    
    rawQDepth=`$CLI get -o queue -v $vhost -n $queue  -a MaximumQueueDepth`
    # Note that MaxQueDepth is returned as Kb but set as b!
    queueDepth=$[ $rawQDepth * 1024 ]
    messageAge=`$CLI get -o queue -v $vhost -n $queue  -a MaximumMessageAge`
    messageCount=`$CLI get -o queue -v $vhost -n $queue  -a MaximumMessageCount`
    messageSize=`$CLI get -o queue -v $vhost -n $queue  -a MaximumMessageSize` 
    
    if [ $OUTPUT == 1 ] ; then    
     echo Current Values:
     echo MaximumQueueDepth   : $queueDepth
     echo MaximumMessageAge   : $messageAge
     echo MaximumMessageCount : $messageCount
     echo MaximumMessageSize  : $messageSize        
    fi
    
    $CLI set -o queue -v $vhost -n $queue  -a MaximumMessageSize -s $messageSize
    $CLI set -o queue -v $vhost -n $queue  -a MaximumMessageAge -s $messageAge
    $CLI set -o queue -v $vhost -n $queue  -a MaximumMessageCount -s $messageCount
    $CLI set -o queue -v $vhost -n $queue  -a MaximumQueueDepth -s $queueDepth	    
}

resetVirtualHost()
{
 vhost=$1
 ignore=0
 for queue in `$CLI list -o queue -v $vhost |grep '|' | cut -d '|' -f 1 ` ; do
 
   if [ $ignore == 0 ] ; then
     ignore=1
   else
     resetQueue $vhost $queue
   fi
 
 done
}

for vhost in `$CLI list -o virtualhost|grep VirtualHost|cut -d '=' -f 3` ; do

 resetVirtualHost $vhost
 
done

