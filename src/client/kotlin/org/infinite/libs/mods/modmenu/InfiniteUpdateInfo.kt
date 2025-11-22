package org.infinite.libs.mods.modmenu
import com.terraformersmc.modmenu.api.UpdateChannel
import com.terraformersmc.modmenu.api.UpdateInfo
import net.minecraft.text.Text
import org.jetbrains.annotations.Nullable

data class InfiniteUpdateInfo(
    private val isUpdate: Boolean,
    private val link: String,
    private val channel: UpdateChannel,
    private val message: Text? = null,
) : UpdateInfo {
    override fun isUpdateAvailable(): Boolean = isUpdate

    override fun getUpdateMessage(): @Nullable Text? = message

    override fun getDownloadLink(): String = link

    override fun getUpdateChannel(): UpdateChannel = channel
}
