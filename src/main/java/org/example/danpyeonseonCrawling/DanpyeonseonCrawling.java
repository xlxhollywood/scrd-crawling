package org.example.danpyeonseonCrawling;

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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.*;

public class DanpyeonseonCrawling {

    private final WebDriver driver;
    private final MongoCollection<Document> reservationCollection;
    private final Map<String, String> storeUrlMap;

    public DanpyeonseonCrawling(WebDriver driver) {
        this.driver = driver;
        MongoClient mongoClient = MongoConfig.getMongoClient();
        MongoDatabase database = mongoClient.getDatabase("scrd");
        this.reservationCollection = database.getCollection("reservation");

        this.storeUrlMap = new HashMap<>();
        this.storeUrlMap.put("강남", "https://www.dpsnnn.com/reserve_g");
        this.storeUrlMap.put("성수", "https://dpsnnn-s.imweb.me/reserve_ss");
    }

    public void crawlAllDates(String referenceDate) {
        for (Map.Entry<String, String> entry : storeUrlMap.entrySet()) {
            String zizumName = entry.getKey();
            String url = entry.getValue();
            try {
                System.out.println("===== [" + zizumName + "점] 크롤링 시작 기준일: " + referenceDate + " =====");
                driver.get(url);

                new WebDriverWait(driver, Duration.ofSeconds(10))
                        .until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".booking_view_container")));

                Thread.sleep(2000);
                parseAllDatesAndSave(zizumName, referenceDate);
            } catch (Exception e) {
                System.err.println("[" + zizumName + "점] 크롤링 중 오류: " + e.getMessage());
            }
        }
    }

    private void parseAllDatesAndSave(String zizumName, String referenceDate) {
        try {
            List<WebElement> dateCells = driver.findElements(By.cssSelector("td.booking_day"));
            for (WebElement dateCell : dateCells) {
                String dataDateAttr = dateCell.getAttribute("data-date");
                if (dataDateAttr == null || dataDateAttr.isEmpty()) continue;

                List<WebElement> bookingLists = dateCell.findElements(By.cssSelector("div.booking_list"));
                if (bookingLists.isEmpty()) continue;

                Map<String, List<String>> themeTimesMap = new HashMap<>();
                for (WebElement bookingItem : bookingLists) {
                    String classAttr = bookingItem.getAttribute("class");
                    boolean isClosed = classAttr.contains("closed") || classAttr.contains("disable");
                    if (isClosed) continue;

                    WebElement aTag = bookingItem.findElement(By.tagName("a"));
                    String rawText = aTag.getText().trim();
                    if (rawText.isEmpty() || rawText.equals("-")) continue;

                    String[] splitted = rawText.split("/");
                    if (splitted.length < 2) continue;

                    String themeName = splitted[0].trim();
                    String timePart = splitted[1].trim();
                    if (themeName.isEmpty() || timePart.isEmpty()) continue;

                    themeTimesMap.putIfAbsent(themeName, new ArrayList<>());
                    themeTimesMap.get(themeName).add(timePart);
                }

                if (!themeTimesMap.isEmpty()) {
                    for (Map.Entry<String, List<String>> entry : themeTimesMap.entrySet()) {
                        String theme = entry.getKey();
                        List<String> times = entry.getValue();
                        System.out.println("✅ [" + zizumName + "점] 날짜: " + dataDateAttr + " | 테마: " + theme + " | times: " + times);
                        saveThemeData(zizumName, theme, dataDateAttr, times);
                    }
                }
            }
        } catch (TimeoutException e) {
            System.out.println("❌ 예약 정보를 찾지 못함.");
        } catch (Exception e) {
            System.err.println("❌ parseAllDatesAndSave 오류: " + e.getMessage());
        }
    }

    private void saveThemeData(String zizumName, String themeName, String date, List<String> availableTimes) {
        try {
            Document filter = new Document("themeName", themeName)
                    .append("zizum", zizumName)
                    .append("date", date);

            long expireTime = System.currentTimeMillis() + 24L * 60 * 60 * 1000;
            Date expireAt = new Date(expireTime);

            Document update = new Document("$set", new Document("availableTimes", availableTimes)
                    .append("store", "danpyeonseon")
                    .append("zizum", zizumName)
                    .append("themeName", themeName)
                    .append("expireAt", expireAt));

            reservationCollection.updateOne(filter, update, new UpdateOptions().upsert(true));
            System.out.println("Saved => [지점:" + zizumName + "점 | 테마:" + themeName + " | 날짜:" + date + "]");
        } catch (Exception e) {
            System.err.println("Error saving theme data: " + e.getMessage());
        }
    }
}