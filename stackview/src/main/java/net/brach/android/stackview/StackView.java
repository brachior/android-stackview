package net.brach.android.stackview;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.widget.CardView;
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
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.concurrent.atomic.AtomicBoolean;

public class StackView extends FrameLayout {
    private static final int DEFAULT_ACTION_COLOR = Color.BLACK;

    private final int[] padding;
    private final int[] margin;
    private final float elevation;
    private final int animDuration;
    private final int swipe;
    private final boolean actionEnable;
    private final int[] initPadding;

    private FrameLayout front;
    private CardView frontContainer;
    private View frontContent;
    private RelativeLayout frontAction;

    private FrameLayout back;
    private CardView backContainer;
    private ImageView backContent;
    private boolean backInit;

    private FrameLayout empty;
    private CardView tmp;

    private float initX = -1;
    private float initY = -1;
    private Adapter adapter;

    private ValueAnimator addActionAnim;
    private ValueAnimator removeActionAnim;
    private RemoveDirectionAnimator removeDirectionAnim;

    private FrontContainerOnTouchListener frontContainerOnTouchListener;
    private BackContentOnGlobalLayoutListener backContentOnGlobalLayoutListener;
    private AnimatorListenerHelper removeAnimatorListener;

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
        if (a.hasValue(R.styleable.StackView_margin)) {
            int marg = (int) a.getDimension(R.styleable.StackView_margin, 0);
            margin = new int[] {marg, marg, marg, marg};
        } else {
            margin = new int[] {
                    (int) a.getDimension(R.styleable.StackView_marginLeft, 0),
                    (int) a.getDimension(R.styleable.StackView_marginTop, 0),
                    (int) a.getDimension(R.styleable.StackView_marginRight, 0),
                    (int) a.getDimension(R.styleable.StackView_marginBottom, 0)
            };
        }
        if (a.hasValue(R.styleable.StackView_padding)) {
            int pad = (int) a.getDimension(R.styleable.StackView_padding, 0);
            padding = new int[] {pad, pad, pad, pad};
        } else {
            padding = new int[] {
                    (int) a.getDimension(R.styleable.StackView_paddingLeft, 0),
                    (int) a.getDimension(R.styleable.StackView_paddingTop, 0),
                    (int) a.getDimension(R.styleable.StackView_paddingRight, 0),
                    (int) a.getDimension(R.styleable.StackView_paddingBottom, 0)
            };
        }

        // card information
        elevation = a.getDimension(R.styleable.StackView_card_elevation, 0);
        int color = a.getColor(R.styleable.StackView_card_backgroundColor, Color.WHITE);
        float radius = a.getDimension(R.styleable.StackView_card_cornerRadius, 0);
        boolean compatPadding = a.getBoolean(R.styleable.StackView_card_useCompatPadding, false);

        actionEnable = a.getBoolean(R.styleable.StackView_action_enable, true);
        int actionColor = a.getColor(R.styleable.StackView_action_color, DEFAULT_ACTION_COLOR);
        actionColor = Color.argb(127, Color.red(actionColor), Color.green(actionColor), Color.blue(actionColor));
        String actionText = a.getString(R.styleable.StackView_action_text);
        int actionAppearance = a.getResourceId(R.styleable.StackView_action_textAppearance, R.style.DefaultActionTextAppearance);

        animDuration = a.getInteger(R.styleable.StackView_animation_duration, 200);
        int actionAnimDuration = a.getInteger(R.styleable.StackView_action_animation_duration, 150);
        int layout = a.getResourceId(R.styleable.StackView_preview_layout, -1);
        a.recycle();

        initPadding = new int[] {getPaddingLeft(), getPaddingTop(), getPaddingRight(), getPaddingBottom()};

        // view configurations
        initViews(context, color, radius, compatPadding, actionColor, actionText, actionAppearance);

        // animation listeners
        initAnimationListeners(actionAnimDuration);

