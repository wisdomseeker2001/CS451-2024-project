package cs451;

import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;

//TODO different lock for counter and window size?

public class LockCounter {
    
    private int max_window_size = 1;
    private int counter = max_window_size;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();

    public void lock() {
        lock.lock();
    }
    public void unlock() {
        lock.unlock();
    }
    //called when Ack is received
    //window Size increased by 1
    //COunter by 2 (1 for ack of packet and 1 for increent in window)
    public void increment() {
        lock();
        try {
            if (counter == 0 || counter == -1) {
            counter += 2;
            max_window_size += 1;
            condition.signalAll();
        } else {
            counter += 2;
            max_window_size += 1;
        }      
        } finally {
            unlock();
        }
    }
    
    public void decrementCounter() {
        lock();
        try {
            counter -= 1;
        } finally {
            unlock();
        }
    }

    public void setNewWindowSize(int value) {
        lock();
        try {
            max_window_size = value;
        } finally {
            unlock();
        }
    }

    public void setCounter(int value) {
        lock();
        try {
            counter = value;
        } finally {
            unlock();
        }
    }

    public int getWindowValue() {
        lock();
        try {
            return max_window_size;
        } finally {
            unlock();
        }
    }

    public int getCounterValue() {
        lock();
        try {
            return counter;
        } finally {
            unlock();
        }
    }

    public void waitForNonZero() throws InterruptedException {
        lock();
        try {
            while (counter == 0) {
                condition.await();
            }
        } finally {
            unlock();
        }
    }


    
}