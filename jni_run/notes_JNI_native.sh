cd ./src

javah -jni gcmServer.jni_run

#after generated, need to change the cpp file method name same as header file also include header file
g++ "-I/System/Library/Frameworks/JavaVM.framework/Versions/CurrentJDK/Headers/" "-I/System/Library/Frameworks/JavaVM.framework/Versions/A/Headers/" -c jni_run.cpp

 g++ -dynamiclib -o libjni_run.jnilib jni_run.o

 java gcmServer.jni_run