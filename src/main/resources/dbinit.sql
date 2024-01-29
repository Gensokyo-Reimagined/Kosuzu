CREATE TABLE IF NOT EXISTS `language`
(
    `code`         VARCHAR(8)  NOT NULL,
    `native_name`  VARCHAR(32) NOT NULL,
    `english_name` VARCHAR(32) NOT NULL,
    PRIMARY KEY (`code`)
);

CREATE TABLE IF NOT EXISTS `user`
(
    `uuid`             VARCHAR(36) NOT NULL,
    `last_known_name`  VARCHAR(16) NOT NULL,
    `default_language` VARCHAR(8)  NOT NULL DEFAULT 'EN-US',
    PRIMARY KEY (`uuid`),
    FOREIGN KEY (`default_language`) REFERENCES `language` (`code`) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS `multilingual`
(
    `uuid`     VARCHAR(36) NOT NULL,
    `language` VARCHAR(8)  NOT NULL,
    PRIMARY KEY (`uuid`, `language`),
    FOREIGN KEY (`uuid`) REFERENCES `user` (`uuid`) ON DELETE CASCADE,
    FOREIGN KEY (`language`) REFERENCES `language` (`code`) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS `message`
(
    `message_id` VARCHAR(36)  NOT NULL,
    `text`       VARCHAR(256) NOT NULL, -- We don't explicitly know the language of the message, so we store it here
    PRIMARY KEY (`message_id`),
    UNIQUE (`text`)
);

CREATE TABLE IF NOT EXISTS `message_translation`
(
    `message_id` VARCHAR(36)  NOT NULL,
    `language`   VARCHAR(8)   NOT NULL,
    `text`       VARCHAR(256) NOT NULL,
    PRIMARY KEY (`message_id`, `language`),
    FOREIGN KEY (`message_id`) REFERENCES `message` (`message_id`) ON DELETE CASCADE,
    FOREIGN KEY (`language`) REFERENCES `language` (`code`) ON DELETE CASCADE
);