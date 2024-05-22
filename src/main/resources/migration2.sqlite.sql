CREATE TABLE IF NOT EXISTS `user_message_lookup`
(
    `uuid`             VARCHAR(36) NOT NULL, -- Quickly generate a UUID when a user sends a message
    `user_message_id`  VARCHAR(36) NOT NULL, -- The actual UUID of the message (once written into `user_message`)
    PRIMARY KEY (`uuid`),
    FOREIGN KEY (`user_message_id`) REFERENCES `user_message` (`uuid`) ON DELETE CASCADE
);

INSERT INTO `user_message_lookup` SELECT `uuid`, `uuid` FROM `user_message`;
