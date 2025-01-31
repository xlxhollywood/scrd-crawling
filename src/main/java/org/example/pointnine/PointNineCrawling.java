package org.example.pointnine;

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

public class PointNineCrawling {
    private final WebDriver driver;
    private final MongoCollection<Document> reservationCollection;

    public PointNineCrawling(WebDriver driver) {
        this.driver = driver;

        // MongoDB 연결 (scrd 데이터베이스)
        MongoClient mongoClient = MongoConfig.getMongoClient();
        MongoDatabase database = mongoClient.getDatabase("scrd");
        this.reservationCollection = database.getCollection("reservation");
    }

    /**
     * 모든 지점의 데이터를 수집
     */
    public void crawlThemesForDate(String date) {
        // 포인트나인 지점 목록 (s_zizum 값)
        Map<String, String> zizumList = new HashMap<>();
        zizumList.put("1", "강남점");
        zizumList.put("4", "강남2호점");
        zizumList.put("5", "건대점");
        zizumList.put("6", "홍대점");

        for (Map.Entry<String, String> entry : zizumList.entrySet()) {
            String zizumId = entry.getKey();
            String zizumName = entry.getValue();

            try {

                // 예약 페이지 접속
                driver.get("https://point-nine.com/layout/res/home.php?go=rev.make&s_zizum=1");

                System.out.println("Crawling for " + zizumName + " (ID: " + zizumId + "), Date: " + date);

                // 날짜 선택
                selectDate(date);

                // 지점 선택
                selectZizum(zizumId);

                // 테마별 예약 가능 시간 크롤링
                Map<String, List<String>> themeTimesMap = fetchAvailableTimesWithTheme();

                // MongoDB에 저장
                for (Map.Entry<String, List<String>> themeEntry : themeTimesMap.entrySet()) {
                    String themeName = themeEntry.getKey();
                    List<String> availableTimes = themeEntry.getValue();

                    saveThemeData(zizumName, themeName, date, availableTimes);
                }

                System.out.println("Data saved for " + zizumName + " on " + date);
            } catch (Exception e) {
                System.err.println("Error while crawling " + zizumName + " on " + date + ": " + e.getMessage());
            }
        }
    }

    /**
     * 날짜 변경
     */
    private void selectDate(String date) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        // JavaScript를 사용하여 날짜 변경
        js.executeScript(String.format("document.querySelector('input[name=\"rev_days\"]').value = '%s';", date));
        js.executeScript("fun_rev_change();");

        // 변경된 데이터가 로드될 때까지 대기
        new WebDriverWait(driver, Duration.ofSeconds(10))
                .until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".theme_box")));
    }

    /**
     * 지점 변경
     */
    private void selectZizum(String zizumId) {
        WebElement zizumSelect = driver.findElement(By.name("s_zizum"));
        new WebDriverWait(driver, Duration.ofSeconds(5)).until(ExpectedConditions.elementToBeClickable(zizumSelect));

        // JavaScript로 지점 변경
        JavascriptExecutor js = (JavascriptExecutor) driver;
        js.executeScript(String.format("document.querySelector('select[name=\"s_zizum\"]').value = '%s';", zizumId));
        js.executeScript("fun_rev_change();");

        // 데이터 로딩 대기
        new WebDriverWait(driver, Duration.ofSeconds(10))
                .until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".theme_box")));
    }

    /**
     * 예약 가능한 시간 크롤링
     */
    private Map<String, List<String>> fetchAvailableTimesWithTheme() {
        Map<String, List<String>> themeTimesMap = new HashMap<>();
        try {
            // 모든 테마 박스 가져오기
            List<WebElement> themeBoxes = driver.findElements(By.cssSelector(".theme_box"));

            for (WebElement themeBox : themeBoxes) {
                // 테마 이름 가져오기
                String themeName = themeBox.findElement(By.cssSelector(".h3_theme")).getText().trim();

                // 예약 가능한 시간 가져오기
                List<WebElement> availableTimeElements = themeBox.findElements(By.cssSelector(".reserve_Time li a:not(.end) .time"));

                List<String> availableTimes = new ArrayList<>();
                for (WebElement timeElement : availableTimeElements) {
                    String time = timeElement.getText().trim();
                    availableTimes.add(time);
                }

                // 테마 이름과 예약 가능한 시간대 저장
                themeTimesMap.put(themeName, availableTimes);
            }
        } catch (Exception e) {
            System.err.println("Error while fetching theme times: " + e.getMessage());
        }

        return themeTimesMap;
    }

    /**
     * MongoDB에 예약 가능 시간 저장
     */
    private void saveThemeData(String zizumName, String themeName, String date, List<String> availableTimes) {
        try {
            // 조건: themeName, zizumName, date가 동일한 데이터 찾기
            Document filter = new Document("themeName", themeName)
                    .append("zizum", zizumName)
                    .append("date", date);

            // TTL 설정: 24시간 후 자동 삭제
            long expireTime = System.currentTimeMillis() + 24 * 60 * 60 * 1000;
            Date expireAt = new Date(expireTime);

            // 업데이트할 데이터 생성
            Document update = new Document("$set", new Document("availableTimes", availableTimes)
                    .append("store", "pointnine") // 포인트나인 지점
                    .append("zizum", zizumName) // 지점 이름 추가
                    .append("themeName", themeName) // 테마 이름 추가
                    .append("expireAt", expireAt));

            // MongoDB에 upsert (있으면 업데이트, 없으면 삽입)
            reservationCollection.updateOne(filter, update, new com.mongodb.client.model.UpdateOptions().upsert(true));

            // 로그 출력
            System.out.println("Saved: " + themeName + " (지점: " + zizumName + ", 날짜: " + date + ")");
            System.out.println("Available Times: " + (availableTimes.isEmpty() ? "None" : String.join(", ", availableTimes)));
        } catch (Exception e) {
            System.err.println("Error saving theme data: " + e.getMessage());
        }
    }
}
