#!/bin/sh

echo ----------------------
echo ALEXA PORT FOR NASCENT
echo ----------------------

platform='unknown'
unamestr=`uname`
if [[ "$unamestr" == 'Linux' ]]; then
   platform='linux'
   cp config.nascent.json config.json
   amixer -c 2 set PCM 100%
   amixer -c 3 set PCM 100%
elif [[ "$unamestr" == 'Darwin' ]]; then
   platform='darwin'
   cp config.osx.json config.json
fi
mvn exec:exec

