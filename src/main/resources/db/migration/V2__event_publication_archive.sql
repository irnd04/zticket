CREATE TABLE IF NOT EXISTS event_publication_archive
(
    id                     BINARY(16)     NOT NULL,
    completion_attempts    INT            NOT NULL,
    completion_date        DATETIME(6),
    last_resubmission_date DATETIME(6),
    publication_date       DATETIME(6),
    event_type             VARCHAR(255),
    listener_id            VARCHAR(255),
    serialized_event       VARCHAR(4000),
    status                 VARCHAR(20),
    PRIMARY KEY (id),
    INDEX idx_event_publication_archive_completion_date (completion_date)
);
