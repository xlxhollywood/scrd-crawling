package org.example.goldenkey;

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

public class GoldenkeyCrawling {

    private final MongoCollection<Document> reservationCollection;

    /**
     * 매장별 URL 호출을 위한 정보
     */
    private static class StoreInfo {
        String sZizum;
        String location;
        String branch;
        StoreInfo(String sZizum, String location, String branch) {
            this.sZizum = sZizum;
            this.location = location;
            this.branch = branch;
        }
    }
    // URL 호출 시 s_zizum 값에 따른 매장 정보 (브랜드 "황금열쇠" 대상)
    private static final Map<String, StoreInfo> STORE_INFO_MAP = new HashMap<>();
    static {
        // URL 설명에 따른 매장 매핑 (테이블 매핑과 일치하도록 지점명을 조정)
        STORE_INFO_MAP.put("1", new StoreInfo("1", "대구", "동성로점"));            // 대구/동성로본점 → 동성로점으로 처리
        STORE_INFO_MAP.put("11", new StoreInfo("11", "대구", "동성로 2호점"));
        STORE_INFO_MAP.put("5", new StoreInfo("5", "강남", "강남 (타임스퀘어)"));
        STORE_INFO_MAP.put("6", new StoreInfo("6", "강남", "강남점 (플라워로드)"));
        STORE_INFO_MAP.put("7", new StoreInfo("7", "건대", "건대점"));
    }

    /**
     * 미리 정의한 테마 매핑 정보
     */
    private static class ThemeInfo {
        int id;
        String brand;
        String location;
        String branch;
        String title;
        ThemeInfo(int id, String brand, String location, String branch, String title) {
            this.id = id;
            this.brand = brand;
            this.location = location;
            this.branch = branch;
            this.title = title;
        }
    }
    // 테이블의 항목들을 리스트로 정의
    private static final List<ThemeInfo> GOLDEN_KEY_THEME_INFO = new ArrayList<>();
    static {
        GOLDEN_KEY_THEME_INFO.add(new ThemeInfo(280, "황금열쇠", "대구", "동성로 2호점", "냥탐정 셜록켓"));
        GOLDEN_KEY_THEME_INFO.add(new ThemeInfo(281, "황금열쇠", "강남", "강남점 (플라워로드)", "Back화점"));
        GOLDEN_KEY_THEME_INFO.add(new ThemeInfo(282, "황금열쇠", "건대", "건대점", "fl[ae]sh"));
        GOLDEN_KEY_THEME_INFO.add(new ThemeInfo(283, "황금열쇠", "건대", "건대점", "NOW HERE"));
        GOLDEN_KEY_THEME_INFO.add(new ThemeInfo(284, "황금열쇠", "대구", "동성로점", "경산"));
        GOLDEN_KEY_THEME_INFO.add(new ThemeInfo(285, "황금열쇠", "대구", "동성로점", "가이아 기적의 땅"));
        GOLDEN_KEY_THEME_INFO.add(new ThemeInfo(286, "황금열쇠", "대구", "동성로점", "JAIL.O"));
        GOLDEN_KEY_THEME_INFO.add(new ThemeInfo(287, "황금열쇠", "대구", "동성로점", "타임스틸러"));
        GOLDEN_KEY_THEME_INFO.add(new ThemeInfo(288, "황금열쇠", "대구", "동성로점", "X됐다"));
        GOLDEN_KEY_THEME_INFO.add(new ThemeInfo(289, "황금열쇠", "대구", "동성로 2호점", "BAD ROB BAD"));
        GOLDEN_KEY_THEME_INFO.add(new ThemeInfo(290, "황금열쇠", "대구", "동성로 2호점", "2Ways"));
        GOLDEN_KEY_THEME_INFO.add(new ThemeInfo(291, "황금열쇠", "대구", "동성로 2호점", "LAST"));
        GOLDEN_KEY_THEME_INFO.add(new ThemeInfo(292, "황금열쇠", "대구", "동성로 2호점", "PILGRIM"));
        GOLDEN_KEY_THEME_INFO.add(new ThemeInfo(293, "황금열쇠", "대구", "동성로 2호점", "지옥"));
        GOLDEN_KEY_THEME_INFO.add(new ThemeInfo(294, "황금열쇠", "대구", "동성로 2호점", "다시, 너에게"));
        GOLDEN_KEY_THEME_INFO.add(new ThemeInfo(295, "황금열쇠", "대구", "동성로 2호점", "HEAVEN"));
        GOLDEN_KEY_THEME_INFO.add(new ThemeInfo(296, "황금열쇠", "강남", "강남점 (플라워로드)", "ANOTHER"));
        GOLDEN_KEY_THEME_INFO.add(new ThemeInfo(297, "황금열쇠", "강남", "강남 (타임스퀘어)", "NOMON : THE ORDEAL"));
        GOLDEN_KEY_THEME_INFO.add(new ThemeInfo(298, "황금열쇠", "강남", "강남 (타임스퀘어)", "섬 : 잊혀진 이야기 (미스터리)"));
    }

