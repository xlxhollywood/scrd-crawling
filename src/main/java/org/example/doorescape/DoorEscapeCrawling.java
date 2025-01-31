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
import java.util.NoSuchElementException;

public class DoorEscapeCrawling {
    private final WebDriver driver;
    private final MongoCollection<Document> reservationCollection;


    public DoorEscapeCrawling(WebDriver driver) {
        this.driver = driver;
        MongoClient mongoClient = MongoConfig.getMongoClient(); // MongoDB 클라이언트 가져오기
        MongoDatabase database = mongoClient.getDatabase("scrd"); // 데이터베이스 선택
        this.reservationCollection = database.getCollection("reservation"); // 컬렉션 설정
    }


    public void crawlThemesForDate(String date) {
        Map<String, String> zizumList = new HashMap<>();
        zizumList.put("aAo1RDEnfyPkbeix", "강남 가든점");
        zizumList.put("NeZqzMtPCBsSvbAq", "신논현 레드점");
        zizumList.put("yGozPSZSJXwrzbin", "신논현 블루점");
        zizumList.put("o83TaXbnod8DtEX5", "홍대점");
        zizumList.put("h1i4d4YyEfBctnpQ", "이수역점");
        zizumList.put("DGpkkgMQYaNLYXTZ", "안산점");
        zizumList.put("fGDxtefVDEyWczai", "대전유성 NC백화점");

        for (Map.Entry<String, String> entry : zizumList.entrySet()) {
            String keycode = entry.getKey();
            String zizumName = entry.getValue();

            try {
                driver.get("https://doorescape.co.kr/reservation.html?keycode=" + keycode);
                new WebDriverWait(driver, Duration.ofSeconds(10))
                        .until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
                System.out.println("웹페이지 로딩 완료: " + zizumName);

                closeAllModalsIfPresent();
                selectDate(date, zizumName);

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
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));

        // XPath 목록 (모달 ID가 다를 경우 여러 개 존재)
        String[] modalXPaths = {
                "//*[@id='cta-modal-0']/div/div/div/div/button[2]",
                "//*[@id='cta-modal-1']/div/div/div/div/button[2]",
                "//*[@id='cta-modal-2']/div/div/div/div/button[2]" // 필요시 추가
        };

        for (String modalXPath : modalXPaths) {
            try {
                // 모달이 존재하는지 확인
                WebElement modalButton = wait.until(ExpectedConditions.elementToBeClickable(By.xpath(modalXPath)));

                // 버튼 클릭
                modalButton.click();
                System.out.println("✅ 모달 닫기 버튼 클릭: " + modalXPath);
            } catch (TimeoutException e) {
                System.out.println("⚠️ 해당 모달 버튼 없음 (XPath: " + modalXPath + ")");
            } catch (NoSuchElementException e) {
                System.out.println("❌ 버튼을 찾을 수 없음 (XPath: " + modalXPath + ")");
            } catch (ElementClickInterceptedException e) {
                System.out.println("⚠️ 버튼이 가려져 클릭되지 않음. JavaScript로 클릭 시도...");

                // JavaScript로 강제 클릭
                try {
                    JavascriptExecutor js = (JavascriptExecutor) driver;
                    WebElement modalButton = driver.findElement(By.xpath(modalXPath));
                    js.executeScript("arguments[0].click();", modalButton);
                    System.out.println("✅ JavaScript로 버튼 클릭 성공! (XPath: " + modalXPath + ")");
                } catch (Exception jsException) {
                    System.out.println("❌ JavaScript로도 버튼 클릭 실패 (XPath: " + modalXPath + "): " + jsException.getMessage());
                }
            }
        }
    }




    private void selectDate(String date, String location) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript("document.querySelector('#datepicker').value = arguments[0];", date);
            js.executeScript("$('#datepicker').trigger('change');");
            js.executeScript("$('#datepicker').trigger('input');");

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
            wait.until(ExpectedConditions.invisibilityOfElementLocated(By.className("loading-spinner")));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".single-events")));

            System.out.println("✅ 날짜 선택 완료: " + date + " | " + location);
        } catch (Exception e) {
            System.err.println("❌ 날짜 선택 실패: " + date + " | " + location + " | 오류: " + e.getMessage());
        }
    }

    private Map<String, List<String>> fetchAvailableTimesWithTheme() {
        Map<String, List<String>> themeTimesMap = new HashMap<>();
        try {
            for (int attempt = 1; attempt <= 3; attempt++) {
                List<WebElement> themeBoxes = driver.findElements(By.cssSelector(".single-events"));
                if (!themeBoxes.isEmpty()) {
                    for (WebElement themeBox : themeBoxes) {
                        String themeName = themeBox.findElement(By.cssSelector(".events-text-title h4")).getText().trim();
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



