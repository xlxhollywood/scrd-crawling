package org.example.naver;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.UpdateOptions;
import org.bson.Document;
import org.example.config.MongoConfig;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class KukuRoom {

    private final WebDriver driver;
    private final MongoCollection<Document> reservationCollection;
    private static final Map<String, String> THEME_URLS = Map.of(
            "신비로운 직원생활", "https://m.booking.naver.com/booking/12/bizes/1073255/items/5626256?startDateTime=",
            "실직요정 비상대책위원회", "https://m.booking.naver.com/booking/12/bizes/1073255/items/5641334?startDateTime=",
            "백순대-셀레스트", "https://m.booking.naver.com/booking/12/bizes/1073255/items/5654295?startDateTime=",
            "쿠쿠마스-2025윈터시즌-", "https://m.booking.naver.com/booking/12/bizes/1073255/items/6480799?startDateTime="
    );

    public KukuRoom(WebDriver driver) {
        this.driver = driver;
        MongoClient mongoClient = MongoConfig.getMongoClient();
        MongoDatabase database = mongoClient.getDatabase("scrd");
        this.reservationCollection = database.getCollection("naver_reservations");
    }

    public void crawlAllDates() {
        try {
            System.out.println("===== [네이버 예약 크롤링 시작] =====");
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            Calendar calendar = Calendar.getInstance();

            for (int i = 0; i < 7; i++) {  // 7일치 크롤링
                String date = sdf.format(calendar.getTime());
                for (Map.Entry<String, String> entry : THEME_URLS.entrySet()) {
                    String theme = entry.getKey();
                    String urlWithDate = entry.getValue() + date + "T00%3A00%3A00%2B09%3A00";
                    driver.get(urlWithDate);

                    try {
                        new WebDriverWait(driver, Duration.ofSeconds(10))
                                .until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".calendar_area")));

                        Thread.sleep(1000);
                        List<WebElement> timeSlots = driver.findElements(By.cssSelector("li.time_item button.btn_time:not(.unselectable)"));

                        if (timeSlots.isEmpty()) {
                            System.out.println("🚫 [" + theme + " - " + date + "] 예약 가능한 슬롯 없음. 크롤링 건너뜀.");
                        } else {
                            parseAndSave(theme, date, timeSlots);
                        }
                    } catch (TimeoutException e) {
                        System.out.println("🚫 [" + theme + " - " + date + "] 예약 가능한 슬롯 없음 (페이지 로드 실패).");
                    }
                }
                calendar.add(Calendar.DAY_OF_YEAR, 1);
            }
        } catch (Exception e) {
            System.err.println("네이버 크롤링 중 오류: " + e.getMessage());
        }
    }

    private void parseAndSave(String theme, String date, List<WebElement> timeSlots) {
        try {
            List<String> availableTimes = new ArrayList<>();
            for (WebElement timeSlot : timeSlots) {
                String timeText = timeSlot.getText().replaceAll("\\s*\\d+매", "").trim();
                if (!timeText.isEmpty()) {
                    availableTimes.add(timeText);
                }
            }

            if (!availableTimes.isEmpty()) {
                saveToDatabase(theme, date, availableTimes);
            } else {
                System.out.println("🚫 [" + theme + " - " + date + "] 예약 가능한 슬롯 없음.");
            }
        } catch (Exception e) {
            System.err.println("❌ 예약 정보 파싱 중 오류: " + e.getMessage());
        }
    }

    private void saveToDatabase(String theme, String date, List<String> availableTimes) {
        try {
            Document filter = new Document("theme", theme).append("date", date);
            Document update = new Document("$set", new Document("availableTimes", availableTimes)
                    .append("source", "naver")
                    .append("expireAt", new Date(System.currentTimeMillis() + 24L * 60 * 60 * 1000)));

            reservationCollection.updateOne(filter, update, new UpdateOptions().upsert(true));
            System.out.println("✅ Saved: " + theme + " | " + date + " | Times: " + availableTimes);
        } catch (Exception e) {
            System.err.println("❌ DB 저장 중 오류: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        System.out.println("네이버 예약 Crawling...");
        System.setProperty("webdriver.chrome.driver", "/Users/pro/Downloads/chromedriver-mac-x64/chromedriver");

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        Runnable naverTask = () -> {
            WebDriver driver = null;
            try {
                driver = new ChromeDriver();
                KukuRoom crawler = new KukuRoom(driver);
                crawler.crawlAllDates();
            } catch (Exception e) {
                System.err.println("[네이버] 크롤링 오류: " + e.getMessage());
            } finally {
                if (driver != null) driver.quit();
            }
        };

        new Thread(naverTask).start();
    }
}