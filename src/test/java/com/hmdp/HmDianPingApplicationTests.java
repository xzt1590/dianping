package com.hmdp;

import com.hmdp.utils.SnowFlakeGenerateIdWorker;
import org.junit.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private SnowFlakeGenerateIdWorker snowFlakeGenerateIdWorker;

    private ExecutorService es = Executors.newFixedThreadPool(100);

    @org.junit.jupiter.api.Test
    void testIdWoker() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(300);
        Runnable task = ()->{
            for (int i = 0; i < 100; i++) {
                long id = snowFlakeGenerateIdWorker.nextId();
                System.out.println("id = " + id);
            }
            countDownLatch.countDown();
        };
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        countDownLatch.await();
        long endTime = System.currentTimeMillis();
        System.out.println("time = " + (endTime - startTime));
    }

}
