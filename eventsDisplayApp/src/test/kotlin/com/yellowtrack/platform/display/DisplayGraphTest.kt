package com.yellowtrack.platform.display

import com.yellowtrack.platform.core.data.auth.AuthRepository
import com.yellowtrack.platform.core.data.event.EventsApi
import com.yellowtrack.platform.feature.display.displayModule
import org.koin.core.annotation.KoinInternalApi
import org.koin.test.verify.verify
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The wiring, checked without a device.
 *
 * A missing Koin binding compiles. It fails when the screen is first composed, which for
 * this application is a tablet on a table at somebody's event — the worst place to discover
 * it and the one nobody is watching. Nothing else here can catch it: the activity, the
 * application and the composable all typecheck whether or not the graph can produce a view
 * model.
 *
 * This does not build anything. It checks that every constructor parameter the definition
 * needs is either provided by a module or named below as coming from elsewhere.
 */
class DisplayGraphTest {
    /**
     * That the definition is there at all.
     *
     * [verify] alone does not say this: it checks the definitions a module *has*, so a module
     * that declares nothing passes it trivially. Deleting the view model's registration
     * outright left the check green, which would have shipped an application whose one screen
     * cannot be composed.
     *
     * Reading a module's own definitions is internal to Koin. Opted into deliberately and
     * only here: the alternative is not checking that the one screen this application has
     * can be composed, and a test that breaks loudly when Koin changes is the better trade.
     * Nothing in the application itself touches this.
     */
    @OptIn(KoinInternalApi::class)
    @Test
    fun `the display view model is registered`() {
        val declared = displayModule.mappings.values.map { it.beanDefinition.primaryType.simpleName }

        assertEquals(listOf("DisplayViewModel"), declared)
    }

    @Test
    fun `the display view model can be built from the graph`() {
        displayModule.verify(
            // Supplied by the modules the host application installs alongside this one —
            // `dataModule` and `networkModule`, through `initKoinAndroid`. Naming them here
            // rather than installing those modules keeps this a check of *this* module's
            // definition, which is the part that changes.
            extraTypes = listOf(EventsApi::class, AuthRepository::class),
        )
    }
}
