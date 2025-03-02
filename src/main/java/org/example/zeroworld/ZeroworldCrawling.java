package org.example.zeroworld;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.UpdateOptions;
import org.bson.Document;
import org.example.config.MongoConfig;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;

public class ZeroworldCrawling {

    // ========================
    // 1) 테마명 → 사용자 정의 ID 매핑
    // ========================
    private static final Map<String, Integer> THEME_ID_MAP = new HashMap<>();
    static {
        THEME_ID_MAP.put("링", 195);
        THEME_ID_MAP.put("포레스트(FORREST)", 196);
        THEME_ID_MAP.put("DONE", 197);
        THEME_ID_MAP.put("아이엠", 198);
        THEME_ID_MAP.put("헐!", 199);
        THEME_ID_MAP.put("제로호텔L", 200);
        THEME_ID_MAP.put("어느 겨울밤2", 201);
        THEME_ID_MAP.put("콜러", 202);
        THEME_ID_MAP.put("나비효과", 203);
    }

    private final WebDriver driver;
    private final MongoCollection<Document> reservationCollection;

    // 고정 정보
    private static final String BRAND = "제로월드";
    private static final String LOCATION = "강남";
    private static final String BRANCH = "강남점";

    public ZeroworldCrawling(WebDriver driver) {
        this.driver = driver;
        MongoClient client = MongoConfig.getMongoClient();
        MongoDatabase db = client.getDatabase("scrd");
        this.reservationCollection = db.getCollection("reservation");
    }

    private String normalizeTitle(String raw) {
        // 1) "[강남] " 제거
        String t = raw.replaceAll("\\[.*?\\]\\s*", "");

        // 2) 괄호 앞뒤 공백 제거
        //    "포레스트 (FORREST)" → "포레스트(FORREST)"
        t = t.replaceAll("\\s*\\(\\s*", "("); // '(' 앞뒤 공백 제거
        t = t.replaceAll("\\s*\\)\\s*", ")"); // ')' 앞뒤 공백 제거

        // 3) 필요하다면 더 처리 (대소문자 변환 등)
        return t.trim();
    }

    /**
     * 주간(7일) 크롤링: 오늘부터 7일간
     */
    public void crawlNext7Days() {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        for (int i = 0; i < 7; i++) {
            String dateStr = sdf.format(cal.getTime());
            System.out.println("\n=== [크롤링 날짜] " + dateStr + " ===");
            crawlOneDay(dateStr);
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }
    }

    /**
     * 지정된 날짜(dateStr) 선택 후, 테마/시간 파싱
     */
    public void crawlOneDay(String dateStr) {
        try {
            // 1) 페이지 접속
            driver.get("https://zerogangnam.com/reservation");

            // 2) 달력이 로드될 때까지 대기
            new WebDriverWait(driver, Duration.ofSeconds(10))
                    .until(ExpectedConditions.visibilityOfElementLocated(By.id("calendar")));

            // 3) 달력에서 해당 날짜 클릭
            selectDateOnCalendar(dateStr);

            // 4) 테마 목록 로드 (#themeChoice) 대기
            new WebDriverWait(driver, Duration.ofSeconds(10))
                    .until(ExpectedConditions.visibilityOfElementLocated(By.id("themeChoice")));

            // 테마 라디오가 최소 1개 이상 뜰 때까지
            new WebDriverWait(driver, Duration.ofSeconds(10))
                    .until(ExpectedConditions.numberOfElementsToBeMoreThan(
                            By.cssSelector("#themeChoice label.hover2"), 0
                    ));

            // 테마 라디오 버튼들 찾기
            List<WebElement> themeLabels = driver.findElements(By.cssSelector("#themeChoice label.hover2"));
            System.out.println("테마 개수: " + themeLabels.size());

            // 테마별로 반복
            for (WebElement themeLabel : themeLabels) {
                WebElement radio = themeLabel.findElement(By.cssSelector("input[type='radio']"));
                // ex) value="23"
                String themeValue = radio.getAttribute("value");
                // ex) "[강남] 링"
                String themeTitle = themeLabel.getText().trim();
                // "[강남]" 제거
                String processedTitle = themeTitle.replaceAll("\\[.*?\\]\\s*", "");

                // 테마 클릭 (스크롤 후)
                ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", themeLabel);
                themeLabel.click();

                // 시간 목록 대기
                new WebDriverWait(driver, Duration.ofSeconds(10))
                        .until(ExpectedConditions.visibilityOfElementLocated(By.id("themeTimeWrap")));
                new WebDriverWait(driver, Duration.ofSeconds(10))
                        .until(ExpectedConditions.numberOfElementsToBeMoreThan(
                                By.cssSelector("#themeTimeWrap label.hover2"), 0
                        ));

                // 시간 파싱
                List<String> availableTimes = fetchAvailableTimes();

                // DB 저장: **사용자 정의 id**로 저장
                saveToDatabase(dateStr, processedTitle, availableTimes);
                System.out.println("  -> " + processedTitle + " (" + themeValue + ") : " + availableTimes);
            }

        } catch (Exception e) {
            System.err.println("[오류] " + dateStr + " 처리 중: " + e.getMessage());
        }
    }

