package org.example.keyescape;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.example.config.MongoConfig;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class KeyescapeCrawling {

    private final WebDriver driver;
    private final MongoCollection<Document> reservationCollection;

    public KeyescapeCrawling(WebDriver driver) {
        this.driver = driver;

        // MongoClient를 통해 scrd 데이터베이스와 reservation 컬렉션에 접근
        MongoClient mongoClient = MongoConfig.getMongoClient();
        MongoDatabase database = mongoClient.getDatabase("scrd");
        this.reservationCollection = database.getCollection("reservation");
    }

    public void crawlThemesForDate(String date) {
        List<String[]> themes = getThemes();
        for (String[] theme : themes) {
            String themeId = theme[0];
            String index = theme[1];
            String themeName = theme[2];

            // 데이터 크롤링
            List<String> availableTimes = fetchAvailableTimesForTheme(themeId, index, date);

            // 데이터 저장
            saveThemeData(themeId, date, availableTimes, themeName);

            System.out.println(String.format("Data saved for theme %s on %s: %s", themeId, date, String.join(", ", availableTimes)));
        }
    }

    private List<String> fetchAvailableTimesForTheme(String themeId, String index, String date) {
        try {
            driver.get("https://www.keyescape.co.kr/web/home.php?go=rev.make");
            selectDate(date);
            selectTheme(themeId, index);
            return fetchAvailableTimes();
        } catch (Exception e) {
            System.err.println(String.format("Error fetching data for theme %s on %s: %s", themeId, date, e.getMessage()));
            return List.of("Error");
        }
    }

    private void selectDate(String date) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        js.executeScript(String.format("fun_days_select('%s', '0');", date));
        new WebDriverWait(driver, Duration.ofSeconds(10))
                .until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#theme_data")));
    }

    private void selectTheme(String themeId, String index) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        js.executeScript(String.format("fun_theme_select('%s', '%s');", themeId, index));
        new WebDriverWait(driver, Duration.ofSeconds(10))
                .until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#theme_time_data")));
    }

    private List<String> fetchAvailableTimes() {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(10))
                    .until(ExpectedConditions.or(
                            ExpectedConditions.presenceOfElementLocated(By.cssSelector("#theme_time_data li.possible")),
                            ExpectedConditions.presenceOfElementLocated(By.cssSelector("#theme_time_data li.impossible"))
                    ));
            List<WebElement> possibleElements = driver.findElements(By.cssSelector("#theme_time_data li.possible"));
            if (!possibleElements.isEmpty()) {
                return possibleElements.stream()
                        .map(WebElement::getText)
                        .collect(Collectors.toList());
            } else {
                return List.of("예약불가");
            }
        } catch (TimeoutException e) {
            return List.of("예약불가");
        }
    }

        // 지점명이나 지역 추가하고 싶을 때 뒤에 넣을 것.
        private List<String[]> getThemes() {
        List<String[]> themes = new ArrayList<>();
        // station 점
        themes.add(new String[]{"65", "0", "머니머니부동산"});
        themes.add(new String[]{"66", "1", "내 방"});
        themes.add(new String[]{"67", "2", "nostalgia vista"});
        // login2 지점
        themes.add(new String[]{"61", "0", "back to The scene"});
        themes.add(new String[]{"64", "1", "a gentle monday"});
        // login1 지점
        themes.add(new String[]{"60", "0", "머니머니패키지"});
        themes.add(new String[]{"63", "1", "for free"});
        // 메모리 컴퍼니 지점
        themes.add(new String[]{"57", "0", "film by eddy"});
        themes.add(new String[]{"58", "1", "film by steve"});
        themes.add(new String[]{"59", "2", "film By bob"});
        // 우주 라이크 지점
        themes.add(new String[]{"55", "0", "us"});
        themes.add(new String[]{"56", "1", "wanna go home"});
        // 강남 더오름
        themes.add(new String[]{"48", "0", "네드"});
        themes.add(new String[]{"51", "1", "엔제리오"});
        // 강남점
        themes.add(new String[]{"5", "0", "월야애담"});
        themes.add(new String[]{"6", "1", "살랑살랑 연구소"});
        themes.add(new String[]{"7", "2", "그카지말캤자나"});
        // 부산점
        themes.add(new String[]{"37", "0", "정신병동"});
        themes.add(new String[]{"38", "1", "파파라치"});
        themes.add(new String[]{"35", "2", "난쟁이의 장난"});
        themes.add(new String[]{"39", "3", "셜록 죽음의 전화"});
        themes.add(new String[]{"36", "4", "신비의 숲 고대마법의 비밀"});
        // 전주점
        themes.add(new String[]{"32", "0", "난쟁이의 장난"});
        themes.add(new String[]{"31", "1", "혜화잡화점"});
        themes.add(new String[]{"29", "2", "월야애담"});
        themes.add(new String[]{"33", "3", "사라진 목격자"});
        themes.add(new String[]{"30", "4", "살랑살랑 연구소"});
        // 홍대점
        themes.add(new String[]{"41", "0", "삐릿-뽀"});
        themes.add(new String[]{"45", "1", "홀리데이"});
        themes.add(new String[]{"43", "2", "고백"});
        return themes;
    }


    public void saveThemeData(String themeId, String date, List<String> availableTimes, String themeName) {
        // 조건 생성: themeId와 date를 기준으로 데이터 찾기
        Document filter = new Document("themeId", themeId).append("date", date);

        // 삭제 시간 설정: 24시간 후
        long expireTime = System.currentTimeMillis() + 24 * 60 * 60 * 1000; // 현재 시간 + 24시간
        Date expireAt = new Date(expireTime);

        // 업데이트할 데이터 생성
        Document update = new Document("$set", new Document("availableTimes", availableTimes)
                .append("store", "keyescape")
                .append("themeName", themeName) // themeName 추가
                .append("expireAt", expireAt)); // 24시간 후 삭제를 위한 expireAt 필드 추가

        // upsert 수행: 데이터가 없으면 삽입, 있으면 업데이트
        reservationCollection.updateOne(filter, update, new com.mongodb.client.model.UpdateOptions().upsert(true));
    }


}

