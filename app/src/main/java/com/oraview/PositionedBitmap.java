package com.oraview;

import android.graphics.Bitmap;

public class PositionedBitmap {
    private Bitmap mBitmap;
    private int mX, mY;

    PositionedBitmap(int x, int y, Bitmap bitmap) {
        mBitmap = bitmap;
        mX = x;
        mY = y;
    }

    public Bitmap getBitmap() {
        return mBitmap;
    }

    public boolean comparePosition(int x, int y) {
        return x == mX && y == mY;
    }

    public void setPosition(int x, int y) {
        mX = x;
        mY = y;
    }
}
