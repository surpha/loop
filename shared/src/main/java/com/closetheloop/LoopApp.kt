package com.closetheloop

import com.closetheloop.data.LoopRepository

/** Implemented by each app's Application so shared code (e.g. MainViewModel.Factory) can reach the repository. */
interface LoopApp {
    val repository: LoopRepository
}
