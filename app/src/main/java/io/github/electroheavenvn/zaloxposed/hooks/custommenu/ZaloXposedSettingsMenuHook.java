package io.github.electroheavenvn.zaloxposed.hooks.custommenu;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import io.github.electroheavenvn.zaloxposed.MorpheConstants;
import io.github.electroheavenvn.zaloxposed.hooks.BaseHook;
import io.github.electroheavenvn.zaloxposed.hooks.ResourceHook;
import io.github.electroheavenvn.zaloxposed.hooks.ui.CallRecordingsDialog;
import io.github.electroheavenvn.zaloxposed.utilities.BackgroundUtils;
import io.github.electroheavenvn.zaloxposed.utilities.Config;
import io.github.electroheavenvn.zaloxposed.utilities.CustomFont;
import io.github.electroheavenvn.zaloxposed.utilities.Logger;
import io.github.electroheavenvn.zaloxposed.utilities.Utils;

import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.enums.StringMatchType;
import org.luckypray.dexkit.query.matchers.FieldMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@SuppressLint("SetTextI18n")
public class ZaloXposedSettingsMenuHook extends BaseHook
{
    private static final String CUSTOM_ITEM_MARKER = "zalo_xposed_settings";
    private static final int CUSTOM_DRAWABLE_ID = -6767;
    private static final int REQUEST_CODE_IMPORT_CONFIG = 3636;
    private static final int REQUEST_CODE_PICK_FONT = 3737;
    private static final int[] BACKUP_INTERVAL_OPTIONS = new int[]{1, 3, 6, 12};
    private static boolean isOpenZaloXposedSettings1 = false;
    private static boolean isOpenZaloXposedSettings2 = false;
    @SuppressLint("StaticFieldLeak")
    private static LinearLayout rootLayout = null;
    private static Runnable backgroundSourceUiRefresher = null;
    private static Runnable customFontUiRefresher = null;
    private static Class<?> headerTextViewClass = null;
    private TextView templateHeader = null;
    private View templateSeparator = null;

