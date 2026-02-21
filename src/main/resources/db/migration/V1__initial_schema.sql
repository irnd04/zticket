CREATE TABLE tickets (
    id           BIGINT       NOT NULL,
    seat_number  INT          NOT NULL,
    status       VARCHAR(10)  NOT NULL,
    queue_token  VARCHAR(255) NOT NULL,
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tickets_seat_number (seat_number),
    INDEX idx_ticket_status (status)
);

CREATE TABLE IF NOT EXISTS event_publication
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
    INDEX idx_event_publication_completion_date (completion_date)
);
