#!/bin/bash

export CLASSPATH=.:dist/*:lib/*
java -Xmx512M -server -Dnet.sf.odinms.wzpath=wz -Dfile.encoding=UTF-8 server.Start
