#!/usr/bin/env bash

# get directory path ---------------------
PRG="$0"

while [ -h "$PRG" ]; do
  ls=`ls -ld "$PRG"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=`dirname "$PRG"`/"$link"
  fi
done

# Get standard environment variables
PRGDIR=`dirname "$PRG"`

#echo $PRGDIR
# ----------------------------------------

CURRENT_DIR=$PRGDIR
#cd "$CURRENT_DIR"
BASEDIR=$CURRENT_DIR
. $BASEDIR/setclasspath.sh

echo JAVA_HOME="$JAVA_HOME"

TMPDIR=$BASEDIR/temp

MAINCLASS=com.beef.easytcp.asyncserver.test.TestTcpProxyServer

CMD_LINE_ARGS=
ACTION=
SECURITY_POLICY_FILE=
DEBUG_OPTS=
JPDA=
JAVA_OPTS="-Xms1024m -Xmx8192m -d64 -Djava.nio.channels.spi.SelectorProvider=sun.nio.ch.EPollSelectorProvider"

# make jar of log4j.properties to override the default one
"$JAVA_HOME/bin/jar" -cvfM config.jar -C "$BASEDIR/conf" log4j.properties
mv "$BASEDIR/config.jar" "$BASEDIR/lib/"

# add ./lib/*.jar to classpath
for filename in $BASEDIR/lib/*.jar;
do
  CLASSPATH=$CLASSPATH:$filename
done
for filename in $BASEDIR/*.jar;
do
  CLASSPATH=$CLASSPATH:$filename
done
CLASSPATH=$CLASSPATH:$BASEDIR/bin/

_EXECJAVA=$_RUNJAVA

$_EXECJAVA $JAVA_OPTS $DEBUG_OPTS -classpath "$CLASSPATH" -Djava.io.tmpdir="$TMPDIR" $MAINCLASS $CMD_LINE_ARGS $ACTION $* &
