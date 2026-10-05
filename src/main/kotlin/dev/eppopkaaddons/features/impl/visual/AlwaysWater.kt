package dev.eppopkaaddons.features.impl.visual

import dev.eppopkaaddons.features.Feature


/**
 * Client-side "always raining" toggle.
 *
 * The actual work happens in two mixins (see dev.eppopkaaddons.mixin):
 *  - LevelRainMixin        -> forces the client rain level to 1.0 (it always rains)
 *  - WeatherEffectRendererMixin -> bypasses the heightmap (roof) check and forces
 *                             rainy precipitation, so rain falls even indoors
 *
 * Everything is purely visual/client side - the server weather is untouched.
 */
object AlwaysWater: Feature("It always rains - even under a roof. Client side only.", name = "Always Water")
