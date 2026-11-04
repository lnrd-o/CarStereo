package com.simple.carlauncher;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.Scroller;

/** Minimal horizontal pager: drag and snap between full-screen pages, no dependencies. */
public class Pager extends ViewGroup {

    public interface OnPage {
        void onPage(int page);
    }

    private final Scroller scroller;
    private final int touchSlop;
    private OnPage listener;
    private float downX, downY, lastX;
    private int page;
    private boolean dragging;

    public Pager(Context c) {
        super(c);
        scroller = new Scroller(c);
        touchSlop = ViewConfiguration.get(c).getScaledTouchSlop();
    }

    public void setOnPage(OnPage l) {
        listener = l;
    }

    public int page() {
        return page;
    }

    public int pages() {
        return getChildCount();
    }

    public void setPage(int p, boolean animate) {
        int count = getChildCount();
        if (count == 0) return;
        p = Math.max(0, Math.min(count - 1, p));
        int x = p * getWidth();
        if (animate && getWidth() > 0) {
            scroller.startScroll(getScrollX(), 0, x - getScrollX(), 0, 260);
            postInvalidateOnAnimation();
        } else {
            scrollTo(x, 0);
        }
        if (page != p) {
            page = p;
            if (listener != null) listener.onPage(p);
        }
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        int h = MeasureSpec.getSize(hSpec);
        setMeasuredDimension(w, h);
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l;
        int h = b - t;
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).layout(i * w, 0, (i + 1) * w, h);
        }
        scrollTo(page * w, 0);
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollTo(scroller.getCurrX(), 0);
            postInvalidateOnAnimation();
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = e.getX();
                downY = e.getY();
                dragging = false;
                scroller.abortAnimation();
                break;
            case MotionEvent.ACTION_MOVE: {
                float dx = Math.abs(e.getX() - downX);
                float dy = Math.abs(e.getY() - downY);
                if (dx > touchSlop && dx > dy * 1.2f) {
                    dragging = true;
                    lastX = e.getX();
                    return true;
                }
                break;
            }
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = e.getX();
                downY = e.getY();
                dragging = false;
                scroller.abortAnimation();
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = lastX - e.getX();
                lastX = e.getX();
                if (!dragging && Math.abs(e.getX() - downX) > touchSlop) dragging = true;
                if (dragging) {
                    int max = (getChildCount() - 1) * getWidth();
                    int nx = (int) Math.max(0, Math.min(max, getScrollX() + dx));
                    scrollTo(nx, 0);
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                int w = getWidth();
                int p = w == 0 ? 0 : Math.round(getScrollX() / (float) w);
                setPage(p, true);
                dragging = false;
                return true;
            }
        }
        return super.onTouchEvent(e);
    }
}
