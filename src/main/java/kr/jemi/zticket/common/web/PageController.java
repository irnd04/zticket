package kr.jemi.zticket.common.web;

import kr.jemi.zticket.seat.application.SeatService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    private final SeatService seatService;

    public PageController(SeatService seatService) {
        this.seatService = seatService;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("remainingSeats", seatService.getAvailableCount());
        return "index";
    }

    @GetMapping("/queue")
    public String queue() {
        return "queue";
    }

    @GetMapping("/purchase")
    public String purchase() {
        return "purchase";
    }

    @GetMapping("/confirmation")
    public String confirmation() {
        return "confirmation";
    }
}
