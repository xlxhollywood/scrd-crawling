package org.example.beatphobia;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.example.config.MongoConfig;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.*;

public class BeatphobiaCrawling {
    private final WebDriver driver;
    private final MongoCollection<Document> reservationCollection;

    public BeatphobiaCrawling(WebDriver driver) {
        this.driver = driver;

        // MongoClient를 통해 scrd 데이터베이스와 reservation 컬렉션에 접근
        MongoClient mongoClient = MongoConfig.getMongoClient();
        MongoDatabase database = mongoClient.getDatabase("scrd");
        this.reservationCollection = database.getCollection("reservation");
    }

    // zizum3,1,5,2,4,6,9,7,10
    public void crawlThemesForDate(String date) {
        // 처리할 지점 목록
        List<String> zizumList = Arrays.asList("3", "1", "5", "2", "4", "6", "9", "7", "10");

        for (String zizum : zizumList) {
            try {
                // Beatphobia 사이트 예약 페이지로 이동 (각 지점별 URL)
                String url = "https://xdungeon.net/layout/res/home.php?go=rev.main&s_zizum=" + zizum;
                driver.get(url);

                System.out.println("Crawling for Zizum: " + zizum + ", Date: " + date);

                // 날짜 선택
                selectDate(date);

                // 테마별 예약 가능 시간 크롤링
                Map<String, List<String>> themeTimesMap = fetchAvailableTimesWithTheme();

                // MongoDB에 저장
                for (Map.Entry<String, List<String>> entry : themeTimesMap.entrySet()) {
                    String themeName = entry.getKey();
                    List<String> availableTimes = entry.getValue();

                    // saveThemeData 호출
                    saveThemeData(themeName, date, availableTimes);
                }

                System.out.println("Data saved for Zizum: " + zizum + " on " + date);
            } catch (Exception e) {
                System.err.println("Error while crawling Beatphobia data for zizum " + zizum + " on date " + date + ": " + e.getMessage());
            }
        }
    }




    private Map<String, List<String>> fetchAvailableTimesWithTheme() {
        Map<String, List<String>> themeTimesMap = new HashMap<>();
        try {
            // 모든 테마 박스를 가져옴 (class="box")
            List<WebElement> themeBoxes = driver.findElements(By.cssSelector(".thm_box .box"));

            for (WebElement themeBox : themeBoxes) {
                // 테마 이름 가져오기 (class="tit")
                String themeName = themeBox.findElement(By.cssSelector(".img_box .tit")).getText();

                // 예약 가능한 시간 가져오기 (class="sale"인 li 태그 중 "dead" 클래스가 없는 경우만)
                List<WebElement> availableTimeElements = themeBox.findElements(By.cssSelector(".time_box ul li.sale:not(.dead) a"));

                List<String> availableTimes = new ArrayList<>();
                for (WebElement timeElement : availableTimeElements) {
                    String time = timeElement.getText();
                    availableTimes.add(time);
                }

                // 테마 이름과 예약 가능한 시간대를 맵에 저장
                themeTimesMap.put(themeName, availableTimes);
            }
        } catch (Exception e) {
            System.err.println("Error while fetching theme times: " + e.getMessage());
        }

        return themeTimesMap;
    }

    private void selectDate(String date) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        // JavaScript를 실행하여 날짜를 선택
        js.executeScript(String.format("document.querySelector('input[name=\"rev_days\"]').value = '%s';", date));
        // 날짜 변경 후 JavaScript를 실행하여 화면을 업데이트
        js.executeScript("fun_search();");
        // 변경된 날짜 데이터가 로드될 때까지 기다림
        new WebDriverWait(driver, Duration.ofSeconds(10))
                .until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".thm_box")));
    }

    /**
     * 예약 데이터를 MongoDB에 저장
     */
    private void saveThemeData(String themeName, String date, List<String> availableTimes) {
        try {
            // 조건 생성: themeName과 date를 기준으로 데이터 찾기
            Document filter = new Document("themeName", themeName).append("date", date);

            // 삭제 시간 설정: 24시간 후
            long expireTime = System.currentTimeMillis() + 24 * 60 * 60 * 1000; // 현재 시간 + 24시간
            Date expireAt = new Date(expireTime);

            // 업데이트할 데이터 생성
            Document update = new Document("$set", new Document("availableTimes", availableTimes)
                    .append("store", "beatphobia") // Store name 설정
                    .append("themeName", themeName) // 테마 이름 추가
                    .append("expireAt", expireAt)); // 24시간 후 삭제를 위한 expireAt 필드 추가

            // upsert 수행: 기존 문서가 있으면 업데이트, 없으면 삽입
            reservationCollection.updateOne(filter, update, new com.mongodb.client.model.UpdateOptions().upsert(true));

            // 로그 출력: 테마 이름, 날짜, 예약 가능한 시간대
            System.out.println("Saved: " + themeName + " (date: " + date + ")");
            System.out.println("Available Times: " + (availableTimes.isEmpty() ? "None" : String.join(", ", availableTimes)));
        } catch (Exception e) {
            System.err.println("Error saving theme data: " + e.getMessage());
        }
    }



}