    public GoldenkeyCrawling() {
        MongoClient mongoClient = MongoConfig.getMongoClient();
        MongoDatabase database = mongoClient.getDatabase("scrd");
        this.reservationCollection = database.getCollection("reservation");
    }

    /**
     * 테마 박스에 담긴 데이터를 임시로 저장할 내부 클래스
     */
    private static class ParsedTheme {
        String extractedTitle; // HTML에서 추출한 제목
        List<String> availableTimes;
        ParsedTheme(String extractedTitle, List<String> availableTimes) {
            this.extractedTitle = extractedTitle;
            this.availableTimes = availableTimes;
        }
    }

    /**
     * 전체 매장에 대해 오늘부터 일주일간 크롤링 수행
     */
    public void crawlAllDates() {
        System.out.println("===== [황금열쇠 예약 크롤링 시작 - (OkHttp + JSoup)] =====");
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

        for (StoreInfo store : STORE_INFO_MAP.values()) {
            Calendar tempCal = (Calendar) cal.clone();
            for (int i = 0; i < 7; i++) {
                String dateStr = dateFormat.format(tempCal.getTime());
                System.out.println("\n>>> [" + store.branch + " / " + store.location + "] " + dateStr + " 크롤링 중...");
                String html = requestDateHtml(store.sZizum, dateStr);
                if (html == null) {
                    System.out.println("   - HTML 응답이 null. 요청 실패.");
                } else {
                    List<ParsedTheme> themes = parseThemes(html);
                    for (ParsedTheme pt : themes) {
                        // 개선된 매핑 로직: 현재 매장의 지점과 제목 유사도를 기반으로 매핑 정보 찾기
                        ThemeInfo mapping = findThemeInfo(pt.extractedTitle, store.branch);
                        String finalTitle = (mapping != null) ? mapping.title : pt.extractedTitle;
                        int finalId = (mapping != null) ? mapping.id : 0;
                        String finalBrand = (mapping != null) ? mapping.brand : "황금열쇠";
                        String finalLocation = (mapping != null) ? mapping.location : store.location;
                        String finalBranch = (mapping != null) ? mapping.branch : store.branch;

                        if (!pt.availableTimes.isEmpty()) {
                            saveToDatabase(finalBrand, finalLocation, finalBranch, finalTitle, finalId, dateStr, pt.availableTimes);
                        } else {
                            System.out.println("   - 테마 [" + finalTitle + "] 예약 가능 시간 없음.");
                        }
                    }
                }
                tempCal.add(Calendar.DAY_OF_MONTH, 1);
            }
        }
        System.out.println("\n===== [황금열쇠 크롤링 종료] =====");
    }

