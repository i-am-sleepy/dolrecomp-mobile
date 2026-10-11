package io.dolrecomp.android

enum class Platform(val label: String) {
    GAMECUBE("GameCube"), WII("Wii"), WIIU("Wii U (unfinished upstream)")
}

enum class Kind { SWITCH, CHOICE, NUMBER, TEXT, FILE }

data class OptionSpec(
    val key: String,
    val group: String,
    val label: String,
    val help: String,
    val kind: Kind,
    val flag: String,
    val choices: List<String> = emptyList(),
    /** TEXT value is a file name resolved inside the project folder (output paths). */
    val relPath: Boolean = false,
    val advanced: Boolean = false,
)

/** One entry per DolRecomp command-line flag. Blank value = leave to the tool's default. */
object Options {
    val specs = listOf(
        OptionSpec("cpu", "Core", "CPU profile", "Blank = tool default (broadway). Wii U projects add espresso automatically.", Kind.CHOICE, "--cpu", listOf("gekko", "broadway", "espresso")),
        OptionSpec("backend", "Core", "Code backend", "c is the default. llvm is not built into this app.", Kind.CHOICE, "--backend", listOf("c", "llvm")),
        OptionSpec("jobs", "Core", "Worker jobs", "Parallel workers for split C output. Try 2-4 on a phone.", Kind.NUMBER, "--jobs"),
        OptionSpec("rel_base", "Core", "REL base address", "Hex override for the first REL, e.g. 0x80500000.", Kind.TEXT, "--rel-base"),

        OptionSpec("map", "Inputs", "Linker MAP file", "Optional function names (DOL input only).", Kind.FILE, "--map"),
        OptionSpec("config", "Inputs", "Patch config (TOML)", "Function patch / replacement configuration.", Kind.FILE, "--config"),
        OptionSpec("native_entry_points", "Inputs", "Native entry points", "File of hex guest PCs to force as prepared entry points.", Kind.FILE, "--native-entry-points", advanced = true),

        OptionSpec("runtime", "Runtime", "Module runtime", "Which runtime the generated module targets.", Kind.CHOICE, "--runtime", listOf("recompcore", "moderngekko")),
        OptionSpec("game_id", "Runtime", "Native game ID", "Six-character native module game ID.", Kind.TEXT, "--game-id"),

        OptionSpec("semantics", "Accuracy", "Float semantics", "exact is the default; fast trades accuracy for speed.", Kind.CHOICE, "--semantics", listOf("exact", "fast")),
        OptionSpec("state_in_memory", "Accuracy", "Keep guest state in CPUState", "", Kind.SWITCH, "--state-in-memory"),
        OptionSpec("no_state_in_memory", "Accuracy", "Do not keep guest state in CPUState", "", Kind.SWITCH, "--no-state-in-memory", advanced = true),
        OptionSpec("instrumentation", "Accuracy", "Instrumentation", "lockstep compiles state-journal objects.", Kind.CHOICE, "--instrumentation", listOf("none", "lockstep"), advanced = true),

        OptionSpec("native_abi", "LLVM (not in this build)", "Native ABI policy", "Only used by the LLVM backend.", Kind.CHOICE, "--native-abi", listOf("unrestricted", "compact", "off"), advanced = true),
        OptionSpec("targets", "LLVM (not in this build)", "Targets", "host, x86-64-v2, x86-64-v3, aarch64, aarch64-a57", Kind.TEXT, "--targets", advanced = true),

        OptionSpec("profile_generate", "Profiling", "Emit profile counters to", "File name inside the project folder.", Kind.TEXT, "--profile-generate", relPath = true, advanced = true),
        OptionSpec("profile_use", "Profiling", "Use profile", "Existing profile to optimize with.", Kind.FILE, "--profile-use", advanced = true),
        OptionSpec("range_profile", "Profiling", "Range profile (runtime CSV)", "Emit only hot LLVM ranges.", Kind.FILE, "--range-profile", advanced = true),
        OptionSpec("range_profile_coverage", "Profiling", "Range coverage %", "Native-cycle coverage target (default 99).", Kind.NUMBER, "--range-profile-coverage", advanced = true),
        OptionSpec("range_profile_min_samples", "Profiling", "Min samples", "Ignore colder ranges below N samples.", Kind.NUMBER, "--range-profile-min-samples", advanced = true),
        OptionSpec("range_profile_miss_min_samples", "Profiling", "Miss min samples", "Ignore colder module misses below N samples.", Kind.NUMBER, "--range-profile-miss-min-samples", advanced = true),
        OptionSpec("range_profile_neighbors", "Profiling", "Neighbor ranges", "Include N adjacent ranges on each side.", Kind.NUMBER, "--range-profile-neighbors", advanced = true),
        OptionSpec("range_profile_call_closure_depth", "Profiling", "Call closure depth", "Follow direct calls to depth N (default unlimited).", Kind.NUMBER, "--range-profile-call-closure-depth", advanced = true),
        OptionSpec("range_profile_successor_closure_depth", "Profiling", "Successor closure depth", "Follow static CFG successors to depth N (default 0).", Kind.NUMBER, "--range-profile-successor-closure-depth", advanced = true),

        OptionSpec("partition_instructions", "Partitioning", "Partition size (instructions)", "Reproducible object partition size.", Kind.NUMBER, "--partition-instructions", advanced = true),
        OptionSpec("partition_seed", "Partitioning", "Partition seed", "Stable partition naming seed.", Kind.NUMBER, "--partition-seed", advanced = true),
    )

    val groups: List<String> = specs.map { it.group }.distinct()
}

/** Splits a free-form args string, honouring simple "double quotes". */
fun tokenize(s: String): List<String> {
    val out = mutableListOf<String>()
    val cur = StringBuilder()
    var quoted = false
    for (c in s) {
        when {
            c == '"' -> quoted = !quoted
            c.isWhitespace() && !quoted -> { if (cur.isNotEmpty()) { out += cur.toString(); cur.clear() } }
            else -> cur.append(c)
        }
    }
    if (cur.isNotEmpty()) out += cur.toString()
    return out
}
