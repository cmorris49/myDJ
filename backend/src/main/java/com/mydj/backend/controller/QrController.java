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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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

    @Value("${qr.signing.secret:}")
    private String signingSecret;

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
        String owner = me.getId();
        String sig = signingSecret.isEmpty() ? "" : hmac(owner, signingSecret);
        String withOwner = appendParam(appendParam(url, "owner", owner), "sig", sig);
        return renderQr(withOwner);
    }

    @GetMapping(value = "/qr-default", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> qrDefault() throws Exception {
        if (!isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var me = spotifyService.getCurrentUserProfile();
        String owner = me.getId();
        String sig = signingSecret.isEmpty() ? "" : hmac(owner, signingSecret);
        String url = appendParam(appendParam(frontendUrl, "owner", owner), "sig", sig);
        return renderQr(url);
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
                + URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String hmac(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(raw.length * 2);
        for (byte b : raw) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
