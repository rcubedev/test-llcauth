package org.limelightcreatives.auth.web;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class PageModel {

    private final Assets assets;

    public PageModel(Assets assets) {
        this.assets = assets;
    }

    @ModelAttribute("assets")
    public Assets assets() {
        return this.assets;
    }
}
