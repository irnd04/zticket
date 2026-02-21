package kr.jemi.zticket.queue.application.service;

import kr.jemi.zticket.queue.application.port.out.ActiveUserPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class ActiveUserServiceTest {

    @Mock
    private ActiveUserPort activeUserPort;

    private ActiveUserService activeUserService;

    @BeforeEach
    void setUp() {
        activeUserService = new ActiveUserService(activeUserPort);
    }

    @Test
    @DisplayName("isActive: active 유저는 true를 반환한다")
    void shouldReturnTrueForActiveUser() {
        given(activeUserPort.isActive("token-1")).willReturn(true);

        assertThat(activeUserService.isActive("token-1")).isTrue();
    }

    @Test
    @DisplayName("isActive: active가 아닌 유저는 false를 반환한다")
    void shouldReturnFalseForInactiveUser() {
        given(activeUserPort.isActive("token-1")).willReturn(false);

        assertThat(activeUserService.isActive("token-1")).isFalse();
    }

    @Test
    @DisplayName("deactivate: ActiveUserPort에 위임한다")
    void shouldDelegateDeactivate() {
        activeUserService.deactivate("token-1");

        then(activeUserPort).should().deactivate("token-1");
    }
}
