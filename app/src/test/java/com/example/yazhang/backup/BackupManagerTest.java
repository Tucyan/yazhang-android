package com.example.yazhang.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.example.yazhang.data.EntryEntity;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import org.json.JSONObject;
import org.junit.Test;

public class BackupManagerTest {

    @Test
    public void backupContainsLedgerButNeverCredentials() throws Exception {
        JSONObject credentials = new JSONObject()
                .put("api_key", "secret")
                .put("token", "another-secret");

        JSONObject json = BackupManager.snapshot(
                Collections.singletonList(sampleEntry("key-1")), credentials);

        assertTrue(json.has("entries"));
        assertEquals(1, json.getInt("schemaVersion"));
        assertFalse(json.toString().contains("secret"));
        assertFalse(json.toString().contains("api_key"));
        assertEquals("key-1",
                json.getJSONArray("entries").getJSONObject(0).getString("requestKey"));
    }

    @Test
    public void csvEscapesQuotesCommasAndNewlines() {
        EntryEntity entry = sampleEntry("key-1");
        entry.merchant = "A,\"B\"";
        entry.detail = "第一行\n第二行";

        String csv = BackupManager.toCsv(Collections.singletonList(entry));

        assertTrue(csv.startsWith("id,type,amountFen,category"));
        assertTrue(csv.contains("\"A,\"\"B\"\"\""));
        assertTrue(csv.contains("\"第一行\n第二行\""));
    }

    @Test
    public void restoreInspectsValidEntriesAndSkipsExistingOrDuplicateKeys() throws Exception {
        JSONObject backup = BackupManager.snapshot(
                Arrays.asList(sampleEntry("new-1"), sampleEntry("old-1"), sampleEntry("new-1")),
                null);

        BackupManager.RestorePlan plan = BackupManager.inspect(
                backup.toString(), new HashSet<>(Collections.singletonList("old-1")));

        assertEquals(1, plan.newEntries.size());
        assertEquals(2, plan.skippedCount);
        assertEquals("new-1", plan.newEntries.get(0).requestKey);
    }

    @Test
    public void restoreRejectsWrongSchemaMissingKeysAndInvalidAmounts() throws Exception {
        JSONObject wrongVersion = validBackup();
        wrongVersion.put("schemaVersion", 2);
        assertInvalidRestore(wrongVersion.toString());

        JSONObject missingKey = validBackup();
        missingKey.getJSONArray("entries").getJSONObject(0).remove("requestKey");
        assertInvalidRestore(missingKey.toString());

        JSONObject invalidAmount = validBackup();
        invalidAmount.getJSONArray("entries").getJSONObject(0).put("amountFen", 0);
        assertInvalidRestore(invalidAmount.toString());

        JSONObject invalidType = validBackup();
        invalidType.getJSONArray("entries").getJSONObject(0).put("type", "TRANSFER");
        assertInvalidRestore(invalidType.toString());

        JSONObject invalidDate = validBackup();
        invalidDate.getJSONArray("entries").getJSONObject(0).put("occurredOn", "2026-02-30");
        assertInvalidRestore(invalidDate.toString());
    }

    @Test
    public void restoreRejectsMalformedJsonWithFriendlyMessage() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> BackupManager.inspect("not-json", Collections.emptySet()));

        assertTrue(error.getMessage().contains("恢复文件"));
        assertFalse(error.getMessage().contains("Exception"));
    }

    private static JSONObject validBackup() throws Exception {
        return BackupManager.snapshot(
                Collections.singletonList(sampleEntry("new-1")), null);
    }

    private static void assertInvalidRestore(String json) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> BackupManager.inspect(json, Collections.emptySet()));
        assertTrue(error.getMessage().contains("恢复文件"));
    }

    private static EntryEntity sampleEntry(String requestKey) {
        EntryEntity entry = new EntryEntity();
        entry.id = 7L;
        entry.type = "EXPENSE";
        entry.amountFen = 1230L;
        entry.category = "FOOD";
        entry.merchant = "小馆";
        entry.title = "午餐";
        entry.detail = "米饭";
        entry.occurredOn = "2026-11-09";
        entry.occurredAt = 100L;
        entry.source = "MANUAL";
        entry.status = "POSTED";
        entry.categoryInferred = false;
        entry.confidence = 0.0;
        entry.requestKey = requestKey;
        entry.goalId = null;
        entry.createdAt = 100L;
        entry.updatedAt = 100L;
        return entry;
    }
}
