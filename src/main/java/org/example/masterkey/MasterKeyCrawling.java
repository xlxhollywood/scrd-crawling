package org.example.masterkey;

import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.UpdateOptions;
import org.bson.Document;
import org.example.config.MongoConfig;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import java.text.SimpleDateFormat;
import java.util.*;

public class MasterKeyCrawling {
    private final MongoCollection<Document> reservationCollection;

    // 마스터키 지점 매핑 정보
    private static class ThemeMapping {
        int id;
        String brand;
        String location;
        String branch;
        String title;
        int bid;  // 해당 지점의 bid 값 추가
        ThemeMapping(int id, String brand, String location, String branch, String title, int bid) {
            this.id = id;
            this.brand = brand;
            this.location = location;
            this.branch = branch;
            this.title = title;
            this.bid = bid;
        }
    }

    // 모든 지점의 테마 매핑 (추가된 건대점, 마스터키강남점 포함)
    private static final List<ThemeMapping> THEME_MAPPINGS = Arrays.asList(
            // 신촌 (bid=32)
            new ThemeMapping(40, "마스터키", "신촌", "프라임 신촌 퍼블릭점", "SCENE : 404 NOT FOUND", 32),
            new ThemeMapping(41, "마스터키", "신촌", "프라임 신촌 퍼블릭점", "그도...그럴 것이다", 32),
            new ThemeMapping(42, "마스터키", "신촌", "프라임 신촌 퍼블릭점", "인투더와일드", 32),
            // 홍대 (bid=41)
            new ThemeMapping(60, "마스터키", "홍대", "노바홍대점", "검은의사", 41),
            new ThemeMapping(64, "마스터키", "홍대", "노바홍대점", "NOEXIT", 41),
            // 건대점 (bid=26)
            new ThemeMapping(43, "마스터키", "건대", "건대점", "DELIVER", 26),
            new ThemeMapping(44, "마스터키", "건대", "건대점", "D-Day", 26),
            new ThemeMapping(56, "마스터키", "건대", "건대점", "교생실습", 26),
            // 마스터키강남점 (bid=35)
            new ThemeMapping(299, "마스터키", "강남", "마스터키강남점", "위로", 35),
            new ThemeMapping(133, "마스터키", "강남", "마스터키강남점", "리허설", 35),
            new ThemeMapping(134, "마스터키", "강남", "마스터키강남점", "갱생", 35),
            new ThemeMapping(135, "마스터키", "강남", "마스터키강남점", "더맨", 35),
            new ThemeMapping(136, "마스터키", "강남", "마스터키강남점", "STAFF ONLY", 35),
            new ThemeMapping(137, "마스터키", "강남", "마스터키강남점", "작은창고", 35)
    );

    public MasterKeyCrawling() {
        MongoClient mongoClient = MongoConfig.getMongoClient(); // MongoConfig 사용
        MongoDatabase database = mongoClient.getDatabase("scrd");
        this.reservationCollection = database.getCollection("reservation");
    }

    /**
     * MongoDB에 예약 데이터를 저장 (Upsert 방식)
     */
    private void saveToDatabase(ThemeMapping mapping, String date, List<String> availableTimes) {
        try {
            Document filter = new Document("title", mapping.title)
                    .append("date", date)
                    .append("brand", mapping.brand)
                    .append("branch", mapping.branch);

            Document docToSave = new Document("brand", mapping.brand)
                    .append("location", mapping.location)
                    .append("branch", mapping.branch)
                    .append("title", mapping.title)
                    .append("id", mapping.id)
                    .append("date", date)
                    .append("availableTimes", availableTimes)
                    .append("updatedAt", new Date())
                    .append("expireAt", new Date(System.currentTimeMillis() + 24L * 60 * 60 * 1000)); // 24시간 후 만료

            reservationCollection.updateOne(filter, new Document("$set", docToSave), new UpdateOptions().upsert(true));
        } catch (Exception e) {
            System.err.println("DB 저장 오류: " + e.getMessage());
        }
    }

    /**
     * Selenium으로 각 지점(bid)의 예약 가능 시간 크롤링
     */
    public void crawlReservations(String startDate, int days) {
        System.setProperty("webdriver.chrome.driver", "/Users/pro/Downloads/chromedriver-mac-x64/chromedriver"); // Mac용 ChromeDriver 경로

        WebDriver driver = new ChromeDriver();

        try {
            for (ThemeMapping mapping : THEME_MAPPINGS) {
                String url = "https://www.master-key.co.kr/booking/bk_detail?bid=" + mapping.bid;
                driver.get(url);
                System.out.println("크롤링 시작: " + mapping.branch + " (" + mapping.title + ") - " + url);

                // 일주일치 날짜 가져오기
                List<WebElement> dateElements = driver.findElements(By.cssSelector(".date_click_div1 p"));

                for (int i = 0; i < Math.min(dateElements.size(), days); i++) {
                    WebElement dateElement = dateElements.get(i);
                    String date = dateElement.getAttribute("data-dd"); // 날짜 값 가져오기
                    dateElement.click();
                    Thread.sleep(2000); // 비동기 로딩 대기

                    // 방 목록 가져오기
                    List<WebElement> rooms = driver.findElements(By.cssSelector("#booking_list .box2-inner"));

                    System.out.println("===== [" + date + "] 예약 가능 시간 - " + mapping.branch + " (" + mapping.title + ") =====");

                    for (WebElement room : rooms) {
                        // 방 이름 가져오기
                        String title = room.findElement(By.cssSelector(".title")).getText().trim();

                        // 예약 가능한 시간 가져오기
                        List<WebElement> availableTimesElements = room.findElements(By.cssSelector(".right p.col.true a"));
                        List<String> availableTimes = new ArrayList<>();

                        for (WebElement timeElement : availableTimesElements) {
                            String rawTime = timeElement.getText().trim();
                            String cleanTime = rawTime.replace("예약가능", "").trim(); // "예약가능" 제거
                            availableTimes.add(cleanTime);
                        }

                        System.out.println("[" + title + "] 예약 가능 시간: " + availableTimes);
                        saveToDatabase(mapping, date, availableTimes); // DB 저장
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit(); // 브라우저 종료
        }
    }

    public static void main(String[] args) {
        MasterKeyCrawling crawler = new MasterKeyCrawling();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String todayStr = sdf.format(new Date());
        crawler.crawlReservations(todayStr, 7); // 오늘부터 7일간 크롤링
    }
}
