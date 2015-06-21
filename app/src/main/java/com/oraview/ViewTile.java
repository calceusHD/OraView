package com.oraview;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.AsyncTask;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public class ViewTile extends View {
    private static final int TILE_SIZE = 512;
    ArrayList<Layer> mLayers = new ArrayList<>();
    ZipFile mZipFile;
    GestureDetector mGestureDetector;
    ScaleGestureDetector mScaleGestureDetector;
    float mScale;
    PointF mPosition;
    Point mImageSize;
    boolean mNewImage;
    Bitmap mBitmap;
    PositionedBitmap mBitmaps[][] = null;
    private int mTileCountX;
    private int mTileCountY;


    public ViewTile(Context context) {
        super(context);
        init(context);
    }

    public ViewTile(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ViewTile(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    @TargetApi(21)
    public ViewTile(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context);
    }

    public void drawOra(String path) {
        try {
            mZipFile = new ZipFile(path);
            ZipEntry zipStack = mZipFile.getEntry("stack.xml");
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(mZipFile.getInputStream(zipStack));
            doc.getDocumentElement().normalize();
            NodeList nList = doc.getElementsByTagName("layer");

            for (int i = 0; i < nList.getLength(); ++i) {
                Node nNode = nList.item(i);
                if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) nNode;
                    mLayers.add(new Layer(element.getAttribute("src")));
                }

            }
            nList = doc.getElementsByTagName("image");
            for (int i = 0; i < nList.getLength(); ++i) {
                Node node = nList.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) node;
                    mImageSize.set(Integer.parseInt(element.getAttribute("w")), Integer.parseInt(element.getAttribute("h")));
                }
            }
            mBitmap = Bitmap.createBitmap(mImageSize.x, mImageSize.y, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(mBitmap);
            for (int i = mLayers.size() - 1; i >= 0; --i) {
                Layer layer = mLayers.get(i);
                ZipEntry layerEntry = mZipFile.getEntry(layer.getPath());
                Bitmap bitmapLayer = null;
                try {
                    bitmapLayer = BitmapFactory.decodeStream(mZipFile.getInputStream(layerEntry));
                    canvas.drawBitmap(bitmapLayer, 0, 0, null);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                //canvas.drawBitmap(bitmapLayer, 0.0f, 0.0f, null);
            }
            mBitmap.prepareToDraw();
            mNewImage = true;

        } catch (IOException | ParserConfigurationException | SAXException e) {
            e.printStackTrace();
        }


    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean retval = mGestureDetector.onTouchEvent(event);
        retval = mScaleGestureDetector.onTouchEvent(event) || retval;
        return super.onTouchEvent(event) || retval;
    }

    private void init(Context context) {
        mGestureDetector = new GestureDetector(context, new GestureListener());
        mScaleGestureDetector = new ScaleGestureDetector(context, new ScaleListener());
        mPosition = new PointF(0.0f, 0.0f);
        mScale = 1.0f;
        mImageSize = new Point(0, 0);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        redraw(canvas);
        super.onDraw(canvas);
    }

    private void redraw(Canvas canvas) {
        if (mBitmaps == null) {
            mTileCountX = 2 * (int) ((float) Math.ceil(getWidth() / TILE_SIZE) + 1);
            mTileCountY = 2 * (int) ((float) Math.ceil(getHeight() / TILE_SIZE) + 1);
            mBitmaps = new PositionedBitmap[mTileCountX][mTileCountY];
            for (int i = 0; i < mBitmaps.length; ++i) {
                for (int j = 0; j < mBitmaps[i].length; ++j) {
                    mBitmaps[i][j] = new PositionedBitmap(0, 0, Bitmap.createBitmap(TILE_SIZE, TILE_SIZE, Bitmap.Config.ARGB_8888));
                }
            }
        }
        if (mNewImage) {
            mScale = Math.min((float) canvas.getWidth() / mImageSize.x, (float) canvas.getHeight() / mImageSize.y);

            mNewImage = false;
        }

        Matrix m = new Matrix();
        m.setScale(mScale, mScale);
        m.postTranslate(mPosition.x, mPosition.y);
        RectF srcRectF = new RectF(0, 0, TILE_SIZE, TILE_SIZE);
        RectF renderRectF = new RectF();
        Rect renderRect = new Rect();
        for (int i = 0; i < mTileCountX; ++i) {
            for (int j = 0; j < mTileCountY; ++j) {
                if (!mBitmaps[i][j].comparePosition(i, j)) {
                    new ImageRenderer(i, j).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                    continue;
                }
                Matrix lm = new Matrix(m);
                lm.preTranslate(i * TILE_SIZE, j * TILE_SIZE);

                lm.mapRect(renderRectF, srcRectF);
                renderRect.set((int) Math.floor(renderRectF.left), (int) Math.floor(renderRectF.top), (int) Math.floor(renderRectF.right), (int) Math.floor(renderRectF.bottom));

                canvas.drawBitmap(mBitmaps[i][j].getBitmap(), null, renderRect, null);

            }
        }


    }

    class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            mPosition.offset(-distanceX, -distanceY);
            invalidate();
            return true;
        }
    }

    class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float scale = detector.getScaleFactor();
            if (scale * mScale < 0.5f) {
                scale = 0.5f / mScale;
                mScale = 0.5f;
            }
            mScale *= scale;

            mPosition.x = (mPosition.x - detector.getFocusX()) * scale + detector.getFocusX();
            mPosition.y = (mPosition.y - detector.getFocusY()) * scale + detector.getFocusY();
            invalidate();
            return true;
        }

    }

    class ImageRenderer extends AsyncTask<Void, Void, Void> {
        private int mXpos, mYpos;

        ImageRenderer(int xpos, int ypos) {
            super();
            mXpos = xpos;
            mYpos = ypos;
        }

        @Override
        protected Void doInBackground(Void... params) {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inBitmap = Bitmap.createBitmap(TILE_SIZE, TILE_SIZE, Bitmap.Config.ARGB_8888);

            Canvas canvas = new Canvas(mBitmaps[mXpos][mYpos].getBitmap());
            for (int k = mLayers.size() - 1; k >= 0; --k) {
                Layer layer = mLayers.get(k);
                ZipEntry layerEntry = mZipFile.getEntry(layer.getPath());
                Bitmap bitmapLayer;
                try {

                    BitmapRegionDecoder brd = BitmapRegionDecoder.newInstance(mZipFile.getInputStream(layerEntry), true);
                    if (mXpos * TILE_SIZE >= brd.getWidth() || mYpos * TILE_SIZE >= brd.getHeight()) {
                        continue;
                    }
                    Rect rect = new Rect(mXpos * TILE_SIZE, mYpos * TILE_SIZE, Math.min((mXpos + 1) * TILE_SIZE, brd.getWidth()), Math.min((mYpos + 1) * TILE_SIZE, brd.getHeight()));
                    System.out.println(rect.toString());
                    bitmapLayer = brd.decodeRegion(rect, opts);
                    canvas.drawBitmap(bitmapLayer, 0, 0, null);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                //canvas.drawBitmap(bitmapLayer, 0.0f, 0.0f, null);
            }
            mBitmaps[mXpos][mYpos].getBitmap().prepareToDraw();


            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            invalidate();
        }
    }
}
