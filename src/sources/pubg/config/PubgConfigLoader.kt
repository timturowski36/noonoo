package sources.pubg.config

import config.EnvConfig

object PubgConfigLoader {

    fun loadApiKey(): String? {
        return EnvConfig.pubgApiKey()
    }
}