    /**
     * URL 호출 (GET) – rev_days, s_zizum, go 파라미터 포함
     */
    private String requestDateHtml(String sZizum, String dateStr) {
        try {
            OkHttpClient client = new OkHttpClient();
            String url = "http://xn--jj0b998aq3cptw.com/layout/res/home.php?rev_days=" + dateStr
                    + "&s_zizum=" + sZizum + "&go=rev.make";
            Request request = new Request.Builder().url(url).get().build();
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
     * HTML에서 각 테마 박스를 파싱하여, 테마 제목과 예약 가능한 시간(예약가능 링크)을 추출
     */
    private List<ParsedTheme> parseThemes(String html) {
        List<ParsedTheme> themeList = new ArrayList<>();
        try {
            org.jsoup.nodes.Document doc = Jsoup.parse(html);
            Elements themeBoxes = doc.select("div.theme_box");
            for (Element box : themeBoxes) {
                Element titleEl = box.selectFirst("h3.h3_theme");
                if (titleEl == null) continue;
                String extractedTitle = titleEl.text().trim();
                List<String> availableTimes = new ArrayList<>();
                Elements liElements = box.select("div.time_Area ul.reserve_Time li");
                for (Element li : liElements) {
                    Element aTag = li.selectFirst("a");
                    if (aTag == null) continue;
                    // 예약가능한 경우: <a>에 href 속성이 있고 내부에 span.possible 존재
                    if (aTag.hasAttr("href") && aTag.selectFirst("span.possible") != null) {
                        Element timeEl = aTag.selectFirst("span.time");
                        if (timeEl != null) {
                            String timeText = timeEl.text().trim();
                            availableTimes.add(timeText);
                            System.out.println("   [DEBUG] " + extractedTitle + " - " + timeText + " => 예약가능 (추가)");
                        }
                    } else {
                        Element timeEl = aTag.selectFirst("span.time");
                        if (timeEl != null) {
                            System.out.println("   [DEBUG] " + extractedTitle + " - " + timeEl.text().trim() + " => 예약마감 (제외)");
                        }
                    }
                }
                themeList.add(new ParsedTheme(extractedTitle, availableTimes));
            }
        } catch (Exception e) {
            System.err.println("parseThemes() 오류: " + e.getMessage());
        }
        return themeList;
    }

    /**
     * 미리 정의한 테마 매핑 목록에서, 현재 매장의 지점(branch)와
     * HTML에서 추출한 제목(extractedTitle)의 유사도를 기반으로 일치하는 항목 찾기
     * 개선된 로직: 문자열을 정규화한 후 Jaro–Winkler 유사도를 계산하여 일정 임계치(예: 0.85) 이상이면 매핑으로 인정
     */
    private ThemeInfo findThemeInfo(String extractedTitle, String storeBranch) {
        String normalizedExtracted = normalize(extractedTitle);
        ThemeInfo bestMatch = null;
        double bestSimilarity = 0.0;

        for (ThemeInfo info : GOLDEN_KEY_THEME_INFO) {
            if (info.branch.equals(storeBranch)) {
                String normalizedMapping = normalize(info.title);
                double similarity = jaroWinklerSimilarity(normalizedExtracted, normalizedMapping);
                if (similarity > bestSimilarity) {
                    bestSimilarity = similarity;
                    bestMatch = info;
                }
            }
        }
        // 예: 유사도가 0.85 이상이면 매핑으로 인정 (임계치는 필요에 따라 조정)
        if (bestMatch != null && bestSimilarity >= 0.85) {
            System.out.println("   [DEBUG] 매핑 성공: " + extractedTitle + " => " + bestMatch.title + " (유사도: " + bestSimilarity + ")");
            return bestMatch;
        }
        System.out.println("   [DEBUG] 매핑 실패: " + extractedTitle + " (최고 유사도: " + bestSimilarity + ")");
        return null;
    }

    /**
     * 문자열 정규화: 소문자 변환 및 한글, 알파벳, 숫자 외 문자 제거
     */
    private String normalize(String s) {
        return s.toLowerCase().replaceAll("[^가-힣a-z0-9]", "");
    }

    /**
     * Jaro–Winkler 유사도 계산
     */
    private double jaroWinklerSimilarity(String s, String t) {
        if (s.equals(t)) {
            return 1.0;
        }
        int sLen = s.length();
        int tLen = t.length();
        if (sLen == 0 || tLen == 0) {
            return 0.0;
        }
        int matchDistance = Math.max(sLen, tLen) / 2 - 1;
        boolean[] sMatches = new boolean[sLen];
        boolean[] tMatches = new boolean[tLen];
        int matches = 0;
        for (int i = 0; i < sLen; i++) {
            int start = Math.max(0, i - matchDistance);
            int end = Math.min(i + matchDistance + 1, tLen);
            for (int j = start; j < end; j++) {
                if (tMatches[j]) {
                    continue;
                }
                if (s.charAt(i) != t.charAt(j)) {
                    continue;
                }
                sMatches[i] = true;
                tMatches[j] = true;
                matches++;
                break;
            }
        }
        if (matches == 0) {
            return 0.0;
        }
        double transpositions = 0;
        int k = 0;
        for (int i = 0; i < sLen; i++) {
            if (!sMatches[i]) continue;
            while (!tMatches[k]) {
                k++;
            }
            if (s.charAt(i) != t.charAt(k)) {
                transpositions++;
            }
            k++;
        }
        transpositions /= 2.0;
        double jaro = ((double) matches / sLen + (double) matches / tLen + ((double) (matches - transpositions)) / matches) / 3.0;
        // Jaro-Winkler: 공통 접두사 최대 4자, 스케일 상수 0.1 적용
        int prefix = 0;
        for (int i = 0; i < Math.min(4, Math.min(sLen, tLen)); i++) {
            if (s.charAt(i) == t.charAt(i)) {
                prefix++;
            } else {
                break;
            }
        }
        return jaro + prefix * 0.1 * (1 - jaro);
    }

    /**
     * MongoDB에 upsert – 저장 구조:
     * brand, location, branch, title, id, date, availableTimes, updatedAt, expireAt
     */
    private void saveToDatabase(String brand,
                                String location,
                                String branch,
                                String title,
                                int id,
                                String date,
                                List<String> availableTimes) {
        try {
            Document filter = new Document("title", title)
                    .append("date", date)
                    .append("brand", brand);
            Document docToSave = new Document()
                    .append("brand", brand)
                    .append("location", location)
                    .append("branch", branch)
                    .append("title", title)
                    .append("id", id)
                    .append("date", date)
                    .append("availableTimes", availableTimes)
                    .append("updatedAt", new Date())
                    .append("expireAt", new Date(System.currentTimeMillis() + 24L * 60 * 60 * 1000));
            Document update = new Document("$set", docToSave);
            reservationCollection.updateOne(filter, update, new UpdateOptions().upsert(true));
            System.out.println("   >> DB 저장 완료");
            System.out.println("   >> Filter: " + filter.toJson());
            System.out.println("   >> Upsert Data: " + docToSave.toJson());
        } catch (Exception e) {
            System.err.println("DB 저장 중 오류: " + e.getMessage());
        }
    }

    /** 실행 */
    public static void main(String[] args) {
        System.out.println("=== [황금열쇠 크롤러 시작 - OkHttp + JSoup] ===");
        GoldenkeyCrawling crawler = new GoldenkeyCrawling();
        crawler.crawlAllDates();
        System.out.println("=== [황금열쇠 크롤링 종료] ===");
    }
}
