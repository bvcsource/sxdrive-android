#!/bin/sh
GENDIR="$(dirname $0)/app/src/main/java/com/skylable/sx/jni"
WRAPPER="$(dirname $0)/app/src/main/jni/sx_wrap.cpp"
SI="$(dirname $0)/app/src/main/jni/sx.i"
rm -fr $GENDIR $WRAPPER
mkdir -p $GENDIR
swig -c++ -I../libsxclient/include -java -package com.skylable.sx.jni -outdir $GENDIR -o $WRAPPER $SI