    private void createCustomMenu() throws Exception
    {
        Context context = rootLayout.getContext();
        View separator;
        TextView headerTitle;
        RelativeLayout listItemSetting;


        headerTitle = createHeaderTitle(context);
        headerTitle.setText(Utils.IsEnglish() ? "Background" : "Hình nền");
        rootLayout.addView(headerTitle);
        listItemSetting = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(listItemSetting);
        ListItemSettingHelper.SetIDTracking(listItemSetting, "");
        ListItemSettingHelper.HideDivider(listItemSetting);
        ListItemSettingHelper.SetTitle(listItemSetting, Utils.IsEnglish() ? "Custom background" : "Hình nền tuỳ chỉnh");
        ListItemSettingHelper.SetSwitch(listItemSetting, Config.getEnableBackground());
        ListItemSettingHelper.SetCheckedChangeListener(listItemSetting, Config::setEnableBackground);

        headerTitle = createHeaderTitle(context);
        headerTitle.setText(Utils.IsEnglish() ? "Background source" : "Nguồn hình nền");
        rootLayout.addView(headerTitle);

        RelativeLayout bgAssetsItem = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(bgAssetsItem);
        ListItemSettingHelper.SetIDTracking(bgAssetsItem, "");
        ListItemSettingHelper.ShowDivider(bgAssetsItem);
        ListItemSettingHelper.SetTitle(bgAssetsItem, Utils.IsEnglish() ? "Built-in backgrounds" : "Hình nền có sẵn");
        ListItemSettingHelper.SetSubtitle(bgAssetsItem, Utils.IsEnglish() ? "Bundled with ZaloXposed" : "Đi kèm ZaloXposed");

        RelativeLayout bgPickerItem = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(bgPickerItem);
        ListItemSettingHelper.SetIDTracking(bgPickerItem, "");
        ListItemSettingHelper.ShowDivider(bgPickerItem);
        ListItemSettingHelper.SetTitle(bgPickerItem, Utils.IsEnglish() ? "Image from device" : "Ảnh từ máy");
        ListItemSettingHelper.SetSubtitle(bgPickerItem, Utils.IsEnglish() ? "Android Photo Picker" : "Photo Picker của Android");

        RelativeLayout bgFolderItem = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(bgFolderItem);
        ListItemSettingHelper.SetIDTracking(bgFolderItem, "");
        ListItemSettingHelper.HideDivider(bgFolderItem);
        ListItemSettingHelper.SetTitle(bgFolderItem, Utils.IsEnglish() ? "Folder " + BackgroundUtils.FOLDER_DISPLAY_PATH : "Thư mục " + BackgroundUtils.FOLDER_DISPLAY_PATH);

        backgroundSourceUiRefresher = () -> updateBackgroundSourceItems(context, bgAssetsItem, bgPickerItem, bgFolderItem);
        refreshBackgroundSourceUi();

        ListItemSettingHelper.SetOnClickListener(bgAssetsItem, v ->
        {
            selectBackgroundSource(context, Config.BG_SOURCE_ASSETS);
        });
        ListItemSettingHelper.SetOnClickListener(bgPickerItem, v ->
        {
            openBackgroundPhotoPicker(context);
        });
        ListItemSettingHelper.SetOnClickListener(bgFolderItem, v ->
        {
            onFolderBackgroundClicked(context);
        });


        separator = createSeparator(context);
        rootLayout.addView(separator);
        headerTitle = createHeaderTitle(context);
        headerTitle.setText("Mini Chat (Chat Head)");
        rootLayout.addView(headerTitle);
        listItemSetting = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(listItemSetting);
        ListItemSettingHelper.SetIDTracking(listItemSetting, "");
        ListItemSettingHelper.HideDivider(listItemSetting);
        ListItemSettingHelper.SetTitle(listItemSetting, Utils.IsEnglish() ? "Replace Android Bubble conversations with Mini Chat" : "Thay thế bong bóng hội thoại Android bằng Mini Chat");
        ListItemSettingHelper.SetSubtitle(listItemSetting, Utils.IsEnglish() ? "Requires Android 11 or higher" : "Yêu cầu Android 11 trở lên");
        ListItemSettingHelper.SetSwitch(listItemSetting, Config.getEnableChatHead());
        ListItemSettingHelper.SetCheckedChangeListener(listItemSetting, Config::setEnableChatHead);
        listItemSetting.setEnabled(Build.VERSION.SDK_INT > Build.VERSION_CODES.Q);


        separator = createSeparator(context);
        rootLayout.addView(separator);
        headerTitle = createHeaderTitle(context);
        headerTitle.setText(Utils.IsEnglish() ? "Disappearing messages" : "Tin nhắn tự xoá");
        rootLayout.addView(headerTitle);
        listItemSetting = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(listItemSetting);
        ListItemSettingHelper.SetIDTracking(listItemSetting, "");
        ListItemSettingHelper.ShowDivider(listItemSetting);
        ListItemSettingHelper.SetTitle(listItemSetting, Utils.IsEnglish() ? "Override disappearing messages config" : "Ghi đè cấu hình tin nhắn tự xoá");
        ListItemSettingHelper.SetSwitch(listItemSetting, Config.getEnableTTLOverride());
        ListItemSettingHelper.SetCheckedChangeListener(listItemSetting, Config::setEnableTTLOverride);
        headerTitle = createHeaderTitle(context);
        headerTitle.setText(Utils.IsEnglish() ? "Disappearing message time" : "Thời gian tin nhắn tự xoá");
        TextView titleView = findTitleTextView(headerTitle);
        if (titleView != null)
        {
            TextView listItemSettingTitle = findTitleTextView(listItemSetting);
            if (listItemSettingTitle != null)
                titleView.setTextColor(listItemSettingTitle.getTextColors());
        }
        rootLayout.addView(headerTitle);
        EditText input = new EditText(context);
        rootLayout.addView(input);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setText(Config.getTTL() + "");
        input.setHint(Utils.IsEnglish() ? "Enter value (milliseconds)" : "Nhập giá trị (mili giây)");
        styleEditText(input, context, listItemSetting);
        input.setOnEditorActionListener((textView, actionId, keyEvent) ->
        {
            if (actionId == EditorInfo.IME_ACTION_DONE)
            {
                try
                {
                    long ttlValue = Long.parseLong(textView.getText().toString());
                    Config.setTTL(ttlValue);
                }
                catch (Exception e)
                {
                    Logger.e(e);
                }
                textView.clearFocus();
                return true;
            }
            return false;
        });
        input.setOnFocusChangeListener((view, hasFocus) ->
        {
            if (!hasFocus)
            {
                try
                {
                    EditText editText = (EditText)view;
                    long ttlValue = Long.parseLong(editText.getText().toString());
                    Config.setTTL(ttlValue);
                    hideKeyboard(editText);
                }
                catch (Exception e)
                {
                    Logger.e(e);
                }
            }
        });
        rootLayout.setOnClickListener(v -> input.clearFocus());


        separator = createSeparator(context);
        rootLayout.addView(separator);
        headerTitle = createHeaderTitle(context);
        headerTitle.setText(Utils.IsEnglish() ? "Ads" : "Quảng cáo");
        rootLayout.addView(headerTitle);
        listItemSetting = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(listItemSetting);
        ListItemSettingHelper.SetIDTracking(listItemSetting, "");
        ListItemSettingHelper.ShowDivider(listItemSetting);
        ListItemSettingHelper.SetTitle(listItemSetting, Utils.IsEnglish() ? "Hide Media Box" : "Ẩn Media Box");
        ListItemSettingHelper.SetSwitch(listItemSetting, Config.getHideMediaBox());
        ListItemSettingHelper.SetCheckedChangeListener(listItemSetting, Config::setHideMediaBox);
        listItemSetting = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(listItemSetting);
        ListItemSettingHelper.SetIDTracking(listItemSetting, "");
        ListItemSettingHelper.ShowDivider(listItemSetting);
        ListItemSettingHelper.SetTitle(listItemSetting, Utils.IsEnglish() ? "Hide Business Box" : "Ẩn Business Box");
        ListItemSettingHelper.SetSwitch(listItemSetting, Config.getHideBizBox());
        ListItemSettingHelper.SetCheckedChangeListener(listItemSetting, Config::setHideBizBox);
        listItemSetting = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(listItemSetting);
        ListItemSettingHelper.SetIDTracking(listItemSetting, "");
        ListItemSettingHelper.ShowDivider(listItemSetting);
        ListItemSettingHelper.SetTitle(listItemSetting, Utils.IsEnglish() ? "Hide ZInstant Ads" : "Ẩn quảng cáo ZInstant");
        ListItemSettingHelper.SetSubtitle(listItemSetting, Utils.IsEnglish() ? "Hide ads in Messages tab" : "Ẩn quảng cáo trong tab Tin nhắn");
        ListItemSettingHelper.SetSwitch(listItemSetting, Config.getHideZInstantAds());
        ListItemSettingHelper.SetCheckedChangeListener(listItemSetting, Config::setHideZInstantAds);
        listItemSetting = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(listItemSetting);
        ListItemSettingHelper.SetIDTracking(listItemSetting, "");
        ListItemSettingHelper.ShowDivider(listItemSetting);
        ListItemSettingHelper.SetTitle(listItemSetting, Utils.IsEnglish() ? "Hide Ads in Timeline" : "Ẩn quảng cáo trong Nhật Ký");
        ListItemSettingHelper.SetSwitch(listItemSetting, Config.getHideFeedItemZInstantAds());
        ListItemSettingHelper.SetCheckedChangeListener(listItemSetting, Config::setHideFeedItemZInstantAds);
        listItemSetting = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(listItemSetting);
        ListItemSettingHelper.SetIDTracking(listItemSetting, "");
        ListItemSettingHelper.ShowDivider(listItemSetting);
        ListItemSettingHelper.SetTitle(listItemSetting, Utils.IsEnglish() ? "Block ads network endpoints" : "Chặn yêu cầu mạng quảng cáo");
        ListItemSettingHelper.SetSwitch(listItemSetting, Config.getBlockAdsEndpoints());
        ListItemSettingHelper.SetCheckedChangeListener(listItemSetting, Config::setBlockAdsEndpoints);

        listItemSetting = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(listItemSetting);
        ListItemSettingHelper.SetIDTracking(listItemSetting, "");
        ListItemSettingHelper.ShowDivider(listItemSetting);
        ListItemSettingHelper.SetTitle(listItemSetting, Utils.IsEnglish() ? "Disable sponsored placements" : "Tắt vị trí quảng cáo tài trợ");
        ListItemSettingHelper.SetSubtitle(listItemSetting, Utils.IsEnglish()
            ? "Disable the Story and community sponsored-placement config gates."
            : "Tắt cấu hình vị trí quảng cáo tài trợ trong Story và cộng đồng.");
        ListItemSettingHelper.SetSwitch(listItemSetting, Config.getDisableSponsoredPlacements());
        ListItemSettingHelper.SetCheckedChangeListener(listItemSetting, Config::setDisableSponsoredPlacements);

        listItemSetting = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(listItemSetting);
        ListItemSettingHelper.SetIDTracking(listItemSetting, "");
        ListItemSettingHelper.HideDivider(listItemSetting);
        ListItemSettingHelper.SetTitle(listItemSetting, Utils.IsEnglish() ? "Disable advertising ID" : "Tắt Advertising ID");
        ListItemSettingHelper.SetSubtitle(listItemSetting, Utils.IsEnglish()
            ? "Return a zeroed, limit-tracking advertising identifier to Google Play Services."
            : "Trả về Advertising ID rỗng và bật giới hạn theo dõi cho Google Play Services.");
        ListItemSettingHelper.SetSwitch(listItemSetting, Config.getDisableAdId());
        ListItemSettingHelper.SetCheckedChangeListener(listItemSetting, Config::setDisableAdId);


        separator = createSeparator(context);
        rootLayout.addView(separator);
        headerTitle = createHeaderTitle(context);
        headerTitle.setText(Utils.IsEnglish() ? "Media" : "Phương tiện");
        rootLayout.addView(headerTitle);
        listItemSetting = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(listItemSetting);
        ListItemSettingHelper.SetIDTracking(listItemSetting, "");
        ListItemSettingHelper.ShowDivider(listItemSetting);
        ListItemSettingHelper.SetTitle(listItemSetting, Utils.IsEnglish() ? "Enable media download" : "Bật tải media");
        ListItemSettingHelper.SetSubtitle(listItemSetting, Utils.IsEnglish()
            ? "Add Download to story, photo and video menus. Files are saved to Pictures/Zalo and Movies/Zalo."
            : "Thêm nút Tải xuống vào menu story, ảnh và video. Tệp được lưu vào Pictures/Zalo và Movies/Zalo.");
        ListItemSettingHelper.SetSwitch(listItemSetting, Config.getEnableDownload());
        ListItemSettingHelper.SetCheckedChangeListener(listItemSetting, Config::setEnableDownload);

        listItemSetting = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(listItemSetting);
        ListItemSettingHelper.SetIDTracking(listItemSetting, "");
        ListItemSettingHelper.ShowDivider(listItemSetting);
        ListItemSettingHelper.SetTitle(listItemSetting, Utils.IsEnglish() ? "Prefer original photo quality" : "Ưu tiên chất lượng ảnh gốc");
        ListItemSettingHelper.SetSubtitle(listItemSetting, Utils.IsEnglish()
            ? "Use Zalo's native Original quality for picker and shared photos."
            : "Dùng chất lượng Gốc của Zalo cho ảnh chọn và ảnh chia sẻ.");
        ListItemSettingHelper.SetSwitch(listItemSetting, Config.getPreferOriginalPhoto());
        ListItemSettingHelper.SetCheckedChangeListener(listItemSetting, Config::setPreferOriginalPhoto);

        listItemSetting = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(listItemSetting);
        ListItemSettingHelper.SetIDTracking(listItemSetting, "");
        ListItemSettingHelper.HideDivider(listItemSetting);
        ListItemSettingHelper.SetTitle(listItemSetting, Utils.IsEnglish() ? "Keep expired media accessible" : "Giữ media hết hạn mở được");
        ListItemSettingHelper.SetSubtitle(listItemSetting, Utils.IsEnglish()
            ? "Keep on-device chat files usable after Zalo marks them expired."
            : "Giữ tệp chat trên máy mở được sau khi Zalo đánh dấu hết hạn.");
        ListItemSettingHelper.SetSwitch(listItemSetting, Config.getKeepExpiredMedia());
        ListItemSettingHelper.SetCheckedChangeListener(listItemSetting, Config::setKeepExpiredMedia);


        separator = createSeparator(context);
        rootLayout.addView(separator);
        headerTitle = createHeaderTitle(context);
        headerTitle.setText(Utils.IsEnglish() ? "Logging and tracking" : "Ghi nhật ký và theo dõi");
        rootLayout.addView(headerTitle);
        listItemSetting = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(listItemSetting);
        ListItemSettingHelper.SetIDTracking(listItemSetting, "");
        ListItemSettingHelper.ShowDivider(listItemSetting);
        ListItemSettingHelper.SetTitle(listItemSetting, Utils.IsEnglish() ? "Disable Firebase logging" : "Tắt ghi nhật ký Firebase");
        ListItemSettingHelper.SetSwitch(listItemSetting, Config.getDisableFirebaseLogging());
        ListItemSettingHelper.SetCheckedChangeListener(listItemSetting, Config::setDisableFirebaseLogging);
        listItemSetting = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(listItemSetting);
        ListItemSettingHelper.SetIDTracking(listItemSetting, "");
        ListItemSettingHelper.ShowDivider(listItemSetting);
        ListItemSettingHelper.SetTitle(listItemSetting, Utils.IsEnglish() ? "Disable Zalo tracking" : "Tắt tính năng theo dõi của Zalo");
        ListItemSettingHelper.SetSwitch(listItemSetting, Config.getDisableZaloTracking());
        ListItemSettingHelper.SetCheckedChangeListener(listItemSetting, Config::setDisableZaloTracking);
        listItemSetting = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(listItemSetting);
        ListItemSettingHelper.SetIDTracking(listItemSetting, "");
        ListItemSettingHelper.ShowDivider(listItemSetting);
        ListItemSettingHelper.SetTitle(listItemSetting, Utils.IsEnglish() ? "Block QoS network endpoints" : "Chặn yêu cầu mạng QoS");
        ListItemSettingHelper.SetSwitch(listItemSetting, Config.getBlockQoSEndpoints());
        ListItemSettingHelper.SetCheckedChangeListener(listItemSetting, Config::setBlockQoSEndpoints);

        listItemSetting = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(listItemSetting);
        ListItemSettingHelper.SetIDTracking(listItemSetting, "");
        ListItemSettingHelper.ShowDivider(listItemSetting);
        ListItemSettingHelper.SetTitle(listItemSetting, Utils.IsEnglish() ? "Disable Firebase Analytics" : "Tắt Firebase Analytics");
        ListItemSettingHelper.SetSubtitle(listItemSetting, Utils.IsEnglish()
            ? "Drop analytics events, user IDs and user properties."
            : "Bỏ qua sự kiện phân tích, user ID và thuộc tính người dùng.");
        ListItemSettingHelper.SetSwitch(listItemSetting, Config.getDisableFirebaseAnalytics());
        ListItemSettingHelper.SetCheckedChangeListener(listItemSetting, Config::setDisableFirebaseAnalytics);

        listItemSetting = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(listItemSetting);
        ListItemSettingHelper.SetIDTracking(listItemSetting, "");
        ListItemSettingHelper.HideDivider(listItemSetting);
        ListItemSettingHelper.SetTitle(listItemSetting, Utils.IsEnglish() ? "Disable Crashlytics" : "Tắt Crashlytics");
        ListItemSettingHelper.SetSubtitle(listItemSetting, Utils.IsEnglish()
            ? "Suppress crash reports, custom keys and Crashlytics logs."
            : "Chặn báo cáo lỗi, custom key và nhật ký Crashlytics.");
        ListItemSettingHelper.SetSwitch(listItemSetting, Config.getDisableCrashlytics());
        ListItemSettingHelper.SetCheckedChangeListener(listItemSetting, Config::setDisableCrashlytics);


        separator = createSeparator(context);
        rootLayout.addView(separator);
        headerTitle = createHeaderTitle(context);
        headerTitle.setText(Utils.IsEnglish() ? "Recalled and deleted messages" : "Thu hồi và xoá tin nhắn");
        rootLayout.addView(headerTitle);
        listItemSetting = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(listItemSetting);
        ListItemSettingHelper.SetIDTracking(listItemSetting, "");
        ListItemSettingHelper.ShowDivider(listItemSetting);
        ListItemSettingHelper.SetTitle(listItemSetting, Utils.IsEnglish() ? "Anti-Recall" : "Chống thu hồi tin nhắn");
        ListItemSettingHelper.SetSwitch(listItemSetting, Config.getEnableAntiRecall());
        ListItemSettingHelper.SetCheckedChangeListener(listItemSetting, Config::setEnableAntiRecall);
        listItemSetting = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(listItemSetting);
        ListItemSettingHelper.SetIDTracking(listItemSetting, "");
        ListItemSettingHelper.ShowDivider(listItemSetting);
        ListItemSettingHelper.SetTitle(listItemSetting, Utils.IsEnglish() ? "Anti-Delete" : "Chống xoá tin nhắn");
        ListItemSettingHelper.SetSwitch(listItemSetting, Config.getEnableAntiDelete());
        ListItemSettingHelper.SetCheckedChangeListener(listItemSetting, Config::setEnableAntiDelete);
        listItemSetting = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(listItemSetting);
        ListItemSettingHelper.SetIDTracking(listItemSetting, "");
        ListItemSettingHelper.ShowDivider(listItemSetting);
        ListItemSettingHelper.SetTitle(listItemSetting, Utils.IsEnglish() ? "Include my recalled messages" : "Bao gồm tin nhắn tôi đã thu hồi");
        ListItemSettingHelper.SetSwitch(listItemSetting, Config.getAntiRecallIncludeMe());
        ListItemSettingHelper.SetCheckedChangeListener(listItemSetting, Config::setAntiRecallIncludeMe);
        listItemSetting = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(listItemSetting);
        ListItemSettingHelper.SetIDTracking(listItemSetting, "");
        ListItemSettingHelper.HideDivider(listItemSetting);
        ListItemSettingHelper.SetTitle(listItemSetting, Utils.IsEnglish() ? "Include messages deleted by me" : "Bao gồm tin nhắn được tôi xoá");
        ListItemSettingHelper.SetSwitch(listItemSetting, Config.getAntiDeleteIncludeMyDeletion());
        ListItemSettingHelper.SetCheckedChangeListener(listItemSetting, Config::setAntiDeleteIncludeMyDeletion);


        separator = createSeparator(context);
        rootLayout.addView(separator);
        headerTitle = createHeaderTitle(context);
        headerTitle.setText(Utils.IsEnglish() ? "Privacy" : "Quyền riêng tư");
        rootLayout.addView(headerTitle);
        listItemSetting = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(listItemSetting);
        ListItemSettingHelper.SetIDTracking(listItemSetting, "");
        ListItemSettingHelper.ShowDivider(listItemSetting);
        ListItemSettingHelper.SetTitle(listItemSetting, Utils.IsEnglish() ? "Hide typing indicators" : "Ẩn trạng thái đang nhập");
        ListItemSettingHelper.SetSwitch(listItemSetting, Config.getEnableSilentTyping());
        ListItemSettingHelper.SetCheckedChangeListener(listItemSetting, Config::setEnableSilentTyping);
        listItemSetting = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(listItemSetting);
        ListItemSettingHelper.SetIDTracking(listItemSetting, "");
        ListItemSettingHelper.ShowDivider(listItemSetting);
        ListItemSettingHelper.SetTitle(listItemSetting, Utils.IsEnglish() ? "Hide \"Seen\" status" : "Ẩn trạng thái Đã xem");
        ListItemSettingHelper.SetSwitch(listItemSetting, Config.getBlockSendSeen());
        ListItemSettingHelper.SetCheckedChangeListener(listItemSetting, Config::setBlockSendSeen);

        listItemSetting = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(listItemSetting);
        ListItemSettingHelper.SetIDTracking(listItemSetting, "");
        ListItemSettingHelper.ShowDivider(listItemSetting);
        ListItemSettingHelper.SetTitle(listItemSetting, Utils.IsEnglish() ? "View stories anonymously" : "Xem story ẩn danh");
        ListItemSettingHelper.SetSubtitle(listItemSetting, Utils.IsEnglish()
            ? "Skip the outbound story-view acknowledgement. Story UI stays unchanged."
            : "Bỏ gói xác nhận đã xem story gửi lên máy chủ. Giao diện story không đổi.");
        ListItemSettingHelper.SetSwitch(listItemSetting, Config.getViewStoriesAnonymously());
        ListItemSettingHelper.SetCheckedChangeListener(listItemSetting, Config::setViewStoriesAnonymously);

        listItemSetting = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(listItemSetting);
        ListItemSettingHelper.SetIDTracking(listItemSetting, "");
        ListItemSettingHelper.HideDivider(listItemSetting);
        ListItemSettingHelper.SetTitle(listItemSetting, Utils.IsEnglish() ? "Open web links externally" : "Mở liên kết web bằng trình duyệt");
        ListItemSettingHelper.SetSubtitle(listItemSetting, Utils.IsEnglish()
            ? "Open chat/feed http(s) links in the system browser. Zalo and mini-app links stay in-app."
            : "Mở liên kết http(s) trong chat/nhật ký bằng trình duyệt. Liên kết Zalo và mini-app vẫn mở trong app.");
        ListItemSettingHelper.SetSwitch(listItemSetting, Config.getOpenLinksExternally());
        ListItemSettingHelper.SetCheckedChangeListener(listItemSetting, Config::setOpenLinksExternally);


        separator = createSeparator(context);
        rootLayout.addView(separator);
        headerTitle = createHeaderTitle(context);
        headerTitle.setText(Utils.IsEnglish() ? "Calls" : "Cuộc gọi");
        rootLayout.addView(headerTitle);
        listItemSetting = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(listItemSetting);
        ListItemSettingHelper.SetIDTracking(listItemSetting, "");
        ListItemSettingHelper.ShowDivider(listItemSetting);
        ListItemSettingHelper.SetTitle(listItemSetting, Utils.IsEnglish() ? "Auto-record calls" : "Tự động ghi âm cuộc gọi");
        ListItemSettingHelper.SetSubtitle(listItemSetting, Utils.IsEnglish()
            ? "Record Zalo one-to-one calls through Zalo's native recorder. Files are kept in Zalo's private app data."
            : "Ghi âm cuộc gọi Zalo 1-1 bằng bộ ghi âm gốc của Zalo. Tệp lưu trong dữ liệu riêng của Zalo.");
        ListItemSettingHelper.SetSwitch(listItemSetting, Config.getEnableCallRecording());
        ListItemSettingHelper.SetCheckedChangeListener(listItemSetting, Config::setEnableCallRecording);

        RelativeLayout callRecordingsItem = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(callRecordingsItem);
        ListItemSettingHelper.SetIDTracking(callRecordingsItem, "");
        ListItemSettingHelper.HideDivider(callRecordingsItem);
        ListItemSettingHelper.SetTitle(callRecordingsItem, Utils.IsEnglish()
            ? "Call recordings" : "Bản ghi âm cuộc gọi");
        ListItemSettingHelper.SetSubtitle(callRecordingsItem, Utils.IsEnglish()
            ? "Listen, delete or export saved recordings. No storage permission needed."
            : "Nghe, xóa hoặc xuất bản ghi âm đã lưu. Không cần quyền bộ nhớ.");
        ListItemSettingHelper.SetOnClickListener(callRecordingsItem,
            v -> CallRecordingsDialog.show(v.getContext()));


        separator = createSeparator(context);
        rootLayout.addView(separator);
        headerTitle = createHeaderTitle(context);
        headerTitle.setText(Utils.IsEnglish() ? "Security" : "Bảo mật");
        rootLayout.addView(headerTitle);
        RelativeLayout passcodeGraceItem = ListItemSettingHelper.CreateNew(context);
        RelativeLayout passcodeGraceToggle = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(passcodeGraceToggle);
        ListItemSettingHelper.SetIDTracking(passcodeGraceToggle, "");
        ListItemSettingHelper.ShowDivider(passcodeGraceToggle);
        ListItemSettingHelper.SetTitle(passcodeGraceToggle, Utils.IsEnglish() ? "Passcode grace period" : "Thời gian miễn nhập mã khoá");
        ListItemSettingHelper.SetSubtitle(passcodeGraceToggle, Utils.IsEnglish()
            ? "Extend how long Zalo stays unlocked before asking for the app passcode"
            : "Kéo dài thời gian Zalo giữ mở khoá trước khi yêu cầu nhập mã");
        ListItemSettingHelper.SetSwitch(passcodeGraceToggle, Config.getPasscodeGrace());
        ListItemSettingHelper.SetCheckedChangeListener(passcodeGraceToggle, enabled ->
        {
            try
            {
                Config.setPasscodeGrace(enabled);
                updatePasscodeGraceAvailability(passcodeGraceItem, enabled);
            }
            catch (Exception e)
            {
                Logger.e(e);
            }
        });
        rootLayout.addView(passcodeGraceItem);
        ListItemSettingHelper.SetIDTracking(passcodeGraceItem, "");
        ListItemSettingHelper.HideDivider(passcodeGraceItem);
        ListItemSettingHelper.SetTitle(passcodeGraceItem, Utils.IsEnglish() ? "Grace duration" : "Thời lượng miễn nhập");
        updatePasscodeGraceRow(passcodeGraceItem, Config.getPasscodeGraceMs());
        updatePasscodeGraceAvailability(passcodeGraceItem, Config.getPasscodeGrace());
        ListItemSettingHelper.SetOnClickListener(passcodeGraceItem, v ->
        {
            try
            {
                int next = nextPasscodeGraceMs(Config.getPasscodeGraceMs());
                Config.setPasscodeGraceMs(next);
                updatePasscodeGraceRow(passcodeGraceItem, next);
            }
            catch (Exception e)
            {
                Logger.e(e);
            }
        });


        separator = createSeparator(context);
        rootLayout.addView(separator);
        headerTitle = createHeaderTitle(context);
        headerTitle.setText(Utils.IsEnglish() ? "Screen effects" : "Hiệu ứng màn hình");
        rootLayout.addView(headerTitle);
        listItemSetting = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(listItemSetting);
        ListItemSettingHelper.SetIDTracking(listItemSetting, "");
        ListItemSettingHelper.ShowDivider(listItemSetting);
        ListItemSettingHelper.SetTitle(listItemSetting, Utils.IsEnglish() ? "Block message effects" : "Chặn hiệu ứng tin nhắn");
        ListItemSettingHelper.SetSubtitle(listItemSetting, Utils.IsEnglish()
            ? "Stop full-screen message animations. Restart Zalo to apply."
            : "Tắt hiệu ứng toàn màn hình khi nhắn tin. Khởi động lại Zalo để áp dụng.");
        ListItemSettingHelper.SetSwitch(listItemSetting, Config.getBlockMessageEffect());
        ListItemSettingHelper.SetCheckedChangeListener(listItemSetting, Config::setBlockMessageEffect);
        listItemSetting = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(listItemSetting);
        ListItemSettingHelper.SetIDTracking(listItemSetting, "");
        ListItemSettingHelper.HideDivider(listItemSetting);
        ListItemSettingHelper.SetTitle(listItemSetting, Utils.IsEnglish() ? "Block sticker effects" : "Chặn hiệu ứng sticker");
        ListItemSettingHelper.SetSubtitle(listItemSetting, Utils.IsEnglish()
            ? "Stop sticker overlays and punch-glass effects. Restart Zalo to apply."
            : "Tắt hiệu ứng sticker toàn màn hình và hiệu ứng punch-glass. Khởi động lại Zalo để áp dụng.");
        ListItemSettingHelper.SetSwitch(listItemSetting, Config.getBlockStickerEffect());
        ListItemSettingHelper.SetCheckedChangeListener(listItemSetting, Config::setBlockStickerEffect);


        separator = createSeparator(context);
        rootLayout.addView(separator);
        headerTitle = createHeaderTitle(context);
        headerTitle.setText(Utils.IsEnglish() ? "Chat reactions" : "Biểu cảm chat");
        rootLayout.addView(headerTitle);
        listItemSetting = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(listItemSetting);
        ListItemSettingHelper.SetIDTracking(listItemSetting, "");
        ListItemSettingHelper.HideDivider(listItemSetting);
        ListItemSettingHelper.SetTitle(listItemSetting, Utils.IsEnglish() ? "Unlock chat reactions" : "Mở khoá biểu cảm chat");
        ListItemSettingHelper.SetSubtitle(listItemSetting, Utils.IsEnglish()
            ? "Show the reaction picker without a subscription package"
            : "Hiện bảng biểu cảm mà không cần gói đăng ký");
        ListItemSettingHelper.SetSwitch(listItemSetting, Config.getUnlockChatReaction());
        ListItemSettingHelper.SetCheckedChangeListener(listItemSetting, Config::setUnlockChatReaction);


        separator = createSeparator(context);
        rootLayout.addView(separator);
        headerTitle = createHeaderTitle(context);
        headerTitle.setText(Utils.IsEnglish() ? "User interface" : "Giao diện");
        rootLayout.addView(headerTitle);
        listItemSetting = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(listItemSetting);
        ListItemSettingHelper.SetIDTracking(listItemSetting, "");
        ListItemSettingHelper.ShowDivider(listItemSetting);
        ListItemSettingHelper.SetTitle(listItemSetting, Utils.IsEnglish() ? "Enable extended grid menu" : "Kích hoạt chat menu mở rộng");
        ListItemSettingHelper.SetSwitch(listItemSetting, Config.getEnableExtendedGridMenu());
        ListItemSettingHelper.SetCheckedChangeListener(listItemSetting, Config::setEnableExtendedGridMenu);
        listItemSetting = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(listItemSetting);
        ListItemSettingHelper.SetIDTracking(listItemSetting, "");
        ListItemSettingHelper.ShowDivider(listItemSetting);
        ListItemSettingHelper.SetTitle(listItemSetting, Utils.IsEnglish() ? "Enable extended group right menu" : "Kích hoạt menu nhóm mở rộng");
        ListItemSettingHelper.SetSwitch(listItemSetting, Config.getEnableExtendedGroupRightMenu());
        ListItemSettingHelper.SetCheckedChangeListener(listItemSetting, Config::setEnableExtendedGroupRightMenu);
        listItemSetting = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(listItemSetting);
        ListItemSettingHelper.SetIDTracking(listItemSetting, "");
        ListItemSettingHelper.ShowDivider(listItemSetting);
        ListItemSettingHelper.SetTitle(listItemSetting, Utils.IsEnglish() ? "Enable extended group settings menu" : "Kích hoạt menu cài đặt nhóm mở rộng");
        ListItemSettingHelper.SetSwitch(listItemSetting, Config.getEnableExtendedGroupSettingMenu());
        ListItemSettingHelper.SetCheckedChangeListener(listItemSetting, Config::setEnableExtendedGroupSettingMenu);
        listItemSetting = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(listItemSetting);
        ListItemSettingHelper.SetIDTracking(listItemSetting, "");
        ListItemSettingHelper.HideDivider(listItemSetting);
        ListItemSettingHelper.SetTitle(listItemSetting, Utils.IsEnglish() ? "Liquid tab bar" : "Thanh tab dạng lỏng");
        ListItemSettingHelper.SetSubtitle(listItemSetting, Utils.IsEnglish()
            ? "Floating rounded tabs with a sliding highlight."
            : "Thanh tab nổi bo tròn với hiệu ứng trượt.");
        ListItemSettingHelper.SetSwitch(listItemSetting, Config.getEnableLiquidUI());
        ListItemSettingHelper.SetCheckedChangeListener(listItemSetting, Config::setEnableLiquidUI);


        separator = createSeparator(context);
        rootLayout.addView(separator);
        headerTitle = createHeaderTitle(context);
        headerTitle.setText(Utils.IsEnglish() ? "Custom font" : "Phông chữ tuỳ chỉnh");
        rootLayout.addView(headerTitle);

        RelativeLayout customFontEnableItem = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(customFontEnableItem);
        ListItemSettingHelper.SetIDTracking(customFontEnableItem, "");
        ListItemSettingHelper.ShowDivider(customFontEnableItem);
        ListItemSettingHelper.SetTitle(customFontEnableItem, Utils.IsEnglish() ? "Enable custom font" : "Kích hoạt phông chữ tuỳ chỉnh");
        ListItemSettingHelper.SetSubtitle(customFontEnableItem, Utils.IsEnglish()
            ? "Apply the selected font to Zalo text and chat"
            : "Áp dụng phông chữ đã chọn cho văn bản và chat Zalo");
        ListItemSettingHelper.SetSwitch(customFontEnableItem, Config.getCustomFontEnabled());
        ListItemSettingHelper.SetCheckedChangeListener(customFontEnableItem, enabled ->
        {
            try
            {
                Config.setCustomFontEnabled(enabled);
                if (enabled && !CustomFont.hasFont(context))
                    showToast(context, Utils.IsEnglish()
                        ? "Choose a font file below to finish enabling."
                        : "Hãy chọn tệp phông chữ bên dưới để hoàn tất.");
            }
            catch (Exception e)
            {
                Logger.e(e);
            }
        });

        RelativeLayout customFontFileItem = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(customFontFileItem);
        ListItemSettingHelper.SetIDTracking(customFontFileItem, "");
        ListItemSettingHelper.ShowDivider(customFontFileItem);
        ListItemSettingHelper.SetTitle(customFontFileItem, Utils.IsEnglish() ? "Font file" : "Tệp phông chữ");
        ListItemSettingHelper.SetOnClickListener(customFontFileItem, v -> openFontPicker(context));

        RelativeLayout customFontResetItem = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(customFontResetItem);
        ListItemSettingHelper.SetIDTracking(customFontResetItem, "");
        ListItemSettingHelper.HideDivider(customFontResetItem);
        ListItemSettingHelper.SetTitle(customFontResetItem, Utils.IsEnglish() ? "Reset custom font" : "Đặt lại phông chữ");
        ListItemSettingHelper.SetSubtitle(customFontResetItem, Utils.IsEnglish()
            ? "Remove the selected font and restore Zalo's default"
            : "Xoá phông chữ đã chọn và khôi phục mặc định của Zalo");
        ListItemSettingHelper.SetOnClickListener(customFontResetItem, v ->
        {
            try
            {
                CustomFont.reset(context);
                refreshCustomFontUi();
                showToast(context, Utils.IsEnglish()
                    ? "Custom font removed. Restart Zalo to apply."
                    : "Đã xoá phông chữ tuỳ chỉnh. Khởi động lại Zalo để áp dụng.");
            }
            catch (Exception e)
            {
                Logger.e(e);
            }
        });

        customFontUiRefresher = () -> updateCustomFontItems(context, customFontEnableItem, customFontFileItem, customFontResetItem);
        refreshCustomFontUi();


        separator = createSeparator(context);
        rootLayout.addView(separator);
        headerTitle = createHeaderTitle(context);
        headerTitle.setText("ZCloud");
        rootLayout.addView(headerTitle);
        listItemSetting = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(listItemSetting);
        ListItemSettingHelper.SetIDTracking(listItemSetting, "");
        ListItemSettingHelper.ShowDivider(listItemSetting);
        ListItemSettingHelper.SetTitle(listItemSetting, Utils.IsEnglish() ? "Unlock ZCloud" : "Mở khoá ZCloud");
        ListItemSettingHelper.SetSubtitle(listItemSetting, Utils.IsEnglish() ? "Does not increase My Documents capacity" : "Không tăng dung lượng bộ nhớ My Documents");
        ListItemSettingHelper.SetSwitch(listItemSetting, Config.getUnlockZCloud());
        ListItemSettingHelper.SetCheckedChangeListener(listItemSetting, Config::setUnlockZCloud);

        listItemSetting = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(listItemSetting);
        ListItemSettingHelper.SetIDTracking(listItemSetting, "");
        ListItemSettingHelper.HideDivider(listItemSetting);
        ListItemSettingHelper.SetTitle(listItemSetting, Utils.IsEnglish() ? "Hide ZCloud banner" : "Ẩn banner ZCloud");
        ListItemSettingHelper.SetSubtitle(listItemSetting, Utils.IsEnglish()
            ? "Hide the ZCloud banner in the message list."
            : "Ẩn banner ZCloud trong danh sách tin nhắn.");
        ListItemSettingHelper.SetSwitch(listItemSetting, Config.getHideZCloudBanner());
        ListItemSettingHelper.SetCheckedChangeListener(listItemSetting, Config::setHideZCloudBanner);


        separator = createSeparator(context);
        rootLayout.addView(separator);
        headerTitle = createHeaderTitle(context);
        headerTitle.setText(Utils.IsEnglish() ? "Backup messages" : "Sao lưu tin nhắn");
        rootLayout.addView(headerTitle);
        listItemSetting = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(listItemSetting);
        ListItemSettingHelper.SetIDTracking(listItemSetting, "");
        ListItemSettingHelper.ShowDivider(listItemSetting);
        ListItemSettingHelper.SetTitle(listItemSetting, Utils.IsEnglish() ? "Restore Google Drive backup options" : "Phục hồi tuỳ chọn sao lưu ảnh bằng Google Drive");
        ListItemSettingHelper.SetSubtitle(listItemSetting, Utils.IsEnglish() ? "Replace the ZCloud backup options" : "Thay thế tuỳ chọn sao lưu bằng ZCloud");
        ListItemSettingHelper.SetSwitch(listItemSetting, Config.getRestoreGoogleDriveBackup());
        ListItemSettingHelper.SetCheckedChangeListener(listItemSetting, Config::setRestoreGoogleDriveBackup);

        RelativeLayout backupIntervalItem = ListItemSettingHelper.CreateNew(context);
        RelativeLayout backupIntervalToggle = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(backupIntervalToggle);
        ListItemSettingHelper.SetIDTracking(backupIntervalToggle, "");
        ListItemSettingHelper.ShowDivider(backupIntervalToggle);
        ListItemSettingHelper.SetTitle(backupIntervalToggle, Utils.IsEnglish() ? "Custom backup interval" : "Chu kỳ sao lưu tuỳ chỉnh");
        ListItemSettingHelper.SetSubtitle(backupIntervalToggle, Utils.IsEnglish()
            ? "Override the automatic server backup interval"
            : "Ghi đè chu kỳ sao lưu tự động của máy chủ");
        ListItemSettingHelper.SetSwitch(backupIntervalToggle, Config.getEnableBackupInterval());
        ListItemSettingHelper.SetCheckedChangeListener(backupIntervalToggle, enabled ->
        {
            try
            {
                Config.setEnableBackupInterval(enabled);
                updateBackupIntervalAvailability(backupIntervalItem, enabled);
            }
            catch (Exception e)
            {
                Logger.e(e);
            }
        });

        rootLayout.addView(backupIntervalItem);
        ListItemSettingHelper.SetIDTracking(backupIntervalItem, "");
        ListItemSettingHelper.HideDivider(backupIntervalItem);
        ListItemSettingHelper.SetTitle(backupIntervalItem, Utils.IsEnglish() ? "Backup interval" : "Chu kỳ sao lưu");
        updateBackupIntervalRow(backupIntervalItem, Config.getBackupIntervalHours());
        updateBackupIntervalAvailability(backupIntervalItem, Config.getEnableBackupInterval());
        ListItemSettingHelper.SetOnClickListener(backupIntervalItem, v ->
        {
            try
            {
                int next = nextBackupIntervalHours(Config.getBackupIntervalHours());
                Config.setBackupIntervalHours(next);
                updateBackupIntervalRow(backupIntervalItem, next);
            }
            catch (Exception e)
            {
                Logger.e(e);
            }
        });


        separator = createSeparator(context);
        rootLayout.addView(separator);
        headerTitle = createHeaderTitle(context);
        headerTitle.setText(Utils.IsEnglish() ? "MicroG & Google Drive" : "MicroG & Google Drive");
        rootLayout.addView(headerTitle);
        listItemSetting = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(listItemSetting);
        ListItemSettingHelper.SetIDTracking(listItemSetting, "");
        ListItemSettingHelper.ShowDivider(listItemSetting);
        ListItemSettingHelper.SetTitle(listItemSetting, Utils.IsEnglish() ? "MicroG Drive support" : "Hỗ trợ Google Drive qua MicroG");
        ListItemSettingHelper.SetSubtitle(listItemSetting, Utils.IsEnglish()
            ? "Use microG-RE for Google Drive account selection and sign-in. Prompts to install it when missing."
            : "Dùng microG-RE để chọn tài khoản và đăng nhập Google Drive. Sẽ nhắc cài đặt nếu chưa có.");
        ListItemSettingHelper.SetSwitch(listItemSetting, Config.getEnableMicroGSupport());
        ListItemSettingHelper.SetCheckedChangeListener(listItemSetting, Config::setEnableMicroGSupport);

        listItemSetting = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(listItemSetting);
        ListItemSettingHelper.SetIDTracking(listItemSetting, "");
        ListItemSettingHelper.HideDivider(listItemSetting);
        ListItemSettingHelper.SetTitle(listItemSetting, Utils.IsEnglish() ? "Remove Drive backup age limit" : "Bỏ giới hạn tuổi backup Drive");
        ListItemSettingHelper.SetSubtitle(listItemSetting, Utils.IsEnglish()
            ? "Include media of any age in Zalo's existing Google Drive backup and restore pipeline."
            : "Bao gồm media mọi độ tuổi trong quy trình sao lưu/phục hồi Google Drive của Zalo.");
        ListItemSettingHelper.SetSwitch(listItemSetting, Config.getRemoveBackupAgeLimit());
        ListItemSettingHelper.SetCheckedChangeListener(listItemSetting, Config::setRemoveBackupAgeLimit);


        separator = createSeparator(context);
        rootLayout.addView(separator);
        headerTitle = createHeaderTitle(context);
        headerTitle.setText(Utils.IsEnglish() ? "Profile" : "Hồ sơ");
        rootLayout.addView(headerTitle);
        listItemSetting = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(listItemSetting);
        ListItemSettingHelper.SetIDTracking(listItemSetting, "");
        ListItemSettingHelper.ShowDivider(listItemSetting);
        ListItemSettingHelper.SetTitle(listItemSetting, Utils.IsEnglish() ? "Restore profile music options" : "Phục hồi tuỳ chọn nhạc nền hồ sơ");
        ListItemSettingHelper.SetSubtitle(listItemSetting, Utils.IsEnglish() ? "Replace the ZStyle profile music options" : "Thay thế tuỳ chọn chọn nhạc nền hồ sơ bằng ZStyle");
        ListItemSettingHelper.SetSwitch(listItemSetting, Config.getRestoreProfileMusic());
        ListItemSettingHelper.SetCheckedChangeListener(listItemSetting, Config::setRestoreProfileMusic);

        RelativeLayout avatarSavingItem = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(avatarSavingItem);
        ListItemSettingHelper.SetIDTracking(avatarSavingItem, "");
        ListItemSettingHelper.ShowDivider(avatarSavingItem);
        ListItemSettingHelper.SetTitle(avatarSavingItem, Utils.IsEnglish() ? "Allow saving profile avatars" : "Cho phép lưu ảnh đại diện hồ sơ");
        ListItemSettingHelper.SetSubtitle(avatarSavingItem, Utils.IsEnglish()
            ? "Restore the avatar save action and screenshots"
            : "Khôi phục nút lưu ảnh và chụp màn hình ảnh đại diện");
        ListItemSettingHelper.SetSwitch(avatarSavingItem, Config.getEnableAvatarSaving());
        ListItemSettingHelper.SetCheckedChangeListener(avatarSavingItem, Config::setEnableAvatarSaving);

        RelativeLayout coverSavingItem = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(coverSavingItem);
        ListItemSettingHelper.SetIDTracking(coverSavingItem, "");
        ListItemSettingHelper.HideDivider(coverSavingItem);
        ListItemSettingHelper.SetTitle(coverSavingItem, Utils.IsEnglish() ? "Allow saving profile cover photos" : "Cho phép lưu ảnh bìa hồ sơ");
        ListItemSettingHelper.SetSubtitle(coverSavingItem, Utils.IsEnglish()
            ? "Restore the cover save action and screenshots"
            : "Khôi phục nút lưu ảnh và chụp màn hình ảnh bìa");
        ListItemSettingHelper.SetSwitch(coverSavingItem, Config.getEnableCoverSaving());
        ListItemSettingHelper.SetCheckedChangeListener(coverSavingItem, Config::setEnableCoverSaving);

    
        separator = createSeparator(context);
        rootLayout.addView(separator);
        headerTitle = createHeaderTitle(context);
        headerTitle.setText(Utils.IsEnglish() ? "Group" : "Nhóm");
        rootLayout.addView(headerTitle);
        listItemSetting = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(listItemSetting);
        ListItemSettingHelper.SetIDTracking(listItemSetting, "");
        ListItemSettingHelper.ShowDivider(listItemSetting);
        ListItemSettingHelper.SetTitle(listItemSetting, Utils.IsEnglish() ? "Fake my role in groups" : "Giả mạo vai trò trong nhóm");
        ListItemSettingHelper.SetSwitch(listItemSetting, Config.getEnableFakeGroupRole());
        ListItemSettingHelper.SetCheckedChangeListener(listItemSetting, Config::setEnableFakeGroupRole);
        RelativeLayout listItemSettingFakeRole = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(listItemSettingFakeRole);
        ListItemSettingHelper.SetIDTracking(listItemSettingFakeRole, "");
        ListItemSettingHelper.HideDivider(listItemSettingFakeRole);
        ListItemSettingHelper.SetTitle(listItemSettingFakeRole, Utils.IsEnglish() ? "Fake role" : "Vai trò giả mạo");
        String fakeRole = getFakeRoleName(Config.getFakeGroupRoleLevel());
        ListItemSettingHelper.SetStateSetting(listItemSettingFakeRole, fakeRole);
        ListItemSettingHelper.SetOnClickListener(listItemSettingFakeRole, v ->
        {
            int roleLevel = Config.getFakeGroupRoleLevel();
            roleLevel = (roleLevel + 1) % 2;
            Config.setFakeGroupRoleLevel(roleLevel);
            String newFakeRole = getFakeRoleName(roleLevel);
            try
            {
                ListItemSettingHelper.SetStateSetting(listItemSettingFakeRole, newFakeRole);
            }
            catch (Exception e)
            {
                Logger.e(e);
            }
        });

    
        separator = createSeparator(context);
        rootLayout.addView(separator);
        headerTitle = createHeaderTitle(context);
        headerTitle.setText(Utils.IsEnglish() ? "Sticker pack" : "Bộ sticker");
        rootLayout.addView(headerTitle);
        listItemSetting = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(listItemSetting);
        ListItemSettingHelper.SetIDTracking(listItemSetting, "");
        ListItemSettingHelper.HideDivider(listItemSetting);
        ListItemSettingHelper.SetTitle(listItemSetting, Utils.IsEnglish() ? "Allow sharing hidden sticker packs" : "Cho phép chia sẻ bộ sticker ẩn");
        ListItemSettingHelper.SetSwitch(listItemSetting, Config.getEnableShareHiddenStickerPack());
        ListItemSettingHelper.SetCheckedChangeListener(listItemSetting, Config::setEnableShareHiddenStickerPack);

        
        separator = createSeparator(context);
        rootLayout.addView(separator);
        headerTitle = createHeaderTitle(context);
        headerTitle.setText(Utils.IsEnglish() ? "Bottom row" : "Thanh dưới");
        rootLayout.addView(headerTitle);
        listItemSetting = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(listItemSetting);
        ListItemSettingHelper.SetIDTracking(listItemSetting, "");
        ListItemSettingHelper.ShowDivider(listItemSetting);
        ListItemSettingHelper.SetTitle(listItemSetting, Utils.IsEnglish() ? "Enable bottom row customization" : "Kích hoạt tuỳ chỉnh thanh dưới");
        ListItemSettingHelper.SetSwitch(listItemSetting, Config.getEnableCustomizeBottomRow());
        ListItemSettingHelper.SetCheckedChangeListener(listItemSetting, Config::setEnableCustomizeBottomRow);
        listItemSetting = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(listItemSetting);
        ListItemSettingHelper.SetIDTracking(listItemSetting, "");
        ListItemSettingHelper.ShowDivider(listItemSetting);
        ListItemSettingHelper.SetTitle(listItemSetting, Utils.IsEnglish() ? "Hide Discovery tab" : "Ẩn tab Khám Phá");
        ListItemSettingHelper.SetSwitch(listItemSetting, Config.getHideDiscoveryTab());
        ListItemSettingHelper.SetCheckedChangeListener(listItemSetting, Config::setHideDiscoveryTab);
        listItemSetting = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(listItemSetting);
        ListItemSettingHelper.SetIDTracking(listItemSetting, "");
        ListItemSettingHelper.ShowDivider(listItemSetting);
        ListItemSettingHelper.SetTitle(listItemSetting, Utils.IsEnglish() ? "Hide Newsfeed tab" : "Ẩn tab Tường Nhà");
        ListItemSettingHelper.SetSwitch(listItemSetting, Config.getHideNewsFeedTab());
        ListItemSettingHelper.SetCheckedChangeListener(listItemSetting, Config::setHideNewsFeedTab);
        listItemSetting = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(listItemSetting);
        ListItemSettingHelper.SetIDTracking(listItemSetting, "");
        ListItemSettingHelper.ShowDivider(listItemSetting);
        ListItemSettingHelper.SetTitle(listItemSetting, Utils.IsEnglish() ? "Show Groups tab" : "Hiện tab Nhóm");
        ListItemSettingHelper.SetSwitch(listItemSetting, Config.getShowGroupsTab());
        ListItemSettingHelper.SetCheckedChangeListener(listItemSetting, Config::setShowGroupsTab);
        listItemSetting = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(listItemSetting);
        ListItemSettingHelper.SetIDTracking(listItemSetting, "");
        ListItemSettingHelper.HideDivider(listItemSetting);
        ListItemSettingHelper.SetTitle(listItemSetting, Utils.IsEnglish() ? "Show More tab" : "Hiện tab Thêm");
        ListItemSettingHelper.SetSwitch(listItemSetting, Config.getShowMoreTab());
        ListItemSettingHelper.SetCheckedChangeListener(listItemSetting, Config::setShowMoreTab);


        separator = createSeparator(context);
        rootLayout.addView(separator);
        headerTitle = createHeaderTitle(context);
        headerTitle.setText(Utils.IsEnglish() ? "Configuration backup & restore" : "Sao lưu & Khôi phục cấu hình");
        rootLayout.addView(headerTitle);

        listItemSetting = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(listItemSetting);
        ListItemSettingHelper.SetIDTracking(listItemSetting, "");
        ListItemSettingHelper.ShowDivider(listItemSetting);
        ListItemSettingHelper.SetTitle(listItemSetting, Utils.IsEnglish() ? "Export configuration file" : "Xuất tệp cấu hình");
        ListItemSettingHelper.SetSubtitle(listItemSetting, Utils.IsEnglish() ? "Save to Downloads/ZaloXposed" : "Lưu vào thư mục Downloads/ZaloXposed");
        ListItemSettingHelper.SetOnClickListener(listItemSetting, v -> exportConfigFile(context));

        listItemSetting = ListItemSettingHelper.CreateNew(context);
        rootLayout.addView(listItemSetting);
        ListItemSettingHelper.SetIDTracking(listItemSetting, "");
        ListItemSettingHelper.HideDivider(listItemSetting);
        ListItemSettingHelper.SetTitle(listItemSetting, Utils.IsEnglish() ? "Import configuration from file" : "Nhập cấu hình từ tệp");
        ListItemSettingHelper.SetSubtitle(listItemSetting, Utils.IsEnglish() ? "Choose a JSON file" : "Chọn tệp JSON từ bộ nhớ");
        ListItemSettingHelper.SetOnClickListener(listItemSetting, v -> openFilePickerForImport(context));


        Object restartButton = ZButtonHelper.CreateNew(context);
        rootLayout.addView((View)restartButton);
        ZButtonHelper.SetText(restartButton, Utils.IsEnglish() ? "Restart app" : "Khởi động lại ứng dụng");
        ZButtonHelper.SetOnClickListener(restartButton, v -> restartApp(context));
        LinearLayout.LayoutParams restartLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int pad16 = dp(context, 16);
        restartLp.setMargins(pad16, pad16, pad16, 0);
        ((View)restartButton).setLayoutParams(restartLp);


        for (int i = 0; i < 5; i++)
        {
            separator = createSeparator(context);
            rootLayout.addView(separator);
        }
    }

