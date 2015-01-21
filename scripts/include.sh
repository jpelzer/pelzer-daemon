# Common scripting support for the daemon system

set -e

BASE_DIR="$PWD"
SCRIPT_NAME=`basename $0`
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
JAVA_OPTS="-Xmx768M -XX:MaxPermSize=256m"


if [[ -w "/var/log/spln/showtime_admin" ]]; then
	LOG_DIR="/var/log/spln/showtime_admin"
elif [[ -w "/var/log/shoany" ]]; then
	LOG_DIR="/var/log/shoany"
elif [[ -w "/var/log" ]]; then
	LOG_DIR="/var/log"
elif [[ -w "/tmp" ]]; then
	LOG_DIR="/tmp"
fi

if [[ -w "/var/run" ]]; then
	PID_DIR="/var/run"
else
	PID_DIR="$LOG_DIR"
fi 

# For deployment, we expect the jar to be one directory up from the scripts directory, but if we're running
# on a local env, it'll be in target
JAR_FILE="$SCRIPT_DIR/pelzer-daemon-1.0.0.one-jar.jar"
if [ ! -e $JAR_FILE ]; then
JAR_FILE="$SCRIPT_DIR/../target/tve-daemon-1.0.0.one-jar.jar"
fi

doDaemon () {
	if [[ -z $PID_DIR || -z $LOG_DIR || -z $DAEMON_NAME  || -z $JAVA_CLASS || -z $PELZER_ENV ]]; then
		echo "PID_DIR, LOG_DIR, DAEMON_NAME, JAVA_CLASS, & PELZER_ENV must be set."
		exit -1
	fi
	
	PID_FILE="$PID_DIR/$DAEMON_NAME.pid"
	LOG_FILE="$LOG_DIR/$DAEMON_NAME.log"

  echo "logging to $LOG_FILE, pid in $PID_FILE"

	if [[ $ACTION == "start" ]]; then
		if [[ -e $PID_FILE ]]; then
			echo "$DAEMON_NAME already running"
			exit
		fi

		if [[ $LOG_TYPE == "multilog" ]]; then
			# Don't use pelzer logging, use multilog
			CMD="nohup java -Done-jar.main.class=$JAVA_CLASS -Dpelzer.environment=$PELZER_ENV -jar $JAR_FILE $@"
#			echo "Command = '$CMD'"
	  		sh -c 'echo $$ > '"$PID_FILE; exec $CMD 2>&1" | multilog s1000000000 n3 $LOG_FILE &
	  	else
	  		# Use pelzer logging
			CMD="nohup java -Done-jar.main.class=$JAVA_CLASS -Dpelzer.log=$LOG_FILE -Dpelzer.environment=$PELZER_ENV -jar $JAR_FILE $@"
	  		sh -c 'echo $$ > '"$PID_FILE; exec $CMD 2>&1" > $LOG_FILE &
		fi
  		echo "$DAEMON_NAME started"
	elif [[ $ACTION == "stop" ]]; then
		if [[ -e $PID_FILE ]]; then
			PID=`cat $PID_FILE`
			if  ps -p $PID > /dev/null 
			then
			    kill $PID
			else
				echo "$PID_FILE pointed to defunct pid $PID"
			fi
		    rm -f $PID_FILE
		    echo "$DAEMON_NAME stopped" 
		fi
	elif [[ $ACTION == "restart" ]]; then
		ACTION=stop
		doDaemon $@
		sleep 3
		ACTION=start
		doDaemon $@
	elif [[ $ACTION == "" ]]; then
		echo "USAGE: $SCRIPT_NAME [start|stop|restart]"
	else
		echo "Don't know how to do '$ACTION'"
		exit -1
	fi

}

doGroovyScript () {
	if [[ -z $LOG_DIR || -z $SCRIPT_NAME  || -z $GROOVY_FILE || -z $PELZER_ENV ]]; then
		echo "LOG_DIR, $SCRIPT_NAME, JAVA_CLASS, & PELZER_ENV must be set."
		exit -1
	fi
	LOG_FILE="$LOG_DIR/$SCRIPT_NAME.log"
  echo "logging to $LOG_FILE"

  if [[ $LOG_TYPE == "multilog" ]]; then
    # Don't use pelzer logging, use multilog
    CMD="groovy $JAVA_OPTS -Dpelzer.environment=$PELZER_ENV $@"
    sh -c "exec $CMD 2>&1" | multilog s1000000000 n3 $LOG_FILE
  else
    # Use pelzer logging
    CMD="groovy $JAVA_OPTS -Dpelzer.environment=$PELZER_ENV -Dpelzer.log=$LOG_FILE $@"
    sh -c "exec $CMD 2>&1" > $LOG_FILE
  fi

  echo "$SCRIPT_NAME done."
}

doScript () {
	if [[ -z $LOG_DIR || -z $SCRIPT_NAME  || -z $JAVA_CLASS || -z $PELZER_ENV ]]; then
		echo "LOG_DIR, $SCRIPT_NAME, JAVA_CLASS, & PELZER_ENV must be set."
		exit -1
	fi

	LOG_FILE="$LOG_DIR/$SCRIPT_NAME.log"
  echo "logging to $LOG_FILE"

  if [[ $LOG_TYPE == "multilog" ]]; then
    # Don't use pelzer logging, use multilog
    CMD="java $JAVA_OPTS -Done-jar.main.class=$JAVA_CLASS -Dpelzer.environment=$PELZER_ENV -jar $JAR_FILE $@"
    sh -c "exec $CMD 2>&1" | multilog s1000000000 n3 $LOG_FILE
  else
    # Use pelzer logging
    CMD="java $JAVA_OPTS -Done-jar.main.class=$JAVA_CLASS -Dpelzer.log=$LOG_FILE -Dpelzer.environment=$PELZER_ENV -jar $JAR_FILE $@"
    sh -c "exec $CMD 2>&1" > $LOG_FILE
  fi

  echo "$SCRIPT_NAME done."
}
