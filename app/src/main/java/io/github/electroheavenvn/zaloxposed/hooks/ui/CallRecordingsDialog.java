package io.github.electroheavenvn.zaloxposed.hooks.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
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
 * <p>The dialog is hosted by Zalo's current activity. Export uses the system
 * document picker, so no storage permission is required.</p>
 */
public final class CallRecordingsDialog
{
    public static final int REQUEST_CODE_EXPORT = 3838;

    private static File pendingExport;

    private final Activity activity;
    private final LinearLayout listContainer;
    private AlertDialog dialog;
    private MediaPlayer player;
    private File playingFile;
    private Button playingButton;

    private CallRecordingsDialog(Activity activity)
    {
        this.activity = activity;
        this.listContainer = new LinearLayout(activity);
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
        new CallRecordingsDialog(activity).show();
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

    private void show()
    {
        boolean english = Utils.IsEnglish();
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(16);
        root.setPadding(padding, padding, padding, padding);

        TextView title = new TextView(activity);
        title.setText(english ? "Call recordings" : "Bản ghi âm cuộc gọi");
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView path = new TextView(activity);
        path.setText(english
            ? "Saved in Zalo's private app data. No storage permission needed."
            : "Lưu trong dữ liệu riêng của Zalo. Không cần quyền bộ nhớ.");
        path.setTextSize(12);
        path.setAlpha(0.65f);
        root.addView(path);

        Button refresh = new Button(activity);
        refresh.setText(english ? "Refresh" : "Làm mới");
        refresh.setAllCaps(false);
        refresh.setOnClickListener(v -> refresh());
        root.addView(refresh);

        ScrollView scroll = new ScrollView(activity);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listContainer, new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        int listHeight = (int)(activity.getResources().getDisplayMetrics().heightPixels * 0.55f);
        root.addView(scroll, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, listHeight));

        dialog = new AlertDialog.Builder(activity)
            .setView(root)
            .setNegativeButton(english ? "Close" : "Đóng", null)
            .create();
        dialog.setOnDismissListener(d -> stopPlayback());
        dialog.show();
        refresh();
    }

    private void refresh()
    {
        listContainer.removeAllViews();
        List<File> files = CallRecordingStore.list(activity);
        if (files.isEmpty())
        {
            TextView empty = new TextView(activity);
            empty.setText(Utils.IsEnglish()
                ? "No recordings yet. Enable auto-record calls and make a Zalo call."
                : "Chưa có bản ghi âm. Hãy bật tự động ghi âm và thực hiện cuộc gọi Zalo.");
            empty.setTextSize(13);
            empty.setAlpha(0.7f);
            empty.setPadding(0, dp(24), 0, dp(24));
            listContainer.addView(empty);
            return;
        }
        for (File file : files)
            listContainer.addView(createRow(file));
    }

    private View createRow(File file)
    {
        boolean english = Utils.IsEnglish();
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));

        TextView title = new TextView(activity);
        title.setText(CallRecordingStore.displayTitle(file));
        title.setTextSize(14);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(title);

        TextView details = new TextView(activity);
        details.setText(CallRecordingStore.displayDetails(file));
        details.setTextSize(12);
        details.setAlpha(0.7f);
        row.addView(details);

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);

        Button play = smallButton(english ? "Play" : "Nghe");
        play.setOnClickListener(v -> togglePlayback(file, play));
        actions.addView(play);

        Button export = smallButton(english ? "Export" : "Xuất");
        export.setOnClickListener(v -> export(file));
        actions.addView(export);

        Button delete = smallButton(english ? "Delete" : "Xóa");
        delete.setOnClickListener(v -> confirmDelete(file));
        actions.addView(delete);

        row.addView(actions);

        View divider = new View(activity);
        divider.setBackgroundColor(0x33888888);
        row.addView(divider, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        return row;
    }

    private Button smallButton(String text)
    {
        Button button = new Button(activity);
        button.setText(text);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(10), dp(4), dp(10), dp(4));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(6), 0, 0, 0);
        button.setLayoutParams(params);
        return button;
    }

    private void togglePlayback(File file, Button button)
    {
        if (player != null && file.equals(playingFile))
        {
            stopPlayback();
            return;
        }
        stopPlayback();
        try
        {
            MediaPlayer next = new MediaPlayer();
            next.setDataSource(file.getAbsolutePath());
            next.setOnCompletionListener(mp -> stopPlayback());
            next.prepare();
            next.start();
            player = next;
            playingFile = file;
            playingButton = button;
            button.setText(Utils.IsEnglish() ? "Stop" : "Dừng");
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
        if (playingButton != null)
        {
            playingButton.setText(Utils.IsEnglish() ? "Play" : "Nghe");
            playingButton = null;
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
}
