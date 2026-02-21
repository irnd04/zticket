package kr.jemi.zticket.integration;

import kr.jemi.zticket.queue.infrastructure.in.scheduler.AdmissionScheduler;
import kr.jemi.zticket.common.scheduler.EventResubmitScheduler;
import kr.jemi.zticket.ticket.infrastructure.out.persistence.TicketJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    @ServiceConnection
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    static {
        mysql.start();
        redis.start();
    }

    @MockitoBean
    AdmissionScheduler admissionScheduler;

    @MockitoBean
    EventResubmitScheduler eventResubmitScheduler;

    @Autowired
    protected StringRedisTemplate redisTemplate;

    @Autowired
    protected TicketJpaRepository ticketJpaRepository;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUp() {
        redisTemplate.execute((RedisCallback<Object>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
        ticketJpaRepository.deleteAll();
        jdbcTemplate.execute("DELETE FROM event_publication");
    }
}
