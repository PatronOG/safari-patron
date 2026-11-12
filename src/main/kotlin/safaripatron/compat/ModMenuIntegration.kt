package safaripatron.compat

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import safaripatron.ui.MainMenuScreen

/** Clicking the config button in Mod Menu opens the /sp menu. Only loaded when Mod Menu is installed. */
class ModMenuIntegration : ModMenuApi {
    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> =
        ConfigScreenFactory<MainMenuScreen> { parent -> MainMenuScreen(parent) }
}
