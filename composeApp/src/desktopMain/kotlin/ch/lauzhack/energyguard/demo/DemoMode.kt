package ch.lauzhack.energyguard.demo

/**
 * Desktop demo clock (`EG_DEMO=1`). The script itself (timeline, engine settings, sample builders) is shared
 * with Android in commonMain demo/DemoScript.kt.
 * Wall-clock start of the current demo run; [restart] (F5) replays the script from zero.
 */
class DemoClock(private val clock: () -> Long = System::currentTimeMillis) {
    @Volatile private var startMs = clock()
    fun restart() { startMs = clock() }
    fun tiredness(): Double = DemoScript.tiredness(clock() - startMs)
}
