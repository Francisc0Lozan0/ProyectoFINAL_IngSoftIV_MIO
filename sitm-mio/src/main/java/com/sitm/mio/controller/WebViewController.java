package com.sitm.mio.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller para servir las vistas HTML (map, dashboard)
 */
@Controller
public class WebViewController {
    
    /**
     * Redirige la raíz al mapa
     */
    @GetMapping("/")
    public String index() {
        return "redirect:/map.html";
    }
    
    /**
     * Sirve la vista del mapa
     */
    @GetMapping("/map")
    public String map() {
        return "forward:/map.html";
    }
    
    /**
     * Sirve el dashboard
     */
    @GetMapping("/dashboard")
    public String dashboard() {
        return "forward:/dashboard.html";
    }
}
