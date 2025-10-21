package com.codapayments.echo.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class EchoController {

    @PostMapping("/echo")
    public <T> ResponseEntity<T> echo(@RequestBody T body){
        return new ResponseEntity<>(body, HttpStatus.OK);
    }
}
