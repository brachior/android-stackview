package net.brach.android.stackview;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

public class StackView extends FrameLayout {
    private final int padding;
    private final int margin;
    private final int radius;
    private final int animDuration;
    private final int swipe;
    private final int[] initPadding;

    private final LinearLayout back;
    private final ImageView backContent;

    private final LinearLayout empty;
    private final LinearLayout front;
    private final LinearLayout tmp;

    private float dX;
    private float dY;
    private float initX = -1;
    private float initY = -1;
    private View frontContent;
    private Adapter adapter;

    public StackView(Context context) {
        this(context, null);
    }

    public StackView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public StackView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.StackView, 0, 0);
        swipe = (int) a.getDimension(R.styleable.StackView_swipe, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 67, displayMetrics));
        margin = (int) a.getDimension(R.styleable.StackView_margin, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, displayMetrics));
        padding = (int) a.getDimension(R.styleable.StackView_padding, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, displayMetrics));
        radius = (int) a.getDimension(R.styleable.StackView_radius, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 0, displayMetrics));
        animDuration = a.getInteger(R.styleable.StackView_animation_duration, 200);
        int layout = a.getResourceId(R.styleable.StackView_preview_layout, -1);
        a.recycle();

        initPadding = new int[] {getPaddingLeft(), getPaddingTop(), getPaddingRight(), getPaddingBottom()};

        removeAllViews();

        tmp = new LinearLayout(context);
        tmp.setOrientation(LinearLayout.HORIZONTAL);
        LayoutParams tmpParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        tmpParams.setMargins(margin, margin, margin, margin);
        tmp.setLayoutParams(tmpParams);
        tmp.setVisibility(INVISIBLE);
        addView(tmp);

        empty = new LinearLayout(context);
        empty.setOrientation(LinearLayout.HORIZONTAL);
        empty.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        empty.setVisibility(GONE);
        addView(empty);

        backContent = new ImageView(context);
        backContent.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        back = new LinearLayout(context);
        back.setOrientation(LinearLayout.HORIZONTAL);
        LayoutParams backParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        backParams.setMargins(margin, margin, margin, margin);
        back.setLayoutParams(backParams);
        back.setPadding(padding, padding, padding, padding);
        back.addView(backContent);
        addView(back);

        front = new LinearLayout(context);
        front.setOrientation(LinearLayout.HORIZONTAL);
        LayoutParams frontParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        front.setLayoutParams(frontParams);
        addView(front);

        if (isInEditMode() && layout != -1) {
            inflate(getContext(), layout, front);
        }
    }

    public void setAdapter(final Adapter adapter) {
        this.adapter = adapter;
        this.adapter.register(this);

        switch (adapter.getItemCount()) {
            case 0:
                View view = adapter.createAndBindEmptyView(empty);
                if (view != null) {
                    empty.setVisibility(VISIBLE);
                }
                break;
            default:
                fillBack();
            case 1:
                empty.setVisibility(GONE);
                fillFront();

                if (initX == -1) {
                    initX = frontContent.getX();
                    initY = frontContent.getY();
                }
                break;
        }
        requestLayout();
    }

    public static abstract class Adapter {
        private StackView listener;

        public enum Position {
            FIRST(0), SECOND(1);

            public final int value;

            Position(int value) {
                this.value = value;
            }
        }

        /**
         * Create/Inflate a view used if no item available.
         *
         * @return the view to use.
         */
        public View createAndBindEmptyView(ViewGroup parent) {
            return null;
        }

        /**
         * Create/Inflate the default view used by the method 'createAndBindView'.
         *
         * @param parent container
         * @param position 'FIRST' or 'SECOND'
         *
         * @return the default view to use.
         */
        public abstract View onCreateView(ViewGroup parent, Position position);

        /**
         * Fill view with the item.
         *
         * @param view view to fill
         * @param position 'FIRST' or 'SECOND'
         */
        public abstract void onBindView(View view, Position position);

        /**
         * Create the view and fill it with the item.
         * Use by default the methods 'onCreateView' and 'onBindView'.
         *
         * @param parent container
         * @param position 'FIRST' or 'SECOND'
         *
         * @return the view corresponding to the item.
         */
        public View createAndBindView(ViewGroup parent, Position position) {
            if (getItemCount() > 0) {
                parent.removeAllViews();
                View view = onCreateView(parent, position);
                parent.addView(view, -1);
                onBindView(view, position);
                return view;
            }
            return null;
        }

        /**
         * Number of items
         *
         * @return number of items
         */
        public abstract int getItemCount();

        /**
         * Remove first element.
         *
         * Should be call when the card is swiped
         * and when the first element needs to be removed.
         */
        public abstract void remove();

        /**
         * Notify any registered observers that the data set has changed.
         */
        public void notifyDataSetChangedOnMainThread() {
            if (listener != null) {
                listener.notifyDataSetChangedOnMainThread();
            }
        }

        /**
         * Notify any registered observers that the data set has changed.
         */
        public void notifyDataSetChanged() {
            if (listener != null) {
                listener.notifyDataSetChanged();
            }
        }

        /*************/
        /** private **/
        /*************/

        void register(StackView listener) {
            this.listener = listener;
        }
    }

    void notifyDataSetChangedOnMainThread() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                notifyDataSetChanged();
            }
        });
    }

    @SuppressWarnings("unchecked")
    void notifyDataSetChanged() {
        switch (adapter.getItemCount()) {
            case 0:
                if (frontContent != null) {
                    frontContent.setVisibility(GONE);
                    frontContent.requestLayout();
                }
                if (backContent.getDrawable() != null) {
                    back.setVisibility(GONE);
                    back.requestLayout();
                }
                View view = adapter.createAndBindEmptyView(empty);
                if (view != null) {
                    empty.setVisibility(VISIBLE);
                }
                break;
            default:
                if (backContent.getDrawable() != null) {
                    fillBack();
                    back.setVisibility(VISIBLE);
                    back.requestLayout();
                } else {
                    fillBack();
                }
            case 1:
                empty.setVisibility(GONE);
                if (frontContent != null) {
                    adapter.onBindView(frontContent, Adapter.Position.FIRST);
                    frontContent.setVisibility(VISIBLE);
                    frontContent.requestLayout();
                } else {
                    fillFront();
                }
                break;
        }
    }

    /*************/
    /** private **/
    /*************/

    private void fillBack() {
        final View view = adapter.createAndBindView(tmp, Adapter.Position.SECOND);
        addOnGlobalLayoutListener(view, new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                removeOnGlobalLayoutListener(view, this);

                if (view.getWidth() != 0 && view.getHeight() != 0) {
                    backContent.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, view.getHeight()));
                    Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(bitmap);
                    view.draw(canvas);

                    RoundedBitmapDrawable dr = RoundedBitmapDrawableFactory.create(getResources(), bitmap);
                    dr.setCornerRadius(radius);

                    backContent.setImageDrawable(dr);
                    back.requestLayout();
                }
            }
        });
    }

    private void fillFront() {
        frontContent = adapter.createAndBindView(front, Adapter.Position.FIRST);
        ((MarginLayoutParams) frontContent.getLayoutParams()).setMargins(margin, margin, margin, margin);
        requestLayout();

        frontContent.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(final View view, MotionEvent event) {
                switch (event.getAction() & MotionEvent.ACTION_MASK) {
                    case MotionEvent.ACTION_DOWN:
                        dX = frontContent.getX() - event.getRawX();
                        dY = frontContent.getY() - event.getRawY();

                        if (backContent.getDrawable() != null) {
                            ValueAnimator animator = ValueAnimator.ofInt(padding, 0).setDuration(500);
                            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                                @Override
                                public void onAnimationUpdate(ValueAnimator animation) {
                                    int value = (int) animation.getAnimatedValue();

                                    back.setPadding(value, value, value, value);
                                    back.requestLayout();
                                }
                            });
                            animator.start();
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        float delta = frontContent.getX() - initX;
                        if (delta > swipe) {
                            remove(initX + frontContent.getWidth(), event.getRawY() + dY);
                        } else if (delta < -swipe) {
                            remove(initX - frontContent.getWidth(), event.getRawY() + dY);
                        } else {
                            frontContent.animate()
                                    .x(initPadding[0] + initX + margin)
                                    .y(initPadding[1] + initY + margin)
                                    .setDuration(animDuration / 2)
                                    .start();
                            if (backContent.getDrawable() != null) {
                                ValueAnimator animator = ValueAnimator.ofInt(0, padding).setDuration(animDuration);
                                animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                                    @Override
                                    public void onAnimationUpdate(ValueAnimator animation) {
                                        int value = (int) animation.getAnimatedValue();
                                        back.setPadding(value, value, value, value);
                                        back.requestLayout();
                                    }
                                });
                                animator.start();
                            }
                        }
                        break;
                    case MotionEvent.ACTION_MOVE:
                        frontContent.animate()
                                .x(event.getRawX() + dX)
                                .y(event.getRawY() + dY)
                                .setDuration(0)
                                .start();
                        break;
                }
                front.invalidate();
                return true;
            }
        });
    }

    private void remove(float x, float y) {
        ViewPropertyAnimator animator = frontContent.animate().x(x).y(y).setDuration(animDuration);
        animator.setListener(new StackView.AnimatorListenerHelper() {
            private boolean done = false;

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!done) {
                    done = true;

                    adapter.remove();
                    back.setVisibility(GONE);
                    switch (adapter.getItemCount()) {
                        case 0: {
                            frontContent.setVisibility(GONE);
                            frontContent.setX(initPadding[0] + initX);
                            frontContent.setY(initPadding[1] + initY);

                            View view = adapter.createAndBindEmptyView(empty);
                            if (view != null) {
                                empty.setVisibility(VISIBLE);
                            }
                            break;
                        }
                        default: {
                            back.setVisibility(VISIBLE);
                            fillBack();
                            back.setPadding(padding, padding, padding, padding);
                        }
                        case 1: {
                            fillFront();
                            frontContent.setX(initPadding[0] + initX);
                            frontContent.setY(initPadding[1] + initY);
                            break;
                        }
                    }
                }
            }
        });
        animator.start();
    }

    private static void addOnGlobalLayoutListener(View view, ViewTreeObserver.OnGlobalLayoutListener listener) {
        view.getViewTreeObserver().addOnGlobalLayoutListener(listener);
    }

    @SuppressWarnings("deprecation")
    private static void removeOnGlobalLayoutListener(View view, ViewTreeObserver.OnGlobalLayoutListener listener) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            view.getViewTreeObserver().removeOnGlobalLayoutListener(listener);
        } else {
            view.getViewTreeObserver().removeGlobalOnLayoutListener(listener);
        }
    }

    private static abstract class AnimatorListenerHelper implements Animator.AnimatorListener {
        @Override
        public void onAnimationStart(Animator animation) {}

        @Override
        public void onAnimationCancel(Animator animation) {}

        @Override
        public void onAnimationRepeat(Animator animation) {}
    }
}