    private static void refreshBackgroundSourceUi()
    {
        if (backgroundSourceUiRefresher == null)
            return;
        try
        {
            backgroundSourceUiRefresher.run();
        }
        catch (Exception e)
        {
            Logger.e(e);
        }
    }

    private static void updateBackupIntervalRow(RelativeLayout item, int hours) throws Exception
    {
        String state;
        if (Utils.IsEnglish())
            state = hours + (hours == 1 ? " hour" : " hours");
        else
            state = hours + " giờ";
        ListItemSettingHelper.SetStateSetting(item, state);
    }

    private static void updateBackupIntervalAvailability(RelativeLayout item, boolean enabled) throws Exception
    {
        ListItemSettingHelper.SetEnabled(item, enabled);
        item.setAlpha(enabled ? 1f : 0.5f);
    }

    private static int nextBackupIntervalHours(int current)
    {
        for (int i = 0; i < BACKUP_INTERVAL_OPTIONS.length; i++)
        {
            if (BACKUP_INTERVAL_OPTIONS[i] == current)
                return BACKUP_INTERVAL_OPTIONS[(i + 1) % BACKUP_INTERVAL_OPTIONS.length];
        }
        return BACKUP_INTERVAL_OPTIONS[0];
    }

    private static void updatePasscodeGraceRow(RelativeLayout item, int milliseconds) throws Exception
    {
        int minutes = milliseconds / 60000;
        String state;
        if (Utils.IsEnglish())
        {
            if (minutes < 60)
                state = minutes + (minutes == 1 ? " minute" : " minutes");
            else
                state = (minutes / 60) + ((minutes / 60) == 1 ? " hour" : " hours");
        }
        else
        {
            if (minutes < 60)
                state = minutes + " phút";
            else
                state = (minutes / 60) + " giờ";
        }
        ListItemSettingHelper.SetStateSetting(item, state);
    }

