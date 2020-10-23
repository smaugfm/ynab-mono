package com.github.smaugfm.events

import com.github.smaugfm.settings.Mappings

abstract class EventHandlerBase(val name: String, protected val mappings: Mappings) : EventHandlerCreator<Event> {
    final override fun create(dispatch: Dispatch): EventHandler = object : IGenericEventHandler<Event> {
        override val name = this@EventHandlerBase.name

        override suspend fun handle(event: Event): Boolean {
            return handle(dispatch, event)
        }
    }

    protected abstract suspend fun handle(dispatch: Dispatch, e: Event): Boolean
}
