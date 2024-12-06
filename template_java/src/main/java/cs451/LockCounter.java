package cs451;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

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
    //signalAll when counter will become > 0 
    //increment max window size for every message received
    //TODO - more conservative?
    
    public void increment(int value) {
        lock();
        try {
            if ((counter <= 0 )&& (counter >= (-value) + 1)) {
            counter += (2 * value);
            max_window_size += value;
            condition.signalAll();
        } else {
            counter += (2 * value);
            max_window_size += value;
        }      
        } finally {
            unlock();
        }
    }
    
    public void decrementCounter(int value) {
        lock();
        try {
            counter -= value;
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