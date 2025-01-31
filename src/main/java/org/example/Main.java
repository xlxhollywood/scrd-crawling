package org.example;

import org.example.beatphobia.BeatphobiaCrawling;
import org.example.doorescape.DoorEscapeCrawling;
import org.example.keyescape.KeyescapeCrawling;
import org.example.pointnine.PointNineCrawling;
import org.openqa.selenium.chrome.ChromeDriver;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) {
        System.out.println("Start DoorEscape Crawling...");
        System.setProperty("webdriver.chrome.driver", "/Users/pro/Downloads/chromedriver-mac-x64/chromedriver");

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1); // 1개의 스레드만 사용

        Runnable task = () -> {
            System.out.println("=== 도어이스케이프 스케줄러 실행 ===");
            System.out.flush(); // 강제 출력
            ChromeDriver driver = null;
            try {
                driver = new ChromeDriver();
                DoorEscapeCrawling doorEscapeCrawling = new DoorEscapeCrawling(driver);

                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
                Calendar calendar = Calendar.getInstance();

                for (int i = 0; i < 7; i++) { // 현재 날짜부터 7일간 반복
                    String date = dateFormat.format(calendar.getTime());
                    System.out.println("[DoorEscape] 날짜: " + date);

                    doorEscapeCrawling.crawlThemesForDate(date); // 도어이스케이프 크롤링 실행

                    calendar.add(Calendar.DAY_OF_YEAR, 1); // 다음 날로 이동
                }

                System.out.println("[DoorEscape] 크롤링 완료");
            } catch (Exception e) {
                System.err.println("[DoorEscape] Error during crawling: " + e.getMessage());
                e.printStackTrace(); // 에러 상세 출력
            } finally {
                if (driver != null) {
                    driver.quit();
                }
            }
        };

        // 매시간마다 크롤링 실행 (0초 후 시작, 1시간 간격 실행)
        scheduler.scheduleAtFixedRate(task, 0, 1, TimeUnit.HOURS);

        // 메인 스레드 유지 (스케줄러가 계속 동작하도록 함)
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            System.err.println("Main thread interrupted: " + e.getMessage());
        }
//        System.out.println("=== Start Multi-Site Crawling ===");
//        System.setProperty("webdriver.chrome.driver", "/Users/pro/Downloads/chromedriver-mac-x64/chromedriver");
//
//        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3); // 3개의 스레드 풀 생성
//
//        // ✅ 1. PointNine 크롤링 실행 스레드
//        Runnable pointNineTask = () -> {
//            String threadName = Thread.currentThread().getName();
//            System.out.println("=== [PointNine] 스케줄러 실행 (Thread: " + threadName + ") ===");
//            long startTime = System.currentTimeMillis();
//
//            ChromeDriver driver = null;
//            try {
//                driver = new ChromeDriver();
//                PointNineCrawling pointNineCrawling = new PointNineCrawling(driver);
//
//                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
//                Calendar calendar = Calendar.getInstance();
//
//                for (int i = 0; i < 7; i++) {
//                    String date = dateFormat.format(calendar.getTime());
//                    System.out.println("[PointNine] (Thread: " + threadName + ") 날짜: " + date);
//                    pointNineCrawling.crawlThemesForDate(date);
//                    calendar.add(Calendar.DAY_OF_YEAR, 1);
//                }
//
//                System.out.println("[PointNine] (Thread: " + threadName + ") 완료");
//            } catch (Exception e) {
//                System.err.println("[PointNine] (Thread: " + threadName + ") Error: " + e.getMessage());
//            } finally {
//                if (driver != null) driver.quit();
//                System.out.println("[PointNine] (Thread: " + threadName + ") Execution Time: " +
//                        (System.currentTimeMillis() - startTime) + "ms");
//            }
//        };
//
//        // ✅ 2. Beatphobia 크롤링 실행 스레드
//        Runnable beatphobiaTask = () -> {
//            String threadName = Thread.currentThread().getName();
//            System.out.println("=== [Beatphobia] 스케줄러 실행 (Thread: " + threadName + ") ===");
//            long startTime = System.currentTimeMillis();
//
//            ChromeDriver driver = null;
//            try {
//                driver = new ChromeDriver();
//                BeatphobiaCrawling beatphobiaCrawling = new BeatphobiaCrawling(driver);
//
//                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
//                Calendar calendar = Calendar.getInstance();
//
//                for (int i = 0; i < 7; i++) {
//                    String date = dateFormat.format(calendar.getTime());
//                    System.out.println("[Beatphobia] (Thread: " + threadName + ") 날짜: " + date);
//                    beatphobiaCrawling.crawlThemesForDate(date);
//                    calendar.add(Calendar.DAY_OF_YEAR, 1);
//                }
//
//                System.out.println("[Beatphobia] (Thread: " + threadName + ") 완료");
//            } catch (Exception e) {
//                System.err.println("[Beatphobia] (Thread: " + threadName + ") Error: " + e.getMessage());
//            } finally {
//                if (driver != null) driver.quit();
//                System.out.println("[Beatphobia] (Thread: " + threadName + ") Execution Time: " +
//                        (System.currentTimeMillis() - startTime) + "ms");
//            }
//        };
//
//        // ✅ 3. Keyescape 크롤링 실행 스레드
//        Runnable keyescapeTask = () -> {
//            String threadName = Thread.currentThread().getName();
//            System.out.println("=== [Keyescape] 스케줄러 실행 (Thread: " + threadName + ") ===");
//            long startTime = System.currentTimeMillis();
//
//            ChromeDriver driver = null;
//            try {
//                driver = new ChromeDriver();
//                KeyescapeCrawling keyescapeCrawling = new KeyescapeCrawling(driver);
//
//                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
//                Calendar calendar = Calendar.getInstance();
//
//                for (int i = 0; i < 7; i++) {
//                    String date = dateFormat.format(calendar.getTime());
//                    System.out.println("[Keyescape] (Thread: " + threadName + ") 날짜: " + date);
//                    keyescapeCrawling.crawlThemesForDate(date);
//                    calendar.add(Calendar.DAY_OF_YEAR, 1);
//                }
//
//                System.out.println("[Keyescape] (Thread: " + threadName + ") 완료");
//            } catch (Exception e) {
//                System.err.println("[Keyescape] (Thread: " + threadName + ") Error: " + e.getMessage());
//            } finally {
//                if (driver != null) driver.quit();
//                System.out.println("[Keyescape] (Thread: " + threadName + ") Execution Time: " +
//                        (System.currentTimeMillis() - startTime) + "ms");
//            }
//        };
//
//        // ✅ 3개의 스케줄러 작업을 병렬로 실행 (매 1시간마다 실행)
//        scheduler.scheduleAtFixedRate(pointNineTask, 0, 1, TimeUnit.HOURS);
//        scheduler.scheduleAtFixedRate(beatphobiaTask, 0, 1, TimeUnit.HOURS);
//        scheduler.scheduleAtFixedRate(keyescapeTask, 0, 1, TimeUnit.HOURS);
//
//        // 메인 스레드 유지 (스케줄러가 계속 동작하도록 함)
//        try {
//            Thread.currentThread().join();
//        } catch (InterruptedException e) {
//            System.err.println("Main thread interrupted: " + e.getMessage());
//        }
    }
}
