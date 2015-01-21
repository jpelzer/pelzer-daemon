#!/bin/bash

source $(dirname $0)/include.sh
# This is an example daemon start/stop script. Copy this file and modify 
# the following variables to make your own magical daemons

DAEMON_NAME=pidwatcher
JAVA_CLASS=com.pelzer.util.daemon.PIDWatcherDaemon
# default or multilog. You probably want default.
LOG_TYPE=default
PELZER_ENV="DEV -Dspring.panic.smtp.host=localhost -Dspring.panic.smtp.port=25 -Dspring.panic.username=foo -Dspring.panic.password=bar -Dspring.mongodb.server=localhost"

# 'start', 'stop', 'restart', etc
ACTION=$1

# doDaemon can take parameters that will be passed as arguments to java
doDaemon 
