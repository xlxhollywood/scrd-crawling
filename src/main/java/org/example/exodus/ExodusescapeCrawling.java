package org.example.exodus;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.UpdateOptions;
import org.bson.Document;
import org.example.config.MongoConfig;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.text.SimpleDateFormat;
import java.util.*;

public class ExodusescapeCrawling {

    private final MongoCollection<Document> reservationCollection;

    // 테마 매핑 정보 (s_theme_num은 HTML의 옵션값과는 다르게, 여기서는 테마명으로 사용)
    private static class ThemeMapping {
        String title;   // 테마명 (예: "CLAIM", "WISH")
        int id;         // 정의한 id
        ThemeMapping(String title, int id) {
            this.title = title;
            this.id = id;
        }
    }
    // 두 테마에 대한 매핑 정보를 정의
    private static final Map<String, ThemeMapping> THEME_MAP = new HashMap<>();
    static {
        THEME_MAP.put("CLAIM", new ThemeMapping("CLAIM", 185));
        THEME_MAP.put("WISH", new ThemeMapping("WISH", 186));
    }

    // 엑소더스이스케이프 고정 정보
    private static final String BRAND = "엑소더스이스케이프";
    private static final String LOCATION = "강남";
    private static final String BRANCH = "강남 1호점";

    // 기본 도메인 및 기타 고정 파라미터
    private static final String BASE_URL = "https://exodusescape.co.kr/layout/res/home.php";
    // go 파라미터는 항상 rev.make

    public ExodusescapeCrawling() {
        MongoClient mongoClient = MongoConfig.getMongoClient();
        MongoDatabase database = mongoClient.getDatabase("scrd");
        this.reservationCollection = database.getCollection("reservation");
    }

    /**
     * HTML에서 각 테마 박스( div.theme_box )를 파싱하여 예약 가능한 시작 시간(예약가능 표시된 <span class="time">)을 추출합니다.
     * @param doc Jsoup Document
     * @return Map<테마명, 예약 가능한 시작시간 리스트>
     */
    private Map<String, List<String>> parseThemeBoxes(org.jsoup.nodes.Document doc) {
        Map<String, List<String>> result = new HashMap<>();
        Elements themeBoxes = doc.select("div.theme_box");
        for (Element box : themeBoxes) {
            // 테마명 추출 (예: "CLAIM" 또는 "WISH") – h3.h3_theme 안의 텍스트
            Element titleEl = box.selectFirst("div.theme_Title h3.h3_theme");
            if (titleEl == null) continue;
            String themeTitle = titleEl.text().trim();
            // 매핑 키는 대문자로 비교
            String themeKey = themeTitle.toUpperCase();

            // 예약 가능한 시간은 div.time_Area 내 ul.reserve_Time 의 li 중 <a>에 <span class="possible">가 있는 경우
            List<String> times = new ArrayList<>();
            Elements liElements = box.select("div.time_Area ul.reserve_Time li");
            for (Element li : liElements) {
                // <a> 태그 내에 span.possible가 존재하면 예약 가능한 시간
                if (li.select("span.possible").size() > 0) {
                    Element timeEl = li.selectFirst("span.time");
                    if (timeEl != null) {
                        String timeText = timeEl.text().replace("☆", "").trim();
                        times.add(timeText);
                    }
                }
            }
            result.put(themeKey, times);
        }
        return result;
    }

    /**
     * MongoDB에 upsert 방식으로 예약 데이터를 저장합니다.
     * 저장 구조:
     * { brand, location, branch, title, id, date, availableTimes, updatedAt, expireAt }
     */
    private void saveToDatabase(String brand, String location, String branch, String title, int id, String date, List<String> availableTimes) {
        try {
            Document filter = new Document("title", title)
                    .append("date", date)
                    .append("brand", brand);
            Document docToSave = new Document("brand", brand)
                    .append("location", location)
                    .append("branch", branch)
                    .append("title", title)
                    .append("id", id)
                    .append("date", date)
                    .append("availableTimes", availableTimes)
                    .append("updatedAt", new Date())
                    .append("expireAt", new Date(System.currentTimeMillis() + 24L * 60 * 60 * 1000));
            reservationCollection.updateOne(filter, new Document("$set", docToSave), new UpdateOptions().upsert(true));
            System.out.println("DB 저장 완료: " + filter.toJson());
        } catch (Exception e) {
            System.err.println("DB 저장 오류: " + e.getMessage());
        }
    }

    /**
     * 지정한 날짜에 대해 예약 페이지의 HTML을 가져와 파싱한 후, 각 테마별로 DB에 저장합니다.
     * @param dateStr 예약 날짜 (yyyy-MM-dd)
     */
    private void processDate(String dateStr) {
        try {
            // URL 구성: rev_days 파라미터와 go=rev.make (s_theme_num 빈 값)
            String url = BASE_URL + "?rev_days=" + dateStr + "&s_theme_num=&go=rev.make";
            System.out.println("URL 호출: " + url);
            org.jsoup.nodes.Document doc = Jsoup.connect(url).get();

            // (옵션) 예약날짜 값이 input#rev_days에 있으므로, 이를 확인할 수도 있음.
            // String pageDate = doc.select("input#rev_days").attr("value");
            // if(!dateStr.equals(pageDate)) { dateStr = pageDate; }

            // 각 테마 박스에서 예약 가능한 시작 시간 추출
            Map<String, List<String>> themeData = parseThemeBoxes(doc);
            // themeData의 키는 대문자 테마명 ("CLAIM", "WISH")
            for (Map.Entry<String, List<String>> entry : themeData.entrySet()) {
                String themeKey = entry.getKey();
                List<String> availableTimes = entry.getValue();
                // 매핑 정보 확인
                if (THEME_MAP.containsKey(themeKey)) {
                    ThemeMapping mapping = THEME_MAP.get(themeKey);
                    System.out.println("[" + dateStr + "] " + mapping.title + " 예약 가능 시작 시간: " + availableTimes);
                    saveToDatabase("엑소더스이스케이프", LOCATION, BRANCH, mapping.title, mapping.id, dateStr, availableTimes);
                } else {
                    System.out.println("매핑 정보 없음 - 테마: " + themeKey);
                }
            }
        } catch (Exception e) {
            System.err.println("processDate() 오류: " + e.getMessage());
        }
    }

    /**
     * 현재 날짜부터 numDays 일간 데이터를 크롤링합니다.
     * @param numDays 크롤링할 일수
     */
    public void crawlFromToday(int numDays) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            Calendar cal = Calendar.getInstance();
            for (int i = 0; i < numDays; i++) {
                String currentDate = sdf.format(cal.getTime());
                System.out.println("크롤링 날짜: " + currentDate);
                processDate(currentDate);
                cal.add(Calendar.DAY_OF_MONTH, 1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        ExodusescapeCrawling crawler = new ExodusescapeCrawling();
        // 프로그램 실행 시점부터 7일간 크롤링
        crawler.crawlFromToday(7);
    }
}
