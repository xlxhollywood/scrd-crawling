package org.example.fantastrick;

import okhttp3.*;

public class BookedAjaxExample {
    public static void main(String[] args) throws Exception {
        OkHttpClient client = new OkHttpClient();

        // 1) 실제 DevTools에서 본 Form Data를 그대로 추가
        FormBody formBody = new FormBody.Builder()
                .add("action", "booked_calendar_date")
                .add("date", "2025-03-3")
                .add("calendar_id", "23")
                .build();

        // 2) 요청 구성 (헤더 포함)
        Request request = new Request.Builder()
                .url("http://fantastrick.co.kr/wp-admin/admin-ajax.php")
                .post(formBody)
                // Content-Type은 꼭 지정
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                // Ajax 요청 시 보통 함께 전송
                .header("X-Requested-With", "XMLHttpRequest")

                // (필요하다면 추가로 아래 헤더들을 복사)
                // .header("Accept", "*/*")
                // .header("Accept-Encoding", "gzip, deflate")
                // .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                // .header("Connection", "keep-alive")
                // .header("Cookie", "PHPSESSID=hn2jisig5i0j56qoh44iiclpbv")
                // .header("Host", "fantastrick.co.kr")
                // .header("Origin", "http://fantastrick.co.kr")
                // .header("Referer", "http://fantastrick.co.kr/rooms/bookofduat/")
                // .header("User-Agent", "Mozilla/5.0 (Macintosh; ...)")
                .build();

        // 3) 요청 실행
        try (Response response = client.newCall(request).execute()) {
            System.out.println("응답 코드: " + response.code());
            String responseBody = response.body().string();
            System.out.println("응답 본문:\n" + responseBody);
        }
    }
}
