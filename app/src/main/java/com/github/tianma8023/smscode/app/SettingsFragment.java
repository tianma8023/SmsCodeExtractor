package com.github.tianma8023.smscode.app;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.SwitchPreference;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.webkit.WebView;
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.github.tianma8023.smscode.BuildConfig;
import com.github.tianma8023.smscode.R;
import com.github.tianma8023.smscode.app.theme.ThemeItem;
import com.github.tianma8023.smscode.constant.Const;
import com.github.tianma8023.smscode.constant.PrefConst;
import com.github.tianma8023.smscode.utils.AppOpsUtils;
import com.github.tianma8023.smscode.utils.PackageUtils;
import com.github.tianma8023.smscode.utils.ResUtils;
import com.github.tianma8023.smscode.utils.SPUtils;
import com.github.tianma8023.smscode.utils.ShellUtils;
import com.github.tianma8023.smscode.utils.StorageUtils;
import com.github.tianma8023.smscode.utils.Utils;
import com.github.tianma8023.smscode.utils.VerificationUtils;
import com.github.tianma8023.smscode.utils.XLog;
import com.github.tianma8023.smscode.utils.rom.MiuiUtils;
import com.github.tianma8023.smscode.utils.rom.RomUtils;
import com.yanzhenjie.permission.Action;
import com.yanzhenjie.permission.AndPermission;
import com.yanzhenjie.permission.Rationale;
import com.yanzhenjie.permission.RequestExecutor;

import java.util.List;

import ch.qos.logback.classic.Level;

import static com.github.tianma8023.smscode.constant.PrefConst.KEY_CHOOSE_THEME;
import static com.github.tianma8023.smscode.constant.PrefConst.KEY_DONATE_BY_ALIPAY;
import static com.github.tianma8023.smscode.constant.PrefConst.KEY_DONATE_BY_WECHAT;
import static com.github.tianma8023.smscode.constant.PrefConst.KEY_ENABLE;
import static com.github.tianma8023.smscode.constant.PrefConst.KEY_ENTRY_AUTO_INPUT_CODE;
import static com.github.tianma8023.smscode.constant.PrefConst.KEY_LISTEN_MODE;
import static com.github.tianma8023.smscode.constant.PrefConst.KEY_MARK_AS_READ;
import static com.github.tianma8023.smscode.constant.PrefConst.KEY_SMSCODE_TEST;
import static com.github.tianma8023.smscode.constant.PrefConst.KEY_SOURCE_CODE;
import static com.github.tianma8023.smscode.constant.PrefConst.KEY_VERBOSE_LOG_MODE;
import static com.github.tianma8023.smscode.constant.PrefConst.KEY_VERSION;

/**
 * 首选项Fragment
 */
public class SettingsFragment extends PreferenceFragment implements Preference.OnPreferenceClickListener, Preference.OnPreferenceChangeListener {

    public static final String EXTRA_KEY_CURRENT_THEME = "extra_key_current_theme";

    private Activity mActivity;

    private SwitchPreference mEnablePreference;
    private String mCurListenMode;


    public interface OnPreferenceClickCallback {
        void onPreferenceClicked(String key, String title, boolean nestedPreference);
    }

    private OnPreferenceClickCallback mPreferenceClickCallback;

    public SettingsFragment() {
    }

    public static SettingsFragment newInstance(ThemeItem curThemeItem) {
        SettingsFragment fragment = new SettingsFragment();
        Bundle args = new Bundle();
        args.putParcelable(EXTRA_KEY_CURRENT_THEME, curThemeItem);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.settings);

        mEnablePreference = (SwitchPreference) findPreference(PrefConst.KEY_ENABLE);
        mEnablePreference.setOnPreferenceChangeListener(this);
        // verbose log preference
        SwitchPreference verboseLogPref = (SwitchPreference) findPreference(KEY_VERBOSE_LOG_MODE);
        verboseLogPref.setOnPreferenceChangeListener(this);
        // listen mode preference
        ListPreference listenModePref = (ListPreference) findPreference(KEY_LISTEN_MODE);
        listenModePref.setOnPreferenceChangeListener(this);

