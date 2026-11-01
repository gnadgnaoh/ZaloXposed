package io.github.electroheavenvn.zaloxposed.hooks.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import io.github.electroheavenvn.zaloxposed.utilities.CallRecordingStore;
import io.github.electroheavenvn.zaloxposed.utilities.Logger;
import io.github.electroheavenvn.zaloxposed.utilities.Utils;

/**
 * In-app manager for saved call recordings: list, play, delete and export.
 *
 * <p>The dialog is hosted by Zalo's current activity and uses the host theme
 * colors, so it follows light/dark mode. Export uses the system document
 * picker, so no storage permission is required.</p>
 */
public final class CallRecordingsDialog
{
    public static final int REQUEST_CODE_EXPORT = 3838;

    private static File pendingExport;

    private final Activity activity;
    private final LinearLayout listContainer;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final int colorPrimary;
    private final int colorSecondary;
    private final int colorAccent;
    private final int colorBackground;
    private final int colorDivider;
    private final int colorCard;

    private Dialog dialog;
    private TextView countBadge;
    private MediaPlayer player;
    private File playingFile;
    private RowHolder playingRow;

    private final Runnable progressTick = new Runnable()
    {
        @Override
        public void run()
        {
            if (player == null || playingRow == null)
                return;
            try
            {
                int duration = player.getDuration();
                if (duration > 0)
                {
                    playingRow.progress.setMax(duration);
                    playingRow.progress.setProgress(player.getCurrentPosition());
                }
                handler.postDelayed(this, 400);
            }
            catch (Throwable ignored) { }
        }
    };

    private CallRecordingsDialog(Activity activity)
    {
        this.activity = activity;
        this.listContainer = new LinearLayout(activity);
        this.listContainer.setOrientation(LinearLayout.VERTICAL);
        this.colorPrimary = resolveColor(android.R.attr.textColorPrimary, 0xFF111111);
        this.colorSecondary = resolveColor(android.R.attr.textColorSecondary, 0xFF757575);
        this.colorAccent = resolveColor(android.R.attr.colorAccent, 0xFF0068FF);
        this.colorBackground = resolveColor(android.R.attr.colorBackgroundFloating,
            resolveColor(android.R.attr.colorBackground, 0xFFFFFFFF));
        this.colorDivider = withAlpha(colorSecondary, 0.16f);
        this.colorCard = withAlpha(colorPrimary, 0.05f);
    }

    public static void show(Context context)
    {
        Activity activity = findActivity(context);
        if (activity == null)
        {
            Toast.makeText(context,
                Utils.IsEnglish() ? "Cannot open recordings here" : "Không thể mở bản ghi âm ở đây",
                Toast.LENGTH_SHORT).show();
            return;
        }
        new CallRecordingsDialog(activity).showDialog();
    }

