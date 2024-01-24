CREATE TABLE IF NOT EXISTS `language`
(
    `code`         VARCHAR(8)  NOT NULL,
    `native_name`  VARCHAR(16) NOT NULL,
    `english_name` VARCHAR(16) NOT NULL,
    PRIMARY KEY (`code`)
);

CREATE TABLE IF NOT EXISTS `user`
(
    `uuid`             VARCHAR(36) NOT NULL,
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