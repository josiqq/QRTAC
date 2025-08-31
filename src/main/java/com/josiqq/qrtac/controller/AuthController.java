package com.josiqq.qrtac.controller;

import com.josiqq.qrtac.model.User;
import com.josiqq.qrtac.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {

    @Autowired
    private UserService userService;

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @GetMapping("/login")
    public String loginForm(@RequestParam(value = "error", required = false) String error, 
                           Model model) {
        if (error != null) {
            model.addAttribute("error", "Usuario o contraseña incorrectos");
        }
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("user", new User());
        return "auth/register";
    }

    @PostMapping("/register")
    public String registerUser(@Valid @ModelAttribute User user, 
                              BindingResult result, 
                              Model model, 
                              RedirectAttributes redirectAttributes) {
        
        if (result.hasErrors()) {
            return "auth/register";
        }

        try {
            userService.registerUser(user);
            redirectAttributes.addFlashAttribute("success", 
                "Usuario registrado exitosamente. Puedes iniciar sesión.");
            return "redirect:/login";
        } catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage());
            return "auth/register";
        }
    }

    @GetMapping("/register/organizer")
    public String registerOrganizerForm(Model model) {
        User user = new User();
        user.setRole(User.Role.ORGANIZER);
        model.addAttribute("user", user);
        return "auth/register-organizer";
    }

    @PostMapping("/register/organizer")
    public String registerOrganizer(@Valid @ModelAttribute User user, 
                                   BindingResult result, 
                                   Model model, 
                                   RedirectAttributes redirectAttributes) {
        
        user.setRole(User.Role.ORGANIZER);
        
        if (result.hasErrors()) {
            return "auth/register-organizer";
        }

        try {
            userService.registerUser(user);
            redirectAttributes.addFlashAttribute("success", 
                "Organizador registrado exitosamente. Puedes iniciar sesión.");
            return "redirect:/login";
        } catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage());
            return "auth/register-organizer";
        }
    }

    @GetMapping("/register/client")
    public String registerClientForm(Model model) {
        User user = new User();
        user.setRole(User.Role.CLIENT);
        model.addAttribute("user", user);
        return "auth/register-client";
    }

    @PostMapping("/register/client")
    public String registerClient(@Valid @ModelAttribute User user, 
                                BindingResult result, 
                                Model model, 
                                RedirectAttributes redirectAttributes) {
        
        user.setRole(User.Role.CLIENT);
        
        if (result.hasErrors()) {
            return "auth/register-client";
        }

        try {
            userService.registerUser(user);
            redirectAttributes.addFlashAttribute("success", 
                "Cliente registrado exitosamente. Puedes iniciar sesión.");
            return "redirect:/login";
        } catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage());
            return "auth/register-client";
        }
    }
}