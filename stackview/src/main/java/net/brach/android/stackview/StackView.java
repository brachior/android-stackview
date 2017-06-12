package net.brach.android.stackview;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class StackView extends FrameLayout {
    private static final int SHADOW_CARD_COLOR = Color.parseColor("#90b1b1b1");
    private static final int DEFAULT_ACTION_COLOR = Color.BLACK;

    private final int padding;
    private final int margin;
    private final int radius;
    private final int animDuration;
    private final int swipe;
    private final float border;
    private final boolean actionEnable;
    private final int[] initPadding;

    private final LinearLayout front;
    private final FrameLayout frontContainer;
    private View frontContent;
    private final RelativeLayout frontAction;

    private final LinearLayout back;
    private final ImageView backContent;

    private final LinearLayout empty;
    private final LinearLayout tmp;

    private float dX;
    private float dY;
    private float initX = -1;
    private float initY = -1;
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

        actionEnable = a.getBoolean(R.styleable.StackView_action_enable, true);
        int actionColor = a.getColor(R.styleable.StackView_action_color, DEFAULT_ACTION_COLOR);
        actionColor = Color.argb(127, Color.red(actionColor), Color.green(actionColor), Color.blue(actionColor));
        String actionText = a.getString(R.styleable.StackView_action_text);
        int actionAppearance = a.getResourceId(R.styleable.StackView_action_textAppearance, R.style.DefaultActionTextAppearance);

        animDuration = a.getInteger(R.styleable.StackView_animation_duration, 200);
        int layout = a.getResourceId(R.styleable.StackView_preview_layout, -1);
        a.recycle();

        initPadding = new int[] {getPaddingLeft(), getPaddingTop(), getPaddingRight(), getPaddingBottom()};
        border = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, displayMetrics);

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

        frontAction = new RelativeLayout(context);
        frontAction.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        frontAction.setGravity(Gravity.CENTER);
        makeAndSetOverlay(frontAction, actionColor, radius);

        TextView frontActionText = new TextView(context);
        frontActionText.setLayoutParams(new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        frontActionText.setText(actionText);
        setTextAppearance(context, frontActionText, actionAppearance);
        frontAction.addView(frontActionText);

        frontContainer = new FrameLayout(context);
        frontContainer.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ((MarginLayoutParams) frontContainer.getLayoutParams()).setMargins(margin, margin, margin, margin);

        front = new LinearLayout(context);
        front.setOrientation(LinearLayout.HORIZONTAL);
        LayoutParams frontParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        front.setLayoutParams(frontParams);
        front.addView(frontContainer);
        addView(front);

        if (isInEditMode() && layout != -1) {
            View inflate = inflate(getContext(), layout, front);
            ((MarginLayoutParams) inflate.getLayoutParams()).setMargins(margin, margin, margin, margin);
        }
    }

    public void setAdapter(final Adapter adapter) {
        this.adapter = adapter;
        this.adapter.register(this);

        switch (adapter.getItemCount()) {
            case 0:
                View view = adapter.createAndBindEmptyView(empty);
                if (view != null) {
                    empty.addView(view, -1);
                    empty.setVisibility(VISIBLE);
                }
                break;
            default:
                fillBack();
            case 1:
                empty.setVisibility(GONE);
                fillFront();

                if (initX == -1) {
                    initX = frontContainer.getX();
                    initY = frontContainer.getY();


                }
                break;
        }
        requestLayout();
    }

    public enum Direction {
        LEFT, RIGHT
    }

    public static abstract class Adapter {
        private StackView stackView;

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
         * Remove first element and animate the view.
         *
         * Call the method remove.
         *
         * @param direction animation direction
         */
        public void remove(Direction direction) {
            stackView.remove(direction);
        }

        /**
         * Notify any registered observers that the data set has changed.
         */
        public void notifyDataSetChangedOnMainThread() {
            if (stackView != null) {
                stackView.notifyDataSetChangedOnMainThread();
            }
        }

        /**
         * Notify any registered observers that the data set has changed.
         */
        public void notifyDataSetChanged() {
            if (stackView != null) {
                stackView.notifyDataSetChanged();
            }
        }

        /*************/
        /** private **/
        /*************/

        void register(StackView listener) {
            this.stackView = listener;
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
                    frontContainer.setVisibility(GONE);
                    frontContainer.requestLayout();
                }
                if (backContent.getDrawable() != null) {
                    back.setVisibility(GONE);
                    back.requestLayout();
                }
                View view = adapter.createAndBindEmptyView(empty);
                if (view != null) {
                    empty.addView(view, -1);
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
                    frontContainer.setVisibility(VISIBLE);
                    frontContainer.requestLayout();
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
        // remove compat padding if CardView (it could be the view or its first child)
        // and get card elevation
        final int elevation = treatCardViewIfNecessary(view);
        int backMargin = margin + elevation * 2;
        ((MarginLayoutParams) back.getLayoutParams()).setMargins(backMargin, backMargin, backMargin, backMargin);
        back.setPadding(padding, padding - elevation, padding, padding);
        addOnGlobalLayoutListener(view, new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                removeOnGlobalLayoutListener(view, this);

                if (view.getWidth() != 0 && view.getHeight() != 0) {
                    backContent.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, view.getHeight() - elevation * 2));
                    Bitmap bitmap = Bitmap.createBitmap(view.getWidth() - elevation * 2, view.getHeight() - elevation * 2, Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(bitmap);
                    view.draw(canvas);

                    if (elevation > 0) {
                        // draw border
                        final Paint paint = new Paint();
                        final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
                        final RectF rectF = new RectF(rect);
                        paint.setColor(SHADOW_CARD_COLOR);
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(border);
                        canvas.drawRoundRect(rectF, radius, radius, paint);
                    }

                    RoundedBitmapDrawable dr = RoundedBitmapDrawableFactory.create(getResources(), bitmap);
                    dr.setCornerRadius(radius);

                    backContent.setImageDrawable(dr);
                    back.requestLayout();
                }
            }
        });
    }

    private void fillFront() {
        frontContent = adapter.createAndBindView(frontContainer, Adapter.Position.FIRST);
        ((MarginLayoutParams) frontContainer.getLayoutParams()).setMargins(margin, margin, margin, margin);
        requestLayout();

        frontContainer.setOnTouchListener(new OnTouchListener() {
            private float tmpX = initX;
            private float tmpY = initY;

            private float lastX = initX;
            private float lastY = initY;

            @Override
            public boolean onTouch(final View view, MotionEvent event) {
                switch (event.getAction() & MotionEvent.ACTION_MASK) {
                    case MotionEvent.ACTION_DOWN: {
                        dX = frontContainer.getX() - event.getRawX();
                        dY = frontContainer.getY() - event.getRawY();

                        if (backContent.getDrawable() != null) {
                            ValueAnimator animator = ValueAnimator.ofInt(padding, 0).setDuration(animDuration);
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
                    }
                    case MotionEvent.ACTION_UP: {
                        float delta = frontContainer.getX() - initX;
                        float m = (frontContainer.getY() - lastY) / (frontContainer.getX() - lastX);
                        float p = lastY - m * lastX;

                        if (delta > swipe) {
                            float nx = initX + (frontContainer.getWidth() + 300);
                            remove(nx, m * nx + p, animDuration);
                        } else if (delta < -swipe) {
                            float nx = initX - (frontContainer.getWidth() + 300);
                            remove(nx, m * nx + p, animDuration);
                        } else {
                            frontContainer.animate()
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
                    }
                    case MotionEvent.ACTION_MOVE: {
                        frontContainer.animate()
                                .x(event.getRawX() + dX)
                                .y(event.getRawY() + dY)
                                .setDuration(0)
                                .start();

                        lastX = tmpX;
                        lastY = tmpY;
                        tmpX = event.getRawX() + dX;
                        tmpY = event.getRawY() + dY;

                        if (actionEnable) {
                            float delta = frontContainer.getX() - initX;
                            if (delta > swipe || delta < -swipe) {
                                frontContainer.removeView(frontAction);
                                frontContainer.addView(frontAction);

                                int elevation = getCardViewElevation(frontContent);
                                ((MarginLayoutParams) frontAction.getLayoutParams()).setMargins(elevation, elevation * 2, elevation, elevation * 2);
                            } else {
                                frontContainer.removeView(frontAction);
                            }
                        }
                        break;
                    }
                }
                front.invalidate();
                return true;
            }
        });
    }

    private void remove(final Direction direction) {
        final ValueAnimator animator = ValueAnimator.ofInt(padding, 0).setDuration(animDuration);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                int value = (int) animation.getAnimatedValue();

                back.setPadding(value, value, value, value);
                back.requestLayout();
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                switch (direction) {
                    case LEFT:
                        remove((int) (initX - frontContainer.getWidth()), initY, animDuration);
                        break;
                    case RIGHT:
                        remove((int) (initX + frontContainer.getWidth()), initY, animDuration);
                        break;
                }
            }
        });
        animator.start();
    }

    private void remove(float x, float y, int duration) {
        if (actionEnable) {
            frontContainer.removeView(frontAction);
        }

        ViewPropertyAnimator animator = frontContainer.animate().x(x).y(y).setDuration(duration);
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
                            frontContainer.setVisibility(GONE);
                            frontContainer.setX(initPadding[0] + initX + margin);
                            frontContainer.setY(initPadding[1] + initY + margin);

                            View view = adapter.createAndBindEmptyView(empty);
                            if (view != null) {
                                empty.addView(view, -1);
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
                            frontContainer.setX(initPadding[0] + initX + margin);
                            frontContainer.setY(initPadding[1] + initY + margin);
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

    @SuppressWarnings("deprecation")
    private void setTextAppearance(Context context, TextView view, int resource) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            view.setTextAppearance(resource);
        } else {
            view.setTextAppearance(context, resource);
        }
    }

    @SuppressWarnings("deprecation")
    private void makeAndSetOverlay(View view, int color, int radius) {
        ShapeDrawable actionDrawable = new ShapeDrawable(new RoundRectShape(
                new float[] { radius, radius, radius, radius, radius, radius, radius, radius }, null, null));
        actionDrawable.getPaint().setColor(color);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            view.setBackground(actionDrawable);
        } else {
            view.setBackgroundDrawable(actionDrawable);
        }
    }

    private int treatCardViewIfNecessary(View view) {
        if (view instanceof ViewGroup) {
            if (!removeCompatPadding(view)) {
                View child = ((ViewGroup) view).getChildAt(0);
                if (removeCompatPadding(child)) {
                    return (int) getElevation(child);
                }
            } else {
                return (int) getElevation(view);
            }
        }
        return 0;
    }

    private int getCardViewElevation(View view) {
        if (view instanceof ViewGroup) {
            int elevation = (int) getElevation(view);
            if (elevation == 0) {
                View child = ((ViewGroup) view).getChildAt(0);
                return (int) getElevation(child);
            } else {
                return elevation;
            }
        }
        return 0;
    }

    private static boolean removeCompatPadding(View view) {
        try {
            Method padding = view.getClass().getDeclaredMethod("setUseCompatPadding", boolean.class);
            padding.setAccessible(true);
            try {
                padding.invoke(view, false);
                return true;
            } catch (InvocationTargetException ignored) {
            } catch (IllegalAccessException ignored) {
            } finally {
                padding.setAccessible(false);
            }
        } catch (NoSuchMethodException ignored) {
        }
        return false;
    }

    private static float getElevation(View view) {
        try {
            Method elevation = view.getClass().getDeclaredMethod("getCardElevation");
            elevation.setAccessible(true);
            try {
                return (float) elevation.invoke(view);
            } catch (InvocationTargetException ignored) {
            } catch (IllegalAccessException ignored) {
            } finally {
                elevation.setAccessible(false);
            }
        } catch (NoSuchMethodException ignored) {
        }
        return .0f;
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