    private static void updatePasscodeGraceAvailability(RelativeLayout item, boolean enabled) throws Exception
    {
        ListItemSettingHelper.SetEnabled(item, enabled);
        item.setAlpha(enabled ? 1f : 0.5f);
    }

    private static int nextPasscodeGraceMs(int current)
    {
        int[] options = new int[]{60000, 300000, 900000, 1800000, 3600000};
        for (int i = 0; i < options.length; i++)
        {
            if (options[i] == current)
                return options[(i + 1) % options.length];
        }
        return options[1];
    }

    private static void refreshCustomFontUi()
    {
        if (customFontUiRefresher == null)
            return;
        try
        {
            customFontUiRefresher.run();
        }
        catch (Exception e)
        {
            Logger.e(e);
        }
    }

    private void updateCustomFontItems(Context context, RelativeLayout enableItem, RelativeLayout fontItem, RelativeLayout resetItem)
    {
        try
        {
            boolean hasFont = CustomFont.hasFont(context);
            String displayName = CustomFont.getDisplayName(context);
            ListItemSettingHelper.SetSwitch(enableItem, Config.getCustomFontEnabled());
            ListItemSettingHelper.SetStateSetting(fontItem,
                hasFont && !displayName.isEmpty()
                    ? displayName
                    : (Utils.IsEnglish() ? "Not selected" : "Chưa chọn"));
            ListItemSettingHelper.SetSubtitle(fontItem, hasFont
                ? (Utils.IsEnglish()
                    ? "Tap to choose a different .ttf, .otf or .ttc file"
                    : "Chạm để chọn tệp .ttf, .otf hoặc .ttc khác")
                : (Utils.IsEnglish()
                    ? "Tap to choose a .ttf, .otf or .ttc file"
                    : "Chạm để chọn tệp .ttf, .otf hoặc .ttc"));
            ListItemSettingHelper.SetEnabled(resetItem, hasFont);
            resetItem.setAlpha(hasFont ? 1f : 0.5f);
        }
        catch (Exception e)
        {
            Logger.e(e);
        }
    }

