package com.github.garamflow.streamsettlement.util;

import org.redisson.api.RLock;

import java.util.concurrent.TimeUnit;

public class LockUtil {
    public static boolean tryWithLock(RLock lock, long waitTime, long leaseTime, Runnable action) {
        try {
            if (lock.tryLock(waitTime, leaseTime, TimeUnit.MILLISECONDS)) {
                try {
                    action.run();
                    return true;
                } finally {
                    lock.unlock();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return false;
    }
}
