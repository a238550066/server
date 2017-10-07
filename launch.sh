#!/bin/bash

export CLASSPATH=.:dist/*:lib/*
java -Xmx256M -server -Dnet.sf.odinms.wzpath=wz -Dfile.encoding=UTF-8 server.Start