    private void updateBackgroundSourceItems(Context context, RelativeLayout assetsItem, RelativeLayout pickerItem, RelativeLayout folderItem)
    {
        try
        {
            String source = Config.getBackgroundSource();
            String inUse = Utils.IsEnglish() ? "In use" : "Đang dùng";
            ListItemSettingHelper.SetStateSetting(assetsItem, Config.BG_SOURCE_ASSETS.equals(source) ? inUse : "");
            ListItemSettingHelper.SetStateSetting(pickerItem, Config.BG_SOURCE_PICKER.equals(source) ? inUse : "");
            ListItemSettingHelper.SetStateSetting(folderItem, Config.BG_SOURCE_FOLDER.equals(source) ? inUse : "");

            boolean hasPickedImage = BackgroundUtils.hasPickedImage(context);
            String pickerSubtitle;
            if (Utils.IsEnglish())
                pickerSubtitle = hasPickedImage ? "Photo Picker - tap to choose another image" : "Photo Picker - tap to choose an image";
            else
                pickerSubtitle = hasPickedImage ? "Photo Picker - chạm để chọn ảnh khác" : "Photo Picker - chạm để chọn ảnh";
            ListItemSettingHelper.SetSubtitle(pickerItem, pickerSubtitle);

            BackgroundUtils.FolderAccess access = BackgroundUtils.getFolderAccess(context);
            String folderSubtitle = BackgroundUtils.FOLDER_DISPLAY_PATH;
            if (access == BackgroundUtils.FolderAccess.NOT_DECLARED)
                folderSubtitle += Utils.IsEnglish()
                    ? " - unavailable: Zalo does not declare storage access"
                    : " - không khả dụng: Zalo không khai báo quyền bộ nhớ";
            else if (access != BackgroundUtils.FolderAccess.GRANTED)
                folderSubtitle += Utils.IsEnglish()
                    ? " - tap to grant storage access"
                    : " - chạm để cấp quyền bộ nhớ";
            ListItemSettingHelper.SetSubtitle(folderItem, folderSubtitle);

            boolean folderUsable = access != BackgroundUtils.FolderAccess.NOT_DECLARED;
            ListItemSettingHelper.SetEnabled(folderItem, folderUsable);
            folderItem.setAlpha(folderUsable ? 1f : 0.5f);
        }
        catch (Exception e)
        {
            Logger.e(e);
        }
    }

