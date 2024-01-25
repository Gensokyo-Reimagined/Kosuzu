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
deepl-api-url: # The API link for DeepL: free edition is https://api-free.deepl.com/v2/translate, but Pro users can use https://api.deepl.com/v2/translate
deepl-api-key: # Your API key for DeepL - most likely ends with ":fx" for free users
default-language: # The default language to translate to, e.g. en-US
ratelimit:
  token_bucket_capacity: # The max number of characters that can be translated at one time
  token_refill_rate: # The number of characters that can be translated per second
storage:
  type: # Either "sqlite" or "mysql"
  sqlite:
    file: # Path to your SQLite database file, e.g. kosuzu.db. The plugin will create the tables automatically
  mysql:
    host: # Hostname of your MySQL server, e.g. localhost
    port: # Port of your MySQL server, e.g. 3306
    database: # Name of your MySQL database, e.g. kosuzu; the plugin will create the tables automatically
    username: # Username to your MySQL database
    password: # Password to your MySQL database
```