        if (isInEditMode() && layout != -1) {
            View inflate = inflate(getContext(), layout, frontContainer);
            ((MarginLayoutParams) inflate.getLayoutParams()).setMargins(margin[0], margin[1], margin[2], margin[3]);
        }
    }

    private void initViews(Context context, int color, float radius, boolean compatPadding, int actionColor, String actionText, int actionAppearance) {
        // tmp
        tmp = initCardView(context, color, elevation, radius, compatPadding);
        tmp.setVisibility(INVISIBLE);
        ((MarginLayoutParams) tmp.getLayoutParams()).setMargins(margin[0], margin[1], margin[2], margin[3]);
        addView(tmp);

        // empty
        empty = new FrameLayout(context);
        empty.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        empty.setVisibility(GONE);
        addView(empty);

        // back
        back = new FrameLayout(context);
        back.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        addView(back);

        backContainer = initCardView(context, color, 0, radius, compatPadding);
        back.addView(backContainer);

        backContent = new ImageView(context);
        backContent.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        backContent.setScaleType(ImageView.ScaleType.FIT_XY);
        backContent.setAdjustViewBounds(true);
        backContainer.addView(backContent);

        // front
        front = new FrameLayout(context);
        front.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addView(front);

        frontContainer = initCardView(context, color, elevation, radius, compatPadding);
        ((MarginLayoutParams) frontContainer.getLayoutParams()).setMargins(margin[0], margin[1], margin[2], margin[3]);
        front.addView(frontContainer);

        frontAction = new RelativeLayout(context);
        frontAction.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        frontAction.setGravity(Gravity.CENTER);
        frontAction.setBackgroundColor(actionColor);

        TextView frontActionText = new TextView(context);
        frontActionText.setLayoutParams(new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        frontActionText.setText(actionText);
        setTextAppearance(context, frontActionText, actionAppearance);
        frontAction.addView(frontActionText);

        requestLayout();
    }

    private void initAnimationListeners(int actionAnimDuration) {
        addActionAnim = ValueAnimator.ofFloat(0.f, 1.f);
        addActionAnim.setDuration(actionAnimDuration);
        addActionAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float value = (float) animation.getAnimatedValue();
                frontAction.setAlpha(value);
                frontAction.requestLayout();
            }
        });
        addActionAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                onOpen.set(false);
            }
        });

        removeActionAnim = ValueAnimator.ofFloat(1.f, 0.f);
        removeActionAnim.setDuration(actionAnimDuration);
        removeActionAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float value = (float) animation.getAnimatedValue();
                frontAction.setAlpha(value);
                frontAction.requestLayout();
            }
        });
        removeActionAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                frontContainer.removeView(frontAction);
                onClose.set(false);
            }
        });

        removeDirectionAnim = new RemoveDirectionAnimator(padding, animDuration);
        removeDirectionAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                back.setPadding(
                        (int) animation.getAnimatedValue("left"),
                        (int) animation.getAnimatedValue("top"),
                        (int) animation.getAnimatedValue("right"),
                        (int) animation.getAnimatedValue("bottom"));
                back.requestLayout();
            }
        });
        removeDirectionAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                switch (removeDirectionAnim.direction) {
                    case LEFT:
                        remove((int) (initX - frontContainer.getWidth()), initY, animDuration);
                        break;
                    case RIGHT:
                        remove((int) (initX + frontContainer.getWidth()), initY, animDuration);
                        break;
                }
            }
        });

        frontContainerOnTouchListener = new FrontContainerOnTouchListener(this);

        backContentOnGlobalLayoutListener = new BackContentOnGlobalLayoutListener(back, backContent);

        removeAnimatorListener = new AnimatorListenerHelper(this, frontContainer, back, empty, padding, margin, initPadding);
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
                    frontContainerOnTouchListener.initPosition();
                    removeAnimatorListener.initPosition();
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

        public View getEmptyView() {
            return stackView != null ? stackView.empty.getChildAt(0) : null;
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

        /********************/
        /** in main thread **/
        /********************/

        public void notifyDataSetChangedOnMainThread() {
            if (stackView != null) {
                stackView.notify(0, true);
            }
        }

        public void notifyItemInsertedOnMainThread(int position) {
            if (stackView != null) {
                stackView.notify(position, true);
            }
        }

        public void notifyItemRemovedOnMainThread(int position) {
            if (stackView != null) {
                stackView.notify(position, true);
            }
        }

        public void notifyItemChangedOnMainThread(int position) {
            if (stackView != null) {
                stackView.notify(position, true);
            }
        }

        public void notifyItemRangeInsertedOnMainThread(int positionStart, int itemCount) {
            if (stackView != null) {
                stackView.notifyRange(positionStart, itemCount, true);
            }
        }

        public void notifyItemRangeRemovedOnMainThread(int positionStart, int itemCount) {
            if (stackView != null) {
                stackView.notifyRange(positionStart, itemCount, true);
            }
        }

        public void notifyItemRangeChangedOnMainThread(int positionStart, int itemCount) {
            if (stackView != null) {
                stackView.notifyRange(positionStart, itemCount, true);
            }
        }

        /************************/
        /** not in main thread **/
        /************************/

        public void notifyDataSetChanged() {
            if (stackView != null) {
                stackView.notify(0, false);
            }
        }

        public void notifyItemInserted(int position) {
            if (stackView != null) {
                stackView.notify(position, false);
            }
        }

        public void notifyItemRemoved(int position) {
            if (stackView != null) {
                stackView.notify(position, false);
            }
        }

        public void notifyItemChanged(int position) {
            if (stackView != null) {
                stackView.notify(position, false);
            }
        }

        public void notifyItemRangeInserted(int positionStart, int itemCount, boolean inMainThread) {
            if (stackView != null) {
                stackView.notifyRange(positionStart, itemCount, false);
            }
        }

        public void notifyItemRangeRemoved(int positionStart, int itemCount, boolean inMainThread) {
            if (stackView != null) {
                stackView.notifyRange(positionStart, itemCount, false);
            }
        }

        public void notifyItemRangeChanged(int positionStart, int itemCount, boolean inMainThread) {
            if (stackView != null) {
                stackView.notifyRange(positionStart, itemCount, false);
            }
        }

        /*************/
        /** private **/
        /*************/

        void register(StackView listener) {
            this.stackView = listener;
        }
    }

    void notify(int position, boolean inMainThread) {
        switch (position) {
            case 0:
            case 1:
                if (inMainThread) {
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            notifyDataSetChanged();
                        }
                    });
                } else {
                    notifyDataSetChanged();
                }
                break;
        }
    }

    void notifyRange(int positionStart, int itemCount, boolean inMainThread) {
        if (itemCount > 0 && positionStart < 2) {
            notify(positionStart, inMainThread);
        }
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
        View view = adapter.createAndBindView(tmp, Adapter.Position.SECOND);

        backContentOnGlobalLayoutListener.init(view);
        addOnGlobalLayoutListener(view, backContentOnGlobalLayoutListener);
    }

    private void fillFront() {
        frontContent = adapter.createAndBindView(frontContainer, Adapter.Position.FIRST);
        ((MarginLayoutParams) frontContainer.getLayoutParams()).setMargins(margin[0], margin[1], margin[2], margin[3]);
        requestLayout();

        if (!backInit) {
            backInit = true;
            addOnGlobalLayoutListener(frontContainer, new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    removeOnGlobalLayoutListener(frontContainer, this);

                    int width = frontContainer.getWidth();
                    int height = frontContainer.getHeight();

                    frontContainer.setLayoutParams(new FrameLayout.LayoutParams(width, height));
                    frontAction.setLayoutParams(new FrameLayout.LayoutParams(width, height));
                    tmp.setLayoutParams(new FrameLayout.LayoutParams(width, height));
                    back.setLayoutParams(new FrameLayout.LayoutParams(width, height));

                    ((MarginLayoutParams) frontContainer.getLayoutParams()).setMargins(margin[0], margin[1], margin[2], margin[3]);
                    ((MarginLayoutParams) back.getLayoutParams()).setMargins(margin[0], margin[1], margin[2], margin[3]);

                    frontContainer.requestLayout();
                    frontAction.requestLayout();
                    tmp.requestLayout();
                    back.requestLayout();
                }
            });
        }

        frontContainerOnTouchListener.init();
        frontContainer.setOnTouchListener(frontContainerOnTouchListener);
    }

    private void remove(final Direction direction) {
        removeDirectionAnim.direction = direction;
        removeDirectionAnim.start();
    }

    private void remove(float x, float y, int duration) {
        if (actionEnable) {
            removeActionView();
        }

        ViewPropertyAnimator animator = frontContainer.animate().x(x).y(y).setDuration(duration);
        removeAnimatorListener.init(adapter);
        animator.setListener(removeAnimatorListener);
        animator.start();
    }

    private CardView initCardView(Context ctx, int color, float elevation, float radius, boolean compatPadding) {
        CardView card = new CardView(ctx);
        card.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        card.setCardElevation(elevation);
        card.setCardBackgroundColor(color);
        card.setRadius(radius);
        card.setUseCompatPadding(compatPadding);

        return card;
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

    private AtomicBoolean onClose = new AtomicBoolean(false);
    private AtomicBoolean onOpen = new AtomicBoolean(false);

    private void removeActionView() {
        if (frontContainer.indexOfChild(frontAction) != -1 && onClose.compareAndSet(false, true)) {
            frontAction.setAlpha(1.f);
            removeActionAnim.start();
        }
    }

    private void addActionView() {
        if (frontContainer.indexOfChild(frontAction) == -1 && onOpen.compareAndSet(false, true)) {
            frontAction.setAlpha(0.f);
            frontContainer.addView(frontAction);

            addActionAnim.start();
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

    /***************/
    /** listeners **/
    /***************/

    private static class BackContentOnGlobalLayoutListener
            implements ViewTreeObserver.OnGlobalLayoutListener {
        private final FrameLayout back;
        private final ImageView backContent;

        private View view;

        private BackContentOnGlobalLayoutListener(FrameLayout back, ImageView backContent) {
            this.back = back;
            this.backContent = backContent;
        }

        void init(View view) {
            this.view = view;
        }

        @Override
        public void onGlobalLayout() {
            removeOnGlobalLayoutListener(view, this);

            if (view.getWidth() != 0 && view.getHeight() != 0) {
                Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                view.draw(canvas);

                backContent.setImageBitmap(bitmap);
                back.requestLayout();
            }
        }
    }

    private static class RemoveDirectionAnimator extends ValueAnimator {
        private final ValueAnimator animator;
        Direction direction;

        RemoveDirectionAnimator(int[] padding, int animDuration) {
            animator = ValueAnimator.ofPropertyValuesHolder(
                    PropertyValuesHolder.ofInt("left", padding[0], 0),
                    PropertyValuesHolder.ofInt("top", padding[1], 0),
                    PropertyValuesHolder.ofInt("right", padding[2], 0),
                    PropertyValuesHolder.ofInt("bottom", padding[3], 0)
            ).setDuration(animDuration);
        }

        public void addListener(AnimatorListener listener) {
            animator.addListener(listener);
        }

        public void addUpdateListener(AnimatorUpdateListener listener) {
            animator.addUpdateListener(listener);
        }

        public void start() {
            animator.start();
        }
    }

    private static class FrontContainerOnTouchListener implements OnTouchListener {
        private final StackView self;
        private final FrameLayout front;
        private final CardView frontContainer;
        private final FrameLayout back;
        private final CardView backContainer;
        private final ImageView backContent;

        private final int[] padding;
        private final int[] margin;
        private final float elevation;
        private final int animDuration;
        private final int swipe;
        private final boolean actionEnable;
        private final int[] initPadding;

        private float dX, dY;
        private float tmpX, tmpY;
        private float lastX, lastY;
        private float initX, initY;

        FrontContainerOnTouchListener(StackView self) {
            this.self = self;
            this.front = self.front;
            this.frontContainer = self.frontContainer;
            this.back = self.back;
            this.backContainer = self.backContainer;
            this.backContent = self.backContent;

            this.padding = self.padding;
            this.elevation = self.elevation;
            this.margin = self.margin;
            this.animDuration = self.animDuration;
            this.swipe = self.swipe;
            this.actionEnable = self.actionEnable;
            this.initPadding = self.initPadding;
        }

        void initPosition() {
            this.initX = self.initX;
            this.initY = self.initY;
        }

        void init() {
            tmpX = initX;
            tmpY = initY;

            lastX = initX;
            lastY = initY;
        }

        @Override
        public boolean onTouch(final View view, MotionEvent event) {
            switch (event.getAction() & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_DOWN: {
                    dX = frontContainer.getX() - event.getRawX();
                    dY = frontContainer.getY() - event.getRawY();

                    if (backContent.getDrawable() != null) {
                        ValueAnimator animator = ValueAnimator.ofPropertyValuesHolder(
                                PropertyValuesHolder.ofInt("left", padding[0], 0),
                                PropertyValuesHolder.ofInt("top", padding[1], 0),
                                PropertyValuesHolder.ofInt("right", padding[2], 0),
                                PropertyValuesHolder.ofInt("bottom", padding[3], 0),
                                PropertyValuesHolder.ofFloat("elevation", 0, elevation)
                        ).setDuration(animDuration);
                        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                            @Override
                            public void onAnimationUpdate(ValueAnimator animation) {
                                back.setPadding(
                                        (int) animation.getAnimatedValue("left"),
                                        (int) animation.getAnimatedValue("top"),
                                        (int) animation.getAnimatedValue("right"),
                                        (int) animation.getAnimatedValue("bottom"));
                                backContainer.setCardElevation((float) animation.getAnimatedValue("elevation"));
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
                        self.remove(nx, m * nx + p, animDuration);
                    } else if (delta < -swipe) {
                        float nx = initX - (frontContainer.getWidth() + 300);
                        self.remove(nx, m * nx + p, animDuration);
                    } else {
                        frontContainer.animate()
                                .x(initPadding[0] + initX + margin[0])
                                .y(initPadding[1] + initY + margin[1])
                                .setDuration(animDuration / 2)
                                .start();
                        if (backContent.getDrawable() != null) {
                            ValueAnimator animator = ValueAnimator.ofPropertyValuesHolder(
                                    PropertyValuesHolder.ofInt("left", 0, padding[0]),
                                    PropertyValuesHolder.ofInt("top", 0, padding[1]),
                                    PropertyValuesHolder.ofInt("right", 0, padding[2]),
                                    PropertyValuesHolder.ofInt("bottom", 0, padding[3]),
                                    PropertyValuesHolder.ofFloat("elevation", elevation, 0)
                            ).setDuration(animDuration);
                            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                                @Override
                                public void onAnimationUpdate(ValueAnimator animation) {
                                    back.setPadding(
                                            (int) animation.getAnimatedValue("left"),
                                            (int) animation.getAnimatedValue("top"),
                                            (int) animation.getAnimatedValue("right"),
                                            (int) animation.getAnimatedValue("bottom"));
                                    backContainer.setCardElevation((float) animation.getAnimatedValue("elevation"));
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
                            self.addActionView();
                        } else {
                            self.removeActionView();
                        }
                    }
                    break;
                }
            }
            front.invalidate();
            return true;
        }
    }

    private static class AnimatorListenerHelper implements Animator.AnimatorListener {
        private final StackView self;

        private final int[] padding;
        private final int[] margin;
        private final int[] initPadding;

        private final CardView frontContainer;
        private final FrameLayout back;
        private final FrameLayout empty;

        private float initX;
        private float initY;
        private Adapter adapter;

        private boolean done = false;

        private AnimatorListenerHelper(
                StackView stackView, CardView frontContainer,
                FrameLayout back, FrameLayout empty,
                int[] padding, int[] margin, int[] initPadding) {
            this.self = stackView;
            this.frontContainer = frontContainer;
            this.back = back;
            this.empty = empty;
            this.padding = padding;
            this.margin = margin;
            this.initPadding = initPadding;
        }

        void initPosition() {
            this.initX = self.initX;
            this.initY = self.initY;
        }

        void init(Adapter adapter) {
            this.adapter = adapter;
            this.done = false;
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            if (!done) {
                done = true;

                adapter.remove();
                back.setVisibility(GONE);
                switch (adapter.getItemCount()) {
                    case 0: {
                        frontContainer.setVisibility(GONE);
                        frontContainer.setX(initPadding[0] + initX + margin[0]);
                        frontContainer.setY(initPadding[1] + initY + margin[1]);

                        View view = adapter.createAndBindEmptyView(empty);
                        if (view != null) {
                            empty.addView(view, -1);
                            empty.setVisibility(VISIBLE);
                        }
                        break;
                    }
                    default: {
                        back.setVisibility(VISIBLE);
                        self.fillBack();
                        back.setPadding(padding[0], padding[1], padding[2], padding[3]);
                    }
                    case 1: {
                        self.fillFront();
                        frontContainer.setX(initPadding[0] + initX + margin[0]);
                        frontContainer.setY(initPadding[1] + initY + margin[1]);
                        break;
                    }
                }
            }
        }

        @Override
        public void onAnimationStart(Animator animation) {}

        @Override
        public void onAnimationCancel(Animator animation) {}

        @Override
        public void onAnimationRepeat(Animator animation) {}
    }
}
