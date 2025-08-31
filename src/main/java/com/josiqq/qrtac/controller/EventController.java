package com.josiqq.qrtac.controller;

import com.josiqq.qrtac.model.Event;
import com.josiqq.qrtac.service.EventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@Controller
@RequestMapping("/events")
public class EventController {

    @Autowired
    private EventService eventService;

    @GetMapping
    public String listPublicEvents(Model model) {
        model.addAttribute("events", eventService.findAvailableEvents());
        return "events/list";
    }

    @GetMapping("/{id}")
    public String viewEvent(@PathVariable Long id, Model model) {
        Optional<Event> event = eventService.findById(id);
        if (event.isEmpty()) {
            return "redirect:/events";
        }
        model.addAttribute("event", event.get());
        return "events/detail";
    }
}