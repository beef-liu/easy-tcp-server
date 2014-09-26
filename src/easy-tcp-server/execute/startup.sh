#!/bin/sh

CURRENT_DIR=${PWD}
#cd "$CURRENT_DIR"
BASEDIR=$CURRENT_DIR
. $BASEDIR/setclasspath.sh

echo JAVA_HOME="$JAVA_HOME"

TMPDIR=$BASEDIR/temp

MAINCLASS=com.beef.easytcp.junittest.TestTcpServer

CMD_LINE_ARGS=
ACTION=
SECURITY_POLICY_FILE=
DEBUG_OPTS=
JPDA=
JAVA_OPTS=-Xms2048m -Xmx2048m -d64

# make jar of log4j.properties to override the default one
"$JAVA_HOME/bin/jar" -cvfM config.jar -C "$BASEDIR/conf" log4j.properties
mv "$BASEDIR/config.jar" "$BASEDIR/lib/"

# add ./lib/*.jar to classpath
for filename in $BASEDIR/lib/*.jar;
do
  CLASSPATH=$CLASSPATH:$filename
done


#test
for filename in $BASEDIR/../lib/*.jar;
do
  CLASSPATH=$CLASSPATH:$filename
done
CLASSPATH=$CLASSPATH:$BASEDIR/../bin/

_EXECJAVA=$_RUNJAVA

$_EXECJAVA $JAVA_OPTS $DEBUG_OPTS -classpath "$CLASSPATH" -Djava.io.tmpdir="$TMPDIR" $MAINCLASS $CMD_LINE_ARGS $ACTION $* &
