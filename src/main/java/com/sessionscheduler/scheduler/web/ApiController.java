package com.sessionscheduler.scheduler.web;

import com.sessionscheduler.scheduler.service.BookingException;
import com.sessionscheduler.scheduler.service.BookingService;
import com.sessionscheduler.scheduler.service.ProctorAvailability;
import com.sessionscheduler.scheduler.service.TimeZones;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** JSON for the page dropdowns: Region → Time zone on the booking form, proctor availability on the portal. */
@RestController
@RequestMapping("/api")
public class ApiController {
    private final BookingService bookings;

    public ApiController(BookingService bookings) {
        this.bookings = bookings;
    }

    @GetMapping("/timezones/regions")
    public List<String> regions() {
        return TimeZones.regions();
    }

    @GetMapping("/timezones")
    public List<String> zones(@RequestParam("region") String region) {
        return TimeZones.zones(region);
    }

    @GetMapping("/proctors/available")
    public List<ProctorAvailability> available(@RequestParam("sessionId") long sessionId) {
        try {
            return bookings.availability(sessionId);
        } catch (BookingException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }
}
