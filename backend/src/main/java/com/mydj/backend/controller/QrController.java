package com.mydj.backend.controller;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.mydj.backend.service.SpotifyService;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

@RestController
public class QrController {

    private final SpotifyService spotifyService;
    public QrController(SpotifyService spotifyService) {
        this.spotifyService = spotifyService;
    }
    @Value("${frontend.url:https://example.com/}")
    private String frontendUrl;

    private boolean isAuthenticated() {
        try {
            spotifyService.getCurrentUserProfile();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @GetMapping(value = "/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> qr(@RequestParam String url) throws Exception {
        if (!isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var me = spotifyService.getCurrentUserProfile();
        String withOwner = appendParam(url, "owner", me.getId());
        return renderQr(withOwner);
    }

    @GetMapping(value = "/qr-default", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> qrDefault() throws Exception {
        if (!isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var me = spotifyService.getCurrentUserProfile();
        String url = appendParam(frontendUrl, "owner", me.getId());
        return qr(url);
    }

    private ResponseEntity<byte[]> renderQr(String data) throws Exception {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.MARGIN, 1);
        BitMatrix matrix = new MultiFormatWriter().encode(
                data, BarcodeFormat.QR_CODE, 600, 600, hints);
        BufferedImage img = MatrixToImageWriter.toBufferedImage(matrix);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", out);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.IMAGE_PNG)
                .body(out.toByteArray());
    }

    private static String appendParam(String base, String key, String value) {
        String sep = base.contains("?") ? "&" : "?";
        return base + sep
                + URLEncoder.encode(key, StandardCharsets.UTF_8)
                + "="
                + URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