    private void selectBackgroundSource(Context context, String source)
    {
        boolean changed = !source.equals(Config.getBackgroundSource());
        Config.setBackgroundSource(source);
        refreshBackgroundSourceUi();
        if (changed)
            showToast(context, Utils.IsEnglish()
                ? "Background source changed. Restart Zalo to apply."
                : "Đã đổi nguồn hình nền. Khởi động lại Zalo để áp dụng.");
    }

    private void openBackgroundPhotoPicker(Context context)
    {
        Activity activity = BackgroundUtils.findActivity(context);
        if (activity == null)
        {
            showToast(context, Utils.IsEnglish() ? "Cannot open Photo Picker!" : "Không thể mở Photo Picker!");
            return;
        }
        try
        {
            Intent intent = new Intent("android.provider.action.PICK_IMAGES");
            intent.setType("image/*");
            activity.startActivityForResult(intent, BackgroundUtils.REQUEST_CODE_PICK_IMAGE);
            return;
        }
        catch (ActivityNotFoundException e)
        {
            Logger.i("Android Photo Picker not available, using document picker");
        }
        catch (Exception e)
        {
            Logger.e(e);
        }
        try
        {
            Intent fallback = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            fallback.addCategory(Intent.CATEGORY_OPENABLE);
            fallback.setType("image/*");
            activity.startActivityForResult(fallback, BackgroundUtils.REQUEST_CODE_PICK_IMAGE);
        }
        catch (Exception e)
        {
            Logger.e(e);
            showToast(context, Utils.IsEnglish() ? "No image picker available!" : "Không có trình chọn ảnh khả dụng!");
        }
    }

