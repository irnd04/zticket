package kr.jemi.zticket.ticket.application.port.out;

public interface ActiveUserCheckPort {

    boolean isActive(String token);

    void deactivate(String token);
}
