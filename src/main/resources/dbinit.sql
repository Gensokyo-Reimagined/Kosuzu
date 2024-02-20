CREATE TABLE IF NOT EXISTS `language`
(
    `code`         VARCHAR(8)  NOT NULL, -- ISO 639-1 code
    `native_name`  VARCHAR(32) NOT NULL, -- The name of the language in the language itself
    `english_name` VARCHAR(32) NOT NULL, -- The name of the language in English
    PRIMARY KEY (`code`)
);

CREATE TABLE IF NOT EXISTS `user`
(
    `uuid`             VARCHAR(36) NOT NULL, -- Minecraft UUID
    `last_known_name`  VARCHAR(16) NOT NULL,
    `default_language` VARCHAR(8)  NOT NULL DEFAULT 'EN-US',
    PRIMARY KEY (`uuid`),
    FOREIGN KEY (`default_language`) REFERENCES `language` (`code`) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS `multilingual`
(
    `uuid`     VARCHAR(36) NOT NULL, -- Surrogate key
    `language` VARCHAR(8)  NOT NULL, -- ISO 639-1 code
    PRIMARY KEY (`uuid`, `language`),
    FOREIGN KEY (`uuid`) REFERENCES `user` (`uuid`) ON DELETE CASCADE,
    FOREIGN KEY (`language`) REFERENCES `language` (`code`) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS `message`
(
    `uuid`       VARCHAR(36)  NOT NULL,
    `text`       VARCHAR(256) NOT NULL, -- We don't explicitly know the language of the message, so we store it here
    `language`   VARCHAR(8), -- ISO 639-1 code of the message, NULL if the language is unknown
    PRIMARY KEY (`uuid`),
    UNIQUE (`text`)
);

CREATE TABLE IF NOT EXISTS `message_translation`
(
    `uuid`       VARCHAR(36)  NOT NULL, -- Surrogate key
    `message_id` VARCHAR(36)  NOT NULL, -- To which message this translation belongs
    `language`   VARCHAR(8)   NOT NULL, -- ISO 639-1 code of translation
    `text`       VARCHAR(512) NOT NULL, -- The translated text
    PRIMARY KEY (`uuid`),
    FOREIGN KEY (`message_id`) REFERENCES `message` (`uuid`) ON DELETE CASCADE,
    FOREIGN KEY (`language`) REFERENCES `language` (`code`) ON DELETE CASCADE
);

CREATE UNIQUE INDEX `message_translation_index` ON `message_translation` (`message_id`, `language`);

CREATE TABLE IF NOT EXISTS `user_message`
(
    `uuid`       VARCHAR(36) NOT NULL, -- Surrogate key
    `message_id` VARCHAR(36) NOT NULL, -- To which message this translation belongs
    `json_msg`   VARCHAR(2048) NOT NULL, -- The JSON message from Minecraft
    PRIMARY KEY (`uuid`),
    FOREIGN KEY (`message_id`) REFERENCES `message` (`uuid`) ON DELETE CASCADE
);

CREATE INDEX `user_message_json_msg_idx` ON `user_message` (json_msg(255));
