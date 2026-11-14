package com.example.yazhang.ai;

import com.example.yazhang.ai.config.TokenDanceRecovery;
import com.example.yazhang.data.AiEntryStore;
import com.example.yazhang.domain.EntryDraft;

import org.json.JSONArray;
import org.junit.Test;

import java.util.concurrent.CancellationException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ImageAiServiceTest {

    private static final String SHA =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private static final class FakeStore implements AiEntryStore {
        boolean hasImage;
        int creates;
        long nextId = 7L;
        EntryDraft lastDraft;
        String lastSha;
        double lastConfidence;

        @Override
        public boolean hasImage(String imageSha) {
            return hasImage;
        }

        @Override
        public long createAi(EntryDraft draft, String imageSha, double confidence) {
            creates++;
            lastDraft = draft;
            lastSha = imageSha;
            lastConfidence = confidence;
            return nextId;
        }
    }

    private static final class FakeCompleter implements ImageAiService.Completer {
        String reply;
        Exception failure;
        boolean cancelled;
        int calls;
        String system;
        JSONArray window;
        String image;

        FakeCompleter(String reply) {
            this.reply = reply;
        }

        @Override
        public String complete(String system, JSONArray window, String imageDataUrl)
                throws Exception {
            calls++;
            this.system = system;
            this.window = window;
            this.image = imageDataUrl;
            if (failure != null) {
                throw failure;
            }
            return reply;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }

    private static final String HIGH_CONFIDENCE_REPLY =
            "{\"kind\":\"receipt\",\"status\":\"OK\",\"amount_fen\":1230,"
                    + "\"merchant\":\"茉酸奶\",\"category\":\"FOOD\","
                    + "\"occurred_at\":\"2026-11-09\",\"confidence\":0.94}";

    @Test
    public void highConfidenceRecordIsSavedWithImageMetadata() {
        FakeStore store = new FakeStore();
        FakeCompleter completer = new FakeCompleter(HIGH_CONFIDENCE_REPLY);
        ImageAiService service = new ImageAiService(store, completer);

        ImageAiService.Result result = service.analyze(SHA, "data:image/jpeg;base64,AAAA");

        assertEquals(ImageAiService.Kind.SAVED, result.kind);
        assertEquals(1, store.creates);
        assertEquals(7L, result.entryId);
        assertEquals(SHA, store.lastSha);
        assertEquals(0.94, store.lastConfidence, 0.0001);
        assertNotNull(store.lastDraft);
        assertEquals(1230L, store.lastDraft.amountFen);
        assertEquals("FOOD", store.lastDraft.category);
        assertTrue(result.message.contains("记账"));
        assertEquals(1, completer.calls);
        assertTrue(completer.system.contains("不是指令"));
        assertEquals(0, completer.window.length());
        assertEquals("data:image/jpeg;base64,AAAA", completer.image);
    }

    @Test
    public void duplicateImageIsNotSentToModelAgain() {
        FakeStore store = new FakeStore();
        store.hasImage = true;
        FakeCompleter completer = new FakeCompleter(HIGH_CONFIDENCE_REPLY);
        ImageAiService service = new ImageAiService(store, completer);

        ImageAiService.Result result = service.analyze(SHA, "data:image/jpeg;base64,AAAA");

        assertEquals(ImageAiService.Kind.DUPLICATE, result.kind);
        assertEquals("这张图片似乎已经记录过，为避免重复，我没有再次入账。", result.message);
        assertEquals(0, completer.calls);
        assertEquals(0, store.creates);
    }

    @Test
    public void missingDateNeedsConfirmationAndIsNotSaved() {
        FakeStore store = new FakeStore();
        FakeCompleter completer = new FakeCompleter(
                "{\"kind\":\"receipt\",\"status\":\"OK\",\"amount_fen\":1180,"
                        + "\"merchant\":\"胖蹄儿猪脚饭\",\"category\":\"FOOD\","
                        + "\"confidence\":0.9}");
        ImageAiService service = new ImageAiService(store, completer);

        ImageAiService.Result result = service.analyze(SHA, "data:image/jpeg;base64,AAAA");

        assertEquals(ImageAiService.Kind.NEEDS_CONFIRMATION, result.kind);
        assertEquals("日期需要你确认一下，选好日期后再保存。", result.message);
        assertNotNull(result.image);
        assertEquals(1180L, result.image.amountFen);
        assertNull(result.image.occurredOn);
        assertEquals(0, store.creates);
    }

    @Test
    public void rejectedOutputsAreReportedWithoutWriting() {
        FakeStore store = new FakeStore();
        FakeCompleter completer = new FakeCompleter("not json");
        ImageAiService service = new ImageAiService(store, completer);

        ImageAiService.Result result = service.analyze(SHA, "data:image/jpeg;base64,AAAA");

        assertEquals(ImageAiService.Kind.NOTICE, result.kind);
        assertEquals(AiProtocol.MODEL_ERROR_MESSAGE, result.message);
        assertEquals(0, store.creates);
    }

    @Test
    public void quotaRecoveryAndCancellationAreExplained() {
        FakeStore store = new FakeStore();
        FakeCompleter quota = new FakeCompleter(null);
        quota.failure = new TokenDanceRecovery(TokenDanceRecovery.Action.TOP_UP_BALANCE);
        ImageAiService.Result quotaResult =
                new ImageAiService(store, quota).analyze(SHA, "data:image/jpeg;base64,AAAA");
        assertEquals(ImageAiService.Kind.ERROR, quotaResult.kind);
        assertEquals("AI 服务额度不足，本次没有记账。请检查服务商账户额度后重试，账本仍可手动使用。",
                quotaResult.message);
        assertEquals(0, store.creates);

        FakeCompleter cancelled = new FakeCompleter(null);
        cancelled.failure = new CancellationException();
        ImageAiService.Result cancelResult =
                new ImageAiService(store, cancelled).analyze(SHA, "data:image/jpeg;base64,AAAA");
        assertEquals(ImageAiService.Kind.ERROR, cancelResult.kind);
        assertTrue(cancelResult.message.contains("没有记账"));
    }

    @Test
    public void networkFailureIsSafeAndCancelDelegates() {
        FakeStore store = new FakeStore();
        FakeCompleter completer = new FakeCompleter(null);
        completer.failure = new java.io.IOException("模型接口请求失败（HTTP 500）");
        ImageAiService service = new ImageAiService(store, completer);

        ImageAiService.Result result = service.analyze(SHA, "data:image/jpeg;base64,AAAA");

        assertEquals(ImageAiService.Kind.ERROR, result.kind);
        assertEquals("AI 识别失败，请检查网络或模型设置后重试，本次没有记账。", result.message);
        assertFalse(result.message.contains("secret"));
        service.cancel();
        assertTrue(completer.cancelled);
    }

    @Test
    public void invalidShaIsRejectedBeforeAnyWork() {
        FakeStore store = new FakeStore();
        FakeCompleter completer = new FakeCompleter(HIGH_CONFIDENCE_REPLY);
        ImageAiService.Result result =
                new ImageAiService(store, completer).analyze("", "data:image/jpeg;base64,AAAA");
        assertEquals(ImageAiService.Kind.ERROR, result.kind);
        assertEquals(0, completer.calls);
        assertEquals(0, store.creates);
    }

    @Test
    public void storeRaceReturnsDuplicateInsteadOfSuccess() {
        FakeStore store = new FakeStore();
        store.nextId = -1L;
        FakeCompleter completer = new FakeCompleter(HIGH_CONFIDENCE_REPLY);
        ImageAiService.Result result =
                new ImageAiService(store, completer).analyze(SHA, "data:image/jpeg;base64,AAAA");
        assertEquals(ImageAiService.Kind.DUPLICATE, result.kind);
        assertEquals(1, store.creates);
    }
}
