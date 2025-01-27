package org.example;

import org.example.beatphobia.BeatphobiaCrawling;
import org.example.keyescape.KeyescapeCrawling;
import org.openqa.selenium.chrome.ChromeDriver;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main {

    public static void main(String[] args) {
        System.out.println("Start");
        System.setProperty("webdriver.chrome.driver", "/Users/pro/Downloads/chromedriver-mac-x64/chromedriver");

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2); // 스레드 풀 크기를 2로 설정

        Runnable keyescapeTask = () -> {
            String threadName = Thread.currentThread().getName();
            System.out.println("=== Keyescape 스케줄러 실행 (Thread: " + threadName + ") ===");
            long startTime = System.currentTimeMillis();

            ChromeDriver driver = null;
            try {
                driver = new ChromeDriver();
                KeyescapeCrawling keyescapeCrawling = new KeyescapeCrawling(driver);

                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
                Calendar calendar = Calendar.getInstance();

                for (int i = 0; i < 7; i++) { // 현재 날짜부터 7일간 반복
                    String date = dateFormat.format(calendar.getTime());
                    System.out.println("[Keyescape] (Thread: " + threadName + ") 날짜: " + date);
                    keyescapeCrawling.crawlThemesForDate(date);
                    calendar.add(Calendar.DAY_OF_YEAR, 1); // 다음 날로 이동
                }

                System.out.println("[Keyescape] (Thread: " + threadName + ") 크롤링 완료");
            } catch (Exception e) {
                System.err.println("[Keyescape] (Thread: " + threadName + ") Error during crawling: " + e.getMessage());
                e.printStackTrace();
            } finally {
                if (driver != null) {
                    driver.quit();
                }
                System.out.println("[Keyescape] (Thread: " + threadName + ") Execution Time: " +
                        (System.currentTimeMillis() - startTime) + "ms");
            }
        };

        Runnable beatphobiaTask = () -> {
            String threadName = Thread.currentThread().getName();
            System.out.println("=== Beatphobia 스케줄러 실행 (Thread: " + threadName + ") ===");
            long startTime = System.currentTimeMillis();

            ChromeDriver driver = null;
            try {
                driver = new ChromeDriver();
                BeatphobiaCrawling beatphobiaCrawling = new BeatphobiaCrawling(driver);

                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
                Calendar calendar = Calendar.getInstance();

                for (int i = 0; i < 7; i++) { // 현재 날짜부터 7일간 반복
                    String date = dateFormat.format(calendar.getTime());
                    System.out.println("[Beatphobia] (Thread: " + threadName + ") 날짜: " + date);
                    beatphobiaCrawling.crawlThemesForDate(date);
                    calendar.add(Calendar.DAY_OF_YEAR, 1); // 다음 날로 이동
                }

                System.out.println("[Beatphobia] (Thread: " + threadName + ") 크롤링 완료");
            } catch (Exception e) {
                System.err.println("[Beatphobia] (Thread: " + threadName + ") Error during crawling: " + e.getMessage());
                e.printStackTrace();
            } finally {
                if (driver != null) {
                    driver.quit();
                }
                System.out.println("[Beatphobia] (Thread: " + threadName + ") Execution Time: " +
                        (System.currentTimeMillis() - startTime) + "ms");
            }
        };

        // 두 개의 스케줄러 작업을 병렬로 실행
        scheduler.scheduleAtFixedRate(keyescapeTask, 0, 1, TimeUnit.HOURS);
        scheduler.scheduleAtFixedRate(beatphobiaTask, 0, 1, TimeUnit.HOURS);

        // 메인 스레드 유지
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            System.err.println("Main thread interrupted: " + e.getMessage());
        }
    }
}
