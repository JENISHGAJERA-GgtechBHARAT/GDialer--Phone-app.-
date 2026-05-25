package com.gg_tech_bharat.gdialer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

public class SwipeToCallMessageCallback extends ItemTouchHelper.SimpleCallback {

    public interface SwipeActionListener {
        void onCallAction(int position);
        void onMessageAction(int position);
    }

    private final Context context;
    private final SwipeActionListener listener;
    private final ColorDrawable bgCall;
    private final ColorDrawable bgMsg;
    private final Drawable iconPhone;
    private final Drawable iconMsg;
    private final Paint paint;

    private RecyclerView.ViewHolder activeViewHolder = null;
    private boolean thresholdReached = false;
    private int activeDirection = 0; 

    public SwipeToCallMessageCallback(Context context, SwipeActionListener listener) {
        super(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
        this.context = context;
        this.listener = listener;

        // Monochromatic colors for swipes
        this.bgCall = new ColorDrawable(context.getResources().getColor(R.color.white));
        this.bgMsg = new ColorDrawable(context.getResources().getColor(R.color.gray_medium));

        this.iconPhone = ContextCompat.getDrawable(context, R.drawable.ic_phone);
        this.iconMsg = ContextCompat.getDrawable(context, R.drawable.ic_message);

        if (iconPhone != null) iconPhone.setColorFilter(context.getResources().getColor(R.color.black), PorterDuff.Mode.SRC_IN);
        if (iconMsg != null) iconMsg.setColorFilter(context.getResources().getColor(R.color.white), PorterDuff.Mode.SRC_IN);

        this.paint = new Paint();
        this.paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
        return false;
    }

    @Override
    public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
        return 2.0f;
    }

    @Override
    public float getSwipeEscapeVelocity(float defaultValue) {
        return defaultValue * 100f;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
    }

    @Override
    public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder,
                            float dX, float dY, int actionState, boolean isCurrentlyActive) {
        
        View itemView = viewHolder.itemView;
        int itemHeight = itemView.getBottom() - itemView.getTop();
        int position = viewHolder.getAdapterPosition();

        if (position == RecyclerView.NO_POSITION) {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            return;
        }

        float triggerPx = 100f * context.getResources().getDisplayMetrics().density;

        if (isCurrentlyActive) {
            if (viewHolder != activeViewHolder) {
                activeViewHolder = viewHolder;
                thresholdReached = false;
                activeDirection = 0;
            }

            if (dX > triggerPx) {
                thresholdReached = true;
                activeDirection = ItemTouchHelper.RIGHT;
            } else if (dX < -triggerPx) {
                thresholdReached = true;
                activeDirection = ItemTouchHelper.LEFT;
            } else {
                thresholdReached = false;
            }
        } else {
            if (viewHolder == activeViewHolder && thresholdReached) {
                thresholdReached = false;
                activeViewHolder = null;
                
                final int finalPos = position;
                final int finalDir = activeDirection;
                itemView.post(() -> {
                    if (finalDir == ItemTouchHelper.RIGHT) {
                        listener.onCallAction(finalPos);
                    } else if (finalDir == ItemTouchHelper.LEFT) {
                        listener.onMessageAction(finalPos);
                    }
                });
            }
        }

        if (dX > 0) { 
            bgCall.setBounds(itemView.getLeft(), itemView.getTop(), itemView.getLeft() + (int) dX, itemView.getBottom());
            bgCall.draw(c);

            if (iconPhone != null) {
                int iconMargin = (itemHeight - iconPhone.getIntrinsicHeight()) / 2;
                int iconTop = itemView.getTop() + iconMargin;
                int iconLeft = itemView.getLeft() + iconMargin;
                int iconRight = itemView.getLeft() + iconMargin + iconPhone.getIntrinsicWidth();
                int iconBottom = iconTop + iconPhone.getIntrinsicHeight();
                iconPhone.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                iconPhone.draw(c);
            }
        } else if (dX < 0) {
            bgMsg.setBounds(itemView.getRight() + (int) dX, itemView.getTop(), itemView.getRight(), itemView.getBottom());
            bgMsg.draw(c);

            if (iconMsg != null) {
                int iconMargin = (itemHeight - iconMsg.getIntrinsicHeight()) / 2;
                int iconTop = itemView.getTop() + iconMargin;
                int iconRight = itemView.getRight() - iconMargin;
                int iconLeft = itemView.getRight() - iconMargin - iconMsg.getIntrinsicWidth();
                int iconBottom = iconTop + iconMsg.getIntrinsicHeight();
                iconMsg.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                iconMsg.draw(c);
            }
        }

        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
    }
}
