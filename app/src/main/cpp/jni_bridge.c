#include <jni.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

/* DolRecomp's main(), renamed at compile time (see CMakeLists.txt). */
extern int dolrecomp_main(int argc, char** argv);

JNIEXPORT jint JNICALL
Java_io_dolrecomp_android_NativeBridge_nativeRun(JNIEnv* env, jclass cls,
                                                 jobjectArray jargs, jstring jlog) {
    (void)cls;
    const char* log = (*env)->GetStringUTFChars(env, jlog, NULL);
    int fd = open(log, O_WRONLY | O_CREAT | O_APPEND, 0644);
    (*env)->ReleaseStringUTFChars(env, jlog, log);
    if (fd >= 0) {
        dup2(fd, 1);
        dup2(fd, 2);
        if (fd > 2) close(fd);
    }
    setvbuf(stdout, NULL, _IONBF, 0);
    setvbuf(stderr, NULL, _IONBF, 0);

    jsize n = (*env)->GetArrayLength(env, jargs);
    char** argv = (char**)calloc((size_t)n + 2, sizeof(char*));
    argv[0] = strdup("dolrecomp");
    for (jsize i = 0; i < n; i++) {
        jstring s = (jstring)(*env)->GetObjectArrayElement(env, jargs, i);
        const char* c = (*env)->GetStringUTFChars(env, s, NULL);
        argv[i + 1] = strdup(c);
        (*env)->ReleaseStringUTFChars(env, s, c);
        (*env)->DeleteLocalRef(env, s);
    }

    int rc = dolrecomp_main((int)n + 1, argv);

    fflush(stdout);
    fflush(stderr);
    for (jsize i = 0; i < n + 1; i++) free(argv[i]);
    free(argv);
    return rc;
}
