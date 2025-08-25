package com.mydj.backend.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DownloadController {

  @GetMapping("/download")
  public ResponseEntity<Void> downloadRedirect() {
    String url = "https://github.com/cmorris49/myDJ/releases/download/v1.0.0/myDJ-win.zip";
    return ResponseEntity.status(HttpStatus.FOUND)
        .header(HttpHeaders.LOCATION, url)
        .build();
  }
}
