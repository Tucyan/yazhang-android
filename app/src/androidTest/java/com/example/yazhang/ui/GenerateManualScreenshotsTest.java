package com.example.yazhang.ui;

import android.graphics.Bitmap;
import android.os.SystemClock;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.yazhang.MainActivity;
import com.example.yazhang.ai.AgentService;
import com.example.yazhang.ai.config.SecureSettings;
import com.example.yazhang.data.AgentMessageDao;
import com.example.yazhang.data.AgentMessageEntity;
import com.example.yazhang.data.AppDatabase;
import com.example.yazhang.data.LedgerRepository;
import com.example.yazhang.domain.EntryDraft;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;

@RunWith(AndroidJUnit4.class)
public class GenerateManualScreenshotsTest {

    private File remoteDir;
    private LedgerRepository repository;
    private SecureSettings settings;
    private AgentMessageDao agentMessageDao;
    private String testApiKey;

    @Before
    public void setUp() {
        remoteDir = ApplicationProvider.getApplicationContext().getExternalFilesDir("manual_screens");
        if (remoteDir != null && !remoteDir.exists()) {
            remoteDir.mkdirs();
        }
        AppDatabase db = AppDatabase.get(ApplicationProvider.getApplicationContext());
        db.clearAllTables();
        repository = new LedgerRepository(db);
        settings = new SecureSettings(ApplicationProvider.getApplicationContext());
        agentMessageDao = db.agentMessageDao();

        String keyArg = InstrumentationRegistry.getArguments().getString("apiKey");
        testApiKey = (keyArg != null && !keyArg.trim().isEmpty())
                ? keyArg.trim()
                : "sk-mock-key-for-ui-testing-only-placeholder";

        try {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .executeShellCommand("am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS");
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .executeShellCommand("input keyevent KEYCODE_BACK");
        } catch (Throwable ignored) {
        }
    }

    private static AgentMessageEntity createMsg(String role, String content) {
        AgentMessageEntity m = new AgentMessageEntity();
        m.sessionId = AgentService.SESSION_ID;
        m.role = role;
        m.content = content;
        m.createdAt = System.currentTimeMillis();
        return m;
    }

    private void sleep(long ms) {
        SystemClock.sleep(ms);
    }

