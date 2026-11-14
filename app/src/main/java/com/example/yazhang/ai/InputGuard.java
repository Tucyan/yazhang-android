package com.example.yazhang.ai;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InputGuard {

    private static final int MAX_LENGTH = 500;
    private static final String EMPTY_MESSAGE =
            "还没有可处理的内容，没有执行任何操作；请输入你想记账、查账或统计的内容。";
    private static final String TOO_LONG_MESSAGE =
            "输入最多 500 字，请精简后再试。没有执行任何操作；你可以改为记账、查账或统计。";
    private static final String CONTROL_MESSAGE =
            "请删除不可见字符后再输入。没有执行任何操作；你可以改为记账、查账或统计。";
    private static final String DENIED_MESSAGE =
            "这项操作不在记账助手权限内。没有执行任何操作；你可以改为记账、查账或统计。";

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    private static final Pattern DELETE_RECORDS = Pattern.compile(
            "(?:删除|删掉|清除|清空|抹除|销毁)"
                    + "|(?:delete|erase)\\s+(?:all\\s+)?(?:entries|records|ledger)", FLAGS
    );
    private static final Pattern EXPORT_FILE = Pattern.compile(
            "导出.{0,20}?(?:文件|csv|excel|xls|pdf|账目|账本|账单|记录|明细|数据|支出|收入|交易|流水)"
                    + "|(?:把|将).{0,20}(?:文件|csv|excel|xls|pdf|账目|账本|账单|记录|明细|数据)"
                    + ".{0,10}导出"
                    + "|export\\s+(?:all\\s+)?(?:files?|ledger|records?|entries|csv|excel|xls|pdf)"
                    + "|(?:下载|另存为|保存到).{0,20}(?:文件|csv|excel|xls|pdf)"
                    + "|download.{0,20}(?:file|csv|ledger|records)"
                    + "|(?:^|[，,；;。])\\s*(?:导出|export)\\s*(?=$|[，,；;。])", FLAGS
    );
    private static final String PAYMENT_VERBS = "(?:充值|付款|支付|转账|缴费|扣款)";
    private static final String AMOUNT_START =
            "(?:\\d|零|[两二三四五六七八九十百千万]|一(?!共|起|些|直|般|定))";
    private static final String HISTORICAL_TIME = "(?:昨天|前天|刚刚|刚才)";
    private static final String REQUEST_PREFIX =
            "(?:(?:帮我|帮忙|麻烦(?:你|您)?|请|现在|先|立即|马上|执行|发起|完成|替我|给我|我要|我想|去)\\s*)";
    private static final String PAYMENT_SEQUENCE_DETAILS =
            "(?:(?:向|给)[^，,；;。]{0,20}?)?"
                    + "(?:\\d+(?:\\.\\d+)?|[零一二两三四五六七八九十百千万]+)?"
                    + "(?:\\s*(?:元|块(?:钱)?))?\\s*"
                    + "(?:(?:向|给)[^，,；;。]{0,20}?)?\\s*";
    private static final Pattern PAYMENT_COMMAND = Pattern.compile(
            REQUEST_PREFIX + "+" + PAYMENT_VERBS
                    + "|(?:^|[，,；;。])\\s*" + PAYMENT_VERBS
                    + "\\s*(?:吧|了|一下)?\\s*(?:$|[，,；;。])",
            FLAGS
    );
    private static final Pattern PAYMENT_DETAILS = Pattern.compile(
            PAYMENT_VERBS + "\\s*" + AMOUNT_START
                    + "|(?:向|给).{0,6}" + PAYMENT_VERBS
                    + "|" + PAYMENT_VERBS + "\\s*(?:向|给).{0,6}",
            FLAGS
    );
    private static final Pattern SQL_EXECUTION = Pattern.compile(
            "(?:执行|运行|跑一下|调用|使用).{0,20}(?:sql|数据库)"
                    + "|(?<![a-z0-9_])(?:select\\b[\\s\\S]*?\\bfrom\\s+\\w+"
                    + "|insert\\s+into\\s+\\w+|update\\s+\\w+\\s+set\\b"
                    + "|delete\\s+from\\s+\\w+|drop\\s+table\\s+\\w+"
                    + "|alter\\s+table\\s+\\w+|truncate\\s+table\\s+\\w+)", FLAGS
    );
    private static final Pattern FILE_ACCESS = Pattern.compile(
            "(?:读取|读|打开|查看|写入|写|修改|删除|读写).{0,20}(?:文件|目录|路径)"
                    + "|(?:文件|目录|路径).{0,20}(?:读取|写入|读写|修改|删除)"
                    + "|(?:读取|读|打开|查看|写入|写|修改|删除|读写|列出|列举).{0,20}"
                    + "(?:[a-z]:[\\\\/]|/(?:[^\\s]+)?)"
                    + "|(?:列出|列举|显示).{0,20}(?:目录|文件夹)"
                    + "|(?:read|write|open|modify|delete).{0,20}(?:file|directory|path)", FLAGS
    );
    private static final Pattern PROMPT_INJECTION = Pattern.compile(
            "(?:无视|忽略|绕过|覆盖|突破|不遵守|抛开).{0,30}"
                    + "(?:系统|规则|指令|提示词|限制|安全)"
                    + "|(?:系统提示词|开发者指令|管理员权限|执行工具|调用工具|工具调用)"
                    + "|(?:执行|运行|跑一下|调用|使用).{0,20}(?:shell|命令行|终端命令)"
                    + "|ignore\\s+(?:all\\s+|previous\\s+|the\\s+)*instructions"
                    + "|reveal\\s+(?:the\\s+)?system\\s+prompt", FLAGS
    );
    private static final Pattern PAYMENT_AMOUNT_QUERY_FILTER = Pattern.compile(
            PAYMENT_VERBS + "\\s*" + AMOUNT_START + "[^，,；;。]{0,20}?"
                    + "(?:元|块(?:钱)?)\\s*(?:以上|以下)\\s*(?:的\\s*)?"
                    + "(?:记录|账目|账单|明细|交易)", FLAGS
    );
    private static final Pattern HISTORICAL_PAYMENT_PREFIX = Pattern.compile(
            "^\\s*" + HISTORICAL_TIME + "\\s*(" + PAYMENT_VERBS + ")", FLAGS
    );
    private static final Pattern MANUAL_PAYMENT_ENTRY = Pattern.compile(
            "^\\s*(?:补记\\s*(?:" + HISTORICAL_TIME + "\\s*)?|记一笔\\s*)"
                    + "(" + PAYMENT_VERBS + ")", FLAGS
    );
    private static final Pattern ADDITIONAL_PAYMENT_ACTION = Pattern.compile(
            "(?:并(?:且)?|随后|然后|接着|同时|再|还要|后|[，,；;。])\\s*"
                    + "(?:(?:帮我|帮忙|麻烦|请|现在|立即|马上|执行|发起|完成|替我|给我|我要|我想|去)\\s*)?"
                    + "(?:(?:向|给).{0,6})?" + PAYMENT_VERBS
                    + "(?=\\s*(?:$|[，,；;。]|" + AMOUNT_START
                    + "|向|给|吧|了|一下))", FLAGS
    );
    private static final Pattern PAYMENT_SEQUENCE = Pattern.compile(
            PAYMENT_VERBS + "\\s*(?:吧|了|一下)?\\s*" + PAYMENT_SEQUENCE_DETAILS
                    + "(?:[，,；;。]\\s*)?"
                    + "(?:(?:然后|之后|而后|以后|再|接着|随后|后|并(?:且)?|同时|还要)\\s*)+"
                    + REQUEST_PREFIX + "*"
                    + "(?:查询|查账|统计|记账|记一笔|记一下|记录一笔)",
            FLAGS
    );
    private static final Pattern IMPLICIT_PAYMENT_BOOKKEEPING = Pattern.compile(
            "^\\s*(?:" + HISTORICAL_TIME + "\\s*)?" + PAYMENT_VERBS
                    + ".{0,40}?(?:[，,；;。]\\s*)?(?:记账|记一笔|记一下|记录一笔)",
            FLAGS
    );
    private static final Pattern[] DENIED_PATTERNS = {
            DELETE_RECORDS, EXPORT_FILE, PAYMENT_COMMAND, PAYMENT_DETAILS,
            PAYMENT_SEQUENCE, IMPLICIT_PAYMENT_BOOKKEEPING, ADDITIONAL_PAYMENT_ACTION,
            SQL_EXECUTION, FILE_ACCESS, PROMPT_INJECTION
    };

    private InputGuard() {
    }

    public static UserMessage checkText(String text) {
        if (isNullOrWhitespace(text)) {
            return new UserMessage(false, EMPTY_MESSAGE);
        }
        if (text.length() > MAX_LENGTH) {
            return new UserMessage(false, TOO_LONG_MESSAGE);
        }
        if (containsControlCharacter(text)) {
            return new UserMessage(false, CONTROL_MESSAGE);
        }
        for (Pattern pattern : DENIED_PATTERNS) {
            if (pattern == PAYMENT_DETAILS && !hasDeniedPaymentDetails(text)) {
                continue;
            }
            if (pattern == IMPLICIT_PAYMENT_BOOKKEEPING
                    && HISTORICAL_PAYMENT_PREFIX.matcher(text).find()
                    && !PAYMENT_SEQUENCE.matcher(text).find()) {
                continue;
            }
            if (pattern.matcher(text).find()) {
                return new UserMessage(false, DENIED_MESSAGE);
            }
        }
        return new UserMessage(true, text);
    }

    private static boolean hasDeniedPaymentDetails(String text) {
        boolean canExempt = !ADDITIONAL_PAYMENT_ACTION.matcher(text).find()
                && !PAYMENT_SEQUENCE.matcher(text).find();
        Matcher details = PAYMENT_DETAILS.matcher(text);
        while (details.find()) {
            if (!canExempt || !isExemptPaymentDetail(text, details)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isExemptPaymentDetail(String text, Matcher detail) {
        Matcher query = PAYMENT_AMOUNT_QUERY_FILTER.matcher(text);
        while (query.find()) {
            if (detail.start() >= query.start() && detail.end() <= query.end()) {
                return true;
            }
        }
        return startsAtPaymentVerb(HISTORICAL_PAYMENT_PREFIX, text, detail.start())
                || startsAtPaymentVerb(MANUAL_PAYMENT_ENTRY, text, detail.start());
    }

    private static boolean startsAtPaymentVerb(Pattern prefix, String text, int start) {
        Matcher matcher = prefix.matcher(text);
        return matcher.find() && matcher.start(1) == start;
    }

    private static boolean isNullOrWhitespace(String text) {
        if (text == null || text.isEmpty()) {
            return true;
        }
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            if (!Character.isWhitespace(codePoint)) {
                return false;
            }
            offset += Character.charCount(codePoint);
        }
        return true;
    }

    private static boolean containsControlCharacter(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isISOControl(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}
