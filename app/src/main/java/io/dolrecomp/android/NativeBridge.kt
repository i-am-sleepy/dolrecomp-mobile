package io.dolrecomp.android

object NativeBridge {
    init { System.loadLibrary("dolrecomp_jni") }

    /** Runs DolRecomp's main() with [args] (argv[0] is added natively). stdout/stderr go to [logPath]. */
    @JvmStatic
    external fun nativeRun(args: Array<String>, logPath: String): Int
}