    private void capture(String filename) throws Exception {
        sleep(500);
        Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        File localFile = new File(remoteDir, filename);
        try (FileOutputStream out = new FileOutputStream(localFile)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, out);
        }
        bitmap.recycle();

        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .executeShellCommand("cp " + localFile.getAbsolutePath() + " /sdcard/manual_screens/" + filename);
        System.gc();
    }

    @Test
    public void test01_homeAndStatsScreenshots() throws Exception {
        // Clear Key initially
        JSONObject provider = settings.getProvider();
        settings.saveProvider(provider, "");

        // 1. Home Empty -> 01_home_empty.png
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> activity.showPage(0));
            capture("01_home_empty.png");

            // 2. Open Entry Dialog -> 02_entry_dialog.png
            scenario.onActivity(activity -> {
                EntryDialog.show(activity, repository, "EXPENSE", null);
            });
            sleep(800);
            capture("02_entry_dialog.png");
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .executeShellCommand("input keyevent KEYCODE_BACK");
            sleep(400);
        }

        // 3. Add expense -> 03_home_after_expense.png
        repository.create(new EntryDraft(
                "EXPENSE", 2550L, "FOOD", "KFC", "工作日午餐", null, LocalDate.now()));
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> activity.showPage(0));
            capture("03_home_after_expense.png");

            // 4. Goal Dialog -> 04_goal_dialog.png
            scenario.onActivity(activity -> {
                HomeView homeView = (HomeView) activity.getPage(0);
                homeView.showGoalDialog();
            });
            capture("04_goal_dialog.png");
        }

        // 5. Create goal -> 05_home_with_goal.png
        long goalId = repository.createGoal("TripFund", 500000L, LocalDate.now().plusMonths(3).toString());
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> activity.showPage(0));
            capture("05_home_with_goal.png");

            // 6. Saving Dialog -> 06_saving_dialog.png
            scenario.onActivity(activity -> {
                EntryDialog.show(activity, repository, "SAVING", null);
            });
            capture("06_saving_dialog.png");
        }

        // 7. Save into goal -> 07_home_after_saving.png
        repository.addSaving(goalId, new EntryDraft(
                "SAVING", 100000L, "SAVING", null, "存入首期基金", null, LocalDate.now()));
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> activity.showPage(0));
            capture("07_home_after_saving.png");

            // Add more expenses for richer statistics chart
            repository.create(new EntryDraft(
                    "EXPENSE", 3800L, "TRANSPORT", "滴滴出行", "市内通勤", null, LocalDate.now()));
            repository.create(new EntryDraft(
                    "EXPENSE", 8900L, "SHOPPING", "优衣库", "购买换季衣物", null, LocalDate.now()));
            repository.create(new EntryDraft(
                    "INCOME", 1200000L, "SALARY", "公司发放", "本月薪资", null, LocalDate.now()));

            // 8. Stats View -> 08_stats_view.png
            try (ActivityScenario<StatsActivity> stats = ActivityScenario.launch(StatsActivity.class)) {
                capture("08_stats_view.png");
            }
        }

        // 15. Landscape mode -> 15_rotation_landscape.png
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .executeShellCommand("settings put system accelerometer_rotation 0");
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .executeShellCommand("settings put system user_rotation 1");
        sleep(1200);

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> activity.showPage(0));
            capture("15_rotation_landscape.png");
        }

        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .executeShellCommand("settings put system user_rotation 0");
        sleep(800);
    }

    @Test
    public void test02_agentAndRechargeScreenshots() throws Exception {
        JSONObject provider = settings.getProvider();
        settings.saveProvider(provider, "");

        // Populate base entries for Settings and Agent
        repository.create(new EntryDraft(
                "EXPENSE", 2550L, "FOOD", "KFC", "工作日午餐", null, LocalDate.now()));
        repository.create(new EntryDraft(
                "EXPENSE", 3800L, "TRANSPORT", "滴滴出行", "市内通勤", null, LocalDate.now()));
        repository.create(new EntryDraft(
                "EXPENSE", 8900L, "SHOPPING", "优衣库", "购买换季衣物", null, LocalDate.now()));
        repository.create(new EntryDraft(
                "INCOME", 1200000L, "SALARY", "公司发放", "本月薪资", null, LocalDate.now()));

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // 9. Agent View without Key -> 09_agent_view_nokey.png
            scenario.onActivity(activity -> activity.showPage(2));
            capture("09_agent_view_nokey.png");

            // 10. Agent send without Key -> 10_agent_nokey_error.png
            scenario.onActivity(activity -> {
                AgentView agentView = (AgentView) activity.getPage(2);
                agentView.setInputText("今天打车花了20元");
                agentView.send();
            });
            sleep(600);
            capture("10_agent_nokey_error.png");
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .executeShellCommand("input keyevent KEYCODE_BACK");
            sleep(400);

        }
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            // 11. Settings View without Key -> 11_settings_view.png
            capture("11_settings_view.png");

            // 16. AI Config Dialog -> 16_ai_config_dialog.png
            scenario.onActivity(activity -> {
                SettingsView settingsView = activity.settingsView();
                settingsView.openAiConfig();
            });
            capture("16_ai_config_dialog.png");
        }

    }

    @Test
    public void test03_configuredScreenshots() throws Exception {
        JSONObject provider = settings.getProvider();
        settings.saveProvider(provider, testApiKey);

        // Populate Agent conversation with real multi-turn messages -> 21_agent_conversation.png
        agentMessageDao.insert(createMsg("user", "help"));
        agentMessageDao.insert(createMsg("assistant",
                "我是芽账本地记账助手，可以帮你记账、查统计和看攒钱进度。\n\n你可以这样说：\n- 记账：今天午饭花了 25 元\n- 收入：这个月 10 号发工资 8000 元\n- 攒钱：往攒钱目标里存 500 元\n- 查统计：看看我 11 月 1 日到 11 月 10 日吃饭花了多少？\n\n请告诉我金额、分类和日期，我就能帮你记账或查询。"));
        agentMessageDao.insert(createMsg("user", "今天打车花了 20 元"));
        agentMessageDao.insert(createMsg("assistant",
                "已帮你记录到本地账本：支出 20.00 元（分类：交通）。"));

        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            // 17. Settings Key Saved -> 17_settings_key_saved.png
            capture("17_settings_key_saved.png");

            // 18. Test text connection popup -> 18_test_text_result.png
            scenario.onActivity(activity -> {
                new androidx.appcompat.app.AlertDialog.Builder(activity)
                        .setTitle("测试成功")
                        .setMessage("服务商已正常响应：\n\n文本探测成功 (耗时 1234ms)")
                        .setPositiveButton("好的", null)
                        .show();
            });
            capture("18_test_text_result.png");
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .executeShellCommand("input keyevent KEYCODE_BACK");
            sleep(400);

        }
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // 21. Agent conversation -> 21_agent_conversation.png
            scenario.onActivity(activity -> activity.showPage(2));
            capture("21_agent_conversation.png");
        }
    }
}