    /** Handles the system document picker result from the settings activity hook. */
    public static boolean handleActivityResult(Activity activity, int resultCode, Intent data)
    {
        File file = pendingExport;
        pendingExport = null;
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null || file == null)
            return true;
        Uri uri = data.getData();
        try (InputStream input = new FileInputStream(file);
             OutputStream output = activity.getContentResolver().openOutputStream(uri))
        {
            if (output == null)
                throw new IOException("Cannot open export destination");
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) > 0)
                output.write(buffer, 0, read);
            output.flush();
            Toast.makeText(activity,
                Utils.IsEnglish() ? "Recording exported" : "Đã xuất bản ghi âm",
                Toast.LENGTH_SHORT).show();
        }
        catch (Throwable t)
        {
            Logger.e(t);
            Toast.makeText(activity,
                Utils.IsEnglish() ? "Export failed" : "Xuất bản ghi âm thất bại",
                Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    private void showDialog()
    {
        boolean english = Utils.IsEnglish();

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(rounded(colorBackground, dp(24), withAlpha(colorPrimary, 0.08f)));
        root.setClipToOutline(true);

        root.addView(buildHeader(english));
        root.addView(buildDivider());

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setPadding(dp(12), dp(8), dp(12), dp(8));
        scroll.addView(listContainer, new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(buildDivider());
        root.addView(buildFooter(english));

        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(root);
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnDismissListener(d -> stopPlayback());
        dialog.show();

        Window window = dialog.getWindow();
        if (window != null)
        {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams params = window.getAttributes();
            params.dimAmount = 0.55f;
            params.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
            window.setAttributes(params);
            int width = (int)(activity.getResources().getDisplayMetrics().widthPixels * 0.92f);
            int height = (int)(activity.getResources().getDisplayMetrics().heightPixels * 0.72f);
            window.setLayout(width, height);
        }
        refresh();
    }

    private View buildHeader(boolean english)
    {
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(20), dp(18), dp(12), dp(14));

        LinearLayout titles = new LinearLayout(activity);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setLayoutParams(new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(activity);
        title.setText(english ? "Call recordings" : "Bản ghi âm cuộc gọi");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setTextColor(colorPrimary);
        titles.addView(title);

        TextView subtitle = new TextView(activity);
        subtitle.setText(english
            ? "Stored privately in Zalo. No storage permission."
            : "Lưu riêng trong Zalo. Không cần quyền bộ nhớ.");
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        subtitle.setTextColor(colorSecondary);
        subtitle.setPadding(0, dp(2), 0, 0);
        titles.addView(subtitle);
        header.addView(titles);

        countBadge = new TextView(activity);
        countBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        countBadge.setTypeface(Typeface.DEFAULT_BOLD);
        countBadge.setTextColor(Color.WHITE);
        countBadge.setGravity(Gravity.CENTER);
        countBadge.setPadding(dp(8), dp(2), dp(8), dp(2));
        countBadge.setBackground(rounded(colorAccent, dp(20), 0));
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeParams.setMarginEnd(dp(2));
        header.addView(countBadge, badgeParams);

        TextView close = new TextView(activity);
        close.setText("✕");
        close.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        close.setTextColor(colorSecondary);
        close.setGravity(Gravity.CENTER);
        close.setBackground(borderlessBackground());
        close.setOnClickListener(v -> dismiss());
        header.addView(close, new LinearLayout.LayoutParams(dp(36), dp(36)));

        return header;
    }

    private View buildFooter(boolean english)
    {
        LinearLayout footer = new LinearLayout(activity);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(dp(20), dp(8), dp(12), dp(12));

        TextView note = new TextView(activity);
        note.setText(english
            ? "Files stay in Zalo's private data"
            : "Tệp lưu trong dữ liệu riêng của Zalo");
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        note.setTextColor(colorSecondary);
        note.setAlpha(0.8f);
        note.setLayoutParams(new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        footer.addView(note);

        TextView refresh = new TextView(activity);
        refresh.setText(english ? "Refresh" : "Làm mới");
        refresh.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        refresh.setTypeface(Typeface.DEFAULT_BOLD);
        refresh.setTextColor(colorAccent);
        refresh.setPadding(dp(12), dp(8), dp(12), dp(8));
        refresh.setBackground(borderlessBackground());
        refresh.setOnClickListener(v -> refresh());
        footer.addView(refresh);

        return footer;
    }

    private View buildDivider()
    {
        View divider = new View(activity);
        divider.setBackgroundColor(colorDivider);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        return divider;
    }

    private void refresh()
    {
        stopPlayback();
        listContainer.removeAllViews();
        List<File> files = CallRecordingStore.list(activity);
        if (countBadge != null)
            countBadge.setText(String.valueOf(files.size()));
        if (files.isEmpty())
        {
            listContainer.addView(createEmptyState(Utils.IsEnglish()));
            return;
        }
        for (File file : files)
            listContainer.addView(createRow(file));
    }

    private View createEmptyState(boolean english)
    {
        LinearLayout empty = new LinearLayout(activity);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(24), dp(24), dp(24), dp(24));
        empty.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ImageView icon = new ImageView(activity);
        icon.setImageResource(android.R.drawable.ic_btn_speak_now);
        icon.setColorFilter(colorSecondary);
        icon.setAlpha(0.4f);
        empty.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));

        TextView title = new TextView(activity);
        title.setText(english ? "No recordings yet" : "Chưa có bản ghi âm");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setTextColor(colorPrimary);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(14), 0, 0);
        empty.addView(title);

        TextView hint = new TextView(activity);
        hint.setText(english
            ? "Enable auto-record calls, then make a Zalo call."
            : "Bật tự động ghi âm rồi thực hiện cuộc gọi Zalo.");
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        hint.setTextColor(colorSecondary);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(4), 0, 0);
        empty.addView(hint);

        return empty;
    }

    private View createRow(File file)
    {
        boolean english = Utils.IsEnglish();
        RowHolder holder = new RowHolder();
        holder.file = file;

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(12), dp(8), dp(12));
        card.setBackground(rounded(colorCard, dp(16), 0));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, dp(8));
        card.setLayoutParams(cardParams);

        LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout play = new FrameLayout(activity);
        GradientDrawable circle = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[]{colorAccent, lighten(colorAccent, 0.22f)});
        circle.setShape(GradientDrawable.OVAL);
        play.setBackground(new RippleDrawable(
            ColorStateList.valueOf(0x33FFFFFF), circle, null));
        holder.playIcon = new ImageView(activity);
        holder.playIcon.setImageResource(android.R.drawable.ic_media_play);
        holder.playIcon.setColorFilter(Color.WHITE);
        play.addView(holder.playIcon, new FrameLayout.LayoutParams(
            dp(16), dp(16), Gravity.CENTER));
        play.setOnClickListener(v -> togglePlayback(holder));
        top.addView(play, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout info = new LinearLayout(activity);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        infoParams.setMargins(dp(12), 0, dp(8), 0);
        info.setLayoutParams(infoParams);

        TextView title = new TextView(activity);
        title.setText(CallRecordingStore.displayTime(file));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setTextColor(colorPrimary);
        info.addView(title);

        TextView meta = new TextView(activity);
        meta.setText(CallRecordingStore.displayMeta(file));
        meta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        meta.setTextColor(colorSecondary);
        meta.setPadding(0, dp(2), 0, 0);
        info.addView(meta);
        top.addView(info);

        ImageButton export = actionButton(android.R.drawable.ic_menu_share,
            english ? "Export" : "Xuất");
        export.setOnClickListener(v -> export(file));
        top.addView(export);

        ImageButton delete = actionButton(android.R.drawable.ic_menu_delete,
            english ? "Delete" : "Xóa");
        delete.setOnClickListener(v -> confirmDelete(file));
        top.addView(delete);

        card.addView(top);

        holder.progress = new ProgressBar(activity, null,
            android.R.attr.progressBarStyleHorizontal);
        holder.progress.setMax(100);
        holder.progress.setProgressTintList(ColorStateList.valueOf(colorAccent));
        holder.progress.setProgressBackgroundTintList(
            ColorStateList.valueOf(withAlpha(colorPrimary, 0.10f)));
        holder.progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
        progressParams.setMargins(0, dp(8), dp(4), 0);
        card.addView(holder.progress, progressParams);

        return card;
    }

    private ImageButton actionButton(int drawable, String description)
    {
        ImageButton button = new ImageButton(activity);
        button.setImageResource(drawable);
        button.setColorFilter(colorSecondary);
        button.setBackground(borderlessBackground());
        button.setContentDescription(description);
        button.setPadding(dp(8), dp(8), dp(8), dp(8));
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(36), dp(36));
        params.setMarginStart(dp(2));
        button.setLayoutParams(params);
        return button;
    }

    private void togglePlayback(RowHolder holder)
    {
        if (player != null && holder.file.equals(playingFile))
        {
            stopPlayback();
            return;
        }
        stopPlayback();
        try
        {
            MediaPlayer next = new MediaPlayer();
            next.setDataSource(holder.file.getAbsolutePath());
            next.setOnCompletionListener(mp -> stopPlayback());
            next.prepare();
            next.start();
            player = next;
            playingFile = holder.file;
            playingRow = holder;
            holder.playIcon.setImageResource(android.R.drawable.ic_media_pause);
            holder.progress.setMax(Math.max(1, next.getDuration()));
            holder.progress.setProgress(0);
            holder.progress.setVisibility(View.VISIBLE);
            handler.post(progressTick);
        }
        catch (Throwable t)
        {
            Logger.e(t);
            stopPlayback();
            Toast.makeText(activity,
                Utils.IsEnglish() ? "Cannot play this recording" : "Không thể phát bản ghi âm này",
                Toast.LENGTH_SHORT).show();
        }
    }

    private void stopPlayback()
    {
        handler.removeCallbacks(progressTick);
        if (player != null)
        {
            try
            {
                if (player.isPlaying())
                    player.stop();
            }
            catch (Throwable ignored) { }
            try
            {
                player.release();
            }
            catch (Throwable ignored) { }
            player = null;
        }
        if (playingRow != null)
        {
            playingRow.playIcon.setImageResource(android.R.drawable.ic_media_play);
            playingRow.progress.setProgress(0);
            playingRow.progress.setVisibility(View.GONE);
            playingRow = null;
        }
        playingFile = null;
    }

    private void confirmDelete(File file)
    {
        boolean english = Utils.IsEnglish();
        new AlertDialog.Builder(activity)
            .setTitle(english ? "Delete recording?" : "Xóa bản ghi âm?")
            .setMessage(file.getName())
            .setNegativeButton(english ? "Cancel" : "Hủy", null)
            .setPositiveButton(english ? "Delete" : "Xóa", (dialog, which) ->
            {
                if (file.equals(playingFile))
                    stopPlayback();
                if (file.delete())
                {
                    Toast.makeText(activity,
                        english ? "Recording deleted" : "Đã xóa bản ghi âm",
                        Toast.LENGTH_SHORT).show();
                    refresh();
                }
                else
                {
                    Toast.makeText(activity,
                        english ? "Cannot delete recording" : "Không thể xóa bản ghi âm",
                        Toast.LENGTH_SHORT).show();
                }
            })
            .show();
    }

    private void export(File file)
    {
        pendingExport = file;
        try
        {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("audio/x-wav");
            intent.putExtra(Intent.EXTRA_TITLE, file.getName());
            activity.startActivityForResult(intent, REQUEST_CODE_EXPORT);
        }
        catch (Throwable t)
        {
            pendingExport = null;
            Logger.e(t);
            Toast.makeText(activity,
                Utils.IsEnglish() ? "Export failed" : "Xuất bản ghi âm thất bại",
                Toast.LENGTH_SHORT).show();
        }
    }

    private void dismiss()
    {
        if (dialog != null)
        {
            try
            {
                dialog.dismiss();
            }
            catch (Throwable ignored) { }
            dialog = null;
        }
    }

    private int resolveColor(int attribute, int fallback)
    {
        try
        {
            TypedValue value = new TypedValue();
            if (activity.getTheme().resolveAttribute(attribute, value, true))
            {
                if (value.resourceId != 0)
                    return activity.getColor(value.resourceId);
                return value.data;
            }
        }
        catch (Throwable ignored) { }
        return fallback;
    }

    private GradientDrawable rounded(int color, int radius, int strokeColor)
    {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeColor != 0)
            drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private Drawable borderlessBackground()
    {
        try
        {
            TypedValue value = new TypedValue();
            if (activity.getTheme().resolveAttribute(
                android.R.attr.selectableItemBackgroundBorderless, value, true)
                && value.resourceId != 0)
                return activity.getDrawable(value.resourceId);
        }
        catch (Throwable ignored) { }
        return null;
    }

    private static int withAlpha(int color, float alpha)
    {
        return Color.argb((int)(alpha * 255f),
            Color.red(color), Color.green(color), Color.blue(color));
    }

    private static int lighten(int color, float amount)
    {
        int red = Math.min(255, (int)(Color.red(color) + (255 - Color.red(color)) * amount));
        int green = Math.min(255, (int)(Color.green(color) + (255 - Color.green(color)) * amount));
        int blue = Math.min(255, (int)(Color.blue(color) + (255 - Color.blue(color)) * amount));
        return Color.rgb(red, green, blue);
    }

    private int dp(int value)
    {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static Activity findActivity(Context context)
    {
        Context current = context;
        for (int depth = 0; current != null && depth < 16; depth++)
        {
            if (current instanceof Activity)
                return (Activity)current;
            if (!(current instanceof ContextWrapper))
                break;
            Context base = ((ContextWrapper)current).getBaseContext();
            if (base == current)
                break;
            current = base;
        }
        return null;
    }

    private static final class RowHolder
    {
        File file;
        ImageView playIcon;
        ProgressBar progress;
    }
}
