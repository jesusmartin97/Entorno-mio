package com.cecapi.app.core.voice

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Something outside the app (the home-screen widget) asked it to start listening right away.
 * The first screen reads this instead of giving its usual greeting, so the user hears
 * "¿En qué te puedo ayudar?" and can talk, without a long welcome in between.
 */
@Singleton
class LaunchRequests @Inject constructor() {
    @Volatile
    private var listen = false

    fun requestListen() {
        listen = true
    }

    /** True once per request. */
    fun consumeListen(): Boolean = listen.also { listen = false }
}
