package cs451;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class ResendSignal {
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private boolean resenderActive = false;

    public void waitForResender() throws InterruptedException {
        lock.lock();
        try {
            while (!resenderActive) {
                condition.await();
            }
            resenderActive = false;
        } finally {
            lock.unlock();
        }
    }

    public void signalResender() {
        lock.lock();
        try {
            resenderActive = true;
            condition.signalAll();
        } finally {
            lock.unlock();
        }
    }
}