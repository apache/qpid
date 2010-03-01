#!/bin/bash 

usage()
{
 echo "processResults.sh <search dir>"
 exit 1
}

root=`pwd`
echo $root
graphFile=graph.data

if [ $# != 1 ] ; then
 usage
fi

mkdir -p results

for file in `find $1 -name $graphFile` ; do

  dir=`dirname $file`

  echo Processing : $dir
  pushd $dir &> /dev/null

  $root/process.sh $graphFile

  echo Copying Images
  cp work/*png $root/results/

  echo Copying Stats
  cp work/*.statistics.txt $root/results/
  

  popd &> /dev/null
  echo Done
done