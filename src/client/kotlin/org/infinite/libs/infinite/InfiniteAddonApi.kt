package org.infinite.libs.infinite

import org.infinite.InfiniteClient

object InfiniteAddonApi {
    fun registerAddon(addon: InfiniteAddon) {
        InfiniteClient.loadedAddons.add(addon)
    }
}