    /**
     * 달력에서 dateStr(yyyy-MM-dd)을 찾아 클릭
     */
    private void selectDateOnCalendar(String dateStr) {
        try {
            String[] parts = dateStr.split("-");
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]); // 1~12
            int day = Integer.parseInt(parts[2]);
            int dataMonth = month - 1; // 3월 -> data-month="2"

            List<WebElement> dayCells = driver.findElements(By.cssSelector(".datepicker--cell.datepicker--cell-day"));
            for (WebElement cell : dayCells) {
                String cellYear = cell.getAttribute("data-year");   // "2025"
                String cellMonth = cell.getAttribute("data-month"); // "2"
                String cellDate = cell.getAttribute("data-date");   // "3"

                // -disabled- 이면 클릭 불가
                if (cell.getAttribute("class").contains("-disabled-")) {
                    continue;
                }
                if (String.valueOf(year).equals(cellYear)
                        && String.valueOf(dataMonth).equals(cellMonth)
                        && String.valueOf(day).equals(cellDate)) {
                    ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", cell);
                    Thread.sleep(300);
                    cell.click();
                    return;
                }
            }
            System.out.println("   => 날짜 셀을 찾지 못했거나 disabled: " + dateStr);

        } catch (Exception e) {
            System.err.println("selectDateOnCalendar() 예외: " + e.getMessage());
        }
    }

    /**
     * #themeTimeWrap 내의 label들을 스캔 -> 예약가능 시간 추출
     */
    private List<String> fetchAvailableTimes() {
        List<String> result = new ArrayList<>();
        try {
            List<WebElement> timeLabels = driver.findElements(By.cssSelector("#themeTimeWrap label.hover2"));
            for (WebElement lbl : timeLabels) {
                WebElement input = lbl.findElement(By.cssSelector("input[name='reservationTime']"));
                boolean isDisabled = (input.getAttribute("disabled") != null);
                boolean hasActiveClass = lbl.getAttribute("class").contains("active");

                if (!isDisabled && !hasActiveClass) {
                    result.add(lbl.getText().trim()); // ex) "14시 50분"
                }
            }
            if (result.isEmpty()) {
                result.add("예약불가");
            }
        } catch (Exception e) {
            System.err.println("fetchAvailableTimes() 오류: " + e.getMessage());
            result.add("오류");
        }
        return result;
    }

    // ========================
    // 2) DB 저장 시 사용자 정의 ID 사용
    // ========================
    private void saveToDatabase(String dateStr, String themeTitle, List<String> times) {
        try {
            // (1) 부분 매칭으로 사용자 정의 ID 얻기
            int customId = getUserDefinedId(themeTitle);

            // (2) filter
            Document filter = new Document("date", dateStr)
                    .append("brand", BRAND)
                    .append("title", themeTitle);

            long expireTime = System.currentTimeMillis() + 24L * 60 * 60 * 1000;
            Date expireAt = new Date(expireTime);

            // (3) 업데이트할 문서
            Document doc = new Document()
                    .append("brand", BRAND)
                    .append("location", LOCATION)
                    .append("branch", BRANCH)
                    .append("title", themeTitle)
                    .append("id", customId)  // 여기서 사용
                    .append("date", dateStr)
                    .append("availableTimes", times)
                    .append("updatedAt", new Date())
                    .append("expireAt", expireAt);

            Document update = new Document("$set", doc);
            reservationCollection.updateOne(filter, update, new UpdateOptions().upsert(true));

        } catch (Exception e) {
            System.err.println("DB 저장 오류: " + e.getMessage());
        }
    }


    /**
     * 부분 문자열로 매칭:
     * "포레스트"가 포함되어 있으면 196,
     * "어느겨울밤2"가 포함되어 있으면 201, ...
     */
    private int getUserDefinedId(String processedTitle) {
        // 대소문자 구분을 피하려면 processedTitle = processedTitle.toLowerCase() 등
        // 그리고 if (processedTitle.contains("어느겨울밤2")) return 201; etc

        if (processedTitle.contains("어느겨울밤2")) {
            return 201;
        } else if (processedTitle.contains("포레스트")) {
            return 196;
        } else if (processedTitle.contains("링")) {
            return 195;
        } else if (processedTitle.contains("DONE")) {
            return 197;
        } else if (processedTitle.contains("아이엠")) {
            return 198;
        } else if (processedTitle.contains("헐!")) {
            return 199;
        } else if (processedTitle.contains("제로호텔L")) {
            return 200;
        } else if (processedTitle.contains("콜러")) {
            return 202;
        } else if (processedTitle.contains("나비효과")) {
            return 203;
        }

        // 없으면 -1
        return -1;
    }

    public static void main(String[] args) {
        System.setProperty("webdriver.chrome.driver", "/Users/pro/Downloads/chromedriver-mac-x64/chromedriver");
        WebDriver driver = new ChromeDriver();
        try {
            ZeroworldCrawling crawler = new ZeroworldCrawling(driver);
            crawler.crawlNext7Days();
        } finally {
            driver.quit();
        }
    }
}