        findPreference(KEY_SOURCE_CODE).setOnPreferenceClickListener(this);
        findPreference(KEY_DONATE_BY_ALIPAY).setOnPreferenceClickListener(this);
        // findPreference(PrefConst.KEY_DONATE_BY_WECHAT).setOnPreferenceClickListener(this);
        findPreference(KEY_SMSCODE_TEST).setOnPreferenceClickListener(this);
        findPreference(KEY_ENTRY_AUTO_INPUT_CODE).setOnPreferenceClickListener(this);
        Preference chooseThemePref = findPreference(PrefConst.KEY_CHOOSE_THEME);
        chooseThemePref.setOnPreferenceClickListener(this);
        initChooseThemePreference(chooseThemePref);

        Preference markAsReadPref = findPreference(KEY_MARK_AS_READ);
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
            markAsReadPref.setOnPreferenceChangeListener(this);
        } else {
            PreferenceGroup generalGroup = (PreferenceGroup) findPreference(PrefConst.KEY_GENERAL);
            generalGroup.removePreference(markAsReadPref);
        }

        // Hide experimental group.
        PreferenceGroup experimentalGroup = (PreferenceGroup) findPreference(PrefConst.KEY_EXPERIMENTAL);
        getPreferenceScreen().removePreference(experimentalGroup);

        // Hide donate by wechat preference item
        Preference donateByWechat = findPreference(KEY_DONATE_BY_WECHAT);
        PreferenceGroup aboutGroup = (PreferenceGroup) findPreference(PrefConst.KEY_ABOUT);
        aboutGroup.removePreference(donateByWechat);

        mCurListenMode = listenModePref.getValue();
        refreshListenModePreference(listenModePref, mCurListenMode);

        refreshVerboseLogPreference(verboseLogPref, verboseLogPref.isChecked());

        // version info preference
        Preference versionPref = findPreference(KEY_VERSION);
        showVersionInfo(versionPref);
    }

    private void initChooseThemePreference(Preference chooseThemePref) {
        Bundle args = getArguments();
        ThemeItem themeItem = args.getParcelable(EXTRA_KEY_CURRENT_THEME);
        if (themeItem != null) {
            chooseThemePref.setSummary(themeItem.getColorNameRes());
        }
    }

    private void refreshListenModePreference(ListPreference listenModePref, String newValue) {
        if (TextUtils.isEmpty(newValue))
            return;
        CharSequence[] entries = listenModePref.getEntries();
        int index = listenModePref.findIndexOfValue(newValue);
        try {
            listenModePref.setSummary(entries[index]);
        } catch (Exception e) {
            //ignore
        }
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mActivity = getActivity();
    }

    public void setOnPreferenceClickCallback(OnPreferenceClickCallback preferenceClickCallback) {
        mPreferenceClickCallback = preferenceClickCallback;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        String key = preference.getKey();
        switch (key) {
            case KEY_ENTRY_AUTO_INPUT_CODE:
                if (mPreferenceClickCallback != null) {
                    mPreferenceClickCallback.onPreferenceClicked(key, preference.getTitle().toString(), true);
                }
                break;
            case KEY_CHOOSE_THEME:
                if (mPreferenceClickCallback != null) {
                    mPreferenceClickCallback.onPreferenceClicked(key, preference.getTitle().toString(), false);
                }
                break;
            case KEY_SMSCODE_TEST:
                showSmsCodeTestDialog();
                break;
            case KEY_SOURCE_CODE:
                aboutProject();
                break;
            case KEY_DONATE_BY_ALIPAY:
                donateByAlipay();
                break;
            case KEY_DONATE_BY_WECHAT:
                donateByWechat();
                break;
            default:
                return false;
        }
        return true;
    }

    private void showVersionInfo(Preference preference) {
        String summary = getString(R.string.pref_version_summary,
                BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE);
        preference.setSummary(summary);
    }

    private void aboutProject() {
        Utils.showWebPage(mActivity, Const.PROJECT_SOURCE_CODE_URL);
    }

    private void donateByWechat() {
        if (!PackageUtils.isWeChatInstalled(mActivity)) { // uninstalled
            Toast.makeText(mActivity, R.string.wechat_install_prompt, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!PackageUtils.isWeChatEnabled(mActivity)) { // installed but disabled
            Toast.makeText(mActivity, R.string.wechat_enable_prompt, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent();
        intent.setClassName(Const.WECHAT_PACKAGE_NAME, Const.WECHAT_LAUNCHER_UI);
        intent.putExtra(Const.WECHAT_KEY_EXTRA_DONATE, true);
        startActivity(intent);
    }

    private void donateByAlipay() {
        if (!PackageUtils.isAlipayInstalled(mActivity)) { // uninstalled
            Toast.makeText(mActivity, R.string.alipay_install_prompt, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!PackageUtils.isAlipayEnabled(mActivity)) { // installed but disabled
            Toast.makeText(mActivity, R.string.alipay_enable_prompt, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(Const.ALIPAY_QRCODE_URI_PREFIX + Const.ALIPAY_QRCODE_URL));
        startActivity(intent);
    }


    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        switch (key) {
            case KEY_ENABLE:
                onEnabledSwitched((Boolean) newValue);
                break;
            case KEY_LISTEN_MODE: {
                if (!newValue.equals(mCurListenMode)) {
                    mCurListenMode = (String) newValue;
                    refreshListenModePreference((ListPreference) preference, mCurListenMode);
                    if (PrefConst.LISTEN_MODE_COMPATIBLE.equals(mCurListenMode)) {
                        showCompatibleModePrompt();
                    }
                }
                break;
            }
            case KEY_MARK_AS_READ:
                onMarkAsReadSwitched((SwitchPreference) preference, (Boolean)newValue);
                break;
            case KEY_VERBOSE_LOG_MODE:
                refreshVerboseLogPreference(preference, (Boolean) newValue);
                break;
            default:
                return false;
        }
        return true;
    }

    private void refreshVerboseLogPreference(Preference preference, boolean on) {
        if (on) {
            preference.setSummary(StorageUtils.getLogDir(getActivity()).getAbsolutePath());
            XLog.setLogLevel(Level.TRACE);
        } else {
            XLog.setLogLevel(Level.INFO);
        }
    }

    private void showSmsCodeTestDialog() {
        new MaterialDialog.Builder(mActivity)
                .title(R.string.pref_smscode_test_title)
                .input(R.string.sms_content_hint, 0, true, new MaterialDialog.InputCallback() {
                    @Override
                    public void onInput(@NonNull MaterialDialog dialog, CharSequence input) {
                        new Thread(new SmsCodeTestTask(mActivity, input.toString())).start();
                    }
                })
                .inputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE)
                .negativeText(R.string.cancel)
                .show();
    }

    private class SmsCodeTestTask implements Runnable {

        private String mMsgBody;
        private Context mContext;

        SmsCodeTestTask(Context context, String msgBody) {
            mMsgBody = msgBody;
            mContext = context;
        }

        @Override
        public void run() {
            Message msg = new Message();
            msg.what = MSG_SMSCODE_TEST;
            if (TextUtils.isEmpty(mMsgBody)) {
                msg.obj = "";
            } else {
                msg.obj = VerificationUtils.parseVerificationCodeIfExists(mContext, mMsgBody);
            }
            mHandler.sendMessage(msg);
        }
    }

    private static final int MSG_SMSCODE_TEST = 0xff;

    private Handler mHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_SMSCODE_TEST:
                    handleSmsCode((String) msg.obj);
                    return true;
            }
            return false;
        }
    });

    private void handleSmsCode(String verificationCode) {
        String text;
        if (TextUtils.isEmpty(verificationCode)) {
            text = getString(R.string.cannot_parse_smscode);
        } else {
            text = getString(R.string.cur_verification_code, verificationCode);
        }
        Toast.makeText(mActivity, text, Toast.LENGTH_LONG).show();
    }

    private void onEnabledSwitched(boolean enable) {
        if (!enable) {
            return;
        }
        if (SPUtils.isFirstRunSinceV1(mActivity)) {
            showPermissionStatement();
            SPUtils.setFirstRunSinceV1(mActivity, false);
        } else {
            tryToAcquireNecessaryPermissions();
        }
    }

    // 申请必要权限
    private void tryToAcquireNecessaryPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }

        Rationale<List<String>> rationale = new Rationale<List<String>>() {
            @Override
            public void showRationale(Context context, List<String> data, final RequestExecutor executor) {
                new MaterialDialog.Builder(mActivity)
                        .title(R.string.permission_requirement)
                        .content(R.string.receive_sms_permission_requirement)
                        .positiveText(R.string.okay)
                        .onPositive(new MaterialDialog.SingleButtonCallback() {
                            @Override
                            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                                executor.execute();
                            }
                        })
                        .negativeText(R.string.cancel)
                        .onNegative(new MaterialDialog.SingleButtonCallback() {
                            @Override
                            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                                executor.cancel();
                            }
                        })
                        .show();
            }
        };

        AndPermission.with(this)
                .runtime()
                .permission(Manifest.permission.READ_SMS,
                        Manifest.permission.RECEIVE_SMS)
                .rationale(rationale)
                .onGranted(new Action<List<String>>() {
                    @Override
                    public void onAction(List<String> data) {
                        requestOtherPermissionsIfNecessary();
                    }
                })
                .onDenied(new Action<List<String>>() {
                    @Override
                    public void onAction(List<String> data) {
                        Toast.makeText(mActivity, R.string.prompt_sms_permission_denied, Toast.LENGTH_LONG).show();
                        mEnablePreference.setChecked(false);
                    }
                })
                .start();
    }

    // 展示权限声明
    private void showPermissionStatement() {
        View dialogView = mActivity.getLayoutInflater().inflate(R.layout.dialog_perm_state, null);
        WebView permStateWebView = dialogView.findViewById(R.id.perm_state_webview);
        String data = ResUtils.loadRawRes(mActivity, R.raw.perm_state);
        permStateWebView.loadDataWithBaseURL("file:///android_asset/",
                data, "text/html", "utf-8", null);
        new MaterialDialog.Builder(mActivity)
                .title(R.string.permission_statement)
                .customView(permStateWebView, false)
                .positiveText(R.string.okay)
                .onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        tryToAcquireNecessaryPermissions();
                    }
                })
                .show();
    }

    // 必要情况下，申请其他更多权限（MIUI的"通知类短信"权限）
    private void requestOtherPermissionsIfNecessary() {
        if (RomUtils.isMiui()) {
            if (!SPUtils.isServiceSmsPromptShown(mActivity)) {
                new MaterialDialog.Builder(mActivity)
                        .title(R.string.permission_requirement)
                        .content(R.string.service_sms_permission_requirement)
                        .positiveText(R.string.okay)
                        .onPositive(new MaterialDialog.SingleButtonCallback() {
                            @Override
                            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                                MiuiUtils.goToPermissionEditorActivity(mActivity);
                                SPUtils.setServiceSmsPromptShown(mActivity, true);
                            }
                        })
                        .negativeText(R.string.cancel)
                        .onNegative(new MaterialDialog.SingleButtonCallback() {
                            @Override
                            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                                Toast.makeText(mActivity, R.string.prompt_service_sms_permission_denied, Toast.LENGTH_LONG).show();
                                mEnablePreference.setChecked(false);
                            }
                        })
                        .show();
            }
        }
    }

    // 对兼容模式进行提示
    private void showCompatibleModePrompt() {
        new MaterialDialog.Builder(mActivity)
                .title(R.string.compatible_mode_prompt_title)
                .content(R.string.compatible_mode_prompt_content)
                .positiveText(R.string.confirm)
                .show();
    }

    private void onMarkAsReadSwitched(final SwitchPreference markAsReadPref, boolean on) {
        if (!on) {
            return;
        }

        final String packageName = BuildConfig.APPLICATION_ID;
        final int uid = Process.myUid();
        if (!AppOpsUtils.checkOp(mActivity, AppOpsUtils.OP_WRITE_SMS, uid, packageName)) {
            new MaterialDialog.Builder(mActivity)
                    .title(R.string.extra_permission_request_prompt_title)
                    .content(R.string.write_sms_appops_prompt_content)
                    .negativeText(R.string.view_adb_setting_help)
                    .onNegative(new MaterialDialog.SingleButtonCallback() {
                        @Override
                        public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                            String url = Utils.getProjectDocUrl(Const.PROJECT_DOC_BASE_URL, Const.DOC_APPOPS_ADB_HELP);
                            Utils.showWebPage(mActivity, url);
                        }
                    })
                    .positiveText(R.string.granted_by_root)
                    .onPositive(new MaterialDialog.SingleButtonCallback() {
                        @Override
                        public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                            if (!ShellUtils.allowOpWriteSMS()) {
                                markAsReadPref.setChecked(false);
                                Toast.makeText(mActivity, R.string.granted_appops_by_root_failed, Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(mActivity, R.string.granted_appops_by_root_succeed, Toast.LENGTH_SHORT).show();
                            }
                        }
                    })
                    .cancelListener(new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            markAsReadPref.setChecked(false);
                        }
                    }).show();
        } else {
            Toast.makeText(mActivity, R.string.relevant_permission_already_granted, Toast.LENGTH_LONG).show();
        }
    }
}
