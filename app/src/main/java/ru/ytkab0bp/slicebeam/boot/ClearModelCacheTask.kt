package ru.ytkab0bp.slicebeam.boot

import ru.ytkab0bp.slicebeam.SliceBeam

class ClearModelCacheTask : BootTask(Runnable {
    val cache = SliceBeam.getModelCacheDir()
    ModelCacheCleanup.removeStaleEntries(cache)
}) {
    init {
        nonCritical = true
        onWorker()
    }
}
