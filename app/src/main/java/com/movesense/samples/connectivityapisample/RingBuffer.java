package com.movesense.samples.connectivityapisample;

public class RingBuffer {
    private int capacity;
    private int internalPos;
    private double[] items;
    private boolean isFilled;
    private boolean isClean;

    public RingBuffer(int size) {
        capacity = size;
        items = new double[capacity];
        internalPos = 0;
        isFilled = false;
        isClean = true;
    }

    public void append(double value) {
        items[internalPos] = value;
        internalPos = (internalPos + 1) % capacity;

        if (isClean) {
            for (int i = 1; i < capacity; i++) {
                items[i] = value;
            }
        }

        if (internalPos == 0) {
            // full round taken -> ring buffer filled
            isFilled = true;
        }

        isClean = false;
    }

    public double get(int pos) {
        return items[(internalPos + pos) % capacity];
    }

    public boolean isFilled() {
        return isFilled;
    }

    public int getCapacity() {
        return capacity;
    }
}
