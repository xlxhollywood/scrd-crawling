package org.example.doorescape;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.example.config.MongoConfig;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.*;

public class DoorEscapeCrawling {
    private final WebDriver driver;
    private final MongoCollection<Document> reservationCollection;

    public DoorEscapeCrawling(WebDriver driver) {
        this.driver = driver;
        // Mongo DB connection
        MongoClient mongoClient = MongoConfig.getMongoClient();
        MongoDatabase database = mongoClient.getDatabase("scrd");
        this.reservationCollection = database.getCollection("reservation");
    }

    public void crawlThemesForDate(String date) {
        Map<String, String> zizumList = new HashMap<>();
        // 예시: 신논현 레드점만
        zizumList.put("NeZqzMtPCBsSvbAq", "신논현 레드점");

        for (Map.Entry<String, String> entry : zizumList.entrySet()) {
            String keycode = entry.getKey();
            String zizumName = entry.getValue();

            try {
                driver.get("https://doorescape.co.kr/reservation.html?keycode=" + keycode);

                new WebDriverWait(driver, Duration.ofSeconds(10))
                        .until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));

                System.out.println("웹페이지 로딩 완료: " + zizumName);

                closeAllModalsIfPresent();

                // 부트스트랩 'update' 대신, 달력 셀 직접 클릭
                selectDateByCalendarClick(date, zizumName);

                // 혹은 추가로 약간 대기
                Thread.sleep(4000);

                // 테마 및 예약가능 시간 fetch
                Map<String, List<String>> themeTimesMap = fetchAvailableTimesWithTheme();

                if (themeTimesMap.isEmpty()) {
                    System.out.println("⚠️ 예약 가능한 테마 없음: " + zizumName + " | " + date);
                }

                for (Map.Entry<String, List<String>> themeEntry : themeTimesMap.entrySet()) {
                    if (themeEntry.getKey().isEmpty()) {
                        System.out.println("⚠️ 테마 이름 없음! 점검 필요: " + zizumName + " | " + date);
                    }
                    System.out.println("예약 가능 시간 - 테마: " + themeEntry.getKey() + " | 시간: " + themeEntry.getValue());
                    saveThemeData(zizumName, themeEntry.getKey(), date, themeEntry.getValue());
                }
                System.out.println("Data saved for " + zizumName + " on " + date);

            } catch (Exception e) {
                System.err.println("Error while crawling " + zizumName + " on " + date + ": " + e.getMessage());
            }
        }
    }

    public void closeAllModalsIfPresent() {
        String[] modalIds = {"cta-modal-0", "cta-modal-1", "cta-modal-2"};
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(3));

        for (String modalId : modalIds) {
            try {
                WebElement modal = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id(modalId)));
                System.out.println("✅ 모달 나타남: " + modalId);

                WebElement closeButton = wait.until(ExpectedConditions.elementToBeClickable(
                        By.xpath("//div[@id='" + modalId + "']//button[@data-dismiss='modal']")));
                closeButton.click();
                System.out.println("✅ 모달 닫기 버튼 클릭: " + modalId);

            } catch (TimeoutException e) {
                System.out.println("⚠️ 모달이 나타나지 않음 또는 닫기버튼이 클릭 불가능 (ID: " + modalId + ")");
            } catch (Exception e) {
                System.out.println("❌ 모달 닫기 실패: " + e.getMessage());
            }
        }
    }

    /**
     * 날짜 셀을 직접 클릭하는 버전. (부트스트랩 datepicker 'update()' 대신)
     */
    private void selectDateByCalendarClick(String dateString, String location) {
        try {
            // 1) datepicker input 클릭 (달력 열기)
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));
            WebElement datepickerInput = wait.until(ExpectedConditions.elementToBeClickable(By.id("datepicker")));

            // 스크롤/JS Click으로 "element click intercepted" 회피
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", datepickerInput);
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", datepickerInput);

            // 2) .day 요소가 뜰 때까지 대기
            String cssForDay = String.format(".day[data-date='%s']", dateString);
            WebElement dayElement = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector(cssForDay)));

            // 3) JS로 날짜셀 클릭
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", dayElement);

            System.out.println("✅ 날짜 셀 클릭 성공: " + dateString + " | " + location);

        } catch (Exception e) {
            System.err.println("❌ 날짜 셀 클릭 방식 실패: " + dateString + " | " + location + " | 오류: " + e.getMessage());
        }
    }

    private Map<String, List<String>> fetchAvailableTimesWithTheme() {
        Map<String, List<String>> themeTimesMap = new HashMap<>();
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        try {
            for (int attempt = 1; attempt <= 3; attempt++) {
                List<WebElement> themeBoxes = wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.cssSelector(".single-events")));

                if (!themeBoxes.isEmpty()) {
                    for (WebElement themeBox : themeBoxes) {
                        WebElement themeNameElement = themeBox.findElement(By.cssSelector(".events-text-title h4"));
                        wait.until(ExpectedConditions.visibilityOf(themeNameElement));
                        String themeName = themeNameElement.getText().trim();

                        List<WebElement> timeElements = themeBox.findElements(By.cssSelector("button.btn-primary.slot"));
                        List<String> availableTimes = new ArrayList<>();
                        for (WebElement timeElement : timeElements) {
                            availableTimes.add(timeElement.getText().split("\n")[0].trim());
                        }
                        themeTimesMap.put(themeName, availableTimes);
                    }
                    break;
                }
                Thread.sleep(4000);
            }
        } catch (Exception e) {
            System.err.println("Error fetching theme times: " + e.getMessage());
        }

        return themeTimesMap;
    }

    private void saveThemeData(String zizumName, String themeName, String date, List<String> availableTimes) {
        try {
            Document filter = new Document("themeName", themeName)
                    .append("zizum", zizumName)
                    .append("date", date);

            long expireTime = System.currentTimeMillis() + 24 * 60 * 60 * 1000;
            Date expireAt = new Date(expireTime);

            Document update = new Document("$set", new Document("availableTimes", availableTimes)
                    .append("store", "doorescape")
                    .append("zizum", zizumName)
                    .append("themeName", themeName)
                    .append("expireAt", expireAt));

            reservationCollection.updateOne(filter, update, new com.mongodb.client.model.UpdateOptions().upsert(true));

            System.out.println("Saved: " + themeName + " (지점: " + zizumName + ", 날짜: " + date + ")");
        } catch (Exception e) {
            System.err.println("Error saving theme data: " + e.getMessage());
        }
    }
}
