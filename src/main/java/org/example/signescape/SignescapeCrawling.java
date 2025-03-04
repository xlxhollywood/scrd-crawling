package org.example.signescape;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.UpdateOptions;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.bson.Document;
import org.example.config.MongoConfig;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.text.SimpleDateFormat;
import java.util.*;

public class SignescapeCrawling {

    private final MongoCollection<Document> reservationCollection;
    private final OkHttpClient client = new OkHttpClient();

    // 테마 매핑 정보: 각 테마의 R_THEMA, 테마명, id
    private static class ThemeMapping {
        String themeCode; // R_THEMA 값
        String title;
        int id;
        ThemeMapping(String themeCode, String title, int id) {
            this.themeCode = themeCode;
            this.title = title;
            this.id = id;
        }
    }

    // 지점(Branch) 매핑 정보: R_JIJEM, location, branch, 그리고 해당 지점의 테마 매핑 목록
    private static class BranchMapping {
        String branchCode; // R_JIJEM 값
        String location;
        String branch;     // 지점명
        List<ThemeMapping> themes;
        BranchMapping(String branchCode, String location, String branch, List<ThemeMapping> themes) {
            this.branchCode = branchCode;
            this.location = location;
            this.branch = branch;
            this.themes = themes;
        }
    }

    // 싸인 이스케이프의 모든 지점 정보를 정의
    private static final List<BranchMapping> BRANCH_MAPPINGS = new ArrayList<>();
    static {
        // 강남시티점 (R_JIJEM=S6)
        BRANCH_MAPPINGS.add(new BranchMapping("S6", "강남", "강남시티점", Arrays.asList(
                new ThemeMapping("A", "러너웨이", 169),
                new ThemeMapping("C", "EXPRESS", 171),
                new ThemeMapping("B", "MUST", 170)
        )));
        // 홍대점 (R_JIJEM=S5)
        BRANCH_MAPPINGS.add(new BranchMapping("S5", "홍대", "홍대점", Arrays.asList(
                new ThemeMapping("A", "거상", 177),
                new ThemeMapping("B", "졸업", 179),
                new ThemeMapping("C", "하이팜", 178)
        )));
        // 인계점 (R_JIJEM=S4)
        BRANCH_MAPPINGS.add(new BranchMapping("S4", "수원", "인계점", Arrays.asList(
                new ThemeMapping("D", "신비의 베이커리", 181),
                new ThemeMapping("C", "악은 어디에나 존재한다", 183),
                new ThemeMapping("B", "트라이 위저드", 182),
                new ThemeMapping("E", "GATE : CCZ (episode 1)", 180),
                new ThemeMapping("A", "NEW", 184)
        )));
        // 성대역점 (R_JIJEM=S2)
        BRANCH_MAPPINGS.add(new BranchMapping("S2", "수원", "성대역점", Arrays.asList(
                new ThemeMapping("B", "각성(Awakening)", 173),
                new ThemeMapping("E", "고시텔(3층)", 176),
                new ThemeMapping("A", "우울증(Depression)", 172),
                new ThemeMapping("C", "인턴(Intern)", 174),
                new ThemeMapping("D", "자멜신부의 비밀", 175)
        )));
    }

    public SignescapeCrawling() {
        MongoClient mongoClient = MongoConfig.getMongoClient();
        MongoDatabase database = mongoClient.getDatabase("scrd");
        this.reservationCollection = database.getCollection("reservation");
    }

    /**
     * URL 호출 및 HTML 파싱을 통해, 예약 가능한 시간(시작 시간만)을 추출한 후 DB에 저장합니다.
     * @param branchMapping 해당 지점 정보
     * @param themeMapping 해당 테마 정보
     * @param dateStr 예약 날짜 (yyyy-MM-dd)
     */
    private void fetchAndStore(BranchMapping branchMapping, ThemeMapping themeMapping, String dateStr) {
        try {
            // URL 구성: choiS_date 파라미터로 날짜 지정, DIS_T는 빈 값으로 고정
            String url = "http://www.signescape.com/sub/sub03_1.html?R_JIJEM="
                    + branchMapping.branchCode
                    + "&chois_date=" + dateStr
                    + "&R_THEMA=" + themeMapping.themeCode
                    + "&DIS_T=";
            System.out.println("URL 호출: " + url);

            // Jsoup으로 HTML 가져오기 (인코딩이 UTF-8이라고 가정)
            org.jsoup.nodes.Document doc = Jsoup.connect(url).get();

            // 예약 시간 영역: div#reser4 내, ul.list의 li 요소 중 class "timeOn"인 것들
            Elements timeElements = doc.select("div#reser4 ul.list li.timeOn");
            List<String> availableTimes = new ArrayList<>();
            for (Element timeEl : timeElements) {
                // 텍스트에서 별 기호(☆) 제거 후, 공백 trim
                String timeText = timeEl.text().replace("☆", "").trim();
                availableTimes.add(timeText);
            }
            System.out.println("[" + dateStr + "] " + branchMapping.branch + " - " + themeMapping.title
                    + " 예약 가능 시작 시간: " + availableTimes);
            // MongoDB에 저장 (저장 시 브랜드는 "싸인 이스케이프")
            saveToDatabase("싸인 이스케이프", branchMapping.location, branchMapping.branch,
                    themeMapping.title, themeMapping.id, dateStr, availableTimes);
        } catch (Exception e) {
            System.err.println("fetchAndStore() 오류: " + e.getMessage());
        }
    }

    /**
     * MongoDB에 upsert – 저장 구조:
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
     * 현재 날짜부터 지정한 일수만큼 반복하여 모든 지점/테마의 데이터를 크롤링합니다.
     * @param numDays 크롤링할 일수
     */
    public void crawlFromToday(int numDays) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            Calendar cal = Calendar.getInstance();
            for (int i = 0; i < numDays; i++) {
                String currentDate = sdf.format(cal.getTime());
                System.out.println("크롤링 날짜: " + currentDate);
                // 각 지점과 해당 테마별로 처리
                for (BranchMapping branchMapping : BRANCH_MAPPINGS) {
                    for (ThemeMapping themeMapping : branchMapping.themes) {
                        fetchAndStore(branchMapping, themeMapping, currentDate);
                    }
                }
                cal.add(Calendar.DAY_OF_MONTH, 1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        SignescapeCrawling crawler = new SignescapeCrawling();
        // 프로그램 실행 시점부터 7일간 크롤링
        crawler.crawlFromToday(7);
    }
}
