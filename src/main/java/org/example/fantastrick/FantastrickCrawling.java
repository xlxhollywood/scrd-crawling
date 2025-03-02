package org.example.fantastrick;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.UpdateOptions;
import okhttp3.*;
import org.bson.Document;
import org.example.config.MongoConfig;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.text.SimpleDateFormat;
import java.util.*;

public class FantastrickCrawling {

    private final MongoCollection<Document> reservationCollection;

    // 테마 -> (calendar_id, title, themeId) 매핑 예시
    // 만약 사자의 서/태초의 신부 모두 동일 calendar_id=23 이라면 아래처럼 두 개 모두 23으로 세팅
    // title, id는 각각 "사자의 서"/241, "태초의 신부"/240
    // 필요하면 더 많은 항목을 넣어도 됩니다.
    private static class ThemeInfo {
        String calendarId;
        String title;
        int id;
        ThemeInfo(String calendarId, String title, int id) {
            this.calendarId = calendarId;
            this.title = title;
            this.id = id;
        }
    }

    private static final Map<String, ThemeInfo> THEME_INFO_MAP = new HashMap<>();
    static {
        THEME_INFO_MAP.put("사자의 서", new ThemeInfo("23", "사자의 서", 241));
        THEME_INFO_MAP.put("태초의 신부", new ThemeInfo("17", "태초의 신부", 240));
    }

    public FantastrickCrawling() {
        // MongoDB 연결
        MongoClient mongoClient = MongoConfig.getMongoClient();
        MongoDatabase database = mongoClient.getDatabase("scrd");
        this.reservationCollection = database.getCollection("reservation");
    }

    /**
     * 메인 크롤링 로직
     */
    public void crawlAllDates() {
        System.out.println("===== [판타스트릭 예약 크롤링 시작 - (OkHttp + JSoup)] =====");

        // 오늘부터 7일 (필요시 조정)
        Calendar cal = Calendar.getInstance();
        // (주의) 실제 ajax 요청할 때에는 "yyyy-MM-d" (2025-03-2) 형식을 써야 하지만
        // DB에 저장할 때는 "MM-dd" 형식을 원하신다고 하셨으므로,
        // 두 가지 포맷을 모두 사용할 예정입니다.
        SimpleDateFormat dateForAjax = new SimpleDateFormat("yyyy-MM-d"); // Ajax용
        SimpleDateFormat dateForStorage = new SimpleDateFormat("yyyy-MM-dd");   // DB 저장용

        for (Map.Entry<String, ThemeInfo> entry : THEME_INFO_MAP.entrySet()) {
            String themeName = entry.getKey();              // "사자의 서" or "태초의 신부"
            ThemeInfo info = entry.getValue();              // calendarId, title, id

            // 7일 반복
            Calendar tempCal = (Calendar) cal.clone();
            for (int i = 0; i < 7; i++) {
                // Ajax 요청용 날짜 (ex: "2025-03-2")
                String dateStrForAjax = dateForAjax.format(tempCal.getTime());
                // DB 저장용 날짜 (ex: "03-02")
                String dateStrForStorage = dateForStorage.format(tempCal.getTime());

                System.out.println("\n>>> [" + themeName + "] " + dateStrForAjax + " 크롤링 중...");

                // (1) admin-ajax.php 요청
                String html = requestDateHtml(info.calendarId, dateStrForAjax);
                if (html == null) {
                    System.out.println("   - HTML 응답이 null. 요청 실패.");
                } else {
                    // (2) 예약가능 시간대 파싱
                    List<String> availableTimes = parseAvailableTimes(html);

                    // (3) DB 저장
                    if (!availableTimes.isEmpty()) {
                        saveToDatabase(
                                "판타스트릭",    // brand
                                "강남",         // location
                                "강남점",       // branch
                                info.title,     // 테마명 ex) "사자의 서"
                                info.id,        // ex) 241
                                dateStrForStorage,
                                availableTimes
                        );
                    } else {
                        System.out.println("   - 예약 가능 시간 없음.");
                    }
                }

                // 다음 날짜로
                tempCal.add(Calendar.DAY_OF_MONTH, 1);
            }
        }
        System.out.println("\n===== [판타스트릭 크롤링 종료] =====");
    }

