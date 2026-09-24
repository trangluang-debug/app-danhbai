package com.example.appdemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.awt.Desktop;
import java.net.URI;

@SpringBootApplication
public class AppDemoApplication {

    public static void main(String[] args) {
        // Cấu hình hỗ trợ Desktop UI trong môi trường headless nếu cần
        System.setProperty("java.awt.headless", "false");
        SpringApplication.run(AppDemoApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void openBrowserAfterStartup() {
        String url = "http://localhost:8080";
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(url));
            } else {
                Runtime runtime = Runtime.getRuntime();
                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("win")) {
                    runtime.exec("rundll32 url.dll,FileProtocolHandler " + url);
                } else if (os.contains("mac")) {
                    runtime.exec("open " + url);
                } else if (os.contains("nix") || os.contains("nux")) {
                    runtime.exec("xdg-open " + url);
                }
            }
        } catch (Exception e) {
            System.out.println("Không thể tự động mở trình duyệt. Hãy truy cập thủ công: " + url);
        }
    }
}