package com.codapayments.echo.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1")
public class EchoController {

    @PostMapping("/echo")
    public <T> ResponseEntity<T> echo(@RequestBody T body, HttpServletRequest request) throws InterruptedException {
        if ("slow".equals(request.getHeader("speed"))) {
            TimeUnit.MILLISECONDS.sleep(1500);
        }
        return new ResponseEntity<>(body, HttpStatus.OK);
    }
}