    /**
     * (1) admin-ajax.php에 POST로 date, calendar_id 전달
     */
    private String requestDateHtml(String calendarId, String dateStrForAjax) {
        try {
            OkHttpClient client = new OkHttpClient();

            FormBody formBody = new FormBody.Builder()
                    .add("action", "booked_calendar_date")
                    .add("date", dateStrForAjax) // ex) "2025-03-2"
                    .add("calendar_id", calendarId)
                    // 필요한 nonce/security 있으면 .add("nonce","...") 등 추가
                    .build();

            Request request = new Request.Builder()
                    .url("http://fantastrick.co.kr/wp-admin/admin-ajax.php")
                    .post(formBody)
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    System.err.println("   - 요청 실패: " + response);
                    return null;
                }
                return response.body().string();
            }
        } catch (Exception e) {
            System.err.println("   - requestDateHtml() 오류: " + e.getMessage());
            return null;
        }
    }

    /**
     * (2) JSoup으로 "예약가능"만 추출
     *    - spots-available에 "예약완료" 문구 있는지 or button.disabled 체크
     */
    private List<String> parseAvailableTimes(String html) {
        List<String> availableTimes = new ArrayList<>();
        try {
            org.jsoup.nodes.Document doc = Jsoup.parse(html);
            Elements timeSlots = doc.select(".timeslot.bookedClearFix");

            for (Element slot : timeSlots) {
                // 시간 텍스트 (예: "오후 5:30")
                Element timeEl = slot.selectFirst(".timeslot-range");
                if (timeEl == null) continue;

                String timeText = timeEl.text().trim();

                // spots-available에 표시된 텍스트 ("예약완료"/"예약가능")
                Element spotEl = slot.selectFirst(".spots-available");
                String spotText = (spotEl != null) ? spotEl.text().trim() : "";

                // 버튼 disabled 여부
                Element btnEl = slot.selectFirst(".new-appt.button");
                boolean isDisabled = (btnEl != null && btnEl.hasAttr("disabled"));

                boolean isCompleted = "예약완료".equals(spotText) || isDisabled;
                if (isCompleted) {
                    System.out.println("   [DEBUG] " + timeText + " => 예약완료 (제외)");
                    continue; // 예약완료 -> 제외
                }

                // 예약가능
                System.out.println("   [DEBUG] " + timeText + " => 예약가능 (추가)");
                availableTimes.add(timeText);
            }
        } catch (Exception e) {
            System.err.println("parseAvailableTimes() 오류: " + e.getMessage());
        }
        return availableTimes;
    }

    /**
     * (3) MongoDB에 Upsert (전처리한 필드 구조로 저장)
     *
     *  brand="판타스트릭"
     *  location="강남"
     *  branch="강남점"
     *  title="사자의 서" or "태초의 신부"
     *  id=241 or 240
     *  date="03-02"
     *  availableTimes=[...]
     */
    private void saveToDatabase(String brand,
                                String location,
                                String branch,
                                String title,
                                int id,
                                String dateMMDD,
                                List<String> availableTimes) {
        try {
            // filter: "title", "date" 등을 사용해도 되지만
            // 여기서는 "title+date"로 중복 구분
            // 혹은 brand, location, branch도 포함해도 괜찮습니다.
            Document filter = new Document("title", title)
                    .append("date", dateMMDD)
                    .append("brand", brand);

            // 저장할 Document
            Document docToSave = new Document()
                    .append("brand", brand)
                    .append("location", location)
                    .append("branch", branch)
                    .append("title", title)
                    .append("id", id)
                    .append("date", dateMMDD)               // "03-02"
                    .append("availableTimes", availableTimes) // 배열(리스트)
                    .append("updatedAt", new Date())
                    .append("expireAt", new Date(System.currentTimeMillis() + 24L * 60 * 60 * 1000));

            Document update = new Document("$set", docToSave);

            reservationCollection.updateOne(filter, update, new UpdateOptions().upsert(true));

            // 콘솔에 찍기
            System.out.println("   >> DB 저장 완료");
            System.out.println("   >> Filter: " + filter.toJson());
            System.out.println("   >> Upsert Data: " + docToSave.toJson());
        } catch (Exception e) {
            System.err.println("DB 저장 중 오류: " + e.getMessage());
        }
    }

    /** 실행 */
    public static void main(String[] args) {
        System.out.println("=== [판타스트릭 크롤러 시작 - OkHttp + JSoup] ===");
        FantastrickCrawling crawler = new FantastrickCrawling();
        crawler.crawlAllDates();
        System.out.println("=== [판타스트릭 크롤링 종료] ===");
    }
}