    private void openFontPicker(Context context)
    {
        Activity activity = BackgroundUtils.findActivity(context);
        if (activity == null)
        {
            showToast(context, Utils.IsEnglish() ? "Cannot open the font picker!" : "Không thể mở trình chọn phông chữ!");
            return;
        }
        try
        {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "font/ttf",
                "font/otf",
                "font/collection",
                "application/x-font-ttf",
                "application/x-font-opentype",
                "application/vnd.ms-opentype",
                "application/octet-stream"
            });
            activity.startActivityForResult(intent, REQUEST_CODE_PICK_FONT);
        }
        catch (Exception e)
        {
            Logger.e(e);
            showToast(context, Utils.IsEnglish() ? "No font picker available!" : "Không có trình chọn phông chữ khả dụng!");
        }
    }

    private void handlePickedFont(Context context, Uri uri)
    {
        new Thread(() ->
        {
            boolean success = CustomFont.importFont(context, uri);
            new Handler(Looper.getMainLooper()).post(() ->
            {
                if (success)
                {
                    refreshCustomFontUi();
                    showToast(context, Utils.IsEnglish()
                        ? "Font selected. Restart Zalo to apply everywhere."
                        : "Đã chọn phông chữ. Khởi động lại Zalo để áp dụng toàn bộ.");
                }
                else
                    showToast(context, Utils.IsEnglish()
                        ? "Invalid or unreadable font file!"
                        : "Tệp phông chữ không hợp lệ hoặc không đọc được!");
            });
        }, "ZaloXposed-FontPicker").start();
    }

    private void onFolderBackgroundClicked(Context context) {
        try {
            BackgroundUtils.FolderAccess access =
                    BackgroundUtils.getFolderAccess(context);
    
            if (access == BackgroundUtils.FolderAccess.NOT_DECLARED) {
                showToast(context, Utils.IsEnglish()
                        ? "Required storage permission is not declared."
                        : "Ứng dụng chưa khai báo quyền truy cập bộ nhớ cần thiết.");
                return;
            }
    
            if (access != BackgroundUtils.FolderAccess.GRANTED) {
                showToast(context, Utils.IsEnglish()
                        ? "Please grant access to the background folder first."
                        : "Vui lòng cấp quyền truy cập thư mục hình nền trước.");
    
                boolean requested = BackgroundUtils.requestFolderAccess(context);
    
                if (!requested) {
                    showToast(context, Utils.IsEnglish()
                            ? "Cannot request folder access. Please check app settings."
                            : "Không thể yêu cầu quyền. Vui lòng kiểm tra cài đặt ứng dụng.");
                }
                return;
            }
    
            File folder = new File(BackgroundUtils.FOLDER_DISPLAY_PATH);
    
            if (!folder.exists()) {
                if (!folder.mkdirs() && !folder.isDirectory()) {
                    showToast(context, Utils.IsEnglish()
                            ? "Cannot create folder: " + folder.getAbsolutePath()
                            : "Không thể tạo thư mục: " + folder.getAbsolutePath());
                    return;
                }
            }
    
            if (!folder.isDirectory() || !folder.canRead()) {
                showToast(context, Utils.IsEnglish()
                        ? "Cannot read the background folder."
                        : "Không thể đọc thư mục hình nền.");
                return;
            }
    
            if (BackgroundUtils.getFolderImages().isEmpty()) {
                showToast(context, Utils.IsEnglish()
                        ? "Folder is ready: " + folder.getAbsolutePath()
                            + ". Add images to use this source."
                        : "Thư mục đã sẵn sàng: " + folder.getAbsolutePath()
                            + ". Hãy thêm ảnh để sử dụng.");
                return;
            }
    
            selectBackgroundSource(context, Config.BG_SOURCE_FOLDER);
    
        } catch (SecurityException e) {
            Logger.e(e);
            showToast(context, Utils.IsEnglish()
                    ? "Storage access denied."
                    : "Không có quyền truy cập bộ nhớ.");
        } catch (Throwable e) {
            Logger.e(e);
            showToast(context, Utils.IsEnglish()
                    ? "Cannot access the background folder."
                    : "Không thể truy cập thư mục hình nền.");
        }
    }

    private void handlePickedBackground(Context context, Uri uri)
    {
        new Thread(() ->
        {
            boolean success = BackgroundUtils.savePickedImage(context, uri);
            new Handler(Looper.getMainLooper()).post(() ->
            {
                if (success)
                {
                    Config.setBackgroundSource(Config.BG_SOURCE_PICKER);
                    refreshBackgroundSourceUi();
                    showToast(context, Utils.IsEnglish()
                        ? "Image selected. Restart Zalo to apply."
                        : "Đã chọn ảnh. Khởi động lại Zalo để áp dụng.");
                }
                else
                    showToast(context, Utils.IsEnglish() ? "Failed to load the selected image!" : "Không thể đọc ảnh đã chọn!");
            });
        }, "ZaloXposed-BgPicker").start();
    }

    private void showToast(Context context, String message)
    {
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
    }

    private void handleFolderAccessResult(Activity activity) {
        if (activity == null)
            return;
    
        try {
            if (BackgroundUtils.onFolderAccessResult(activity)) {
                Config.setBackgroundSource(Config.BG_SOURCE_FOLDER);
                refreshBackgroundSourceUi();
    
                showToast(activity, Utils.IsEnglish()
                    ? "Storage access granted. Restart Zalo to apply."
                    : "Đã cấp quyền và thư mục đã sẵn sàng. Khởi động lại Zalo để áp dụng.");
            } else {
                refreshBackgroundSourceUi();
    
                showToast(activity, Utils.IsEnglish()
                    ? "Storage access was not granted."
                    : "Chưa được cấp quyền truy cập thư mục.");
            }
        } catch (Throwable e) {
            Logger.e(e);
        }
    }

    private boolean isStoragePermission(String[] permissions)
    {
        if (permissions == null)
            return false;
        for (String permission : permissions)
        {
            if (Manifest.permission.READ_EXTERNAL_STORAGE.equals(permission)
                || Manifest.permission.READ_MEDIA_IMAGES.equals(permission)
                || Manifest.permission.MANAGE_EXTERNAL_STORAGE.equals(permission))
                return true;
        }
        return false;
    }

    private void hookActivityForPermissionResult()
    {
        try
        {
            Method method = Activity.class.getDeclaredMethod("onRequestPermissionsResult", int.class, String[].class, int[].class);
            Logger.i("Hooking: " + method);
            module.hook(method).setPriority(10).intercept(chain ->
            {
                int requestCode = (int)chain.getArg(0);
                String[] permissions = (String[])chain.getArg(1);
                if (requestCode == BackgroundUtils.REQUEST_CODE_PERMISSION
                    && isStoragePermission(permissions)) {
                    Activity activity = (Activity) chain.getThisObject();
                
                    handleFolderAccessResult(activity);
                }
                return chain.proceed();
            });
        }
        catch (Exception e)
        {
            Logger.e(e);
        }
    }

    @Override
    public void hook() throws Throwable
    {
        ResourceHook.map(CUSTOM_DRAWABLE_ID, "extension_24px", "zds_oic_premium_crown_color_24");
        ListItemSettingHelper.Init(classLoader);
        ZButtonHelper.Init(classLoader);
        TabMeSettingItemHelper.Init(bridge, classLoader);
        hookTabMeView();
        hookSettingPrivateView();
        hookActivityForFilePicker();
        hookActivityForPermissionResult();
        List<MethodData> methods = bridge.findMethod(FindMethod.create()
            .matcher(MethodMatcher.create()
                .declaredClass("com.zing.zalo.ui.maintab.me.TabMeView")
                .modifiers(Modifier.PUBLIC | Modifier.FINAL)
                .returnType("boolean")
                .addUsingString("features@tabme@fullzins@enable", StringMatchType.Equals)
                .addUsingNumber(0)
                .addUsingNumber(1)
            ));
        if (methods.isEmpty())
        {
            Logger.e("Target method not found 5");
            return;
        }
        for (MethodData methodData : methods)
        {
            Method method = methodData.getMethodInstance(classLoader);
            Logger.i("Hooking: " + method);
            module.hook(method).setPriority(10).intercept(chain -> false);
        }
    }

    private void exportConfigFile(Context context)
    {
        try
        {
            String jsonString = Config.getConfigJsonString();
            boolean success = false;
            String exportedPath = "";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            {
                ContentResolver resolver = context.getContentResolver();
                Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
                String fileName = "zaloxposed_config.json";
                String relativePath = android.os.Environment.DIRECTORY_DOWNLOADS + "/ZaloXposed/";
                try
                {
                    String selection = MediaStore.MediaColumns.DISPLAY_NAME + " = ?";
                    String[] selectionArgs = new String[]{fileName};
                    resolver.delete(collection, selection, selectionArgs);
                }
                catch (Exception ignored) { }
                android.content.ContentValues contentValues = new android.content.ContentValues();
                contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
                contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath);
                Uri uri = resolver.insert(collection, contentValues);
                if (uri != null)
                {
                    try (java.io.OutputStream os = resolver.openOutputStream(uri, "rwt"))
                    {
                        if (os != null)
                        {
                            os.write(jsonString.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                            os.flush();
                            success = true;
                            exportedPath = "Download/ZaloXposed/" + fileName;
                        }
                    }
                }
            }
            if (!success)
            {
                File downloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
                File exportDir = new File(downloadDir, "ZaloXposed");
                if (!exportDir.exists())
                    exportDir.mkdirs();
                File targetFile = new File(exportDir, "zaloxposed_config.json");
                success = Config.exportToFile(targetFile);
                if (success)
                    exportedPath = targetFile.getAbsolutePath();
            }
            boolean finalSuccess = success;
            String finalPath = exportedPath;
            new Handler(Looper.getMainLooper()).post(() ->
            {
                if (finalSuccess)
                    Toast.makeText(context, Utils.IsEnglish() ? "Exported config file to: " + finalPath : "Đã xuất tệp cấu hình ra: " + finalPath, Toast.LENGTH_LONG).show();
                else
                    Toast.makeText(context, Utils.IsEnglish() ? "Export failed (permission denied)!" : "Xuất cấu hình thất bại (không có quyền bộ nhớ)!", Toast.LENGTH_SHORT).show();
            });
        }
        catch (Exception e)
        {
            Logger.e(e);
        }
    }

    private void openFilePickerForImport(Context context)
    {
        try
        {
            Activity activity = null;
            Context ctx = context;
            while (ctx instanceof ContextWrapper)
            {
                if (ctx instanceof Activity)
                {
                    activity = (Activity)ctx;
                    break;
                }
                ctx = ((ContextWrapper)ctx).getBaseContext();
            }
            if (activity == null)
            {
                new Handler(Looper.getMainLooper()).post(() ->
                {
                    Toast.makeText(context, Utils.IsEnglish() ? "Cannot find activity to open file picker!" : "Không tìm thấy Activity để mở trình chọn file!", Toast.LENGTH_SHORT).show();
                });
                return;
            }
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            String[] extraMimeTypes = new String[]{"application/json", "text/*"};
            intent.putExtra(Intent.EXTRA_MIME_TYPES, extraMimeTypes);
            activity.startActivityForResult(intent, REQUEST_CODE_IMPORT_CONFIG);
        }
        catch (Exception e)
        {
            Logger.e(e);
            try
            {
                // Fallback to direct ACTION_GET_CONTENT
                if (context instanceof Activity)
                {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.setType("*/*");
                    ((Activity)context).startActivityForResult(intent, REQUEST_CODE_IMPORT_CONFIG);
                }
            }
            catch (Exception ex)
            {
                Logger.e(ex);
            }
        }
    }

    private void hookActivityForFilePicker()
    {
        try
        {
            Method onActivityResultMethod = Activity.class.getDeclaredMethod("onActivityResult", int.class, int.class, Intent.class);
            Logger.i("Hooking: " + onActivityResultMethod);
            module.hook(onActivityResultMethod).setPriority(10).intercept(chain ->
            {
                int requestCode = (int)chain.getArg(0);
                if (requestCode == REQUEST_CODE_IMPORT_CONFIG)
                {
                    int resultCode = (int)chain.getArg(1);
                    Intent data = (Intent)chain.getArg(2);
                    Activity activity = (Activity)chain.getThisObject();
                    if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null)
                        handleImportUri(activity, data.getData());
                    return null;
                }
                if (requestCode == REQUEST_CODE_PICK_FONT)
                {
                    int resultCode = (int)chain.getArg(1);
                    Intent data = (Intent)chain.getArg(2);
                    Activity activity = (Activity)chain.getThisObject();
                    if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null)
                        handlePickedFont(activity, data.getData());
                    return null;
                }
                if (requestCode == BackgroundUtils.REQUEST_CODE_PICK_IMAGE)
                {
                    int resultCode = (int)chain.getArg(1);
                    Intent data = (Intent)chain.getArg(2);
                    Activity activity = (Activity)chain.getThisObject();
                    if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null)
                        handlePickedBackground(activity, data.getData());
                    return null;
                }
                if (requestCode == BackgroundUtils.REQUEST_CODE_MANAGE_STORAGE) {
                    Activity activity = (Activity) chain.getThisObject();
                    handleFolderAccessResult(activity);
                    return null;
                }
                if (requestCode == CallRecordingsDialog.REQUEST_CODE_EXPORT)
                {
                    int resultCode = (int)chain.getArg(1);
                    Intent data = (Intent)chain.getArg(2);
                    Activity activity = (Activity)chain.getThisObject();
                    CallRecordingsDialog.handleActivityResult(activity, resultCode, data);
                    return null;
                }
                return chain.proceed();
            });
        }
        catch (Exception e)
        {
            Logger.e(e);
        }
    }

    private void handleImportUri(Context context, Uri uri)
    {
        try
        {
            boolean success = false;
            try (java.io.InputStream inputStream = context.getContentResolver().openInputStream(uri))
            {
                if (inputStream != null)
                    success = Config.importFromStream(inputStream);
            }
            boolean finalSuccess = success;
            new Handler(Looper.getMainLooper()).post(() ->
            {
                if (finalSuccess)
                    Toast.makeText(context, Utils.IsEnglish() ? "Configuration imported successfully, restart app to apply." : "Đã nhập cấu hình, khởi động lại app để áp dụng.", Toast.LENGTH_LONG).show();
                else
                    Toast.makeText(context, Utils.IsEnglish() ? "Failed to read or parse configuration file!" : "Không thể đọc tệp hoặc định dạng JSON không hợp lệ!", Toast.LENGTH_SHORT).show();
            });
        }
        catch (Exception e)
        {
            Logger.e(e);
            new Handler(Looper.getMainLooper()).post(() ->
            {
                Toast.makeText(context, Utils.IsEnglish() ? "Error importing: " + e.getMessage() : "Lỗi khi nhập tệp: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void restartApp(Context context)
    {
        try
        {
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            if (intent == null)
                return;
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            context.startActivity(intent);
            if (Activity.class.isAssignableFrom(context.getClass()))
                ((Activity)context).finish();
            new Handler(Looper.getMainLooper()).postDelayed(() -> Runtime.getRuntime().exit(0), 0);
        }
        catch (Exception e)
        {
            Logger.e(e);
        }
    }

    @SuppressLint("PrivateApi")
    @SuppressWarnings("unchecked")
    private void hookTabMeView() throws Exception
    {
        List<MethodData> methods = bridge.findMethod(FindMethod.create()
            .matcher(MethodMatcher.create()
                .declaredClass("com.zing.zalo.ui.maintab.me.TabMeView")
                .modifiers(Modifier.STATIC)
                .returnType("java.util.ArrayList")
                .addUsingString("tab_me_privacy", StringMatchType.Equals)
                .addUsingString("tab_me_tool_storage", StringMatchType.Equals)
                .addUsingString("tab_me_account_and_security", StringMatchType.Equals)
                .addUsingString("tab_me_business_tools", StringMatchType.Equals)
            ));
        if (methods.isEmpty())
        {
            Logger.e("Target method not found 1");
            return;
        }
        Method method = methods.get(0).getMethodInstance(classLoader);
        Logger.i("Hooking: " + method);
        module.hook(method).setPriority(10).intercept(chain ->
        {
            Object result = chain.proceed();
            if (!(result instanceof ArrayList))
                return result;
            try
            {
                String moduleName = MorpheConstants.getModuleName();
                Object separatorItem = null;
                ArrayList<Object> items = (ArrayList<Object>)result;
                for (int i = items.size() - 1; i >= 0; i--)
                {
                    Object item = items.get(i);
                    if (!item.toString().contains("SettingData(id="))
                    {
                        separatorItem = Utils.Clone(item);
                        break;
                    }
                }
                Object customItem = TabMeSettingItemHelper.CreateNew(SettingItemID.PRIVACY, CUSTOM_DRAWABLE_ID, moduleName);
                TabMeSettingItemHelper.SetDescription(customItem, Utils.IsEnglish() ? moduleName + " settings" : "Cài đặt " + moduleName);
                TabMeSettingItemHelper.SetTracking(customItem, CUSTOM_ITEM_MARKER);
                items.add(2, customItem);
                items.add(3, separatorItem);
                return items;
            }
            catch (Throwable t)
            {
                Logger.e(t);
            }
            return result;
        });

        methods = bridge.findMethod(FindMethod.create()
            .matcher(MethodMatcher.create()
                .modifiers(Modifier.PUBLIC)
                .paramCount(1)
                .returnType("void")
                .addUsingString("settingData", StringMatchType.Equals)
                .addUsingString("STR_SOURCE_START_VIEW", StringMatchType.Equals)
                .addUsingString("EXTRA_FEATURE_ID", StringMatchType.Equals)
                .addUsingString("features@business_account@zinstant@business_tool@enable", StringMatchType.Equals)
                .addUsingString("EXTRA_SOURCE_OPEN_MA", StringMatchType.Equals)
                .addUsingString("features@business_account@item_promotion@open_action@action_name", StringMatchType.Equals)
                .addUsingString("tab_me_open_item", StringMatchType.Equals)
                // .addUsingString("https://h5.zdn.vn/zapps/220259427665569271/", StringMatchType.Equals)
            ));
        if (methods.isEmpty())
        {
            Logger.e("Target onClick method not found");
            return;
        }
        method = methods.get(0).getMethodInstance(classLoader);
        Logger.i("Hooking: " + method);
        module.hook(method).setPriority(10).intercept(chain ->
        {
            Object tabMeItem = chain.getArg(0);
            if (tabMeItem == null)
                return chain.proceed();
            try
            { 
                isOpenZaloXposedSettings1 = isOpenZaloXposedSettings2 = CUSTOM_ITEM_MARKER.equals(TabMeSettingItemHelper.GetTracking(tabMeItem));
            }
            catch (Throwable t)
            {
                Logger.e(t);
            }
            return chain.proceed();
        });
    }

    private void hookSettingPrivateView() throws Exception
    {
        headerTextViewClass = Class.forName("com.zing.zalo.ui.widget.RobotoTextView", false, classLoader);
        List<MethodData> methods = bridge.findMethod(FindMethod.create()
            .matcher(MethodMatcher.create()
                .declaredClass("com.zing.zalo.ui.settings.SettingPrivateV2View")
                .modifiers(Modifier.PUBLIC | Modifier.FINAL)
                .returnType("android.view.View")
                .paramCount(3)
                .paramTypes("android.view.LayoutInflater", "android.widget.LinearLayout", "android.os.Bundle")
                .addUsingString("inflater", StringMatchType.Equals)
                .addUsingString("EXTRA_CURRENT_SEEN_SETTING", StringMatchType.Equals)));
        if (methods.isEmpty())
        {
            Logger.e("Target method not found 2");
            return;
        }
        Method method = methods.get(0).getMethodInstance(classLoader);
        Logger.i("Hooking: " + method);
        module.hook(method).setPriority(10).intercept(chain ->
        {
            Object result = chain.proceed();
            if (isOpenZaloXposedSettings1 || isOpenZaloXposedSettings2)
                rootLayout = (LinearLayout)chain.getArg(1);
            else 
            {
                rootLayout = null;
                backgroundSourceUiRefresher = null;
                customFontUiRefresher = null;
            }
            return result;
        });
        methods = bridge.findMethod(FindMethod.create()
            .matcher(MethodMatcher.create()
                .declaredClass("com.zing.zalo.ui.settings.SettingPrivateV2View")
                .modifiers(Modifier.PUBLIC | Modifier.FINAL)
                .returnType("void")
                .paramCount(0)
                .addUsingString("view_birthday", StringMatchType.Equals)
                .addUsingString("recently_online_status", StringMatchType.Equals)
                .addUsingString("display_seen_status", StringMatchType.Equals)
                .addUsingString("accept_stranger_call", StringMatchType.Equals)
                .addUsingString("allow_auto_friend_click", StringMatchType.Equals)
                .addUsingString("SETTING_PRIVACY_APP", StringMatchType.Equals)
            ));
        if (methods.isEmpty())
        {
            Logger.e("Target method not found 3");
            return;
        }
        method = methods.get(0).getMethodInstance(classLoader);
        Logger.i("Hooking: " + method);
        module.hook(method).setPriority(10).intercept(chain ->
        {
            Object result = chain.proceed();
            if (!isOpenZaloXposedSettings1)
                return result;
            if (rootLayout == null)
                return result;
            isOpenZaloXposedSettings1 = false; 
            templateHeader = null;
            templateSeparator = null;
            try
            {
                loadTemplates();
                createCustomMenu();
            }
            catch (Exception e)
            {
                Logger.e(e);
            }
            return result;
        });
        methods = bridge.findMethod(FindMethod.create()
            .matcher(MethodMatcher.create()
                .declaredClass("com.zing.zalo.ui.settings.SettingPrivateV2View")
                .modifiers(Modifier.PUBLIC | Modifier.FINAL)
                .returnType("void")
                .paramCount(0)
                .addUsingString("getString(...)", StringMatchType.Equals)
                // .addUsingField(FieldMatcher.create().name("str_title_setting_private"))
                .addInvoke(MethodMatcher.create()
                    .declaredClass("com.zing.zalo.zdesign.component.header.ZdsActionBar")
                    .modifiers(Modifier.PUBLIC | Modifier.FINAL)
                    .returnType("void")
                    .name("setMiddleTitle")
                )
            ));
        if (methods.isEmpty())
        {
            Logger.e("Target method not found 4");
            return;
        }
        method = methods.get(0).getMethodInstance(classLoader);
        // Field actionBarField = Utils.FindFieldByType(Class.forName("com.zing.zalo.ui.settings.SettingPrivateV2View", false, classLoader), "com.zing.zalo.zdesign.component.header.ZdsActionBar");
        Field actionBarField = Utils.FindFieldByType(Class.forName("com.zing.zalo.ui.zviews.ZaloViewNewActionBar", false, classLoader), "com.zing.zalo.zdesign.component.header.ZdsActionBar");
        Logger.i("Hooking: " + method);
        module.hook(method).setPriority(10).intercept(chain ->
        {
            Object result = chain.proceed();
            if (!isOpenZaloXposedSettings2)
                return result;
            if (actionBarField == null)
                return result;
            isOpenZaloXposedSettings2 = false;
            try
            {
                Object actionBar = actionBarField.get(chain.getThisObject());
                if (actionBar == null)
                    return result;
                Method setMiddleTitle = actionBar.getClass().getMethod("setMiddleTitle", String.class);
                String moduleName = MorpheConstants.getModuleName();
                setMiddleTitle.invoke(actionBar, Utils.IsEnglish() ? moduleName + " Settings" : "Cài đặt " + moduleName);
            }
            catch (Exception t)
            {
                Logger.e(t);
            }
            return result;
        });
    }

    private static String getFakeRoleName(int roleLevel)
    {
        return switch (roleLevel)
        {
            case 0 -> Utils.IsEnglish() ? "Admin" : "Phó nhóm";
            case 1 -> Utils.IsEnglish() ? "Owner" : "Trưởng nhóm";
            default -> "";
        };
    }

    private TextView createHeaderTitle(Context context) throws Exception
    {
        TextView headerTitle = (TextView)headerTextViewClass.getConstructor(Context.class).newInstance(context);
        headerTitle.setVisibility(View.VISIBLE);
        if (templateHeader != null)
        {
            headerTitle.setTextColor(templateHeader.getTextColors());
            headerTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, templateHeader.getTextSize());
            headerTitle.setTypeface(templateHeader.getTypeface());
            headerTitle.setPadding(templateHeader.getPaddingLeft(), templateHeader.getPaddingTop(), templateHeader.getPaddingRight(), templateHeader.getPaddingBottom());
            try
            {
                headerTitle.setBackground(Objects.requireNonNull(templateHeader.getBackground()
                    .getConstantState())
                    .newDrawable()
                    .mutate()
                );
            }
            catch (Exception ignored)
            {
                headerTitle.setBackgroundColor(Color.TRANSPARENT);
            }
            if (templateHeader.getLayoutParams() instanceof LinearLayout.LayoutParams)
            {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((LinearLayout.LayoutParams)templateHeader.getLayoutParams());
                headerTitle.setLayoutParams(lp);
            }
        }
        else
        {
            headerTitle.setTextColor(0xFF808080);
            headerTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            headerTitle.setBackgroundColor(Color.TRANSPARENT);
            int pad16 = dp(context, 16);
            int pad8 = dp(context, 8);
            headerTitle.setPadding(pad16, pad16, pad16, pad8);
        }
        return headerTitle;
    }

    private View createSeparator(Context context)
    {
        View separator = new View(context);
        if (templateSeparator != null)
        {
            try
            {
                separator.setBackground(Objects.requireNonNull(templateSeparator.getBackground()
                    .getConstantState())
                    .newDrawable()
                    .mutate()
                );
            }
            catch (Exception ignored) { }
            if (templateSeparator.getLayoutParams() instanceof LinearLayout.LayoutParams)
            {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((LinearLayout.LayoutParams)templateSeparator.getLayoutParams());
                separator.setLayoutParams(lp);
            }
        }
        else
        {
            separator.setBackgroundColor(Color.TRANSPARENT);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 8));
            separator.setLayoutParams(lp);
        }
        return separator;
    }

    private void loadTemplates()
    {
        // Find templates, then hide all existing children
        int count = rootLayout.getChildCount();
        for (int i = 0; i < count; i++)
        {
            View child = rootLayout.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE)
                continue;
            if (templateHeader == null && child.getClass() == headerTextViewClass)
                templateHeader = (TextView)child;
            else if (templateSeparator == null && child.getClass() == View.class)
                templateSeparator = child;
        }
        for (int i = 0; i < count; i++)
        {
            View child = rootLayout.getChildAt(i);
            child.setVisibility(View.GONE);
        }
    }

    private int dp(Context context, int value)
    {
        return (int)(value * context.getResources().getDisplayMetrics().density);
    }

    private void hideKeyboard(View view)
    {
        InputMethodManager imm = (InputMethodManager)view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null)
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void styleEditText(EditText editText, Context context, RelativeLayout templateItem)
    {
        int pad16 = dp(context, 16);
        int pad12 = dp(context, 12);
        editText.setPadding(pad16, pad12, pad16, pad12);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        try
        {
            TextView titleView = findTitleTextView(templateItem);
            if (titleView != null)
            {
                editText.setTextColor(titleView.getTextColors());
                editText.setHintTextColor(titleView.getTextColors());
            }
            editText.setBackground(Objects.requireNonNull(templateItem.getBackground().getConstantState())
                .newDrawable()
                .mutate());
        }
        catch (Exception ignored) { }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        editText.setLayoutParams(lp);
    }

    private TextView findTitleTextView(View view)
    {
        if (view instanceof TextView)
            return (TextView)view;
        if (view instanceof ViewGroup group)
        {
            for (int i = 0; i < group.getChildCount(); i++)
            {
                TextView found = findTitleTextView(group.getChildAt(i));
                if (found != null)
                    return found;
            }
        }
        return null;
    }
}