package sources.claude.config

import config.EnvConfig

object ClaudeConfigLoader {

    fun loadApiKey(): String? {
        return EnvConfig.claudeApiKey()
    }
}
