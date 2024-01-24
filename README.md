# Kosuzu
A plugin to provide in-game translation services with Minecraft.

## Features
This plugin provides the following features:
- [x] Translation of chat messages
- [x] Per-user language settings
- [x] Translation on demand
  - Simply click on a message to translate it
- [ ] Automatic translation
  - Note that for DeepL, token usage will rapidly increase with this feature enabled

For storing user settings, SQLite or MySQL/MariaDB can be used.
For the latter, credentials must be provided in the `config.yml` file.

For translation, two systems are currently used: [DeepLX](https://github.com/OwO-Network/PyDeepLX) and the normal [DeepL API](https://deepl.com/).
DeepLX allows for translation without the need for an API key, but has very strict rate limits and may not work for some IP addresses.
Future versions of this plugin will allow for the use of other translation services.

## Configuration (as of 1.1.0)

```yaml

```